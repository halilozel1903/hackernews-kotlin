package io.github.halilozel1903.hackernews

import io.github.halilozel1903.hackernews.comments.CommentTree
import io.github.halilozel1903.hackernews.comments.CommentTreeBuilder
import io.github.halilozel1903.hackernews.comments.CommentTreeOptions
import io.github.halilozel1903.hackernews.comments.RemovedComments
import io.github.halilozel1903.hackernews.model.Item
import io.github.halilozel1903.hackernews.model.ItemDto
import io.github.halilozel1903.hackernews.model.StoryPage
import io.github.halilozel1903.hackernews.model.Updates
import io.github.halilozel1903.hackernews.model.User
import io.github.halilozel1903.hackernews.transport.HackerNewsTransport
import io.github.halilozel1903.hackernews.transport.KtorTransport
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlin.coroutines.cancellation.CancellationException

/**
 * A coroutine client for the official [Hacker News API](https://github.com/HackerNews/API).
 *
 * ```kotlin
 * val hn = HackerNewsClient()
 * val page = hn.stories(StoryList.Top, page = 0, pageSize = 30)
 * val story = page.items.first() as Item.Story
 * val comments = hn.commentTree(story, CommentTreeOptions(maxDepth = 4))
 * val author = hn.user(story.by!!)
 * ```
 *
 * The API has one url per item, so lists are loaded as ids first and the items of a page are then requested
 * concurrently, at most [HackerNewsConfig.maxConcurrency] at a time, keeping the ranked order. Items and users are
 * cached in memory for [HackerNewsConfig.cacheTtl], and identical requests running at the same time share one
 * network call.
 *
 * Every function is main-safe and throws a [HackerNewsException] on failure. Close the client when you no longer
 * need it.
 */
public class HackerNewsClient(configure: HackerNewsConfig.() -> Unit = {}) : AutoCloseable {

    private val config = HackerNewsConfig().apply(configure)

    init {
        require(config.maxConcurrency >= 1) { "maxConcurrency must be at least 1, was ${config.maxConcurrency}" }
    }

    /** The API root, always ending with a slash. */
    public val baseUrl: String = config.baseUrl.let { if (it.endsWith('/')) it else "$it/" }

    /** How many item requests run at the same time when many items load. */
    public val maxConcurrency: Int = config.maxConcurrency

    private val transport: HackerNewsTransport = config.transport ?: KtorTransport(
        httpClient = config.httpClient,
        engine = config.engine,
        userAgent = config.userAgent,
        requestTimeoutMillis = config.requestTimeoutMillis,
        retries = config.retries,
    )

    internal var clock: () -> Long = { System.currentTimeMillis() }
    private val cache: ResponseCache? = if (config.cacheSize > 0) {
        ResponseCache(config.cacheSize, config.cacheTtl.inWholeMilliseconds) { clock() }
    } else {
        null
    }
    private val inFlightLock = Mutex()
    private val inFlight = HashMap<String, CompletableDeferred<String>>()

    // region Lists

    /** The ids of [list] in ranked order: `GET /topstories`, `/newstories`, `/beststories`, `/askstories`, ... */
    public suspend fun storyIds(list: StoryList): List<Long> =
        fetch("$baseUrl${list.path}.json", ListSerializer(Long.serializer()).nullable, cached = false).orEmpty()

    /**
     * One page of [list]: loads the list's ids, then the items at `page * pageSize` until the next page,
     * concurrently. Items that no longer exist are left out, and so are deleted and dead ones unless
     * [includeRemoved].
     */
    public suspend fun stories(
        list: StoryList,
        page: Int = 0,
        pageSize: Int = DEFAULT_PAGE_SIZE,
        includeRemoved: Boolean = false,
    ): StoryPage = page(list, storyIds(list), page, pageSize, includeRemoved)

    /**
     * Every page of [list], loaded lazily as the flow is collected. The ids are loaded once, when collection starts,
     * so pages do not shift while you scroll. Stop early with `take(n)`.
     */
    public fun pages(
        list: StoryList,
        pageSize: Int = DEFAULT_PAGE_SIZE,
        includeRemoved: Boolean = false,
    ): Flow<StoryPage> = flow {
        val ids = storyIds(list)
        var index = 0
        while (index * pageSize < ids.size) {
            emit(page(list, ids, index, pageSize, includeRemoved))
            index++
        }
    }

    /** One page of [list] built from [ids] you already loaded with [storyIds]. */
    public suspend fun page(
        list: StoryList,
        ids: List<Long>,
        page: Int,
        pageSize: Int = DEFAULT_PAGE_SIZE,
        includeRemoved: Boolean = false,
    ): StoryPage {
        require(page >= 0) { "page must not be negative, was $page" }
        require(pageSize > 0) { "pageSize must be positive, was $pageSize" }
        val from = (page.toLong() * pageSize).coerceAtMost(ids.size.toLong()).toInt()
        val to = (from + pageSize).coerceAtMost(ids.size)
        val items = items(ids.subList(from, to)).filter { includeRemoved || !it.isRemoved }
        return StoryPage(list, page, pageSize, ids.size, items)
    }

    // endregion

    // region Items and users

    /** `GET /item/{id}`, or `null` when there is no such item. */
    public suspend fun item(id: Long): Item? {
        require(id > 0) { "Item ids start at 1, was $id" }
        return fetch(itemUrl(id), ItemDto.serializer().nullable, cached = true)?.toItem()
    }

