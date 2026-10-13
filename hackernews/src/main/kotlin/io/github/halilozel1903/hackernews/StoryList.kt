package io.github.halilozel1903.hackernews

/** The ranked story lists of the API, each an array of item ids. */
public enum class StoryList(
    /** The path below the API root, without `.json`. */
    public val path: String,
    /** A short label, as the site uses it. */
    public val label: String,
) {
    /** `/topstories`: the front page ranking, up to 500 ids. Includes jobs. */
    Top("topstories", "Top"),

    /** `/newstories`: the newest stories, up to 500 ids. */
    New("newstories", "New"),

    /** `/beststories`: the best stories of the last days, up to 500 ids. */
    Best("beststories", "Best"),

    /** `/askstories`: the latest Ask HN posts, up to 200 ids. */
    Ask("askstories", "Ask"),

    /** `/showstories`: the latest Show HN posts, up to 200 ids. */
    Show("showstories", "Show"),

    /** `/jobstories`: the latest job ads, up to 200 ids. */
    Jobs("jobstories", "Jobs"),
    ;

    public companion object {
        /** The list whose [path] or [label] is [key], ignoring case, or `null`. */
        public fun from(key: String): StoryList? =
            entries.firstOrNull { it.path.equals(key, ignoreCase = true) || it.label.equals(key, ignoreCase = true) || it.name.equals(key, ignoreCase = true) }
    }
}
