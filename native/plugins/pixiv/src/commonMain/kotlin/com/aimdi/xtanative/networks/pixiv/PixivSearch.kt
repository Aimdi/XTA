package com.aimdi.xtanative.networks.pixiv

import com.aimdi.xtanative.networks.Page
import java.time.LocalDate

/** Where a search looks. */
enum class PixivSearchTarget(val api: String) {
    PARTIAL_TAGS("partial_match_for_tags"),
    EXACT_TAGS("exact_match_for_tags"),
    TITLE_CAPTION("title_and_caption"),

    /** A novel's body text. */
    TEXT("text"),

    /** A novel's tags, title and caption together. */
    KEYWORD("keyword"),
}

/** How a search's results are ordered. */
enum class PixivSearchSort(val api: String) {
    NEWEST("date_desc"),
    OLDEST("date_asc"),
    POPULAR("popular_desc"),
    POPULAR_MALE("popular_male_desc"),
    POPULAR_FEMALE("popular_female_desc"),
    ;

    val byPopularity: Boolean get() = this != NEWEST && this != OLDEST
}

/** What a search looks for. Works and novels each have their own places to look, and their own orders, some for Premium only. */
enum class PixivSearchKind(
    val targets: List<PixivSearchTarget>,
    val sorts: List<PixivSearchSort>,
    /** Orders Pixiv refuses an account without Premium. */
    val premiumSorts: Set<PixivSearchSort>,
) {
    WORKS(
        listOf(PixivSearchTarget.PARTIAL_TAGS, PixivSearchTarget.EXACT_TAGS, PixivSearchTarget.TITLE_CAPTION),
        PixivSearchSort.entries,
        setOf(PixivSearchSort.POPULAR_MALE, PixivSearchSort.POPULAR_FEMALE),
    ),
    NOVELS(
        listOf(PixivSearchTarget.PARTIAL_TAGS, PixivSearchTarget.EXACT_TAGS, PixivSearchTarget.TEXT, PixivSearchTarget.KEYWORD),
        listOf(PixivSearchSort.NEWEST, PixivSearchSort.OLDEST, PixivSearchSort.POPULAR),
        setOf(PixivSearchSort.POPULAR),
    ),
    ;

    /** Only works come as ugoira, take bookmark brackets and have a free popular preview. */
    val isWorks: Boolean get() = this == WORKS

    /** The orders worth offering: Pixiv answers the Premium ones only to Premium. */
    fun offeredSorts(isPremium: Boolean): List<PixivSearchSort> = sorts.filter { isPremium || it !in premiumSorts }
}

/** Ugoira kept, only ugoira, or none. Pixiv cannot filter this itself, so it runs over each page on the device. */
enum class PixivUgoiraFilter {
    ALL, ONLY, NONE;

    fun applyTo(illusts: List<PixivIllust>): List<PixivIllust> = when (this) {
        ALL -> illusts
        ONLY -> illusts.filter { it.isUgoira }
        NONE -> illusts.filterNot { it.isUgoira }
    }
}

/** The posting dates a search covers. */
enum class PixivDatePreset { ANY, DAY, WEEK, MONTH, HALF_YEAR, YEAR, CUSTOM }

/** Posting dates from [start] to [end], both whole days. */
data class PixivDateRange(val start: LocalDate, val end: LocalDate)

/** A bookmark-count bracket; no [max] means [min] and above. */
data class PixivBookmarkRange(val min: Int, val max: Int? = null)

