package com.aimdi.xtanative.ui.saved

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.aimdi.xtanative.ui.common.LocalGraph
import com.aimdi.xtanative.ui.downloads.PlatformFilesHost
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.cancel
import com.aimdi.xtanative.ui.generated.resources.downloads_batch
import com.aimdi.xtanative.ui.generated.resources.downloads_cancelled
import com.aimdi.xtanative.ui.generated.resources.downloads_downloading
import com.aimdi.xtanative.ui.generated.resources.downloads_failed
import com.aimdi.xtanative.ui.generated.resources.downloads_open_failed
import com.aimdi.xtanative.ui.generated.resources.groups_changes_saved
import com.aimdi.xtanative.ui.generated.resources.groups_undo
import com.aimdi.xtanative.ui.generated.resources.groups_undo_failed
import com.aimdi.xtanative.ui.generated.resources.downloads_open_file
import com.aimdi.xtanative.ui.generated.resources.downloads_retry_hint
import com.aimdi.xtanative.ui.generated.resources.downloads_saved_to
import com.aimdi.xtanative.ui.generated.resources.offline_article_detail
import com.aimdi.xtanative.ui.generated.resources.offline_failed
import com.aimdi.xtanative.ui.generated.resources.offline_media_count
import com.aimdi.xtanative.ui.generated.resources.offline_unavailable
import com.aimdi.xtanative.ui.generated.resources.saved_long_press_hint
import com.aimdi.xtanative.ui.generated.resources.saved_media_saved
import com.aimdi.xtanative.ui.generated.resources.saved_no_media_file
import com.aimdi.xtanative.ui.generated.resources.saved_note_save_error
import com.aimdi.xtanative.ui.generated.resources.saved_note_saved
import com.aimdi.xtanative.ui.generated.resources.saved_notice_likes
import com.aimdi.xtanative.ui.generated.resources.saved_notice_notes
import com.aimdi.xtanative.ui.generated.resources.saved_notice_saves
import com.aimdi.xtanative.ui.generated.resources.saved_something_wrong
import com.aimdi.xtanative.ui.generated.resources.set_a_download_folder_to_auto_download
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * What saving, liking and downloading need once in the window: the line they
 * say at its foot, and the platform's save dialog for downloads that ask
 * where to go. The window's root composes it once, beside its screens.
 */
@Composable
fun KeepHost() {
    PlatformFilesHost()
    KeepNoticeHost()
}

/** The notice showing now, at the foot of the window above the navigation bar, as XTA's snack bars are. */
@Composable
private fun KeepNoticeHost() {
    val shown by KeepNotices.shown.collectAsState()
    val current = shown ?: return
    val graph = LocalGraph.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(current.serial) {
        delay(
            when {
                current.notice.working -> 60_000L
                current.notice is KeepNotice.ChangesSaved -> 8_000L
                current.notice.long -> 6_000L
                else -> 4_000L
            },
        )
        KeepNotices.hide(current)
    }
    val density = LocalDensity.current
    val lift = WindowInsets.navigationBars.getBottom(density) + with(density) { 16.dp.roundToPx() }
    val open: (@Composable () -> Unit)? = (current.notice as? KeepNotice.SavedTo)?.let { saved ->
        {
            TextButton(onClick = {
                KeepNotices.hide(current)
                scope.launch { if (!graph.platformFiles.open(saved.savedUri, saved.fileName)) KeepNotices.show(KeepNotice.OpenFailed) }
            }) { Text(stringResource(Res.string.downloads_open_file), color = MaterialTheme.colorScheme.inversePrimary) }
        }
    } ?: (current.notice as? KeepNotice.ChangesSaved)?.let { changed ->
        {
            TextButton(onClick = {
                KeepNotices.hide(current)
                graph.keep { if (!changed.undo()) KeepNotices.show(KeepNotice.UndoFailed) }
            }) { Text(stringResource(Res.string.groups_undo), color = MaterialTheme.colorScheme.inversePrimary) }
        }
    }
    val position = remember(lift) { FootOfWindow(lift) }
    val notice = current.notice
    Popup(popupPositionProvider = position, properties = PopupProperties(focusable = false)) {
        Snackbar(modifier = Modifier.padding(horizontal = 16.dp).widthIn(max = 560.dp), action = open ?: (notice as? KeepNotice.Progress)?.onCancel?.let(::cancelButton)) {
            if (notice is KeepNotice.Progress) ProgressLine(notice) else Text(noticeText(notice))
        }
    }
}

