package com.aimdi.xtanative.networks.pixiv

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.networks.NetworkRequestException
import com.aimdi.xtanative.networks.Page
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl

/** A Pixivision article as Pixiv's spotlight list announces it. */
data class PixivSpotlightArticle(
    val id: Long,
    val title: String,
    val thumbnailUrl: String,
    /** The title without the decorations Pixiv adds to [title]. */
    val pureTitle: String = "",
    val articleUrl: String = "",
    val publishedAt: Instant? = null,
) {
    val displayTitle: String get() = pureTitle.ifEmpty { title }

    companion object {
        /** Null without an id or any title. */
        fun parse(json: J): PixivSpotlightArticle? {
            val id = json["id"].long?.takeIf { it > 0 } ?: return null
            val title = PixivText.nonEmpty(json["title"].string) ?: PixivText.nonEmpty(json["pure_title"].string) ?: return null
            return PixivSpotlightArticle(
                id = id,
                title = title,
                thumbnailUrl = PixivText.nonEmpty(json["thumbnail"].string).orEmpty(),
                pureTitle = PixivText.nonEmpty(json["pure_title"].string).orEmpty(),
                articleUrl = PixivText.nonEmpty(json["article_url"].string).orEmpty(),
                publishedAt = PixivText.instant(json["publish_date"].string),
            )
        }
    }
}

/** An illustration or manga series as its page introduces it. */
data class PixivIllustSeries(
    val id: Long,
    val title: String,
    val user: PixivUser,
    /** Plain text: Pixiv sends series captions as HTML, like work captions. */
    val caption: String = "",
    val coverUrl: String? = null,
    val workCount: Long = 0,
    val createdAt: Instant? = null,
    val watchlistAdded: Boolean = false,
) {
    val url: String get() = pixivSeriesUrl(user.id, id)

    companion object {
        /** `illust_series_detail`, or null when it names no series. */
        fun parse(detail: J): PixivIllustSeries? {
            val id = detail["id"].long?.takeIf { it > 0 } ?: return null
            return PixivIllustSeries(
                id = id,
                title = PixivText.nonEmpty(detail["title"].string).orEmpty(),
                user = PixivUser.parse(detail["user"]),
                caption = PixivText.plain(detail["caption"].string),
                coverUrl = PixivText.nonEmpty(detail["cover_image_urls"]["medium"].string),
                workCount = detail["series_work_count"].long ?: 0,
                createdAt = PixivText.instant(detail["create_date"].string),
                watchlistAdded = detail["watchlist_added"].boolean == true,
            )
        }
    }
}

/** One page of a series: its header, which Pixiv repeats on every page, and its works. */
data class PixivSeriesPage(val series: PixivIllustSeries?, val works: Page<PixivIllust>)

/** Where a work sits in its series, and the works either side of it. */
data class PixivSeriesContext(
    /** The work's place in the series, from 1. */
    val order: Int,
    /** Works in the series; 0 when Pixiv did not say. */
    val total: Long = 0,
    val previous: PixivIllust? = null,
    val next: PixivIllust? = null,
) {
    companion object {
        /** `/v1/illust-series/illust`; null when the work has no place in a series. [allowed] decides which neighbours may be offered. */
        fun parse(json: J, allowed: (PixivIllust) -> Boolean = { true }): PixivSeriesContext? {
            val context = json["illust_series_context"]
            val order = context["content_order"].int?.takeIf { it > 0 } ?: return null
            fun neighbour(key: String) = PixivIllust.parse(context[key])?.takeIf(allowed)
            return PixivSeriesContext(
                order = order,
                total = json["illust_series_detail"]["series_work_count"].long ?: 0,
                previous = neighbour("prev"),
                next = neighbour("next"),
            )
        }
    }
}

/** A series on the reader's manga or novel watchlist, which send the same shape. */
data class PixivWatchlistSeries(
    val id: Long,
    val title: String,
    val userId: Long,
    val userName: String,
    val coverUrl: String? = null,
    /** Pixiv's reason the series cannot be shown, such as a restricted work. */
    val maskText: String? = null,
    val latestContentId: Long? = null,
    val lastPublishedAt: Instant? = null,
    val publishedCount: Long = 0,
) {
    companion object {
        /** Null without an id. */
        fun parse(json: J): PixivWatchlistSeries? {
            val id = json["id"].long?.takeIf { it > 0 } ?: return null
            return PixivWatchlistSeries(
                id = id,
                title = PixivText.nonEmpty(json["title"].string).orEmpty(),
                userId = json["user"]["id"].long ?: 0,
                userName = PixivText.nonEmpty(json["user"]["name"].string).orEmpty(),
                coverUrl = PixivText.nonEmpty(json["url"].string),
                maskText = PixivText.nonEmpty(json["mask_text"].string),
                latestContentId = json["latest_content_id"].long?.takeIf { it > 0 },
                lastPublishedAt = PixivText.instant(json["last_published_content_datetime"].string),
                publishedCount = json["published_content_count"].long ?: 0,
            )
        }

        /** A watchlist page's `series`; rows that do not parse are skipped. */
        fun list(json: J): List<PixivWatchlistSeries> = json["series"].list.mapNotNull { parse(it) }
    }
}

