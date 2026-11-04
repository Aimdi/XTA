package com.aimdi.xtanative.networks.pixiv

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.networks.Page

/** The tag Pixiv files a bookmark under when it carries none of the reader's own. */
const val PIXIV_UNCLASSIFIED_TAG = "未分類"

/** One of the reader's bookmark tags, and how many works carry it. */
data class PixivBookmarkTag(val name: String, val count: Long)

/** A tag as a work's bookmark lists it: [checked] when the bookmark is filed under it. */
data class PixivBookmarkTagChoice(val name: String, val checked: Boolean)

/** A work's bookmark, from `/v2/illust/bookmark/detail`: its tags are the work's own and the reader's, each marked when filed. */
data class PixivBookmarkDetail(
    val isBookmarked: Boolean,
    /** `public` or `private`. */
    val restrict: String,
    val tags: List<PixivBookmarkTagChoice> = emptyList(),
) {
    companion object {
        /** A missing or reshaped detail reads as a public work not bookmarked. */
        fun parse(json: J): PixivBookmarkDetail {
            val detail = json["bookmark_detail"]
            return PixivBookmarkDetail(
                isBookmarked = detail["is_bookmarked"].boolean == true,
                restrict = if (detail["restrict"].string == "private") "private" else "public",
                tags = detail["tags"].list.mapNotNull { tag ->
                    PixivText.nonEmpty(tag["name"].string)?.let { PixivBookmarkTagChoice(it, tag["is_registered"].boolean == true) }
                },
            )
        }
    }
}

/**
 * The reader's bookmarks as XTA reads them: how one work is bookmarked, and
 * the tags they file bookmarks under. The bookmarked works themselves are
 * [PixivClient.bookmarks]. Nothing is bookmarked on Pixiv from here.
 */
class PixivBookmarkApi(private val client: PixivClient) {
    /** Whether and how the reader bookmarked [illustId], and the tags it is or could be filed under. */
    suspend fun detail(illustId: Long): PixivBookmarkDetail =
        PixivBookmarkDetail.parse(client.getJson("/v2/illust/bookmark/detail", mapOf("illust_id" to "$illustId")))

    /** One page of the reader's [restrict] bookmark tags, with their counts. */
    suspend fun tags(restrict: String, next: String? = null): Page<PixivBookmarkTag> {
        if (next != null) return tagPage(client.getNextJson(next))
        val userId = client.ensureUserId()
        return tagPage(client.getJson("/v1/user/bookmark-tags/illust", mapOf("user_id" to "$userId", "restrict" to restrict)))
    }

    /** The reader's [restrict] tags, page after page up to [maxPages]: enough for a large collection without walking thousands. */
    suspend fun allTags(restrict: String, maxPages: Int = KNOWN_TAG_PAGE_LIMIT, next: String? = null): List<PixivBookmarkTag> {
        val page = tags(restrict, next)
        val more = page.next
        return if (more == null || maxPages <= 1) page.items else page.items + allTags(restrict, maxPages - 1, more)
    }

    private fun tagPage(json: J): Page<PixivBookmarkTag> = Page(
        json["bookmark_tags"].list.mapNotNull { tag ->
            PixivText.nonEmpty(tag["name"].string)?.let { PixivBookmarkTag(it, tag["count"].long ?: 0) }
        },
        nextOf(json),
    )

    companion object {
        /** How many pages of tags [allTags] reads by default. */
        const val KNOWN_TAG_PAGE_LIMIT = 10
    }
}
