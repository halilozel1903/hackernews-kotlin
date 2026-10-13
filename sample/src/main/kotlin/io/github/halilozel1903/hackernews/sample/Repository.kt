package io.github.halilozel1903.hackernews.sample

import io.github.halilozel1903.hackernews.HackerNewsClient
import io.github.halilozel1903.hackernews.StoryList
import io.github.halilozel1903.hackernews.comments.CommentTree
import io.github.halilozel1903.hackernews.comments.CommentTreeOptions
import io.github.halilozel1903.hackernews.model.Item
import io.github.halilozel1903.hackernews.model.StoryPage
import io.github.halilozel1903.hackernews.model.User
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** A story with its comments. */
data class StoryDetail(val story: Item, val comments: CommentTree)

/** A profile with its newest submissions. */
data class UserDetail(val user: User, val submissions: List<Item>)

/**
 * The sample's only data source: the live Hacker News API through [HackerNewsClient]. There is no bundled or
 * fallback data.
 *
 * Story ids are kept per list until a refresh, so pages do not shift while you scroll.
 */
class ReaderRepository(
    private val client: HackerNewsClient,
    private val commentBudget: Int,
    /** Screenshot scenes: a story must arrive with comments, or the scene fails. */
    private val strict: Boolean = false,
) {
    private val idsLock = Mutex()
    private val ids = HashMap<StoryList, List<Long>>()

    private suspend fun ids(list: StoryList, refresh: Boolean): List<Long> = idsLock.withLock {
        if (!refresh) ids[list]?.let { return@withLock it }
        client.storyIds(list).also { ids[list] = it }
    }

    /** Page [page] of [list]; page 0 with [refresh] reloads the ranking. */
    suspend fun page(list: StoryList, page: Int, refresh: Boolean = false): StoryPage =
        client.page(list, ids(list, refresh && page == 0), page, PAGE_SIZE)

    /** The first story on the front page with a real discussion. */
    suspend fun storyWithDiscussion(): Long {
        val page = page(StoryList.Top, 0)
        val story = page.items.filterIsInstance<Item.Story>().firstOrNull { it.descendants >= 15 && it.kids.size >= 3 }
            ?: page.items.filterIsInstance<Item.Story>().maxByOrNull { it.descendants }
            ?: error("The front page has no stories right now.")
        return story.id
    }

    suspend fun story(id: Long): StoryDetail {
        val item = client.item(id) ?: error("Story $id does not exist.")
        val tree = client.commentTree(item, CommentTreeOptions(maxDepth = 8, maxComments = commentBudget))
        if (strict && tree.size == 0) error("Story $id has no comments to show.")
        return StoryDetail(item, tree)
    }

    suspend fun user(name: String): UserDetail {
        val user = client.user(name) ?: error("There is no user named $name.")
        return UserDetail(user, client.submissions(user, limit = 20))
    }

    companion object {
        const val PAGE_SIZE = 30
    }
}

/** The app's repositories, created on first use. */
object Repositories {
    private var live: ReaderRepository? = null
    private var scenes: ReaderRepository? = null

    private fun client(retries: Int) = HackerNewsClient {
        engine = OkHttp.create()
        userAgent = "hackernews-kotlin-sample/1.0.0 (+https://github.com/halilozel1903/hackernews-kotlin)"
        maxConcurrency = 8
        this.retries = retries
    }

    /** Normal use. */
    @Synchronized
    fun live(): ReaderRepository =
        live ?: ReaderRepository(client(retries = 2), commentBudget = 400).also { live = it }

    /** Screenshot scenes: more retries for a busy CI network, a smaller comment budget, and no empty stories. */
    @Synchronized
    fun scenes(): ReaderRepository =
        scenes ?: ReaderRepository(client(retries = 4), commentBudget = 80, strict = true).also { scenes = it }
}
