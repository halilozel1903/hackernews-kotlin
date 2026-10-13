package io.github.halilozel1903.hackernews

import io.github.halilozel1903.hackernews.model.Item
import io.github.halilozel1903.hackernews.transport.FixtureTransport
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class HackerNewsClientTest {

    @Test
    fun loadsStoryIds() = runTest {
        val transport = Fixtures.transport()
        val client = Fixtures.client(transport)
        assertEquals(listOf(8863L, 3000001L, 3000002L, 121003L, 3000003L, 9999999L, 192327L, 126809L), client.storyIds(StoryList.Top))
        assertEquals(listOf("https://hacker-news.firebaseio.com/v0/topstories.json"), transport.requests)
    }

    @Test
    fun pageKeepsRankedOrderAndDropsMissingDeletedAndDead() = runTest {
        val client = Fixtures.client()
        val page = client.stories(StoryList.Top, page = 0, pageSize = 8)
        assertEquals(listOf(8863L, 3000001L, 121003L, 192327L, 126809L), page.items.map { it.id })
        assertEquals(8, page.totalIds)
        assertEquals(false, page.hasNext)
        assertEquals(1, page.pageCount)

        val withRemoved = client.stories(StoryList.Top, pageSize = 8, includeRemoved = true)
        assertEquals(listOf(8863L, 3000001L, 3000002L, 121003L, 3000003L, 192327L, 126809L), withRemoved.items.map { it.id })
    }

    @Test
    fun pagesSplitTheList() = runTest {
        val transport = Fixtures.transport()
        val client = Fixtures.client(transport)
        val pages = client.pages(StoryList.Top, pageSize = 3).toList()
        assertEquals(listOf(0, 1, 2), pages.map { it.page })
        assertEquals(listOf(1, 4, 7), pages.map { it.firstRank })
        assertEquals(listOf(true, true, false), pages.map { it.hasNext })
        assertEquals(listOf(8863L, 3000001L), pages[0].items.map { it.id })
        assertEquals(listOf(121003L), pages[1].items.map { it.id })
        assertEquals(listOf(192327L, 126809L), pages[2].items.map { it.id })
        // The id list is loaded once for all pages.
        assertEquals(1, transport.requests.count { it.endsWith("topstories.json") })
    }

    @Test
    fun pageBeyondTheEndIsEmpty() = runTest {
        val page = Fixtures.client().stories(StoryList.Ask, page = 5, pageSize = 30)
        assertTrue(page.items.isEmpty())
        assertEquals(1, page.totalIds)
    }

    @Test
    fun missingItemsAndUsersAreNull() = runTest {
        val client = Fixtures.client()
        assertNull(client.item(9999999))
        assertNull(client.user("nobody-here"))
        assertFailsWith<IllegalArgumentException> { client.item(0) }
    }

    @Test
    fun loadsUsersAndSubmissions() = runTest {
        val client = Fixtures.client()
        val user = client.user("jl")!!
        assertEquals(2937, user.karma)
        assertEquals(1173923446L, user.created)
        assertEquals("This is a test", user.aboutText?.text)
        assertEquals(5, user.submitted.size)
        assertEquals("https://news.ycombinator.com/user?id=jl", user.webUrl)

        val pg = client.user("pg")!!
        val submissions = client.submissions(pg, limit = 4)
        // 9999998 does not exist and is left out; order follows `submitted`.
        assertEquals(listOf(126809L, 2921983L, 8863L), submissions.map { it.id })
        assertEquals(listOf(126809L), client.submissions(pg, limit = 1).map { it.id })
    }

    @Test
    fun loadsMaxItemAndUpdates() = runTest {
        val client = Fixtures.client()
        assertEquals(41234567L, client.maxItem())
        val updates = client.updates()
        assertEquals(listOf(8423305L, 8420805L, 8423379L), updates.items)
        assertEquals(listOf("thefox", "mdda", "plinkplonk"), updates.profiles)
    }

    @Test
    fun cachesItemsButNotLists() = runTest {
        val transport = Fixtures.transport()
        val client = Fixtures.client(transport)
        client.item(8863)
        client.item(8863)
        client.storyIds(StoryList.Show)
        client.storyIds(StoryList.Show)
        client.item(9999999)
        client.item(9999999)
        assertEquals(1, transport.requests.count { it.endsWith("item/8863.json") })
        assertEquals(2, transport.requests.count { it.endsWith("showstories.json") })
        // A missing item may appear soon, so "null" is not cached.
        assertEquals(2, transport.requests.count { it.endsWith("item/9999999.json") })
    }

    @Test
    fun cacheEntriesExpire() = runTest {
        val transport = Fixtures.transport()
        val client = Fixtures.client(transport) { cacheTtl = 5.minutes }
        var now = 1_000_000L
        client.clock = { now }
        client.item(8863)
        now += 4 * 60_000
        client.item(8863)
        assertEquals(1, transport.requests.size)
        now += 2 * 60_000
        client.item(8863)
        assertEquals(2, transport.requests.size)
        client.clearCache()
        client.item(8863)
        assertEquals(3, transport.requests.size)
    }

    @Test
    fun cachingCanBeTurnedOff() = runTest {
        val transport = Fixtures.transport()
        val client = Fixtures.client(transport) { cacheSize = 0 }
        client.item(8863)
        client.item(8863)
        assertEquals(2, transport.requests.size)
    }

    @Test
    fun identicalRequestsShareOneCall() = runTest {
        var calls = 0
        val client = HackerNewsClient {
            transport = io.github.halilozel1903.hackernews.transport.HackerNewsTransport { url ->
                calls++
                delay(100)
                Fixtures.read(FixtureTransport.pathFor(url)!!)!!
            }
        }
        val results = List(5) { async { client.item(8863) } }.awaitAll()
        assertEquals(1, calls)
        assertTrue(results.all { it is Item.Story })
    }

    @Test
    fun itemsRunWithBoundedParallelismInOrder() = runTest {
        var running = 0
        var peak = 0
        val client = HackerNewsClient {
            maxConcurrency = 3
            cacheSize = 0
            transport = io.github.halilozel1903.hackernews.transport.HackerNewsTransport { url ->
                running++
                peak = maxOf(peak, running)
                val id = url.substringAfterLast('/').removeSuffix(".json").toLong()
                // Later ids answer faster, so the order must come from the ids, not from completion.
                delay(1_000 - id * 10)
                running--
                """{"id":$id,"type":"story","title":"Story $id","by":"user$id","time":1700000000,"score":$id}"""
            }
        }
        val ids = (1L..20L).toList()
        val items = client.items(ids)
        assertEquals(ids, items.map { it.id })
        assertEquals(3, peak)

        peak = 0
        client.items(ids, parallelism = 7)
        assertEquals(7, peak)
        assertFailsWith<IllegalArgumentException> { client.items(ids, parallelism = 0) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun parallelismAffectsElapsedTime() = runTest {
        val client = HackerNewsClient {
            maxConcurrency = 4
            transport = io.github.halilozel1903.hackernews.transport.HackerNewsTransport { url ->
                delay(500)
                val id = url.substringAfterLast('/').removeSuffix(".json")
                """{"id":$id,"type":"comment","parent":1,"text":"hi","time":1}"""
            }
        }
        val start = testScheduler.currentTime
        client.items((1L..8L).toList())
        assertEquals(1_000, testScheduler.currentTime - start)
    }

    @Test
    fun httpErrorsAreTyped() = runTest {
        val engine = MockEngine { request ->
            when {
                request.url.encodedPath.endsWith("topstories.json") -> respondError(HttpStatusCode.ServiceUnavailable)
                request.url.encodedPath.endsWith("newstories.json") -> respondError(HttpStatusCode.NotFound)
                request.url.encodedPath.endsWith("maxitem.json") ->
                    respond("<html>oops</html>", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "text/html"))
                request.url.encodedPath.endsWith("item/1.json") -> throw java.io.IOException("connection reset")
                else -> respond("null", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }
        val client = HackerNewsClient {
            this.engine = engine
            retries = 0
        }
        val http = assertFailsWith<HackerNewsException.Http> { client.storyIds(StoryList.Top) }
        assertEquals(503, http.status)
        assertFailsWith<HackerNewsException.NotFound> { client.storyIds(StoryList.New) }
        assertFailsWith<HackerNewsException.Decoding> { client.maxItem() }
        assertIs<HackerNewsException.Network>(assertFailsWith<HackerNewsException> { client.item(1) })
        assertNull(client.item(2))
        assertEquals(emptyList<Long>(), client.storyIds(StoryList.Best))
    }

    @Test
    fun sendsRequestsToTheApiRoot() = runTest {
        val urls = mutableListOf<String>()
        val engine = MockEngine { request ->
            urls += request.url.toString()
            respond("[1,2,3]", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val client = HackerNewsClient { this.engine = engine }
        StoryList.entries.forEach { client.storyIds(it) }
        assertEquals(
            listOf("topstories", "newstories", "beststories", "askstories", "showstories", "jobstories")
                .map { "https://hacker-news.firebaseio.com/v0/$it.json" },
            urls,
        )
        assertEquals(StoryList.Ask, StoryList.from("ask"))
        assertEquals(StoryList.Jobs, StoryList.from("jobstories"))
        assertNull(StoryList.from("past"))
    }
}