/**
 * Pixiv's browse surfaces: rankings, recommended manga and creators,
 * Pixivision, series, the manga watchlist, and the showcase readable without
 * an account. The Following feed is [PixivClient.following].
 */
class PixivDiscoveryApi(private val client: PixivClient) {
    /** Manga Pixiv recommends, under the reader's choices like any feed. */
    suspend fun mangaRecommended(next: String? = null): Page<PixivIllust> = client.illustPage(
        client.getPage("/v1/manga/recommended", mapOf("include_ranking_label" to "true", "filter" to ANDROID), next),
    )

    /**
     * One ranking board ([PixivRanking.ILLUST_MODES]; Pixiv has no
     * `/v1/ranking/illust`). [date] opens that day's archived board; none is
     * the latest. An AI board keeps its AI works under Hide AI: the reader
     * asked for it by name.
     */
    suspend fun ranking(mode: String, date: LocalDate? = null, next: String? = null): Page<PixivIllust> {
        val query = buildMap {
            put("mode", mode)
            date?.let { put("date", it.toString()) }
            put("filter", ANDROID)
        }
        return client.illustPage(client.getPage("/v1/illust/ranking", query, next), includeAi = if (PixivRanking.isAi(mode)) true else null)
    }

    /** Creators Pixiv suggests, each with the preview works the reader's choices allow. */
    suspend fun recommendedUsers(next: String? = null): Page<PixivUserPreview> {
        val json = client.getPage("/v1/user/recommended", mapOf("filter" to ANDROID), next)
        return Page(PixivUserPreview.list(json, client.showR18, !client.hideAi), nextOf(json))
    }

    /** Pixivision's articles from every category, newest first. */
    suspend fun spotlightArticles(next: String? = null): Page<PixivSpotlightArticle> {
        val json = client.getPage("/v1/spotlight/articles", mapOf("filter" to ANDROID, "category" to "all"), next)
        return Page(json["spotlight_articles"].list.mapNotNull { PixivSpotlightArticle.parse(it) }, nextOf(json))
    }

    /** One page of an illustration or manga series, its header with it. */
    suspend fun illustSeries(seriesId: Long, next: String? = null): PixivSeriesPage {
        val json = client.getPage("/v1/illust/series", mapOf("illust_series_id" to "$seriesId", "filter" to ANDROID), next)
        return PixivSeriesPage(PixivIllustSeries.parse(json["illust_series_detail"]), client.illustPage(json))
    }

    /** Where [illustId] sits in its series; neighbours the reader's choices would hide are left out. */
    suspend fun seriesContext(illustId: Long): PixivSeriesContext? = PixivSeriesContext.parse(
        client.getJson("/v1/illust-series/illust", mapOf("illust_id" to "$illustId")),
    ) { it.allowed(client.showR18, !client.hideAi) }

    /** The manga series the reader watches. */
    suspend fun mangaWatchlist(next: String? = null): Page<PixivWatchlistSeries> {
        val json = client.getPage("/v1/watchlist/manga", emptyMap(), next)
        return Page(PixivWatchlistSeries.list(json), nextOf(json))
    }

    /** The showcase Pixiv offers before sign-in: the one read that takes no token. */
    suspend fun walkthrough(next: String? = null): Page<PixivIllust> =
        client.illustPage(client.getJson(next?.ifEmpty { null } ?: "/v1/walkthrough/illusts", auth = false))

    /** A Pixivision article in the reader's language, read from its web page as a desktop browser, without any token. */
    suspend fun pixivisionArticle(id: Long): PixivisionArticle {
        val language = Pixivision.language(client.locale())
        val url = Pixivision.articleUrl(id, language).toHttpUrl()
        val headers = mapOf(
            "User-Agent" to Pixivision.USER_AGENT,
            "Referer" to "https://www.pixivision.net/$language/",
            "Accept-Language" to pixivAcceptLanguage(client.locale()),
            "Accept" to "text/html",
        )
        val response = client.http.get(url, headers, followRedirects = true)
        if (!response.isSuccessful) throw NetworkRequestException(PIXIVISION_NETWORK, response.code, "$url: HTTP ${response.code}")
        val article = withContext(Dispatchers.Default) { Pixivision.parse(response.body) }
        if (article.isEmpty) throw NetworkRequestException(PIXIVISION_NETWORK, response.code, "$url: no article body")
        return article
    }
}

/** A series' page on pixiv.net, which is also what its links look like. */
fun pixivSeriesUrl(userId: Long, seriesId: Long): String = "https://www.pixiv.net/user/$userId/series/$seriesId"
