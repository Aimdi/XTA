package com.aimdi.xtanative.ui.home

import com.aimdi.xtanative.core.feed.FeedKeys
import com.aimdi.xtanative.core.feed.FeedSort
import com.aimdi.xtanative.core.feed.Unread
import com.aimdi.xtanative.core.session.Session

/**
 * The unread dots of Home's timeline picker and the account menu
 * (group_unread_store.dart): a feed lights up when it read something from X
 * newer than the last post the reader is known to have read there, and only
 * a feed that keeps a reading position does ([Unread.tracks]).
 */
object HomeUnread {
    /** The key [tab]'s feed keeps its read mark under; null for a tab with no X feed of its own. */
    fun feedKey(tab: HomeTab): String? = when (tab) {
        HomeTab.Following -> FeedKeys.FOLLOWING
        HomeTab.ForYou -> FeedKeys.FOR_YOU
        is HomeTab.Group -> tab.id
        is HomeTab.Network -> null
    }

    /**
     * Whether [tab]'s feed keeps a reading position: XTA's Settings › Posts ›
     * Remember reading position, or its own catch-up mode, for a feed in
     * time order; For you, which has neither order nor catch-up, with the setting.
     */
    fun tracks(session: Session, tab: HomeTab, readingPosition: Boolean): Boolean {
        if (tab == HomeTab.ForYou) return Unread.tracks(popular = false, readingPosition = readingPosition, catchUp = false)
        val key = feedKey(tab) ?: return false
        val settings = session.settingsOf(key)
        return Unread.tracks(popular = settings.sort == FeedSort.POPULAR, readingPosition = readingPosition, catchUp = settings.catchUp)
    }

    /** The feeds that can light up: Following, For you and every group. */
    fun feeds(session: Session): List<HomeTab> = listOf(HomeTab.Following, HomeTab.ForYou) + session.groups.map { HomeTab.Group(it.id, it.name) }
}
