package com.aimdi.xtanative.networks.pixiv

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.networks.Page
import java.time.Instant

/** One novel as Pixiv's lists, rankings, bookmarks and series send it. */
data class PixivNovel(
    val id: Long,
    val title: String,
    val user: PixivUser,
    /** Plain text; [captionHtml] keeps Pixiv's links and line breaks. */
    val caption: String = "",
    val captionHtml: String = "",
    val coverUrl: String? = null,
    val tags: List<PixivTag> = emptyList(),
    val series: PixivSeriesRef? = null,
    /** Characters of text, as Pixiv counts them. */
    val textLength: Long = 0,
    val pageCount: Int = 1,
    val createdAt: Instant? = null,
    val totalBookmarks: Long = 0,
    val totalViews: Long = 0,
    val totalComments: Long = 0,
    val isBookmarked: Boolean = false,
    /** Pixiv's age rating: 0 for everyone, 1 for R-18, 2 for R-18G. */
    val xRestrict: Int = 0,
    /** The author marked the novel as AI-generated. */
    val isAi: Boolean = false,
    val isOriginal: Boolean = false,
) {
    val isR18: Boolean get() = xRestrict > 0
    val isR18G: Boolean get() = xRestrict >= 2
    val url: String get() = pixivNovelUrl(id)

    /** Whether the reader's choices let this novel through: R-18 only with [includeR18], AI only with [includeAi]. */
    fun allowed(includeR18: Boolean, includeAi: Boolean): Boolean = (includeR18 || !isR18) && (includeAi || !isAi)

    companion object {
        /** One novel object, or null when it names none or Pixiv withholds it (deleted, or for the author's My pixiv only). */
        fun parse(data: J): PixivNovel? {
            val id = data["id"].long?.takeIf { it > 0 } ?: return null
            if (data["visible"].boolean == false) return null
            val caption = data["caption"].string?.trim().orEmpty()
            return PixivNovel(
                id = id,
                title = data["title"].string?.trim().orEmpty(),
                user = PixivUser.parse(data["user"]),
                caption = PixivText.plain(caption),
                captionHtml = caption,
                coverUrl = coverOf(data["image_urls"]),
                tags = PixivTag.list(data["tags"]),
                series = PixivSeriesRef.parse(data["series"]),
                textLength = data["text_length"].long ?: 0,
                pageCount = data["page_count"].int ?: 1,
                createdAt = PixivText.instant(data["create_date"].string),
                totalBookmarks = data["total_bookmarks"].long ?: 0,
                totalViews = data["total_view"].long ?: 0,
                totalComments = data["total_comments"].long ?: 0,
                isBookmarked = data["is_bookmarked"].boolean == true,
                xRestrict = data["x_restrict"].int ?: 0,
                isAi = data["novel_ai_type"].long == 2L,
                isOriginal = data["is_original"].boolean == true,
            )
        }

        /** The `novels` of a list: what does not parse skipped, R-18 and AI novels left out unless asked for. */
        fun list(json: J, includeR18: Boolean = false, includeAi: Boolean = true): List<PixivNovel> =
            json["novels"].list.mapNotNull(::parse).filter { it.allowed(includeR18, includeAi) }

        /** The aspect-preserving `medium` cover first. Pixiv's stand-in for a missing cover is a real picture, so it is kept. */
        private fun coverOf(urls: J): String? =
            PixivText.nonEmpty(urls["medium"].string) ?: PixivText.nonEmpty(urls["square_medium"].string)
                ?: PixivText.nonEmpty(urls["large"].string)
    }
}

/** A novel series as its page introduces it. */
data class PixivNovelSeries(
    val id: Long,
    val title: String,
    val user: PixivUser,
    val caption: String = "",
    val captionHtml: String = "",
    val isConcluded: Boolean = false,
    /** Chapters published so far. */
    val contentCount: Long = 0,
    val totalCharacterCount: Long = 0,
    val isOriginal: Boolean = false,
    val watchlistAdded: Boolean = false,
) {
    val url: String get() = pixivNovelSeriesUrl(id)

    companion object {
        /** `novel_series_detail`, or null when it names no series. */
        fun parse(detail: J): PixivNovelSeries? {
            val id = detail["id"].long?.takeIf { it > 0 } ?: return null
            val caption = detail["caption"].string?.trim().orEmpty()
            return PixivNovelSeries(
                id = id,
                title = PixivText.nonEmpty(detail["title"].string).orEmpty(),
                user = PixivUser.parse(detail["user"]),
                caption = PixivText.plain(caption),
                captionHtml = caption,
                isConcluded = detail["is_concluded"].boolean == true,
                contentCount = detail["content_count"].long ?: 0,
                totalCharacterCount = detail["total_character_count"].long ?: 0,
                isOriginal = detail["is_original"].boolean == true,
                watchlistAdded = detail["watchlist_added"].boolean == true,
            )
        }
    }
}

/** A chapter and its place in its series, counted over every chapter Pixiv listed, so a hidden one keeps its number. */
data class PixivNovelChapter(val order: Int, val novel: PixivNovel)

/**
 * One page of `/v2/novel/series`: the series (Pixiv repeats it on every page),
 * its first and newest chapters, and this page's chapters numbered from one
 * within it (a reader adds the chapters of the pages before).
 */
data class PixivNovelSeriesPage(
    val series: PixivNovelSeries? = null,
    val first: PixivNovel? = null,
    val latest: PixivNovel? = null,
    val chapters: Page<PixivNovelChapter> = Page(emptyList(), null),
    /** How many chapters Pixiv listed on this page, shown or not. */
    val listed: Int = 0,
) {
    companion object {
        /** Chapters [allowed] hides are left out but counted, and a first or newest chapter it hides is not offered. */
        fun parse(json: J, allowed: (PixivNovel) -> Boolean = { true }): PixivNovelSeriesPage {
            val listed = json["novels"].list
            fun shown(item: J): PixivNovel? = PixivNovel.parse(item)?.takeIf(allowed)
            return PixivNovelSeriesPage(
                series = PixivNovelSeries.parse(json["novel_series_detail"]),
                first = shown(json["novel_series_first_novel"]),
                latest = shown(json["novel_series_latest_novel"]),
                chapters = Page(listed.mapIndexedNotNull { index, item -> shown(item)?.let { PixivNovelChapter(index + 1, it) } }, nextOf(json)),
                listed = listed.size,
            )
        }
    }
}

/** A novel's page on pixiv.net, which is also what its links look like. */
fun pixivNovelUrl(id: Long): String = "https://www.pixiv.net/novel/show.php?id=$id"

/** A novel series' page on pixiv.net. */
fun pixivNovelSeriesUrl(id: Long): String = "https://www.pixiv.net/novel/series/$id"