/** Everything a search narrows by besides its words. */
data class PixivSearchFilter(
    val target: PixivSearchTarget = PixivSearchTarget.PARTIAL_TAGS,
    val sort: PixivSearchSort = PixivSearchSort.NEWEST,
    val datePreset: PixivDatePreset = PixivDatePreset.ANY,
    /** The reader's own dates, used while [datePreset] is custom. */
    val customRange: PixivDateRange? = null,
    /** Only works carrying the `<N>users入り` tag; null for any. */
    val usersIri: Int? = null,
    /** Premium only. */
    val bookmarks: PixivBookmarkRange? = null,
    /** Asks Pixiv to leave AI-generated works out (`search_ai_type=1`). */
    val hideAi: Boolean = false,
    val ugoira: PixivUgoiraFilter = PixivUgoiraFilter.ALL,
) {
    /** A custom [preset] without [custom] dates means any date. */
    fun withDates(preset: PixivDatePreset, custom: PixivDateRange? = null): PixivSearchFilter {
        val usable = preset != PixivDatePreset.CUSTOM || custom != null
        return copy(
            datePreset = if (usable) preset else PixivDatePreset.ANY,
            customRange = if (usable && preset == PixivDatePreset.CUSTOM) custom else null,
        )
    }

    /**
     * What the account may send for [kind]: a place to look the kind does not
     * have goes back to its first one, an order Pixiv will not answer falls
     * back to newest, and the bookmark bracket stays only for works on Premium.
     * Novels take no ugoira choice.
     */
    fun forAccount(isPremium: Boolean, kind: PixivSearchKind = PixivSearchKind.WORKS): PixivSearchFilter = copy(
        target = if (target in kind.targets) target else kind.targets.first(),
        sort = if (sort in kind.offeredSorts(isPremium)) sort else PixivSearchSort.NEWEST,
        bookmarks = if (isPremium && kind.isWorks) bookmarks else null,
        ugoira = if (kind.isWorks) ugoira else PixivUgoiraFilter.ALL,
    )

    /**
     * The `/v1/search/illust` query for [word], or with [kind] novels the
     * `/v1/search/novel` one, with relative dates counted back from [today].
     * Novel search takes no AI or bookmark parameters; Hide AI applies to its
     * pages on the device.
     */
    fun query(word: String, today: LocalDate, kind: PixivSearchKind = PixivSearchKind.WORKS): Map<String, String> {
        val range = range(today)
        val brackets = bookmarks?.takeIf { kind.isWorks }
        return buildMap {
            put("word", searchWord(word, usersIri))
            put("search_target", target.api)
            put("sort", sort.api)
            if (kind.isWorks) put("search_ai_type", if (hideAi) "1" else "0")
            range?.let {
                put("start_date", it.start.toString())
                put("end_date", it.end.toString())
            }
            brackets?.let { bracket ->
                put("bookmark_num_min", "${bracket.min}")
                bracket.max?.let { put("bookmark_num_max", "$it") }
            }
            put("merge_plain_keyword_results", "true")
            put("filter", ANDROID)
        }
    }

    /**
     * The dates [datePreset] covers on [today]. Relative presets are counted
     * afresh each time, so a kept "past week" always means the week before
     * the search; a month back from the 31st lands on a shorter month's last day.
     */
    fun range(today: LocalDate): PixivDateRange? = when (datePreset) {
        PixivDatePreset.ANY -> null
        PixivDatePreset.CUSTOM -> customRange
        PixivDatePreset.DAY -> PixivDateRange(today.minusDays(1), today)
        PixivDatePreset.WEEK -> PixivDateRange(today.minusDays(7), today)
        PixivDatePreset.MONTH -> PixivDateRange(today.minusMonths(1), today)
        PixivDatePreset.HALF_YEAR -> PixivDateRange(today.minusMonths(6), today)
        PixivDatePreset.YEAR -> PixivDateRange(today.minusMonths(12), today)
    }

    companion object {
        /** Pixiv opened in September 2007: nothing was posted before. */
        val EARLIEST_DATE: LocalDate = LocalDate.of(2007, 9, 13)

        /** The popularity tags readers attach (`1000users入り`); they work without Premium. */
        val USERS_IRI_THRESHOLDS = listOf(100, 250, 500, 1000, 5000, 7500, 10000, 20000, 30000, 50000, 100000)

        /** The brackets Pixiv offers Premium accounts. */
        val BOOKMARK_RANGES = listOf(
            PixivBookmarkRange(10000),
            PixivBookmarkRange(50000, 99999),
            PixivBookmarkRange(10000, 49999),
            PixivBookmarkRange(5000, 9999),
            PixivBookmarkRange(1000, 4999),
            PixivBookmarkRange(500, 999),
            PixivBookmarkRange(300, 499),
            PixivBookmarkRange(100, 299),
            PixivBookmarkRange(50, 99),
            PixivBookmarkRange(30, 49),
            PixivBookmarkRange(10, 29),
        )

        /** The words sent: the popularity tag goes on here alone, so the field and the history keep what the reader typed. */
        fun searchWord(word: String, usersIri: Int?): String = if (usersIri == null) word.trim() else "${word.trim()} ${usersIri}users入り"

        /** The query for Pixiv's free popular preview of [word], which also matches translated tags. */
        fun popularPreviewQuery(target: PixivSearchTarget, word: String): Map<String, String> = linkedMapOf(
            "word" to word.trim(),
            "search_target" to target.api,
            "merge_plain_keyword_results" to "true",
            "include_translated_tag_results" to "true",
            "filter" to ANDROID,
        )
    }
}

/** Pixiv's search: works, creators, the free popular preview, and the landing's trending tags, suggested creators and tag suggestions. */
class PixivSearchApi(private val client: PixivClient) {
    val isPremium: Boolean get() = client.isPremium

    /** One page of `/v1/search/illust` for a [PixivSearchFilter.query]; [includeAi] is the search's own AI choice rather than the feeds'. */
    suspend fun illusts(query: Map<String, String>, next: String? = null, includeAi: Boolean? = null): Page<PixivIllust> =
        client.illustPage(client.getPage("/v1/search/illust", query, next), includeAi = includeAi)

    /** Pixiv's free popular preview, which also matches translated tags. */
    suspend fun popularPreview(word: String, target: PixivSearchTarget, next: String? = null, includeAi: Boolean? = null): Page<PixivIllust> =
        client.illustPage(
            client.getPage("/v1/search/popular-preview/illust", PixivSearchFilter.popularPreviewQuery(target, word), next),
            includeAi = includeAi,
        )

    /** Creators matching [word], each with the works Pixiv previews beside them. */
    suspend fun users(word: String, next: String? = null): Page<PixivUserPreview> {
        val json = client.getPage("/v1/search/user", mapOf("word" to word.trim(), "filter" to ANDROID), next)
        return Page(PixivUserPreview.list(json, client.showR18, !client.hideAi), nextOf(json))
    }

    /** The tags trending among works, for the landing. */
    suspend fun trendingTags(): List<PixivTrendTag> = client.trendingTags()

    /** The creators the landing suggests. */
    suspend fun recommendedUsers(): List<PixivUser> =
        PixivUserPreview.list(client.getJson("/v1/user/recommended", mapOf("filter" to ANDROID))).map { it.user }

    /** Tags suggested while [word] is typed. */
    suspend fun autocomplete(word: String): List<PixivTrendTag> = client.autocomplete(word)
}
