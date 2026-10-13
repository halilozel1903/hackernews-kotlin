package io.github.halilozel1903.hackernews.model

import io.github.halilozel1903.hackernews.StoryList

/**
 * One page of a story list: the loaded [items] for positions `page * pageSize` until the next page, in ranked order.
 * Items that do not exist any more are left out, so [items] can be shorter than [pageSize] on a full page.
 */
public data class StoryPage(
    val list: StoryList,
    /** Zero based page index. */
    val page: Int,
    val pageSize: Int,
    /** How many ids the whole list has (up to 500 for top, new and best; 200 for Ask, Show and jobs). */
    val totalIds: Int,
    val items: List<Item>,
) {
    /** The rank of the first item of this page, starting at 1. */
    val firstRank: Int get() = page * pageSize + 1

    /** `true` when there is a page after this one. */
    val hasNext: Boolean get() = (page + 1) * pageSize < totalIds

    /** How many pages the list has. */
    val pageCount: Int get() = if (totalIds == 0) 0 else (totalIds + pageSize - 1) / pageSize
}
