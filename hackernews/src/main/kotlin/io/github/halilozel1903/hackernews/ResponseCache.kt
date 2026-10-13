package io.github.halilozel1903.hackernews

/** A small thread-safe LRU of response bodies by url, with a time to live. */
internal class ResponseCache(
    private val maxEntries: Int,
    private val ttlMillis: Long,
    private val nowMillis: () -> Long,
) {
    private class Entry(val body: String, val storedAt: Long)

    private val map = object : LinkedHashMap<String, Entry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?): Boolean = size > maxEntries
    }

    fun get(url: String): String? = synchronized(map) {
        val entry = map[url] ?: return null
        if (nowMillis() - entry.storedAt > ttlMillis) {
            map.remove(url)
            return null
        }
        entry.body
    }

    fun put(url: String, body: String) {
        if (maxEntries <= 0) return
        synchronized(map) { map[url] = Entry(body, nowMillis()) }
    }

    fun remove(url: String) {
        synchronized(map) { map.remove(url) }
    }

    fun clear() {
        synchronized(map) { map.clear() }
    }

    val size: Int get() = synchronized(map) { map.size }
}
