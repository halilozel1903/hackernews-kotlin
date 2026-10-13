package io.github.halilozel1903.hackernews

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Runs [transform] on every element with at most [parallelism] calls running at the same time, and returns the
 * results in the order of this collection. The first failure cancels the remaining calls and is rethrown.
 */
public suspend fun <T, R> Collection<T>.mapConcurrently(parallelism: Int, transform: suspend (T) -> R): List<R> {
    require(parallelism >= 1) { "parallelism must be at least 1, was $parallelism" }
    if (isEmpty()) return emptyList()
    val semaphore = Semaphore(parallelism)
    return coroutineScope {
        map { item -> async { semaphore.withPermit { transform(item) } } }.awaitAll()
    }
}
