package com.aimdi.xtanative.networks.instagram

import com.aimdi.xtanative.core.repairs.RepairKind
import com.aimdi.xtanative.core.repairs.RepairSlot
import com.aimdi.xtanative.networks.DESKTOP_CHROME
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
    val appId: String = APP_ID.shipped,
    val userAgent: String = USER_AGENT.shipped,
) {
    companion object {
        val APP_ID = RepairSlot("instagram.app_id", "936619743392459", RepairKind.DIGITS)
        val USER_AGENT = RepairSlot("instagram.user_agent", DESKTOP_CHROME, RepairKind.HEADER)

        /** Every Instagram value `repairs.json` may replace. */
        val SLOTS = listOf(APP_ID, USER_AGENT)

        /** The shipped values with [published]'s Instagram entries that fit in force. */
        fun published(published: Map<String, String>) = InstagramRepairs(APP_ID.valueIn(published), USER_AGENT.valueIn(published))
    }
}
