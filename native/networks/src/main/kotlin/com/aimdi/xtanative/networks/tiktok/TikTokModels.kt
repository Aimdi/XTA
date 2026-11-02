package com.aimdi.xtanative.networks.tiktok

import com.aimdi.xtanative.networks.Page
import java.time.Instant

/** A TikTok account as its profile page describes it. Its videos are asked for by [secUid]. */
data class TikTokProfile(
    val id: String,
    val secUid: String,
    val uniqueId: String,
    val nickname: String,
    val signature: String? = null,
    val avatarUrl: String? = null,
    val privateAccount: Boolean = false,
    val verified: Boolean = false,
    val followerCount: Long = 0,
    val followingCount: Long = 0,
    val videoCount: Long = 0,
    /** Likes the account's videos received. */
    val heartCount: Long = 0,
) {
    /** The nickname, or the username when the account gave none. */
    val displayName: String get() = nickname.ifBlank { uniqueId }

    /** The profile on tiktok.com, for "open on the site". */
    val url: String get() = "https://www.tiktok.com/@$uniqueId"
}

/** Who posted a video: enough to draw its header and to open the account. */
data class TikTokAuthor(
    val uniqueId: String,
    val nickname: String,
    val secUid: String? = null,
    val avatarUrl: String? = null,
    val verified: Boolean = false,
) {
    val displayName: String get() = nickname.ifBlank { uniqueId }
}

/**
 * One file a video plays from. [label] is its quality ("720p") when TikTok
 * named one, "download" for the download copy, else null.
 */
data class TikTokVideoSource(val url: String, val label: String? = null)

/**
 * A TikTok video, or a photo post shown by its cover. [sources] are the
 * video's files, the quality ladder first; they play only with
 * [TikTokClient.playbackHeaders], and often not from a datacenter at all,
 * so [embedUrl] is the player to fall back on.
 */
data class TikTokPost(
    val id: String,
    /** The caption, with its #tags and @mentions as typed. */
    val desc: String,
    val createdAt: Instant?,
    val author: TikTokAuthor,
    val coverUrl: String? = null,
    val durationSeconds: Int = 0,
    val width: Int = 0,
    val height: Int = 0,
    val diggCount: Long = 0,
    val commentCount: Long = 0,
    val shareCount: Long = 0,
    val playCount: Long = 0,
    val sources: List<TikTokVideoSource> = emptyList(),
) {
    /** Width over height, portrait 9:16 when TikTok gave no size. */
    val aspectRatio: Float get() = if (width > 0 && height > 0) width.toFloat() / height else 9f / 16

    /** The file to play first. */
    val playUrl: String? get() = sources.firstOrNull()?.url

    /** A photo post: a cover and nothing to play. */
    val isPhoto: Boolean get() = sources.isEmpty() && coverUrl != null

    /** The video on tiktok.com. */
    val url: String get() = "https://www.tiktok.com/@${author.uniqueId}/video/$id"

    /** TikTok's own embedded player, for when the files will not play. */
    val embedUrl: String get() = "https://www.tiktok.com/embed/v3/$id"

    /** The length as a card shows it, "1:05"; empty when unknown. */
    val durationText: String
        get() = if (durationSeconds <= 0) "" else "${durationSeconds / 60}:${(durationSeconds % 60).toString().padStart(2, '0')}"
}

/**
 * A page of TikTok's creator video list as it came. [statusCode] is TikTok's
 * own: 0 when it answered, 10201 when there is no such account.
 */
data class TikTokItemList(val posts: List<TikTokPost>, val cursor: String?, val hasMore: Boolean, val statusCode: Int?) {
    /** The posts, and the cursor to ask with next while TikTok says there are more. */
    val page: Page<TikTokPost> get() = Page(posts, cursor?.takeIf { hasMore && it.isNotEmpty() })
}

/** A person search or discover found: enough to show them and open `@uniqueId`. */
data class TikTokSearchUser(
    val uniqueId: String,
    val nickname: String,
    val avatarUrl: String? = null,
    val signature: String? = null,
    val secUid: String? = null,
    val verified: Boolean = false,
    val followerCount: Long = 0,
) {
    val displayName: String get() = nickname.ifBlank { uniqueId }

    companion object {
        /** A profile read by username, as a search shows it. */
        fun of(profile: TikTokProfile) = TikTokSearchUser(
            uniqueId = profile.uniqueId,
            nickname = profile.nickname,
            avatarUrl = profile.avatarUrl,
            signature = profile.signature,
            secUid = profile.secUid,
            verified = profile.verified,
            followerCount = profile.followerCount,
        )
    }
}

/** What a guest search finds: people, a few of their newest videos, and TikTok's suggestions for the words typed. */
data class TikTokSearchPage(
    val users: List<TikTokSearchUser> = emptyList(),
    val posts: List<TikTokPost> = emptyList(),
    val suggestions: List<String> = emptyList(),
)

/**
 * The guest session TikTok hands out, which XTA keeps between starts: its
 * cookies (`ttwid`, `msToken`, …) as a Cookie header, and the device id the
 * web API is asked with. Empty is a new guest.
 */
data class TikTokSession(val cookies: String = "", val deviceId: String = "")

/**
 * What TikTok may change that XTA's published repairs replace without a
 * release (`tiktok.aid` and `tiktok.user_agent` in repairs.json): the web
 * app id every API read carries, and the browser it reads as.
 */
data class TikTokRepairs(
    val aid: String = "1988",
    val userAgent: String =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
)
