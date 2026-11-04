package com.aimdi.xtanative.plugins.pixiv

import com.aimdi.xtanative.networks.pixiv.PixivImageQuality
import com.aimdi.xtanative.networks.pixiv.PixivRanking
import com.aimdi.xtanative.networks.pixiv.PixivRankingMode
import com.aimdi.xtanative.store.settings.PixivDetailLayout
import com.aimdi.xtanative.store.settings.PixivImageQuality as StoredQuality
import com.aimdi.xtanative.store.settings.PixivPrefs
import com.aimdi.xtanative.store.settings.PixivSection
import com.aimdi.xtanative.store.settings.Pref
import com.aimdi.xtanative.store.settings.Settings

/** The ranking boards pinned as chips, one list for works and one for novels, as XTA keeps them. */
internal object PixivRankingPins {
    fun prefOf(novels: Boolean): Pref<List<String>> = if (novels) PixivPrefs.novelRankingModes else PixivPrefs.rankingModes

    fun tableOf(novels: Boolean): List<PixivRankingMode> = if (novels) PixivRanking.NOVEL_MODES else PixivRanking.ILLUST_MODES

    /** The pins kept for [novels]: unknown and repeated boards dropped, the defaults when none is left. */
    fun of(settings: Settings, novels: Boolean): List<String> {
        val pref = prefOf(novels)
        val known = tableOf(novels).map { it.id }.toSet()
        return settings[pref].filter { it in known }.distinct().ifEmpty { pref.default }
    }

    /** The chips the row shows: pinned boards the reader may see, in the table's order, never none. */
    fun visible(pins: List<String>, table: List<PixivRankingMode>, showR18: Boolean): List<PixivRankingMode> =
        PixivRanking.offered(table, showR18).filter { it.id in pins }.ifEmpty { listOf(table.first()) }

    /** The board to show: the chosen one while its chip is there, else the first chip. */
    fun effective(chosen: String, visible: List<PixivRankingMode>): String = if (visible.any { it.id == chosen }) chosen else visible.first().id

    /** [pins] with [id] pinned or unpinned, in the table's order. */
    fun toggled(pins: List<String>, id: String, table: List<PixivRankingMode>): List<String> =
        table.map { it.id }.filter { if (it == id) it !in pins else it in pins }

    /** Whether unpinning [id] would leave no chip the reader may see. */
    fun lastVisible(pins: List<String>, id: String, offered: List<PixivRankingMode>): Boolean =
        id in pins && offered.count { it.id in pins } <= 1
}

/** Where an image size is chosen, the sizes offered there and the one it starts at. Downloads always save the original. */
internal enum class PixivQualitySlot(val pref: Pref<StoredQuality>) {
    FEED(PixivPrefs.qualityFeed),
    DETAIL(PixivPrefs.qualityDetail),
    READER(PixivPrefs.qualityReader),
    ;

    val choices: List<StoredQuality>
        get() = when (this) {
            FEED -> listOf(StoredQuality.MEDIUM, StoredQuality.LARGE)
            DETAIL -> StoredQuality.entries
            READER -> listOf(StoredQuality.LARGE, StoredQuality.ORIGINAL)
        }

    /** The size picked here, as the reading layer names it. */
    fun of(settings: Settings): PixivImageQuality = when (settings[pref]) {
        StoredQuality.MEDIUM -> PixivImageQuality.MEDIUM
        StoredQuality.LARGE -> PixivImageQuality.LARGE
        StoredQuality.ORIGINAL -> PixivImageQuality.ORIGINAL
    }
}

/** How works lay out: the grid's columns and whether a work's pictures sit beside its details. */
internal object PixivLayout {
    /** The fixed counts a reader can pick instead of letting the width decide (0). */
    val COLUMN_CHOICES = listOf(2, 3, 4)

    /** Below this a forced count drops columns, so a narrow pane never shows slivers of art. */
    private const val MIN_TILE_WIDTH = 96f

    /** Columns for a works grid [width] dp wide: the count picked for the orientation, else as many as fit art readably. */
    fun columns(width: Float, picked: Int, largeText: Boolean): Int {
        if (picked !in COLUMN_CHOICES) return galleryColumns(width, largeText)
        return picked.coerceAtMost((width / MIN_TILE_WIDTH).toInt()).coerceAtLeast(1)
    }

    /** XTA's gallery layout: artwork stays readable at large text and gains columns on wide views, four at most. */
    fun galleryColumns(width: Float, largeText: Boolean): Int = (width / (if (largeText) 240f else 152f)).toInt().coerceIn(1, 4)

    /** The column count stored for the orientation. */
    fun picked(settings: Settings, landscape: Boolean): Int =
        settings[if (landscape) PixivPrefs.gridColumnsLandscape else PixivPrefs.gridColumnsPortrait]

    const val SPLIT_MIN_WIDTH = 840f
    const val SPLIT_MIN_INFO = 320f
    const val SPLIT_MIN_IMAGES = 240f
    const val SPLIT_GUTTER = 16f

    /** Whether a work's detail [width] dp wide puts its pictures beside the details. */
    fun splits(width: Float, layout: PixivDetailLayout): Boolean = when (layout) {
        PixivDetailLayout.AUTO -> width >= SPLIT_MIN_WIDTH
        PixivDetailLayout.VERTICAL -> false
        PixivDetailLayout.SPLIT -> width >= SPLIT_MIN_IMAGES + SPLIT_GUTTER + SPLIT_MIN_INFO
    }

    /** The picture pane of a [width]-wide split at [fraction], leaving the details and the pictures at least their minimum. */
    fun splitImageWidth(width: Float, fraction: Float): Float {
        val widest = maxOf(SPLIT_MIN_IMAGES, width - SPLIT_GUTTER - SPLIT_MIN_INFO)
        return (width * fraction).coerceIn(SPLIT_MIN_IMAGES, widest)
    }

    /** How tall the pages of a work [illustWidth] × [illustHeight] stand above its details: its shape, within the screen. */
    fun viewerHeight(screenWidth: Float, screenHeight: Float, illustWidth: Int, illustHeight: Int): Float {
        val ratio = if (illustWidth > 0 && illustHeight > 0) illustWidth.toFloat() / illustHeight else 1f
        return (screenWidth / ratio).coerceIn(160f, maxOf(160f, screenHeight * 0.70f))
    }
}

/** The five sections the home tab switches between, in tab order; the first four may be where it opens. */
internal enum class PixivTab { HOME, RANKING, FAVORITES, SEARCH, MORE;

    companion object {
        fun of(section: PixivSection): PixivTab = when (section) {
            PixivSection.HOME -> HOME
            PixivSection.RANKING -> RANKING
            PixivSection.FAVORITES -> FAVORITES
            PixivSection.SEARCH -> SEARCH
        }
    }
}
