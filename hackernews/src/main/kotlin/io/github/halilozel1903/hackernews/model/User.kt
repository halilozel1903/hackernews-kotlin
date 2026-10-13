package io.github.halilozel1903.hackernews.model

import io.github.halilozel1903.hackernews.text.HnHtml
import io.github.halilozel1903.hackernews.text.HnText
import io.github.halilozel1903.hackernews.text.RelativeTime
import kotlinx.serialization.Serializable

/** A Hacker News user. Only users with public activity (comments or story submissions) are in the API. */
@Serializable
public data class User(
    /** The case-sensitive username. */
    val id: String,
    /** Account creation time in Unix seconds. */
    val created: Long = 0,
    /** The user's karma. */
    val karma: Int = 0,
    /** The self-description as Hacker News HTML, or `null` when empty. Parse it with [HnHtml]. */
    val about: String? = null,
    /** Ids of the user's stories, polls and comments, newest first. */
    val submitted: List<Long> = emptyList(),
) {
    /** [about] parsed into plain text and spans, or `null` when empty. */
    val aboutText: HnText? get() = about?.takeIf { it.isNotBlank() }?.let { HnHtml.parse(it) }

    /** The profile page on the site. */
    val webUrl: String get() = "https://news.ycombinator.com/user?id=$id"

    /** "15 years ago" style account age at [nowSeconds]. */
    public fun age(nowSeconds: Long = RelativeTime.nowSeconds()): String = RelativeTime.format(created, nowSeconds)
}
