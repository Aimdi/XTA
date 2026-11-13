package com.aimdi.xtanative.ui.community

import com.aimdi.xtanative.core.x.model.XCommunity

/** A community's two tabs, as XTA has them: its Top posts, and About. */
enum class CommunityTab { TOP, ABOUT }

/** What XTA's community header and About tab show of a community. */
object CommunityPage {
    /** A profile banner's height at most, as XTA draws it. */
    const val BANNER_MAX_DP = 168f
    const val BANNER_MIN_DP = 96f

    /** The few member faces beside the count. */
    const val FACES = 5

    /** The banner's height for a screen [widthDp] wide: a third of it, within XTA's bounds. */
    fun bannerHeight(widthDp: Float): Float = (widthDp / 3).coerceIn(BANNER_MIN_DP, BANNER_MAX_DP)

    fun faces(community: XCommunity): List<String> = community.memberAvatarUrls.take(FACES)

    /** The rules, numbered from 1. */
    fun numberedRules(community: XCommunity): List<Pair<Int, String>> = community.rules.mapIndexed { index, rule -> index + 1 to rule }

    /** The description, when there is one to show. */
    fun description(community: XCommunity): String? = community.description?.takeIf { it.isNotEmpty() }

    /** The tab a page opens on: About for an `/about` link. */
    fun openingTab(about: Boolean): CommunityTab = if (about) CommunityTab.ABOUT else CommunityTab.TOP
}
