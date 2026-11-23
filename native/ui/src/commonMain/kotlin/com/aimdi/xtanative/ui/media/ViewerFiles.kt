package com.aimdi.xtanative.ui.media

import androidx.compose.runtime.Composable

/**
 * What the media viewer's right-click menu, Ctrl+C and Ctrl+S do where the
 * platform has them (the desktop, as a browser's image menu): the picture
 * itself onto the clipboard, and a file saved where the reader picks.
 */
internal interface ViewerFileActions {
    /** The picture at [url] on the clipboard; whether it got there. */
    suspend fun copyImage(url: String): Boolean

    /** [save] saved where the reader picks in the save dialog, as a download is, whatever the download setting says. */
    fun saveAs(save: MediaSave)
}

/** The desktop's [ViewerFileActions]; null on Android, whose viewer shares and saves through its bar. */
@Composable
internal expect fun rememberViewerFileActions(): ViewerFileActions?
