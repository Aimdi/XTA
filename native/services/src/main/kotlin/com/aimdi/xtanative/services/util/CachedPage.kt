package com.aimdi.xtanative.services.util

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * A page read through an optional cache that can never hold a good answer,
 * or its retry, hostage (cached_page.dart): a fresh cached copy wins unless
 * [bypassCache]; otherwise the page is fetched and written back in the
 * background; when the fetch fails, a stale copy stands in and else the
 * fetch's own error is thrown. A cache read that fails or takes longer than
 * [cacheTimeoutMillis] counts as empty, and the write is never waited on.
 */
suspend fun <T : Any> loadCachedPage(
    readFresh: suspend () -> T?,
    readStale: suspend () -> T?,
    fetch: suspend () -> T,
    write: suspend (T) -> Unit,
    background: CoroutineScope,
    bypassCache: Boolean = false,
    cacheTimeoutMillis: Long = 1_000,
): T {
    suspend fun read(action: suspend () -> T?): T? = try {
        withTimeoutOrNull(cacheTimeoutMillis) { action() }
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        null
    }

    if (!bypassCache) read(readFresh)?.let { return it }
    val page = try {
        fetch()
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        return read(readStale) ?: throw error
    }
    background.launch { runCatching { write(page) } }
    return page
}
