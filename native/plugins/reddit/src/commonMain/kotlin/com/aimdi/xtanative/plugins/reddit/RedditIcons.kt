package com.aimdi.xtanative.plugins.reddit

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async

/**
 * A community's picture (XTA's `RedditIcons`): the one kept this week, else
 * looked up once per session, two rows asking at once sharing the lookup.
 * A miss is remembered for the session only, never kept on the device.
 */
internal class RedditIcons(
    private val cache: RedditIconCache,
    private val scope: CoroutineScope,
    private val lookup: suspend (String) -> String?,
) {
    private val asked = ConcurrentHashMap<String, Deferred<String?>>()

    suspend fun icon(subreddit: String): String? {
        val key = subreddit.lowercase()
        return asked.computeIfAbsent(key) { scope.async { find(key) } }.await()
    }

    private suspend fun find(key: String): String? {
        cache.get(key)?.let { return it }
        val found = attempt { lookup(key) }.getOrNull()?.takeIf { it.isNotEmpty() } ?: return null
        cache.put(key, found)
        return found
    }
}
