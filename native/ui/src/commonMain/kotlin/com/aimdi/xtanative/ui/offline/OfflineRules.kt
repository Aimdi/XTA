package com.aimdi.xtanative.ui.offline

import com.aimdi.xtanative.store.offline.OfflineEntry
import com.aimdi.xtanative.store.offline.OfflineKind
import com.aimdi.xtanative.store.offline.OfflineSaved
import com.aimdi.xtanative.store.offline.OfflineState
import com.aimdi.xtanative.store.saved.SavedContent
import com.aimdi.xtanative.ui.saved.KeepNotice
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.util.Locale

/** The icon an entry of the offline library starts with. */
enum class OfflineIcon { SENSITIVE, ARTICLE, MEDIA }

/** What tapping an entry of the offline library does. */
enum class OfflineOpen { NOTHING, MEDIA, SENSITIVE_MEDIA, ARTICLE }

/** How much of an entry was kept: an article's text with [saved] of its [total] pictures, or [saved] of a post's [total] media files. */
data class OfflineDetail(val article: Boolean, val saved: Int, val total: Int)

/** The offline library's rules (offline_ui.dart, offline_library_screen.dart). */
object OfflineRules {
    fun icon(entry: OfflineEntry): OfflineIcon = when {
        entry.sensitive -> OfflineIcon.SENSITIVE
        entry.kind == OfflineKind.ARTICLE -> OfflineIcon.ARTICLE
        else -> OfflineIcon.MEDIA
    }

    fun detail(entry: OfflineEntry): OfflineDetail = OfflineDetail(entry.hasArticle, entry.files.size, entry.totalMedia)

    /** A sensitive post's media opens only once the reader agrees to see it. */
    fun open(entry: OfflineEntry): OfflineOpen = when {
        !entry.canOpen -> OfflineOpen.NOTHING
        entry.kind == OfflineKind.MEDIA && entry.sensitive -> OfflineOpen.SENSITIVE_MEDIA
        entry.kind == OfflineKind.MEDIA -> OfflineOpen.MEDIA
        else -> OfflineOpen.ARTICLE
    }

    /** How full the library is, from 0 to 1. */
    fun fullness(bytes: Long, limit: Long): Float = if (limit <= 0) 1f else (bytes.toDouble() / limit).coerceIn(0.0, 1.0).toFloat()

    /** [bytes] as XTA writes the library's sizes: B, KiB or MiB with one decimal at most, in [locale]'s digits. */
    fun bytes(bytes: Long, locale: Locale): String {
        val decimal = DecimalFormat("0.#", DecimalFormatSymbols.getInstance(locale))
        return when {
            bytes < 1024 -> "${NumberFormat.getIntegerInstance(locale).format(bytes)} B"
            bytes < 1024 * 1024 -> "${decimal.format(bytes / 1024.0)} KiB"
            else -> "${decimal.format(bytes / (1024.0 * 1024.0))} MiB"
        }
    }

    /** Clear is offered while anything is kept or being kept. */
    fun canClear(state: OfflineState): Boolean = state.entries.isNotEmpty() || state.busy.isNotEmpty()

    /** What keeping [id] said once it ended: it failed, nothing could be kept, or how much was. */
    fun afterKeeping(state: OfflineState, id: String): KeepNotice {
        val entry = state.entry(id)
        return when {
            id in state.failed -> KeepNotice.OfflineFailed
            entry == null -> KeepNotice.OfflineUnavailable
            else -> detail(entry).let { KeepNotice.OfflineKept(it.article, it.saved, it.total) }
        }
    }
}

/** What a saved post's "keep offline" row says under its title. */
sealed interface SavedOfflineLine {
    /** Reddit's video is kept without its sound. */
    data object VideoOnly : SavedOfflineLine

    data class Kept(val detail: OfflineDetail) : SavedOfflineLine

    /** Not kept yet: none of its [total] media files. */
    data class NotKept(val total: Int) : SavedOfflineLine

    companion object {
        fun of(content: SavedContent, entry: OfflineEntry?): SavedOfflineLine = when {
            content.kind == SavedContent.Kind.REDDIT && content.post["videoFallbackUrl"].string != null -> VideoOnly
            entry != null -> Kept(OfflineRules.detail(entry))
            else -> NotKept(OfflineSaved.media(content).size)
        }
    }
}
