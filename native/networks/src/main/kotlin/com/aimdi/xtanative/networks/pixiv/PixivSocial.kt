package com.aimdi.xtanative.networks.pixiv

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.networks.Page

/** The two people lists a profile opens. */
enum class PixivUserListKind { FOLLOWING, FOLLOWERS }

/**
 * A creator's whole profile, from `/v1/user/detail`. A field the creator
 * marked private in `profile_publicity` is left empty even when Pixiv sends
 * it, so nothing they chose to hide is shown.
 */
data class PixivUserProfile(
    val user: PixivUser,
    val backgroundUrl: String? = null,
    val totalIllusts: Long = 0,
    val totalManga: Long = 0,
    val totalNovels: Long = 0,
    val publicBookmarks: Long = 0,
    val webpage: String = "",
    val twitterAccount: String = "",
    val twitterUrl: String = "",
    val pawooUrl: String = "",
    val gender: String = "",
    val region: String = "",
    val job: String = "",
    /** `MM-DD`, empty when unknown or private. */
    val birthDay: String = "",
    val birthYear: Int? = null,
    val isPremium: Boolean = false,
) {
    val id: Long get() = user.id
    val url: String get() = pixivUserUrl(id)

    /** The tab a reader most likely came for: manga when there is more of it. */
    val defaultWorkType: PixivWorkType get() = if (totalManga > totalIllusts) PixivWorkType.MANGA else PixivWorkType.ILLUST

    /** Whether both kinds have works, so the Works tab offers a switch. */
    val hasBothWorkTypes: Boolean get() = totalIllusts > 0 && totalManga > 0

    /** The X link, built from the account when Pixiv sent no address. */
    val twitterLink: String
        get() = if (twitterUrl.isNotEmpty() || twitterAccount.isEmpty()) twitterUrl else "https://x.com/${twitterAccount.replaceFirst("@", "")}"

    companion object {
        /** `/v1/user/detail`; a missing or reshaped answer reads as a profile of nobody (id 0). */
        fun parse(json: J): PixivUserProfile {
            val profile = json["profile"]
            val publicity = json["profile_publicity"]
            fun shown(field: String) = if (isPrivate(publicity[field])) "" else text(profile[field])
            return PixivUserProfile(
                user = PixivUser.detail(json),
                backgroundUrl = link(profile["background_image_url"]),
                totalIllusts = profile["total_illusts"].long ?: 0,
                totalManga = profile["total_manga"].long ?: 0,
                totalNovels = profile["total_novels"].long ?: 0,
                publicBookmarks = profile["total_illust_bookmarks_public"].long ?: 0,
                webpage = link(profile["webpage"]).orEmpty(),
                twitterAccount = text(profile["twitter_account"]),
                twitterUrl = link(profile["twitter_url"]).orEmpty(),
                pawooUrl = if (publicity["pawoo"].boolean == false) "" else link(profile["pawoo_url"]).orEmpty(),
                gender = shown("gender"),
                region = shown("region"),
                job = shown("job"),
                birthDay = shown("birth_day"),
                birthYear = if (isPrivate(publicity["birth_year"])) null else profile["birth_year"].int?.takeIf { it > 0 },
                isPremium = profile["is_premium"].boolean == true,
            )
        }

        /** Only `private` hides a field: Pixiv sends a My pixiv-only one only to readers allowed to see it. */
        private fun isPrivate(publicity: J): Boolean = publicity.string == "private"

        private fun text(value: J): String = value.string?.trim().orEmpty()

        private fun link(value: J): String? = text(value).takeIf { it.startsWith("https://") || it.startsWith("http://") }
    }
}

/** Whether and how the reader follows a creator, from `/v1/user/follow/detail`. */
data class PixivFollowDetail(
    val isFollowed: Boolean,
    /** `public` or `private`; empty when not followed. */
    val restrict: String = "",
) {
    val isPrivate: Boolean get() = restrict == "private"

    companion object {
        /** A missing or reshaped answer reads as not followed. */
        fun parse(json: J): PixivFollowDetail {
            val detail = json["follow_detail"]
            return PixivFollowDetail(detail["is_followed"].boolean == true, detail["restrict"].string?.trim().orEmpty())
        }
    }
}

/**
 * Profiles: a creator's detail, works and bookmarks, the people they follow
 * and who follows them, whether the reader follows them, and the reader's own
 * AI display setting. Read-only: following happens in XTA Native alone.
 */
class PixivSocialApi(private val client: PixivClient) {
    private fun isSelf(userId: Long) = userId == client.storedUserId

    /** The signed-in reader's id, for their own lists. */
    suspend fun ownUserId(): Long = client.ensureUserId()

    /** [userId]'s whole profile; an answer naming nobody is a bad answer. */
    suspend fun userProfile(userId: Long): PixivUserProfile {
        val profile = PixivUserProfile.parse(client.getJson("/v1/user/detail", mapOf("user_id" to "$userId", "filter" to ANDROID)))
        if (profile.id == 0L) throw unusableAnswer("no user $userId")
        return profile
    }

    /** One kind of [userId]'s works; the reader's own keep everything they posted. */
    suspend fun userWorks(userId: Long, type: PixivWorkType, next: String? = null): Page<PixivIllust> =
        client.userIllusts(userId, type, ownList = isSelf(userId), next = next)

    /** [userId]'s bookmarks; someone else's follow the reader's R-18 and AI choices. */
    suspend fun userBookmarks(userId: Long, restrict: String = "public", tag: String? = null, next: String? = null): Page<PixivIllust> =
        client.illustPage(client.getPage("/v1/user/bookmarks/illust", client.bookmarkQuery(userId, restrict, tag), next), ownList = isSelf(userId))

    /** Who [userId] follows; [restrict] `private` is only answered for the reader themself. */
    suspend fun userFollowing(userId: Long, restrict: String = "public", next: String? = null): Page<PixivUserPreview> =
        previewPage(client.getPage("/v1/user/following", mapOf("user_id" to "$userId", "restrict" to restrict, "filter" to ANDROID), next))

    /** Who follows [userId]; always the public list. */
    suspend fun userFollowers(userId: Long, next: String? = null): Page<PixivUserPreview> =
        previewPage(client.getPage("/v1/user/follower", mapOf("user_id" to "$userId", "restrict" to "public", "filter" to ANDROID), next))

    /** One page of the [kind] list of [userId]. */
    suspend fun userList(kind: PixivUserListKind, userId: Long, restrict: String = "public", next: String? = null): Page<PixivUserPreview> =
        when (kind) {
            PixivUserListKind.FOLLOWING -> userFollowing(userId, restrict, next)
            PixivUserListKind.FOLLOWERS -> userFollowers(userId, next)
        }

    /** Whether, and how visibly, the reader follows [userId] on Pixiv. */
    suspend fun followDetail(userId: Long): PixivFollowDetail =
        PixivFollowDetail.parse(client.getJson("/v1/user/follow/detail", mapOf("user_id" to "$userId")))

    /** Whether the signed-in account shows AI-generated works (true) or partly hides them (false) everywhere on Pixiv; it is changed on pixiv.net. */
    suspend fun showsAiWorks(): Boolean =
        client.getJson("/v1/user/ai-show-settings")["show_ai"].boolean ?: throw unusableAnswer("ai-show-settings without show_ai")

    private fun previewPage(json: J): Page<PixivUserPreview> =
        Page(PixivUserPreview.list(json, client.showR18, !client.hideAi), nextOf(json))
}