    /**
     * The items of [ids], requested concurrently with at most [parallelism] requests at a time, in the order of
     * [ids]. Ids without an item are left out; deleted and dead items are kept (check [Item.isRemoved]).
     */
    public suspend fun items(ids: List<Long>, parallelism: Int = maxConcurrency): List<Item> =
        ids.mapConcurrently(parallelism) { item(it) }.filterNotNull()

    /** `GET /user/{id}`, or `null` when there is no such user. Usernames are case-sensitive. */
    public suspend fun user(id: String): User? {
        require(id.isNotBlank()) { "The username is empty" }
        return fetch("${baseUrl}user/$id.json", User.serializer().nullable, cached = true)
    }

    /**
     * The newest [limit] submissions of [user] (stories, comments, polls), loaded concurrently. Deleted and dead
     * ones are left out unless [includeRemoved].
     */
    public suspend fun submissions(user: User, limit: Int = DEFAULT_PAGE_SIZE, includeRemoved: Boolean = false): List<Item> {
        require(limit >= 0) { "limit must not be negative, was $limit" }
        return items(user.submitted.take(limit)).filter { includeRemoved || !it.isRemoved }
    }

    /** `GET /maxitem`: the largest item id so far. Walk backwards from it to read every new item. */
    public suspend fun maxItem(): Long = fetch("${baseUrl}maxitem.json", Long.serializer(), cached = false)

    /** `GET /updates`: items and profiles that changed recently. */
    public suspend fun updates(): Updates = fetch("${baseUrl}updates.json", Updates.serializer(), cached = false)

    // endregion

    // region Comments

    /**
     * Loads the comments below [parent] (a story, poll or comment) and builds a [CommentTree].
     *
     * Comments load level by level, each level concurrently with at most [maxConcurrency] requests at a time,
     * until [CommentTreeOptions.maxDepth] levels or [CommentTreeOptions.maxComments] items are loaded. Replies of
     * comments that [CommentTreeOptions] drops are not requested.
     */
    public suspend fun commentTree(parent: Item, options: CommentTreeOptions = CommentTreeOptions()): CommentTree {
        val loaded = HashMap<Long, Item?>()
        var level = parent.kids.distinct()
        var depth = 0
        var budget = options.maxComments
        while (level.isNotEmpty() && depth < options.maxDepth && budget > 0) {
            val batch = level.distinct().filter { it !in loaded && it != parent.id }.take(budget)
            budget -= batch.size
            val items = batch.mapConcurrently(maxConcurrency) { id -> id to item(id) }
            val next = ArrayList<Long>()
            for ((id, item) in items) {
                loaded[id] = item
                if (item is Item.Comment && options.policyFor(item) != RemovedComments.Drop) next += item.kids
            }
            level = next
            depth++
        }
        return CommentTreeBuilder.build(parent.id, parent.kids, loaded, options)
    }

    /** Loads item [id] and the comments below it, or returns `null` when there is no such item. */
    public suspend fun commentTree(id: Long, options: CommentTreeOptions = CommentTreeOptions()): CommentTree? =
        item(id)?.let { commentTree(it, options) }

    // endregion

    /** Removes every cached item and user. */
    public fun clearCache() {
        cache?.clear()
    }

    /** Releases the HTTP client this instance created. */
    override fun close() {
        (transport as? AutoCloseable)?.close()
    }

    private fun itemUrl(id: Long) = "${baseUrl}item/$id.json"

    private suspend fun <T> fetch(url: String, deserializer: DeserializationStrategy<T>, cached: Boolean): T {
        val body = (if (cached) cache?.get(url) else null) ?: loadShared(url, cached)
        return try {
            json.decodeFromString(deserializer, body)
        } catch (e: SerializationException) {
            cache?.remove(url)
            throw HackerNewsException.Decoding(url, e)
        } catch (e: IllegalArgumentException) {
            cache?.remove(url)
            throw HackerNewsException.Decoding(url, e)
        }
    }

    /** Joins a request for the same url that is already running, or starts one. */
    private suspend fun loadShared(url: String, cached: Boolean): String {
        var owner = false
        val deferred = inFlightLock.withLock {
            inFlight[url] ?: CompletableDeferred<String>().also {
                inFlight[url] = it
                owner = true
            }
        }
        if (!owner) {
            return try {
                deferred.await()
            } catch (e: CancellationException) {
                // The coroutine that made the request was cancelled, not necessarily this one: try again.
                currentCoroutineContext().ensureActive()
                loadShared(url, cached)
            }
        }
        val result = runCatching { request(url) }
        withContext(NonCancellable) { inFlightLock.withLock { inFlight.remove(url) } }
        result.fold(
            onSuccess = { body ->
                // "null" is the API's answer for a missing item or user; do not keep it, it may appear soon.
                if (cached && body.trim() != "null") cache?.put(url, body)
                deferred.complete(body)
            },
            onFailure = { deferred.completeExceptionally(it) },
        )
        return result.getOrThrow()
    }

    private suspend fun request(url: String): String = try {
        transport.get(url)
    } catch (e: CancellationException) {
        throw e
    } catch (e: HackerNewsException) {
        throw e
    } catch (e: Exception) {
        throw HackerNewsException.Network(url, e)
    }

    public companion object {
        /** `https://hacker-news.firebaseio.com/v0/`. */
        public const val DEFAULT_BASE_URL: String = "https://hacker-news.firebaseio.com/v0/"

        /** 30 stories, a page of the site. */
        public const val DEFAULT_PAGE_SIZE: Int = 30

        internal val json: Json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            coerceInputValues = true
        }

        /** Decodes one item JSON object as the API sends it, or `null` for the JSON literal `null`. */
        public fun decodeItem(json: String): Item? =
            this.json.decodeFromString(ItemDto.serializer().nullable, json)?.toItem()
    }
}
