package io.github.halilozel1903.hackernews

import io.github.halilozel1903.hackernews.transport.FixtureTransport

/** Test fixtures under src/test/resources/fixtures, served like the API serves them. */
object Fixtures {
    fun read(path: String): String? = Fixtures::class.java.getResource("/fixtures/$path")?.readText()

    fun transport(): FixtureTransport = FixtureTransport { path -> read(path) }

    fun client(transport: FixtureTransport = transport(), configure: HackerNewsConfig.() -> Unit = {}): HackerNewsClient =
        HackerNewsClient {
            this.transport = transport
            configure()
        }
}
