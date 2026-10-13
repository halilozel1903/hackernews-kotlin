package io.github.halilozel1903.hackernews.model

import io.github.halilozel1903.hackernews.text.HnHtml
import io.github.halilozel1903.hackernews.text.HnText
import io.github.halilozel1903.hackernews.text.RelativeTime

/**
 * One Hacker News item: a story, comment, job, poll or poll option. Everything on Hacker News is an item with a
 * numeric [id]; the API's `type` field picks the subclass.
 *
 * Deleted and dead items are still items. A [deleted] item usually has no [by] and no text; a [dead] item was
 * flagged or killed and is hidden on the site unless "showdead" is on. Check them before showing an item.
 */
public sealed interface Item {
    /** The item's unique id. */
    public val id: Long

    /** The author's username, or `null` for deleted items. */
    public val by: String?

    /** Creation time in Unix seconds, or 0 when the API left it out. */
    public val time: Long

    /** The ids of the item's direct replies, in ranked display order. */
    public val kids: List<Long>

    /** `true` when the item was deleted. */
    public val deleted: Boolean

    /** `true` when the item is dead (flagged or killed). */
    public val dead: Boolean

    /** `true` when the item is [deleted] or [dead], so it would not show on the site by default. */
    public val isRemoved: Boolean get() = deleted || dead

    /** The item's page on the site, `https://news.ycombinator.com/item?id=<id>`. */
    public val webUrl: String get() = "https://news.ycombinator.com/item?id=$id"

    /** "3 hours ago" style age of the item at [nowSeconds]. */
    public fun age(nowSeconds: Long = RelativeTime.nowSeconds()): String = RelativeTime.format(time, nowSeconds)

    /**
     * A story: a link ([url] set) or a text post such as Ask HN ([text] set). Launch HN and Show HN posts are stories
     * too; see [kind].
     */
    public data class Story(
        override val id: Long,
        override val by: String?,
        override val time: Long,
        /** The title, as HTML-free text. */
        val title: String,
        /** The linked url, or `null` for text posts. */
        val url: String?,
        /** The body of a text post as Hacker News HTML; parse it with [HnHtml]. */
        val text: String?,
        /** Points. */
        val score: Int,
        /** Total comment count, including replies to replies. */
        val descendants: Int,
        override val kids: List<Long> = emptyList(),
        override val deleted: Boolean = false,
        override val dead: Boolean = false,
    ) : Item {
        /** What kind of story this is, from its title and url. */
        val kind: StoryKind get() = StoryKind.of(title, url)

        /** The url's host without `www.`, such as "github.com", or `null` for text posts. */
        val domain: String? get() = url?.let { domainOf(it) }

        /** [text] parsed into plain text and spans, or `null` for link posts. */
        val body: HnText? get() = text?.let { HnHtml.parse(it) }
    }

    /** A comment on a story, poll or another comment ([parent]). */
    public data class Comment(
        override val id: Long,
        override val by: String?,
        override val time: Long,
        /** The comment as Hacker News HTML, or `null` when deleted. Parse it with [HnHtml]. */
        val text: String?,
        /** The item this comment replies to: a story, poll or comment. */
        val parent: Long,
        override val kids: List<Long> = emptyList(),
        override val deleted: Boolean = false,
        override val dead: Boolean = false,
    ) : Item {
        /** [text] parsed into plain text and spans; empty when there is no text. */
        val body: HnText get() = HnHtml.parse(text.orEmpty())
    }

    /** A job ad from a YC company. Jobs have no comments. */
    public data class Job(
        override val id: Long,
        override val by: String?,
        override val time: Long,
        val title: String,
        val url: String?,
        val text: String?,
        val score: Int,
        override val kids: List<Long> = emptyList(),
        override val deleted: Boolean = false,
        override val dead: Boolean = false,
    ) : Item {
        /** The url's host without `www.`, or `null` when the job has no link. */
        val domain: String? get() = url?.let { domainOf(it) }
    }

    /** A poll. Its options are separate [PollOption] items listed in [parts]. */
    public data class Poll(
        override val id: Long,
        override val by: String?,
        override val time: Long,
        val title: String,
        val text: String?,
        val score: Int,
        val descendants: Int,
        /** The ids of the poll's options, in display order. */
        val parts: List<Long>,
        override val kids: List<Long> = emptyList(),
        override val deleted: Boolean = false,
        override val dead: Boolean = false,
    ) : Item

    /** One option of a [Poll]. Its [score] is its vote count. */
    public data class PollOption(
        override val id: Long,
        override val by: String?,
        override val time: Long,
        val text: String?,
        /** The poll this option belongs to. */
        val poll: Long,
        val score: Int,
        override val kids: List<Long> = emptyList(),
        override val deleted: Boolean = false,
        override val dead: Boolean = false,
    ) : Item

    /** An item whose `type` this version does not know (or a deleted item without one). */
    public data class Unknown(
        override val id: Long,
        /** The raw `type`, or `null` when missing. */
        val type: String?,
        override val by: String?,
        override val time: Long,
        override val kids: List<Long> = emptyList(),
        override val deleted: Boolean = false,
        override val dead: Boolean = false,
    ) : Item
}

/** The kind of a [Item.Story], from Hacker News title conventions. */
public enum class StoryKind {
    /** A link to another site. */
    Link,

    /** "Ask HN:" posts: questions to the community, usually text only. */
    Ask,

    /** "Show HN:" posts: something the author made. */
    Show,

    /** "Launch HN:" posts by YC companies. */
    Launch,

    /** Any other text post without a link. */
    Text,
    ;

    internal companion object {
        fun of(title: String, url: String?): StoryKind {
            val t = title.trimStart()
            return when {
                t.startsWith("Ask HN", ignoreCase = true) -> Ask
                t.startsWith("Show HN", ignoreCase = true) -> Show
                t.startsWith("Launch HN", ignoreCase = true) -> Launch
                url.isNullOrBlank() -> Text
                else -> Link
            }
        }
    }
}

/** The title of a story, job or poll, or `null` for comments and poll options. */
public val Item.title: String?
    get() = when (this) {
        is Item.Story -> title
        is Item.Job -> title
        is Item.Poll -> title
        else -> null
    }

/** The points of a story, job, poll or poll option, or `null` for comments. */
public val Item.score: Int?
    get() = when (this) {
        is Item.Story -> score
        is Item.Job -> score
        is Item.Poll -> score
        is Item.PollOption -> score
        else -> null
    }

/** The host of [url] without `www.`, or `null` when it has none. */
internal fun domainOf(url: String): String? {
    val afterScheme = url.substringAfter("://", missingDelimiterValue = url)
    val host = afterScheme.substringBefore('/').substringBefore('?').substringBefore('#')
        .substringAfterLast('@').substringBefore(':').lowercase()
    if (host.isEmpty() || '.' !in host) return null
    return host.removePrefix("www.")
}
