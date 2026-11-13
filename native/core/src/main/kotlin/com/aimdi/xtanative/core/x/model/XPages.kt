package com.aimdi.xtanative.core.x.model

import java.time.Instant

/**
 * One page of accounts: who follows a profile or whom it follows, who
 * reposted a post, a list's or a community's members, a people search.
 * [bottomCursor] asks for the next page; null when there is none.
 */
data class XUserPage(val users: List<XUser>, val bottomCursor: String?, val topCursor: String? = null)

/** An X list's name and size; [name] is null when the list is gone or private. */
data class XListInfo(val id: String, val name: String?, val memberCount: Int?)

/** An X community: its header, rules and the few member faces X shows beside the count. */
data class XCommunity(
    val id: String,
    val name: String,
    val description: String? = null,
    val memberCount: Long = 0,
    val bannerUrl: String? = null,
    val topic: String? = null,
    val createdAt: Instant? = null,
    val rules: List<String> = emptyList(),
    val memberAvatarUrls: List<String> = emptyList(),
) {
    val url: String get() = "https://x.com/i/communities/$id"
}

/** One page of a community search. */
data class XCommunityPage(val communities: List<XCommunity>, val cursor: String?)

/**
 * A post's text in another language, as X's Grok translation gives it: the
 * [text] with its [entities] in code points of it, like a post's.
 */
data class XTranslation(val text: String, val entities: List<XEntity> = emptyList())
