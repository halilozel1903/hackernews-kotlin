package io.github.halilozel1903.hackernews.transport

/**
 * Fetches the body of a Hacker News API url. The client uses a Ktor based transport by default; supply your own
 * through `HackerNewsConfig.transport` to serve responses from files ([FixtureTransport]), a database or another
 * HTTP stack.
 *
 * Return the text `null` for missing items and users, as the API does. Throw
 * [io.github.halilozel1903.hackernews.HackerNewsException.NotFound] for missing urls; other exceptions are reported as
 * [io.github.halilozel1903.hackernews.HackerNewsException.Network].
 */
public fun interface HackerNewsTransport {
    public suspend fun get(url: String): String
}
