package com.aimdi.xtanative.networks.pixiv

import com.aimdi.xtanative.core.json.J
import java.time.Instant

/** The reader's Show R-18 and Hide AI choices, which drop works as each page is read. */
data class PixivFilters(val showR18: Boolean = false, val hideAi: Boolean = false)

/** A work's tag, with the translation Pixiv sent in the reader's language. */
data class PixivTag(val name: String, val translatedName: String? = null) {
    /** The translation, when Pixiv sent one that differs from [name]. */
    val translation: String? get() = translatedName?.trim()?.takeIf { it.isNotEmpty() && it != name }

    val displayName: String get() = translation ?: name

    companion object {
        /** A work's `tags`, illustration or novel alike; nameless entries are skipped. */
        fun list(tags: J): List<PixivTag> = tags.list.mapNotNull { tag ->
            PixivText.nonEmpty(tag["name"].string)?.let { PixivTag(it, tag["translated_name"].string?.trim()) }
        }
    }
}

/** The series a work belongs to, as its `series` object names it. */
data class PixivSeriesRef(val id: Long, val title: String) {
    companion object {
        /** Null when the object names no series. */
        fun parse(series: J): PixivSeriesRef? =
            series["id"].long?.takeIf { it > 0 }?.let { PixivSeriesRef(it, series["title"].string?.trim().orEmpty()) }
    }
}

/** The two kinds of work a creator's Works tab switches between, named as `/v1/user/illusts` takes them. */
enum class PixivWorkType(val api: String) { ILLUST("illust"), MANGA("manga") }

