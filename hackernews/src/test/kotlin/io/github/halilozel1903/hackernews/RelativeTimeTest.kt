package io.github.halilozel1903.hackernews

import io.github.halilozel1903.hackernews.model.Item
import io.github.halilozel1903.hackernews.text.RelativeTime
import io.github.halilozel1903.hackernews.text.RelativeTime.Style
import kotlin.test.Test
import kotlin.test.assertEquals

class RelativeTimeTest {
    private val now = 1_760_000_000L

    private fun ago(seconds: Long, style: Style = Style.Full) = RelativeTime.format(now - seconds, now, style)

    @Test
    fun fullStyle() {
        assertEquals("just now", ago(0))
        assertEquals("just now", ago(59))
        assertEquals("1 minute ago", ago(60))
        assertEquals("59 minutes ago", ago(3_599))
        assertEquals("1 hour ago", ago(3_600))
        assertEquals("23 hours ago", ago(86_399))
        assertEquals("1 day ago", ago(86_400))
        assertEquals("29 days ago", ago(29 * 86_400L))
        assertEquals("1 month ago", ago(30 * 86_400L))
        assertEquals("12 months ago", ago(364 * 86_400L))
        assertEquals("1 year ago", ago(365 * 86_400L))
        assertEquals("19 years ago", ago(19 * 365 * 86_400L + 5))
        assertEquals("just now", ago(-500))
        assertEquals("", RelativeTime.format(0, now))
    }

    @Test
    fun compactStyle() {
        assertEquals("now", ago(5, Style.Compact))
        assertEquals("5m", ago(300, Style.Compact))
        assertEquals("3h", ago(3 * 3_600L, Style.Compact))
        assertEquals("2d", ago(2 * 86_400L, Style.Compact))
        assertEquals("4mo", ago(125 * 86_400L, Style.Compact))
        assertEquals("3y", ago(3 * 366 * 86_400L, Style.Compact))
    }

    @Test
    fun nextChange() {
        assertEquals(60, RelativeTime.secondsUntilChange(now, now))
        assertEquals(15, RelativeTime.secondsUntilChange(now - 45, now))
        assertEquals(3_600 - 600, RelativeTime.secondsUntilChange(now - 3_600 - 600, now))
        assertEquals(86_400 - 10, RelativeTime.secondsUntilChange(now - 2 * 86_400 - 10, now))
    }

    @Test
    fun itemAge() {
        val story = Item.Story(1, "a", now - 7_200, "t", null, null, 1, 0)
        assertEquals("2 hours ago", story.age(now))
    }
}
