package com.aimdi.xtanative.ui.home

import com.aimdi.xtanative.core.feed.FeedKeys
import com.aimdi.xtanative.core.feed.FeedSort
import com.aimdi.xtanative.core.feed.Unread
import com.aimdi.xtanative.core.session.Session

/**
 * The unread dots of the strip and the account menu. A dot follows XTA's
 * rule ([Unread.tracks]): only a feed that keeps a reading position lights
 * up. What it says is that the feed put posts above the reader that they have
 * not scrolled up to yet, which is what XTA's "newer than the last read mark"
 * comes to while a feed is open.
 */
object HomeUnread {
    /** The feed settings key of [tab]; null for a tab with no X feed of its own. */
    fun feedKey(tab: HomeTab): String? = when (tab) {
        HomeTab.Following -> FeedKeys.FOLLOWING
        is HomeTab.Group -> tab.id
        HomeTab.ForYou, is HomeTab.Network -> null
    }

    /** Whether [tab]'s feed keeps a reading position: XTA's Settings › Posts › Remember reading position, or its own catch-up mode. */
    fun tracks(session: Session, tab: HomeTab, readingPosition: Boolean): Boolean {
        val key = feedKey(tab) ?: return false
        val settings = session.settingsOf(key)
        return Unread.tracks(popular = settings.sort == FeedSort.POPULAR, readingPosition = readingPosition, catchUp = settings.catchUp)
    }

    fun unread(tracks: Boolean, newAbove: Int): Boolean = tracks && newAbove > 0
}
