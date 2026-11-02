package com.aimdi.xtanative.networks.instagram

import java.time.Instant

/**
 * An Instagram account as `web_profile_info` describes it. [id] is
 * Instagram's numeric user id (its `pk`): the paged feed is asked by it.
 */
data class InstagramProfile(
    val id: String,
    val username: String,
    val fullName: String,
    val biography: String? = null,
    val avatarUrl: String? = null,
    val isPrivate: Boolean = false,
    val isVerified: Boolean = false,
    val followerCount: Long = 0,
    val followingCount: Long = 0,
    val mediaCount: Long = 0,
) {
    /** The full name, or the username when the account gave none. */
    val displayName: String get() = fullName.ifBlank { username }

    /** The profile on instagram.com, for "open on the site". */
    val url: String get() = "https://www.instagram.com/$username/"
}

/** Who posted: enough to draw a post's header and to open or follow the account. [pk] is empty when Instagram left it out. */
data class InstagramAuthor(
    val username: String,
    val fullName: String,
    val avatarUrl: String? = null,
    val pk: String = "",
    val isVerified: Boolean = false,
) {
    val displayName: String get() = fullName.ifBlank { username }
}

/**
 * A photo, carousel, video or reel. [coverUrl] is its picture, or a video's
 * poster; [carouselUrls] are a carousel's slides in order, one picture each.
 * Video files are not read: XTA shows a video as its cover and opens it on
 * instagram.com, as Instagram's CDN refuses most players without a session.
 */
data class InstagramPost(
    val id: String,
    val shortcode: String,
    val caption: String,
    val createdAt: Instant?,
    val author: InstagramAuthor,
    val coverUrl: String? = null,
    val isVideo: Boolean = false,
    val likeCount: Long = 0,
    val commentCount: Long = 0,
    val carouselUrls: List<String> = emptyList(),
) {
    /** The post on instagram.com: a video under /reel/, anything else under /p/. */
    val url: String get() = "https://www.instagram.com/${if (isVideo) "reel" else "p"}/$shortcode/"

    /** The pictures a card shows: a carousel's slides, else the cover alone. */
    val displayUrls: List<String> get() = carouselUrls.ifEmpty { listOfNotNull(coverUrl?.takeIf { it.isNotEmpty() }) }
}

/** An account people search found. [id] is its pk, or its username when Instagram gave none. */
data class InstagramSearchUser(
    val id: String,
    val username: String,
    val fullName: String,
    val avatarUrl: String? = null,
    val isPrivate: Boolean = false,
    val isVerified: Boolean = false,
) {
    val displayName: String get() = fullName.ifBlank { username }
}

/**
 * What Instagram may change that XTA's published repairs replace without a
 * release (`instagram.app_id` and `instagram.user_agent` in repairs.json):
 * the public web app id every read carries, and the browser it reads as.
 */
data class InstagramRepairs(
    val appId: String = "936619743392459",
    val userAgent: String =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
)
