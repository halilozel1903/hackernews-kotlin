package io.github.halilozel1903.hackernews.sample

import io.github.halilozel1903.hackernews.StoryList

/**
 * Screenshot scenes, picked with the `scene` intent extra. Every scene loads live data from the Hacker News API
 * like a normal launch; [loads] is how many loads must finish before the scene is complete.
 */
enum class Scene(
    val key: String,
    val feed: StoryList,
    /** Opens the first top story that has a real discussion. */
    val opensStory: Boolean = false,
    /** Opens this user's profile. */
    val user: String? = null,
    val loads: Int,
) {
    Top("top", StoryList.Top, loads = 1),
    Story("story", StoryList.Top, opensStory = true, loads = 2),
    Ask("ask", StoryList.Ask, loads = 1),
    User("user", StoryList.Top, user = "pg", loads = 1),
    Tablet("tablet", StoryList.Top, opensStory = true, loads = 3),
    ;

    companion object {
        fun from(key: String?): Scene? = entries.firstOrNull { it.key == key }
    }
}