/** One illustration, manga or ugoira, as Pixiv's lists and its detail send it. */
data class PixivIllust(
    val id: Long,
    val title: String,
    /** Plain text; [captionHtml] keeps Pixiv's links and line breaks. */
    val caption: String,
    val type: String,
    /** The aspect-preserving `medium` preview a grid tile shows. */
    val thumbnailUrl: String,
    val pageCount: Int,
    val userId: Long,
    val userName: String,
    val userAccount: String,
    val captionHtml: String = "",
    val largeUrl: String? = null,
    /** Each page as the viewer shows it: `large`, lighter than the multi-megabyte original. */
    val pageUrls: List<String> = emptyList(),
    /** Each page's full-resolution file, index-aligned with [viewerUrls]. */
    val originalUrls: List<String> = emptyList(),
    /** Each page's small preview for a page overview, index-aligned with [viewerUrls]. */
    val pageThumbUrls: List<String> = emptyList(),
    val tags: List<PixivTag> = emptyList(),
    val width: Int = 0,
    val height: Int = 0,
    val userAvatarUrl: String? = null,
    val createdAt: Instant? = null,
    val totalBookmarks: Long = 0,
    val totalViews: Long = 0,
    val totalComments: Long = 0,
    val isR18: Boolean = false,
    /** Pixiv's 0–8 content rating; 6 and above counts as R-18. */
    val sanityLevel: Int = 0,
    /** The creator marked the work as AI-generated. */
    val isAi: Boolean = false,
    val isBookmarked: Boolean = false,
    val userIsFollowed: Boolean = false,
    val series: PixivSeriesRef? = null,
) {
    val url: String get() = pixivArtworkUrl(id)
    val userUrl: String get() = pixivUserUrl(userId)
    val isManga: Boolean get() = pageCount > 1 || type == "manga"
    val isUgoira: Boolean get() = type == "ugoira"

    /** Width over height for staggered grids; square when Pixiv did not say. */
    val aspectRatio: Double get() = if (width > 0 && height > 0) width.toDouble() / height else 1.0

    /** The pages the viewer shows: every page, else the large picture, else the preview. */
    val viewerUrls: List<String>
        get() = pageUrls.ifEmpty { listOf(largeUrl?.takeIf { it.isNotEmpty() } ?: thumbnailUrl) }

    /** The file to save for [page]: its original when Pixiv sent one. */
    fun downloadUrlAt(page: Int): String = alignedOr(originalUrls, page)

    /** A light preview of [page]. */
    fun thumbUrlAt(page: Int): String = alignedOr(pageThumbUrls, page)

    /** Whether the reader's choices let this work through: R-18 only with [includeR18], AI only with [includeAi]. */
    fun allowed(includeR18: Boolean, includeAi: Boolean): Boolean = (includeR18 || !isR18) && (includeAi || !isAi)

    private fun alignedOr(urls: List<String>, page: Int): String {
        val pages = viewerUrls
        val index = page.coerceIn(0, pages.size - 1)
        return if (urls.size == pages.size && urls[index].isNotEmpty()) urls[index] else pages[index]
    }

    companion object {
        /** One illust object, or null when it has no id or picture, or is Pixiv's stand-in for a deleted or restricted work. */
        fun parse(data: J): PixivIllust? {
            val id = data["id"].long ?: return null
            val thumb = firstImageUrl(data)?.takeIf { it.isNotEmpty() } ?: return null
            if (!isAccessible(data)) return null
            val user = data["user"]
            return PixivIllust(
                id = id,
                title = data["title"].string?.trim().orEmpty(),
                caption = PixivText.plain(data["caption"].string),
                captionHtml = data["caption"].string?.trim().orEmpty(),
                type = data["type"].string ?: "illust",
                thumbnailUrl = thumb,
                largeUrl = largeImageUrl(data),
                pageUrls = pageUrls(data),
                originalUrls = perPage(data, ::pageOriginalUrl, data["meta_single_page"]["original_image_url"].string ?: largeImageUrl(data)),
                pageThumbUrls = perPage(data, ::pageThumbUrl, pageThumbUrl(data) ?: firstImageUrl(data)),
                tags = PixivTag.list(data["tags"]),
                pageCount = data["page_count"].int ?: 1,
                width = data["width"].int ?: 0,
                height = data["height"].int ?: 0,
                userId = user["id"].long ?: 0,
                userName = user["name"].string?.trim().orEmpty(),
                userAccount = user["account"].string?.trim().orEmpty(),
                userAvatarUrl = user["profile_image_urls"]["medium"].string?.ifEmpty { null },
                createdAt = PixivText.instant(data["create_date"].string),
                totalBookmarks = data["total_bookmarks"].long ?: 0,
                totalViews = data["total_view"].long ?: 0,
                totalComments = data["total_comments"].long ?: 0,
                isR18 = isR18(data),
                sanityLevel = data["sanity_level"].int ?: 0,
                isAi = data["illust_ai_type"].long == 2L,
                isBookmarked = data["is_bookmarked"].boolean == true,
                userIsFollowed = user["is_followed"].boolean == true,
                series = PixivSeriesRef.parse(data["series"]),
            )
        }

        /** The `illusts` of a following, ranking, bookmark or search page, without what the reader's choices leave out. */
        fun list(json: J, includeR18: Boolean = false, includeAi: Boolean = true): List<PixivIllust> =
            json["illusts"].list.mapNotNull(::parse).filter { it.allowed(includeR18, includeAi) }

        private fun isR18(data: J): Boolean = (data["x_restrict"].long ?: 0) > 0 || (data["sanity_level"].long ?: 0) >= 6

        /** A real, viewable work for this account, not one Pixiv hides behind a stand-in picture. */
        private fun isAccessible(data: J): Boolean =
            data["visible"].boolean != false && !PixivImages.isLimitPlaceholder(firstImageUrl(data))

        /** The aspect-preserving `medium` first, as Pixez does, not the cropped `square_medium`. */
        private fun firstImageUrl(data: J): String? {
            val urls = data["image_urls"]
            return urls["medium"].string ?: urls["square_medium"].string ?: urls["large"].string
                ?: data["meta_single_page"]["original_image_url"].string
        }

        private fun largeImageUrl(data: J): String? =
            data["image_urls"]["large"].string ?: data["meta_single_page"]["original_image_url"].string ?: firstImageUrl(data)

        /** A viewer page: `large` before the multi-megabyte `original`, for browsing speed. */
        private fun pageImageUrl(page: J): String? {
            val urls = page["image_urls"]
            return urls["large"].string ?: urls["medium"].string ?: urls["original"].string ?: urls["square_medium"].string
        }

        private fun pageOriginalUrl(page: J): String? = page["image_urls"]["original"].string ?: pageImageUrl(page)

        /** A page preview: the aspect-preserving `medium`, since overview tiles are page-shaped. */
        private fun pageThumbUrl(page: J): String? {
            val urls = page["image_urls"]
            return urls["medium"].string ?: urls["square_medium"].string ?: pageImageUrl(page)
        }

        /** Every page through [pick], or the single-page fallback when there are no pages. */
        private fun perPage(data: J, pick: (J) -> String?, single: String?): List<String> {
            val pages = data["meta_pages"].list
            if (pages.isNotEmpty()) return pages.mapNotNull(pick)
            return if (single.isNullOrEmpty()) emptyList() else listOf(single)
        }

        private fun pageUrls(data: J): List<String> =
            perPage(data, ::pageImageUrl, largeImageUrl(data) ?: data["meta_single_page"]["original_image_url"].string)
    }
}

