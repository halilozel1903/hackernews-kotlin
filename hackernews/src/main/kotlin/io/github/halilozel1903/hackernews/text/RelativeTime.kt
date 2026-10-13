package io.github.halilozel1903.hackernews.text

/** Formats Unix times as ages, the way Hacker News shows them ("3 hours ago"). */
public object RelativeTime {

    /** How [format] writes an age. */
    public enum class Style {
        /** "just now", "1 minute ago", "5 hours ago", "2 days ago", "3 months ago", "1 year ago". */
        Full,

        /** "now", "1m", "5h", "2d", "3mo", "1y". */
        Compact,
    }

    /** The current time in Unix seconds. */
    public fun nowSeconds(): Long = System.currentTimeMillis() / 1000

    /**
     * The age of [epochSeconds] at [nowSeconds]. Times in the future count as "just now"; a time of 0 or less (the
     * API left it out) gives an empty string.
     */
    public fun format(epochSeconds: Long, nowSeconds: Long = nowSeconds(), style: Style = Style.Full): String {
        if (epochSeconds <= 0) return ""
        val seconds = (nowSeconds - epochSeconds).coerceAtLeast(0)
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24
        val (amount, word, suffix) = when {
            seconds < 60 -> return if (style == Style.Full) "just now" else "now"
            minutes < 60 -> Triple(minutes, "minute", "m")
            hours < 24 -> Triple(hours, "hour", "h")
            days < 30 -> Triple(days, "day", "d")
            days < 365 -> Triple(days / 30, "month", "mo")
            else -> Triple(days / 365, "year", "y")
        }
        return when (style) {
            Style.Full -> "$amount $word${if (amount == 1L) "" else "s"} ago"
            Style.Compact -> "$amount$suffix"
        }
    }

    /** How many seconds until the text of [format] for [epochSeconds] next changes, for refreshing a label. */
    public fun secondsUntilChange(epochSeconds: Long, nowSeconds: Long = nowSeconds()): Long {
        val age = (nowSeconds - epochSeconds).coerceAtLeast(0)
        val unit = when {
            age < 3_600 -> 60L
            age < 86_400 -> 3_600L
            else -> 86_400L
        }
        return (unit - age % unit).coerceAtLeast(1)
    }
}
