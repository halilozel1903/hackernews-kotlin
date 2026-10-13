package io.github.halilozel1903.hackernews.comments

import io.github.halilozel1903.hackernews.model.Item

/** What to do with a deleted or dead comment in a [CommentTree]. */
public enum class RemovedComments {
    /** Leave the comment and all of its replies out. */
    Drop,

    /** Keep the comment as a placeholder when it has replies, so the replies keep their place; otherwise drop it. */
    KeepIfReplies,

    /** Always keep the comment. */
    Keep,
}

/**
 * How [io.github.halilozel1903.hackernews.HackerNewsClient.commentTree] loads and builds a tree.
 *
 * @property maxDepth how many levels to load: 1 loads only top-level comments, 2 adds their direct replies, and so on.
 * @property maxComments the most comment items to request in total. Levels load breadth first in ranked order, so a
 * budget keeps the best placed comments of each level.
 * @property deleted what to do with deleted comments.
 * @property dead what to do with dead (flagged or killed) comments.
 */
public data class CommentTreeOptions(
    val maxDepth: Int = Int.MAX_VALUE,
    val maxComments: Int = Int.MAX_VALUE,
    val deleted: RemovedComments = RemovedComments.KeepIfReplies,
    val dead: RemovedComments = RemovedComments.KeepIfReplies,
) {
    init {
        require(maxDepth >= 1) { "maxDepth must be at least 1, was $maxDepth" }
        require(maxComments >= 0) { "maxComments must not be negative, was $maxComments" }
    }

    internal fun policyFor(comment: Item.Comment): RemovedComments? = when {
        comment.deleted -> deleted
        comment.dead -> dead
        else -> null
    }
}

/**
 * One comment with its loaded replies.
 *
 * @property depth 0 for top-level comments, 1 for their replies, and so on.
 * @property unloadedReplies direct replies that exist but were not loaded, because of
 * [CommentTreeOptions.maxDepth] or [CommentTreeOptions.maxComments].
 */
public data class CommentNode(
    val comment: Item.Comment,
    val depth: Int,
    val replies: List<CommentNode>,
    val unloadedReplies: Int = 0,
) {
    /** `true` for a deleted or dead comment kept as a placeholder. */
    val isRemoved: Boolean get() = comment.isRemoved

    /** This comment and every loaded reply below it. */
    val size: Int = 1 + replies.sumOf { it.size }

    /** This node and every node below it, depth first in display order. */
    public fun walk(): Sequence<CommentNode> = sequence {
        yield(this@CommentNode)
        for (reply in replies) yieldAll(reply.walk())
    }
}

/**
 * The comments of a story, poll or comment ([parentId]) as a tree in ranked order.
 *
 * @property unloadedReplies top-level comments that exist but were not loaded.
 */
public data class CommentTree(
    val parentId: Long,
    val comments: List<CommentNode>,
    val unloadedReplies: Int = 0,
) {
    /** How many comments the tree holds, placeholders included. */
    val size: Int = comments.sumOf { it.size }

    /** The deepest [CommentNode.depth] plus one, or 0 for an empty tree. */
    val depth: Int get() = walk().maxOfOrNull { it.depth + 1 } ?: 0

    /** `true` when every reply was loaded. */
    val isComplete: Boolean get() = unloadedReplies == 0 && walk().all { it.unloadedReplies == 0 }

    /** Every node, depth first in display order. */
    public fun walk(): Sequence<CommentNode> = comments.asSequence().flatMap { it.walk() }

    /** The node of comment [id], or `null`. */
    public fun find(id: Long): CommentNode? = walk().firstOrNull { it.comment.id == id }

    /**
     * The tree as rows for a lazy list, depth first. The replies of every comment in [collapsed] are left out and
     * its row reports how many it hides.
     */
    public fun flatten(collapsed: Set<Long> = emptySet()): List<CommentRow> = buildList<CommentRow> {
        fun add(node: CommentNode) {
            val isCollapsed = node.comment.id in collapsed
            add(CommentRow(node, isCollapsed))
            if (!isCollapsed) node.replies.forEach { add(it) }
        }
        comments.forEach { add(it) }
    }
}

/** One row of [CommentTree.flatten]. */
public data class CommentRow(val node: CommentNode, val collapsed: Boolean) {
    val comment: Item.Comment get() = node.comment
    val depth: Int get() = node.depth

    /** How many loaded replies the collapsed row hides; 0 when expanded. */
    val hiddenReplies: Int get() = if (collapsed) node.size - 1 else 0
}

/** Builds [CommentTree]s from items that are already loaded. */
public object CommentTreeBuilder {

    /**
     * Builds the tree below [parentId], whose direct replies are [topLevelIds].
     *
     * [items] maps ids to loaded items: an id that is missing from the map was not loaded (it counts as an unloaded
     * reply), an id mapped to `null` does not exist any more and is left out. Items that are not comments are left
     * out. Deleted and dead comments follow [options].
     */
    public fun build(
        parentId: Long,
        topLevelIds: List<Long>,
        items: Map<Long, Item?>,
        options: CommentTreeOptions = CommentTreeOptions(),
    ): CommentTree {
        val seen = HashSet<Long>()
        seen += parentId

        fun node(id: Long, depth: Int): CommentNode? {
            if (!seen.add(id)) return null
            val comment = items[id] as? Item.Comment ?: return null
            val policy = options.policyFor(comment)
            if (policy == RemovedComments.Drop) return null
            var unloaded = 0
            val replies = if (depth + 1 >= options.maxDepth) {
                unloaded = comment.kids.size
                emptyList()
            } else {
                comment.kids.mapNotNull { kid ->
                    if (kid !in items) {
                        unloaded++
                        null
                    } else {
                        node(kid, depth + 1)
                    }
                }
            }
            if (policy == RemovedComments.KeepIfReplies && replies.isEmpty() && unloaded == 0) return null
            return CommentNode(comment, depth, replies, unloaded)
        }

        var unloadedTop = 0
        val roots = topLevelIds.mapNotNull { id ->
            if (id !in items) {
                unloadedTop++
                null
            } else {
                node(id, 0)
            }
        }
        return CommentTree(parentId, roots, unloadedTop)
    }
}
