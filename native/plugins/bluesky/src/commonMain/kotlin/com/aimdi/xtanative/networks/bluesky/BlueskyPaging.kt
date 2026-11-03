package com.aimdi.xtanative.networks.bluesky

import com.aimdi.xtanative.networks.Page
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * How XTA pages through Bluesky's lists: AppViews hand cursors out that can
 * come round again, and pages that overlap, so a list must stop on a cursor
 * it already used and show each item once.
 */
object BlueskyPaging {
    /** The cursor to read the next page with, or null to stop: [answered] is blank, or one of the cursors already [used]. */
    fun next(answered: String?, used: Collection<String>): String? = answered?.trim()?.takeIf { it.isNotEmpty() && it !in used }

    /**
     * [shown] followed by [more], each item once by [key] (a post's URI, a
     * person's [BlueskyProfile.identity]); an item with an empty key is dropped.
     */
    fun <T> merge(shown: List<T>, more: List<T>, key: (T) -> String): List<T> =
        (shown + more).filter { key(it).isNotEmpty() }.distinctBy(key)

    /**
     * Every page of [read], first to last, as XTA's imports walk a follow list
     * or a list's members: until a page brings no one, has no cursor or hands
     * back the one it was read with; [pauseMillis] apart, to stay polite on
     * the public AppView.
     */
    fun <T> walk(pauseMillis: Long = 200, read: suspend (cursor: String?) -> Page<T>): Flow<List<T>> = flow {
        var cursor: String? = null
        while (true) {
            val page = read(cursor)
            emit(page.items)
            val next = page.next
            if (next.isNullOrEmpty() || next == cursor || page.items.isEmpty()) break
            cursor = next
            delay(pauseMillis)
        }
    }
}
