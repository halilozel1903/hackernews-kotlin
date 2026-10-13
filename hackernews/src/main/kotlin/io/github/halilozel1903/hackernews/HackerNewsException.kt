package io.github.halilozel1903.hackernews

/** Every failure of [HackerNewsClient]. Catch the subclasses you care about, or all of them. */
public sealed class HackerNewsException(
    /** The requested url. */
    public val url: String,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {

    /** The API answered 404. Missing items and users are not errors: the API answers `null` and the client returns `null`. */
    public class NotFound(url: String) : HackerNewsException(url, "Not found: $url")

    /** The API answered with another unsuccessful [status] (401, 429, 5xx, ...). */
    public class Http(url: String, public val status: Int) : HackerNewsException(url, "HTTP $status for $url")

    /** The request failed before a response arrived (offline, timeout, DNS). */
    public class Network(url: String, cause: Throwable) :
        HackerNewsException(url, "Request failed for $url: ${cause.message ?: cause::class.simpleName}", cause)

    /** The response was not the JSON this library expects. */
    public class Decoding(url: String, cause: Throwable) :
        HackerNewsException(url, "Could not decode the response of $url: ${cause.message}", cause)
}
