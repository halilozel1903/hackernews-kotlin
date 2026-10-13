package io.github.halilozel1903.hackernews.transport

import io.github.halilozel1903.hackernews.HackerNewsException
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlin.coroutines.cancellation.CancellationException

/** The default transport: GET requests with Ktor. Closes the client only when it created it. */
internal class KtorTransport(
    httpClient: HttpClient?,
    engine: HttpClientEngine?,
    private val userAgent: String,
    requestTimeoutMillis: Long,
    retries: Int,
) : HackerNewsTransport, AutoCloseable {

    private val ownsClient = httpClient == null
    private val client: HttpClient = httpClient ?: run {
        val setup: HttpClientConfig<*>.() -> Unit = {
            expectSuccess = false
            install(HttpTimeout) {
                this.requestTimeoutMillis = requestTimeoutMillis
            }
            if (retries > 0) {
                install(HttpRequestRetry) {
                    retryOnServerErrors(maxRetries = retries)
                    retryOnException(maxRetries = retries, retryOnTimeout = true)
                    exponentialDelay()
                }
            }
        }
        if (engine != null) HttpClient(engine, setup) else HttpClient(CIO, setup)
    }

    override suspend fun get(url: String): String {
        try {
            val response = client.get(url) {
                header(HttpHeaders.UserAgent, userAgent)
                accept(ContentType.Application.Json)
            }
            when {
                response.status == HttpStatusCode.NotFound -> throw HackerNewsException.NotFound(url)
                !response.status.isSuccess() -> throw HackerNewsException.Http(url, response.status.value)
            }
            return response.bodyAsText()
        } catch (e: HackerNewsException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw HackerNewsException.Network(url, e)
        }
    }

    override fun close() {
        if (ownsClient) client.close()
    }
}
