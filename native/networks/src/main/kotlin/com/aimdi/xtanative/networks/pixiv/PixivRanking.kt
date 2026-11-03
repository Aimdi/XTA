package com.aimdi.xtanative.networks.pixiv

/**
 * One ranking board: the mode Pixiv names it by, the XTA string that labels
 * it, and the gates it sits behind.
 */
data class PixivRankingMode(
    val id: String,
    /** The key of its label among XTA's strings. */
    val labelKey: String,
    /** Offered only while Show R-18 is on. */
    val r18: Boolean = false,
    /** An AI board, which keeps its AI works whatever Hide AI says. */
    val ai: Boolean = false,
)

/** Pixiv's ranking boards for works and for novels, in the order XTA's chips show them. */
object PixivRanking {
    /** Every illustration board. `day_manga` is XTA's own extra; the rest are the boards Pixiv's app offers. */
    val ILLUST_MODES: List<PixivRankingMode> = listOf(
        PixivRankingMode("day", "plugin_pixiv_ranking_day"),
        PixivRankingMode("week", "plugin_pixiv_ranking_week"),
        PixivRankingMode("month", "plugin_pixiv_ranking_month"),
        PixivRankingMode("day_male", "plugin_pixiv_ranking_day_male"),
        PixivRankingMode("day_female", "plugin_pixiv_ranking_day_female"),
        PixivRankingMode("week_original", "plugin_pixiv_ranking_week_original"),
        PixivRankingMode("week_rookie", "plugin_pixiv_ranking_rookie"),
        PixivRankingMode("day_manga", "plugin_pixiv_ranking_day_manga"),
        PixivRankingMode("day_ai", "plugin_pixiv_ranking_day_ai", ai = true),
        PixivRankingMode("day_r18", "plugin_pixiv_ranking_day_r18", r18 = true),
        PixivRankingMode("day_r18_ai", "plugin_pixiv_ranking_day_r18_ai", r18 = true, ai = true),
        PixivRankingMode("week_r18", "plugin_pixiv_ranking_week_r18", r18 = true),
        PixivRankingMode("week_r18g", "plugin_pixiv_ranking_week_r18g", r18 = true),
    )

    /** Every novel board. Pixiv's only AI novel boards are weekly, so the illustration labels name them. */
    val NOVEL_MODES: List<PixivRankingMode> = listOf(
        PixivRankingMode("day", "plugin_pixiv_ranking_day"),
        PixivRankingMode("week", "plugin_pixiv_ranking_week"),
        PixivRankingMode("day_male", "plugin_pixiv_ranking_day_male"),
        PixivRankingMode("day_female", "plugin_pixiv_ranking_day_female"),
        PixivRankingMode("week_ai", "plugin_pixiv_ranking_day_ai", ai = true),
        PixivRankingMode("day_r18", "plugin_pixiv_ranking_day_r18", r18 = true),
        PixivRankingMode("week_ai_r18", "plugin_pixiv_ranking_day_r18_ai", r18 = true, ai = true),
        PixivRankingMode("week_r18", "plugin_pixiv_ranking_week_r18", r18 = true),
        PixivRankingMode("week_r18g", "plugin_pixiv_ranking_week_r18g", r18 = true),
    )

    /** Whether [id] names an AI board of [table]. */
    fun isAi(id: String, table: List<PixivRankingMode> = ILLUST_MODES): Boolean = table.any { it.id == id && it.ai }

    /** The boards a reader may pick: R-18 boards only while Show R-18 is on. */
    fun offered(table: List<PixivRankingMode>, showR18: Boolean): List<PixivRankingMode> = table.filter { showR18 || !it.r18 }
}