/** A Pixiv creator, from a work, a list or `/v1/user/detail`. Pixiv's app API has no follower count. */
data class PixivUser(
    val id: Long,
    val name: String,
    val account: String,
    val comment: String = "",
    val avatarUrl: String? = null,
    /** Illustrations and manga together; only a detail's `profile` says. */
    val worksCount: Long = 0,
    val followingCount: Long = 0,
    val mypixivCount: Long = 0,
    /** Whether the signed-in account follows them on Pixiv. */
    val isFollowed: Boolean = false,
) {
    val url: String get() = pixivUserUrl(id)

    companion object {
        /** A `user` object, with the counts of a detail's [profile] when there is one; id 0 when it is missing. */
        fun parse(user: J, profile: J = J(null)): PixivUser = PixivUser(
            id = user["id"].long ?: 0,
            name = user["name"].string?.trim().orEmpty(),
            account = user["account"].string?.trim().orEmpty(),
            comment = user["comment"].string?.trim().orEmpty(),
            avatarUrl = user["profile_image_urls"]["medium"].string?.ifEmpty { null },
            worksCount = (profile["total_illusts"].long ?: 0) + (profile["total_manga"].long ?: 0),
            followingCount = profile["total_follow_users"].long ?: 0,
            mypixivCount = profile["total_mypixiv_users"].long ?: 0,
            isFollowed = user["is_followed"].boolean == true,
        )

        /** `/v1/user/detail`'s `user` and `profile`. */
        fun detail(json: J): PixivUser = parse(json["user"], json["profile"])
    }
}

/** A creator with the works Pixiv previews beside them in recommended, related, search and follow lists. */
data class PixivUserPreview(
    val user: PixivUser,
    val illusts: List<PixivIllust> = emptyList(),
    val novels: List<PixivNovel> = emptyList(),
    /** The account muted this creator on Pixiv itself. */
    val isMuted: Boolean = false,
) {
    companion object {
        /** `user_previews`, their works and novels without what the reader's choices leave out. */
        fun list(json: J, includeR18: Boolean = false, includeAi: Boolean = true): List<PixivUserPreview> =
            json["user_previews"].list.mapNotNull { item ->
                previewUser(item)?.takeIf { it.id != 0L }?.let { user ->
                    PixivUserPreview(
                        user = user,
                        illusts = PixivIllust.list(item, includeR18, includeAi),
                        novels = PixivNovel.list(item, includeR18, includeAi),
                        isMuted = item["is_muted"].boolean == true,
                    )
                }
            }

        private fun previewUser(item: J): PixivUser? = when {
            item["user"].exists -> PixivUser.parse(item["user"])
            item["id"].exists -> PixivUser.parse(item)
            else -> null
        }
    }
}

/** A tag Pixiv suggests or finds trending, with the work it picked to stand for it. */
data class PixivTrendTag(val name: String, val translatedName: String? = null, val illust: PixivIllust? = null) {
    companion object {
        /** `trend_tags` of works or novels: nameless entries skipped, and a picture the reader's choices keep out leaves its tag bare. */
        fun list(json: J, includeR18: Boolean, includeAi: Boolean): List<PixivTrendTag> = json["trend_tags"].list.mapNotNull { entry ->
            entry["tag"].string?.ifEmpty { null }?.let { name ->
                PixivTrendTag(name, entry["translated_name"].string, PixivIllust.parse(entry["illust"])?.takeIf { it.allowed(includeR18, includeAi) })
            }
        }
    }
}

/** A work's page on pixiv.net. */
fun pixivArtworkUrl(id: Long): String = "https://www.pixiv.net/artworks/$id"

/** A creator's profile on pixiv.net. */
fun pixivUserUrl(id: Long): String = "https://www.pixiv.net/users/$id"