private fun cancelButton(onCancel: () -> Unit): @Composable () -> Unit = {
    TextButton(onClick = onCancel) { Text(stringResource(Res.string.cancel), color = MaterialTheme.colorScheme.inversePrimary) }
}

/** A long piece of work: a small ring, filling as it goes once it can say how far, what it does, and its note under it (XTA's PixivWorkingRow). */
@Composable
private fun ProgressLine(notice: KeepNotice.Progress) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val ring = Modifier.size(16.dp)
        val color = MaterialTheme.colorScheme.inversePrimary
        val track = LocalContentColor.current.copy(alpha = 0.16f)
        val fraction = notice.fraction
        if (fraction == null) CircularProgressIndicator(ring, color = color, strokeWidth = 2.dp, trackColor = track)
        else CircularProgressIndicator(progress = { fraction }, modifier = ring, color = color, strokeWidth = 2.dp, trackColor = track)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(notice.text)
            notice.note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

/** Centred at the foot of the window, [lift] pixels above its edge. */
private class FootOfWindow(private val lift: Int) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset =
        IntOffset((windowSize.width - popupContentSize.width) / 2, (windowSize.height - popupContentSize.height - lift).coerceAtLeast(0))
}

/** What [notice] says, in the reader's language. */
@Composable
fun noticeText(notice: KeepNotice): String = when (notice) {
    KeepNotice.Downloading -> stringResource(Res.string.downloads_downloading)
    KeepNotice.MediaSaved -> stringResource(Res.string.saved_media_saved)
    is KeepNotice.SavedTo -> stringResource(Res.string.downloads_saved_to, notice.folder)
    KeepNotice.RetryHint -> stringResource(Res.string.downloads_retry_hint)
    KeepNotice.DownloadFailed -> stringResource(Res.string.downloads_failed)
    KeepNotice.NoMediaFile -> stringResource(Res.string.saved_no_media_file)
    KeepNotice.NeedsDownloadFolder -> stringResource(Res.string.set_a_download_folder_to_auto_download)
    is KeepNotice.Batch -> stringResource(Res.string.downloads_batch, notice.saved, notice.total)
    KeepNotice.FolderHint -> stringResource(Res.string.saved_long_press_hint)
    KeepNotice.LikesStayHere -> stringResource(Res.string.saved_notice_likes)
    is KeepNotice.OnDevice -> "🔒 " + onDeviceText(notice.notice)
    KeepNotice.OpenFailed -> stringResource(Res.string.downloads_open_failed)
    KeepNotice.OfflineFailed -> stringResource(Res.string.offline_failed)
    KeepNotice.OfflineUnavailable -> stringResource(Res.string.offline_unavailable)
    is KeepNotice.OfflineKept ->
        if (notice.article) stringResource(Res.string.offline_article_detail, notice.saved, notice.total)
        else stringResource(Res.string.offline_media_count, notice.saved, notice.total)
    KeepNotice.NoteSaveFailed -> stringResource(Res.string.saved_note_save_error)
    KeepNotice.NoteSaved -> stringResource(Res.string.saved_note_saved)
    KeepNotice.SomethingWentWrong -> stringResource(Res.string.saved_something_wrong)
    is KeepNotice.Said -> notice.text
    is KeepNotice.ChangesSaved -> stringResource(Res.string.groups_changes_saved)
    KeepNotice.UndoFailed -> stringResource(Res.string.groups_undo_failed)
    is KeepNotice.Progress -> notice.text
    KeepNotice.Cancelled -> stringResource(Res.string.downloads_cancelled)
}

/** The line behind the lock: what the open tab keeps stays on this device. */
@Composable
fun onDeviceText(notice: SavedNotice): String = when (notice) {
    SavedNotice.SAVES -> stringResource(Res.string.saved_notice_saves)
    SavedNotice.LIKES -> stringResource(Res.string.saved_notice_likes)
    SavedNotice.NOTES -> stringResource(Res.string.saved_notice_notes)
}
