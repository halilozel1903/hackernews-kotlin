package io.github.halilozel1903.hackernews

import io.github.halilozel1903.hackernews.comments.CommentTree
import io.github.halilozel1903.hackernews.comments.CommentTreeBuilder
import io.github.halilozel1903.hackernews.comments.CommentTreeOptions
import io.github.halilozel1903.hackernews.comments.RemovedComments
import io.github.halilozel1903.hackernews.model.Item
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The fixture thread below story 4000000:
 *
 * ```
 * 4000001 alice
 *   4000011 bob
 *     4000111 alice
 *   4000012 deleted, no replies
 * 4000002 deleted, reply 4000021 carol
 * 4000003 dead, reply 4000031 dave
 * 4000004 dead, no replies
 * 4000005 missing
 * 4000006 erin
 * ```
 */
class CommentTreeTest {

    private fun CommentTree.shape(): List<String> = walk().map { "  ".repeat(it.depth) + it.comment.id }.toList()

    @Test
    fun buildsTheTreeWithDefaults() = runTest {
        val client = Fixtures.client()
        val tree = client.commentTree(4000000)!!
        assertEquals(
            listOf(
                "4000001",
                "  4000011",
                "    4000111",
                "4000002",
                "  4000021",
                "4000003",
                "  4000031",
                "4000006",
            ),
            tree.shape(),
        )
        assertEquals(8, tree.size)
        assertEquals(3, tree.depth)
        assertTrue(tree.isComplete)
        assertTrue(tree.find(4000002)!!.isRemoved)
        assertEquals(1, tree.find(4000011)!!.depth)
        assertEquals("bob", tree.find(4000011)!!.comment.by)
    }

    @Test
    fun keepAndDropPolicies() = runTest {
        val client = Fixtures.client()
        val keepAll = client.commentTree(4000000, CommentTreeOptions(deleted = RemovedComments.Keep, dead = RemovedComments.Keep))!!
        assertEquals(
            listOf("4000001", "  4000011", "    4000111", "  4000012", "4000002", "  4000021", "4000003", "  4000031", "4000004", "4000006"),
            keepAll.shape(),
        )
        val dropAll = client.commentTree(4000000, CommentTreeOptions(deleted = RemovedComments.Drop, dead = RemovedComments.Drop))!!
        assertEquals(listOf("4000001", "  4000011", "    4000111", "4000006"), dropAll.shape())
    }

    @Test
    fun droppedCommentsDoNotLoadTheirReplies() = runTest {
        val transport = Fixtures.transport()
        val client = Fixtures.client(transport)
        client.commentTree(4000000, CommentTreeOptions(deleted = RemovedComments.Drop, dead = RemovedComments.Drop))
        assertFalse(transport.requests.any { it.endsWith("4000021.json") || it.endsWith("4000031.json") })
        assertTrue(transport.requests.any { it.endsWith("4000111.json") })
    }

    @Test
    fun maxDepthLimitsLevelsAndCountsUnloadedReplies() = runTest {
        val transport = Fixtures.transport()
        val client = Fixtures.client(transport)
        val tree = client.commentTree(4000000, CommentTreeOptions(maxDepth = 1))!!
        // Top level only; deleted 4000002 and dead 4000003 stay because they have (unloaded) replies.
        assertEquals(listOf("4000001", "4000002", "4000003", "4000006"), tree.shape())
        assertEquals(2, tree.find(4000001)!!.unloadedReplies)
        assertFalse(tree.isComplete)
        assertFalse(transport.requests.any { it.endsWith("4000011.json") })

        val two = client.commentTree(4000000, CommentTreeOptions(maxDepth = 2))!!
        assertEquals(1, two.find(4000011)!!.unloadedReplies)
        assertEquals(0, two.find(4000011)!!.replies.size)
    }

    @Test
    fun maxCommentsIsABreadthFirstBudget() = runTest {
        val transport = Fixtures.transport()
        val client = Fixtures.client(transport)
        val tree = client.commentTree(4000000, CommentTreeOptions(maxComments = 4))!!
        // The first four top-level ids are requested; 4000005 and 4000006 are not.
        assertEquals(5, transport.requests.size) // the story and four comments
        assertEquals(listOf("4000001", "4000002", "4000003"), tree.shape())
        assertEquals(2, tree.unloadedReplies)
        assertFailsWith<IllegalArgumentException> { CommentTreeOptions(maxDepth = 0) }
    }

    @Test
    fun flattenSkipsRepliesOfCollapsedComments() = runTest {
        val tree = Fixtures.client().commentTree(4000000)!!
        val rows = tree.flatten(collapsed = setOf(4000001L))
        assertEquals(listOf(4000001L, 4000002L, 4000021L, 4000003L, 4000031L, 4000006L), rows.map { it.comment.id })
        assertTrue(rows.first().collapsed)
        assertEquals(2, rows.first().hiddenReplies)
        assertEquals(0, rows[1].hiddenReplies)
        assertEquals(listOf(0, 0, 1, 0, 1, 0), rows.map { it.depth })
        assertEquals(8, tree.flatten().size)
    }

    @Test
    fun builderIgnoresCyclesAndNonComments() {
        val a = Item.Comment(id = 2, by = "a", time = 1, text = "a", parent = 1, kids = listOf(3))
        val b = Item.Comment(id = 3, by = "b", time = 1, text = "b", parent = 2, kids = listOf(2, 4))
        val story = Item.Story(4, "c", 1, "Not a comment", null, null, 1, 0)
        val tree = CommentTreeBuilder.build(1, listOf(2, 2), mapOf(2L to a, 3L to b, 4L to story))
        assertEquals(listOf(2L, 3L), tree.walk().map { it.comment.id }.toList())
    }

    @Test
    fun loadedCommentsKeepTheirText() = runTest {
        val tree = Fixtures.client().commentTree(4000000)!!
        val first = assertNotNull(tree.find(4000001)).comment.body
        assertEquals("First! https://example.com/a?b=1&c=2", first.text)
        assertEquals("https://example.com/a?b=1&c=2", first.links.single().url)
    }
}
