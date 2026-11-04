package com.aimdi.xtanative.ui.plugins

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** How a [PluginFeed] reads what it follows for the app's timelines. */
object FeedReads {
    /** How many of a network's accounts XTA reads at once for its timelines. */
    const val ACCOUNTS_AT_ONCE = 4

    /**
     * [read] of each of [keys] (a feed, a list, an account), [atOnce] at a
     * time, as [PluginFeed.posts] reads what it follows: their answers in
     * [keys]' order, one that fails adding nothing. Only when every one fails
     * does this throw, the first failure, for the app to say so beside the
     * other networks' posts.
     */
    suspend fun <K, T> each(keys: List<K>, atOnce: Int = Int.MAX_VALUE, read: suspend (K) -> List<T>): List<T> = coroutineScope {
        val gate = Semaphore(atOnce.coerceIn(1, keys.size.coerceAtLeast(1)))
        val answers = keys.map { key -> async { gate.withPermit { attempt { read(key) } } } }.awaitAll()
        answers.firstNotNullOfOrNull { it.exceptionOrNull() }?.takeIf { answers.none { answer -> answer.isSuccess } }?.let { throw it }
        answers.flatMap { it.getOrNull().orEmpty() }
    }

    private suspend fun <T> attempt(read: suspend () -> T): Result<T> = try {
        Result.success(read())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
}
