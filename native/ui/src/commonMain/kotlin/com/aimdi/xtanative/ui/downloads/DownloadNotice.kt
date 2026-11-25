package com.aimdi.xtanative.ui.downloads

import com.aimdi.xtanative.store.downloads.DownloadCenterState
import com.aimdi.xtanative.store.downloads.DownloadEntry
import com.aimdi.xtanative.store.downloads.DownloadStatus

/**
 * What the phone's notification says of the downloads under way: the file
 * arriving now, how many are still to end, and how far they are together.
 * [percent] is null while a size is unknown, for a bar that runs.
 */
data class DownloadNotice(val fileName: String, val count: Int, val percent: Int?) {
    /** One download is named in the title; several stand under the list's own name, the one arriving beneath it. */
    val single: Boolean get() = count == 1
}

/** The notification's rules, apart from Android so they are tested. */
object DownloadNotices {
    /** The notice for [state]; null once nothing runs or waits, when the notification goes. */
    fun of(state: DownloadCenterState): DownloadNotice? {
        val active = state.entries.filter { it.active }.sortedBy { it.createdAt }
        val shown = active.firstOrNull { it.status != DownloadStatus.QUEUED } ?: active.firstOrNull() ?: return null
        return DownloadNotice(shown.fileName, active.size, percent(active))
    }

    /** How much of [active] has arrived, in whole percent so the notification is posted a hundred times at most; null when any size is unknown. */
    private fun percent(active: List<DownloadEntry>): Int? {
        val totals = active.map { it.total?.takeIf { size -> size > 0 } ?: return null }
        val received = active.sumOf { it.received.coerceAtLeast(0) }
        return (received * 100 / totals.sum()).toInt().coerceIn(0, 100)
    }
}
