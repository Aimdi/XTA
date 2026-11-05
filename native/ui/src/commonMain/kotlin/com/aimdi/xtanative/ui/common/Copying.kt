package com.aimdi.xtanative.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

/** [text] as the platform's clipboard holds plain text. */
internal expect fun plainTextClip(text: String): ClipEntry

/**
 * What puts a text on the clipboard. It is put there before the call
 * returns, so a sheet or screen that closes right after loses nothing.
 */
@Composable
fun rememberCopyText(): (String) -> Unit {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    return remember(clipboard, scope) {
        { text -> scope.launch(start = CoroutineStart.UNDISPATCHED) { clipboard.setClipEntry(plainTextClip(text)) } }
    }
}
