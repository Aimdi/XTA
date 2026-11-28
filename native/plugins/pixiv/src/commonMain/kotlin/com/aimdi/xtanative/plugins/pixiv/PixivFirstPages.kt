package com.aimdi.xtanative.plugins.pixiv

import com.aimdi.xtanative.networks.Page

/**
 * The first pages of the lists every surface shows alike (the works before
 * sign-in, Home's), kept a while for the whole plugin: the Pixiv screen, its
 * Home pin, its Deck column and its sidebar page each keep their own list,
 * and without this each read the same page again, a Deck column once more
 * every time it came back. A pull still reads afresh, and puts what it read
 * here for the others.
 */
internal class PixivFirstPages(
    private val keepMillis: Long = KEEP_MILLIS,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private class Kept(val at: Long, val page: Page<*>)

    private val kept = HashMap<String, Kept>()

    /** The first page kept under [key], while it is fresh. */
    @Synchronized
    @Suppress("UNCHECKED_CAST")
    fun <T> get(key: String): Page<T>? {
        val entry = kept[key] ?: return null
        if (now() - entry.at > keepMillis) {
            kept.remove(key)
            return null
        }
        return entry.page as Page<T>
    }

    @Synchronized
    fun put(key: String, page: Page<*>) {
        kept[key] = Kept(now(), page)
    }

    /** Forgets every page, as when the account or what it may see changed. */
    @Synchronized
    fun clear() = kept.clear()

    companion object {
        const val KEEP_MILLIS = 5 * 60_000L
    }
}
