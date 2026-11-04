package com.aimdi.xtanative.ui.plugins

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** How a [PluginFeed] reads what it follows for the app's timelines. */
object FeedReads {
    /**
     * [read] of each of [keys] at once (a feed, a list), as [PluginFeed.posts]
     * reads its accounts: their answers in [keys]' order, one that fails
     * adding nothing. Only when every one fails does this throw, the first
     * failure, for the app to say so beside the other networks' posts.
     */
    suspend fun <K, T> each(keys: List<K>, read: suspend (K) -> List<T>): List<T> = coroutineScope {
        val answers = keys.map { key -> async { attempt { read(key) } } }.awaitAll()
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
