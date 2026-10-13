package io.github.halilozel1903.hackernews

import io.github.halilozel1903.hackernews.transport.HackerNewsTransport
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/** Settings for [HackerNewsClient], set in its builder block. */
public class HackerNewsConfig internal constructor() {
    /** The API root. */
    public var baseUrl: String = HackerNewsClient.DEFAULT_BASE_URL

    /**
     * The Ktor engine. `null` uses CIO. Pass `OkHttp.create()` (add `io.ktor:ktor-client-okhttp`) to share your
     * app's OkHttp stack, or a `MockEngine` in tests. The client does not close an engine you pass.
     */
    public var engine: HttpClientEngine? = null

    /** A ready Ktor client to use as is, instead of [engine]. [requestTimeoutMillis] and [retries] then do not apply. */
    public var httpClient: HttpClient? = null

    /** Replaces HTTP entirely, for example with a [io.github.halilozel1903.hackernews.transport.FixtureTransport]. */
    public var transport: HackerNewsTransport? = null

    /** Sent with every request. */
    public var userAgent: String = "hackernews-kotlin/1.0.0 (+https://github.com/halilozel1903/hackernews-kotlin)"

    /** Timeout of one request, in milliseconds. */
    public var requestTimeoutMillis: Long = 20_000

    /** Retries on network errors and 5xx responses, with exponential backoff. */
    public var retries: Int = 2

    /**
     * How many item requests may run at the same time when the client loads many items (a page of stories, a level
     * of a comment tree). The API has one url per item, so this is what makes a page fast without flooding the
     * connection. Must be at least 1.
     */
    public var maxConcurrency: Int = 8

    /** How many items and users the in-memory cache keeps; 0 turns caching off. Story lists are never cached. */
    public var cacheSize: Int = 1_000

    /** How long a cached item or user is reused. Scores and comment counts change, so keep it short. */
    public var cacheTtl: Duration = 5.minutes
}
