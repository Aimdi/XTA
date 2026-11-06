package com.aimdi.xtanative.ui.group

import com.aimdi.xtanative.core.feed.FeedState

/** Where the reader left a group feed: the post at the top and how far past it, and whether that was the top of the feed. */
data class KeptPlace(val postId: String, val offset: Int, val atTop: Boolean)

/**
 * A feed as the reader left it: where its reading stood ([state], every
 * chunk's cursor), what it showed and when that was read. [readKey] is the
 * plan's [com.aimdi.xtanative.core.feed.GroupFeedPlan.readKey]: a feed that
 * reads otherwise since starts again.
 */
data class KeptFeed(val readKey: String, val state: FeedState, val shown: FeedShown, val savedAt: Long?)

/**
 * The feeds left in this run of the app, as XTA's FeedSessionCache keeps a
 * pushed feed's loaded pages and scroll offset: reopening one shows what it
 * had, where the reader was, without asking X. Bounded to the [maxEntries]
 * feeds used last, since each holds every page it loaded.
 */
class FeedSessionCache(private val maxEntries: Int = 8) {
    private val feeds = HashMap<String, KeptFeed>()
    private val places = HashMap<String, KeptPlace>()

    /** Keys from the least recently used on. */
    private val order = ArrayList<String>()

    val size: Int @Synchronized get() = order.size

    /** The place [key] was left at, if it is still kept. */
    @Synchronized
    fun place(key: String): KeptPlace? = places[key]

    @Synchronized
    fun keepPlace(key: String, place: KeptPlace) {
        places[key] = place
        touch(key)
    }

    @Synchronized
    fun feed(key: String): KeptFeed? = feeds[key]?.also { touch(key) }

    @Synchronized
    fun keepFeed(key: String, feed: KeptFeed) {
        feeds[key] = feed
        touch(key)
    }

    /** Forgets [key]'s pages, as a feed stopped halfway through a load leaves them; its place stays. */
    @Synchronized
    fun dropFeed(key: String) {
        feeds.remove(key)
    }

    /** Forgets [key] altogether: the next opening starts empty. */
    @Synchronized
    fun evict(key: String) {
        feeds.remove(key)
        places.remove(key)
        order.remove(key)
    }

    @Synchronized
    fun clear() {
        feeds.clear()
        places.clear()
        order.clear()
    }

    private fun touch(key: String) {
        order.remove(key)
        order.add(key)
        while (order.size > maxEntries) {
            val oldest = order.removeAt(0)
            feeds.remove(oldest)
            places.remove(oldest)
        }
    }
}

/** The app's [FeedSessionCache], gone with the app. */
object FeedSessions {
    val feeds = FeedSessionCache()
}
