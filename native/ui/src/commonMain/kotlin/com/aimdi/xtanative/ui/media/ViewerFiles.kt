package com.aimdi.xtanative.ui.media

import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.aimdi.xtanative.core.x.model.XMedia
import com.aimdi.xtanative.ui.common.rememberCopyText
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.media_copy_failed
import com.aimdi.xtanative.ui.generated.resources.media_copy_image
import com.aimdi.xtanative.ui.generated.resources.media_save_as
import com.aimdi.xtanative.ui.generated.resources.posts_copied
import com.aimdi.xtanative.ui.generated.resources.reader_link_copied
import com.aimdi.xtanative.ui.generated.resources.reader_link_copy
import com.aimdi.xtanative.ui.post.PostMenuSurface
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

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

/**
 * The desktop viewers' file menu, one for the app's viewer and the
 * plugins': [open] opens it at the pointer for what is shown, [key] takes
 * Ctrl+C (a picture) and Ctrl+S, and [Menu] draws it. Copy image, Save as…
 * and Copy link; what happened is said in [snackbar].
 */
internal class ViewerFileMenu(
    private val actions: ViewerFileActions,
    private val shown: () -> MediaSave?,
    private val texts: Texts,
    private val later: (suspend () -> Unit) -> Unit,
    private val say: suspend (String) -> Unit,
    private val copyLink: (String) -> Unit,
    private val refocus: () -> Unit,
) {
    /** What the menu says when it is done, in the reader's language. */
    data class Texts(val copied: String, val notCopied: String, val linkCopied: String)

    var open by mutableStateOf<MediaSave?>(null)
        private set

    /** Opens the menu for the page shown, where it shows something. */
    fun openForShown() {
        open = shown()
    }

    /** The viewer's Ctrl+C and Ctrl+S; whether the key was taken. */
    fun key(key: ViewerKey): Boolean {
        val save = shown() ?: return false
        return when (key) {
            ViewerKey.COPY -> (save.kind == XMedia.Kind.PHOTO).also { if (it) copy(save) }
            ViewerKey.SAVE_AS -> true.also { actions.saveAs(save) }
            else -> false
        }
    }

    private fun copy(save: MediaSave) = later { say(if (actions.copyImage(save.url)) texts.copied else texts.notCopied) }

    private fun close() {
        open = null
        refocus()
    }

    @Composable
    fun Menu() {
        val save = open ?: return
        PostMenuSurface(onDismiss = ::close) {
            if (save.kind == XMedia.Kind.PHOTO) Item(Res.string.media_copy_image) { copy(save) }
            Item(Res.string.media_save_as) { actions.saveAs(save) }
            Item(Res.string.reader_link_copy) {
                copyLink(save.url)
                later { say(texts.linkCopied) }
            }
        }
    }

    @Composable
    private fun Item(label: StringResource, action: () -> Unit) {
        DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = {
            close()
            action()
        })
    }
}

/** The viewer's [ViewerFileMenu] over what [shown] says the page shows, telling in [snackbar]; null where the platform has none (Android). */
@Composable
internal fun rememberViewerFileMenu(snackbar: SnackbarHostState, refocus: () -> Unit, shown: () -> MediaSave?): ViewerFileMenu? {
    val actions = rememberViewerFileActions() ?: return null
    val scope = rememberCoroutineScope()
    val copyText = rememberCopyText()
    val latestShown by rememberUpdatedState(shown)
    val latestRefocus by rememberUpdatedState(refocus)
    val texts = ViewerFileMenu.Texts(stringResource(Res.string.posts_copied), stringResource(Res.string.media_copy_failed), stringResource(Res.string.reader_link_copied))
    return remember(actions, snackbar, texts) {
        ViewerFileMenu(
            actions = actions,
            shown = { latestShown() },
            texts = texts,
            later = { work -> scope.launch { work() } },
            say = { text -> snackbar.showSnackbar(text) },
            copyLink = copyText,
            refocus = { latestRefocus() },
        )
    }
}
