package io.github.halilozel1903.hackernews

import io.github.halilozel1903.hackernews.model.Item
import io.github.halilozel1903.hackernews.model.StoryKind
import io.github.halilozel1903.hackernews.model.score
import io.github.halilozel1903.hackernews.model.title
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ItemTest {
    private fun item(id: Long): Item = HackerNewsClient.decodeItem(Fixtures.read("item/$id.json")!!)!!

    @Test
    fun decodesALinkStory() {
        val story = assertIs<Item.Story>(item(8863))
        assertEquals("dhouston", story.by)
        assertEquals("My YC app: Dropbox - Throw away your USB drive", story.title)
        assertEquals(111, story.score)
        assertEquals(71, story.descendants)
        assertEquals(listOf(8952L, 9224L, 8917L), story.kids)
        assertEquals(1175714200L, story.time)
        assertEquals(StoryKind.Link, story.kind)
        assertEquals("getdropbox.com", story.domain)
        assertNull(story.body)
        assertEquals("https://news.ycombinator.com/item?id=8863", story.webUrl)
    }

    @Test
    fun decodesAnAskStoryWithHtmlText() {
        val story = assertIs<Item.Story>(item(121003))
        assertEquals(StoryKind.Ask, story.kind)
        assertNull(story.url)
        assertNull(story.domain)
        val body = story.body!!
        assertTrue(body.text.startsWith("or HN: the Next Iteration\n\nI get the impression"))
        assertTrue("I'm envisioning a spike." in body.text)
    }

    @Test
    fun decodesAJobWithAnEmptyUrl() {
        val job = assertIs<Item.Job>(item(192327))
        assertNull(job.url)
        assertEquals(6, job.score)
        assertEquals("Justin.tv is looking for a Lead Flash Engineer!", job.title)
    }

    @Test
    fun decodesPollsAndOptions() {
        val poll = assertIs<Item.Poll>(item(126809))
        assertEquals(listOf(126810L, 126811L, 126812L), poll.parts)
        assertNull(poll.text)
        val option = assertIs<Item.PollOption>(item(160705))
        assertEquals(160704L, option.poll)
        assertEquals(335, option.score)
        assertEquals(335, (option as Item).score)
        assertNull((option as Item).title)
    }

    @Test
    fun decodesComments() {
        val comment = assertIs<Item.Comment>(item(2921983))
        assertEquals(2921506L, comment.parent)
        assertEquals("norvig", comment.by)
        assertEquals(
            "Aw shucks, guys ... you make me blush with your compliments.\n\nTell you what, Ill make a deal: I'll keep writing if you keep reading. K?",
            comment.body.text,
        )
    }

    @Test
    fun deletedAndDeadItemsAreRemoved() {
        val deleted = item(3000003)
        assertTrue(deleted.deleted)
        assertTrue(deleted.isRemoved)
        assertNull(deleted.by)
        val dead = item(3000002)
        assertTrue(dead.dead)
        assertTrue(dead.isRemoved)
        val deletedComment = assertIs<Item.Comment>(item(4000012))
        assertNull(deletedComment.text)
        assertEquals("", deletedComment.body.text)
    }

    @Test
    fun unknownTypesAndNullDecode() {
        val unknown = assertIs<Item.Unknown>(HackerNewsClient.decodeItem("""{"id":5,"type":"essay","by":"pg","time":1,"extra":true}"""))
        assertEquals("essay", unknown.type)
        assertNull(HackerNewsClient.decodeItem("null"))
    }

    @Test
    fun storyKinds() {
        fun kind(title: String, url: String? = "https://example.com") = Item.Story(1, "a", 1, title, url, null, 1, 0).kind
        assertEquals(StoryKind.Show, kind("Show HN: A tiny terminal clock"))
        assertEquals(StoryKind.Launch, kind("Launch HN: Acme (YC W26) – Rockets as a service"))
        assertEquals(StoryKind.Ask, kind("Ask HN: Who is hiring?", url = null))
        assertEquals(StoryKind.Text, kind("Thoughts on parsing", url = null))
        assertEquals(StoryKind.Link, kind("Parsing is easy"))
    }

    @Test
    fun domains() {
        fun domain(url: String) = Item.Story(1, "a", 1, "t", url, null, 1, 0).domain
        assertEquals("github.com", domain("https://github.com/HackerNews/API"))
        assertEquals("example.co.uk", domain("http://www.example.co.uk:8080/path?q=1"))
        assertEquals("blog.example.com", domain("https://user@blog.example.com#top"))
        assertNull(domain("not a url"))
    }
}
