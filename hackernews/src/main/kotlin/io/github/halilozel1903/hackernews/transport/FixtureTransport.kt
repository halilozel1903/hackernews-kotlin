package io.github.halilozel1903.hackernews.transport

import io.github.halilozel1903.hackernews.HackerNewsClient
import io.github.halilozel1903.hackernews.HackerNewsException

/**
 * Serves API responses from local JSON files, for unit tests and Compose previews. [read] gets a relative path and
 * returns the file's text, or `null` when there is no such file. Urls map to paths like this:
 *
 * | Url | Path |
 * | --- | --- |
 * | `.../v0/topstories.json` | `topstories.json` |
 * | `.../v0/item/8863.json` | `item/8863.json` |
 * | `.../v0/user/pg.json` | `user/pg.json` |
 *
 * A missing item or user file answers `null`, like the real API does for ids that do not exist; any other missing
 * file is a [io.github.halilozel1903.hackernews.HackerNewsException.NotFound].
 *
 * ```kotlin
 * val client = HackerNewsClient {
 *     transport = FixtureTransport { path -> javaClass.getResource("/fixtures/$path")?.readText() }
 * }
 * ```
 */
public class FixtureTransport(
    private val baseUrl: String = HackerNewsClient.DEFAULT_BASE_URL,
    private val read: (path: String) -> String?,
) : HackerNewsTransport {

    private val lock = Any()
    private val recorded = mutableListOf<String>()

    /** Every url requested so far, in order. */
    public val requests: List<String> get() = synchronized(lock) { recorded.toList() }

    override suspend fun get(url: String): String {
        synchronized(lock) { recorded += url }
        val path = pathFor(url, baseUrl) ?: throw HackerNewsException.NotFound(url)
        read(path)?.let { return it }
        if (path.startsWith("item/") || path.startsWith("user/")) return "null"
        throw HackerNewsException.NotFound(url)
    }

    public companion object {
        /** The fixture path for [url], or `null` when the url is not below the API root. */
        public fun pathFor(url: String, baseUrl: String = HackerNewsClient.DEFAULT_BASE_URL): String? {
            val base = if (baseUrl.endsWith('/')) baseUrl else "$baseUrl/"
            val relative = when {
                url.startsWith(base) -> url.removePrefix(base)
                "/v0/" in url -> url.substringAfter("/v0/")
                else -> return null
            }
            val path = relative.substringBefore('?').trim('/')
            return path.takeIf { it.isNotEmpty() && it.endsWith(".json") }
        }
    }
}
