package com.aimdi.xtanative.networks.pixiv

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.networks.NetworkRequestException
import com.aimdi.xtanative.networks.Page
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The novel side of Pixiv, read: feeds, rankings, bookmarks, the series
 * watchlist, series, search, trending tags, a creator's novels, and a
 * novel's text. Lists follow the reader's Show R-18 and Hide AI choices,
 * except the reader's own, which keep what they saved or wrote.
 */
class PixivNovelApi(private val client: PixivClient) {
    /** A list payload as the reader's choices show it; [ownList] keeps everything the reader saved on purpose. */
    fun novelPage(json: J, includeAi: Boolean? = null, ownList: Boolean = false): Page<PixivNovel> =
        Page(PixivNovel.list(json, ownList || client.showR18, includeAi ?: (ownList || !client.hideAi)), nextOf(json))

    /** Whether the reader's choices let [novel] through. */
    fun allowed(novel: PixivNovel): Boolean = novel.allowed(client.showR18, !client.hideAi)

    /** The novels Pixiv recommends, today's ranking mixed in. */
    suspend fun recommended(next: String? = null): Page<PixivNovel> = novelPage(
        client.getPage(
            "/v1/novel/recommended",
            mapOf("include_privacy_policy" to "true", "filter" to ANDROID, "include_ranking_novels" to "true"),
            next,
        ),
    )

    /** New novels from followed creators: [restrict] is `public` or `private`. */
    suspend fun following(restrict: String = "public", next: String? = null): Page<PixivNovel> =
        novelPage(client.getPage("/v1/novel/follow", mapOf("restrict" to restrict), next))

    /**
     * One novel ranking board ([PixivRanking.NOVEL_MODES]); [date] opens that
     * day's archived board. An AI board keeps its AI novels under Hide AI: the
     * reader asked for it by name.
     */
    suspend fun ranking(mode: String, date: LocalDate? = null, next: String? = null): Page<PixivNovel> {
        val query = buildMap {
            put("mode", mode)
            date?.let { put("date", it.toString()) }
            put("filter", ANDROID)
        }
        val ai = PixivRanking.isAi(mode, PixivRanking.NOVEL_MODES)
        return novelPage(client.getPage("/v1/novel/ranking", query, next), includeAi = if (ai) true else null)
    }

    /** [userId]'s novel bookmarks; the reader's own keep R-18 and AI novels, someone else's follow the reader's choices. */
    suspend fun bookmarks(userId: Long, restrict: String = "public", next: String? = null): Page<PixivNovel> = novelPage(
        client.getPage("/v1/user/bookmarks/novel", mapOf("user_id" to "$userId", "restrict" to restrict), next),
        ownList = userId == client.storedUserId,
    )

    /** The reader's own novel bookmarks. */
    suspend fun ownBookmarks(restrict: String = "public", next: String? = null): Page<PixivNovel> =
        bookmarks(client.ensureUserId(), restrict, next)

    /** The novel series the reader watches; rows that do not parse are skipped. */
    suspend fun watchlist(next: String? = null): Page<PixivWatchlistSeries> {
        val json = client.getPage("/v1/watchlist/novel", emptyMap(), next)
        return Page(PixivWatchlistSeries.list(json), nextOf(json))
    }

    /** One novel's card, for a novel opened by its id alone; a 404 when Pixiv no longer has it or withholds it. */
    suspend fun detail(novelId: Long): PixivNovel =
        PixivNovel.parse(client.getJson("/v2/novel/detail", mapOf("novel_id" to "$novelId"))["novel"])
            ?: throw NetworkRequestException(PIXIV_NETWORK, 404, "no novel $novelId")

    /** One page of `/v1/search/novel` for a [PixivSearchFilter.query] of novels; [includeAi] is the search's own AI choice. */
    suspend fun search(query: Map<String, String>, next: String? = null, includeAi: Boolean? = null): Page<PixivNovel> =
        novelPage(client.getPage("/v1/search/novel", query, next), includeAi = includeAi)

    /** The tags trending among novels, each over the picture Pixiv chose for it. */
    suspend fun trendingTags(): List<PixivTrendTag> = PixivTrendTag.list(
        client.getJson("/v1/trending-tags/novel", mapOf("filter" to ANDROID)),
        client.showR18,
        !client.hideAi,
    )

    /** [userId]'s own novels; the reader's own keep R-18 and AI ones. */
    suspend fun userNovels(userId: Long, next: String? = null): Page<PixivNovel> = novelPage(
        client.getPage("/v1/user/novels", mapOf("user_id" to "$userId", "filter" to ANDROID), next),
        ownList = userId == client.storedUserId,
    )

    /** A novel's text, its series neighbours and its pictures, read from the page Pixiv's app shows it in. */
    suspend fun content(novelId: Long): PixivNovelContent {
        val html = client.getText("/webview/v2/novel", mapOf("id" to "$novelId"))
        return withContext(Dispatchers.Default) { PixivNovelContent.fromHtml(html) }
            ?: throw unusableAnswer("no text in novel $novelId")
    }

    /** One page of a series; chapters the reader's choices hide are left out but counted. */
    suspend fun series(seriesId: Long, next: String? = null): PixivNovelSeriesPage =
        PixivNovelSeriesPage.parse(client.getPage("/v2/novel/series", mapOf("series_id" to "$seriesId"), next), ::allowed)
}
