package com.aimdi.xtanative.ui.group

import com.aimdi.xtanative.core.backup.FollowGroup

/**
 * The group title's menu (feed_switcher_menu.dart): short by design, Home's
 * feeds, a few pinned groups for one tap, and every group one row further.
 */
object FeedSwitcher {
    /** How many pinned groups the menu offers directly (kFeedSwitcherPinnedLimit). */
    const val PINNED_LIMIT = 5

    /** The pinned groups of [groups], in their order, without the one open ([currentId]). */
    fun pinned(groups: List<FollowGroup>, currentId: String?): List<FollowGroup> =
        groups.filter { it.pinned && it.id != currentId }.take(PINNED_LIMIT)
}
