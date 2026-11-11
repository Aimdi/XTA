package com.aimdi.xtanative.ui.plugins

import androidx.compose.runtime.Composable

/**
 * Asks the reader for a text file (an OPML list, a plugin's export), the
 * platform's way: Android's document picker, the desktop's open dialog titled
 * [title]. The returned function opens it; [onChosen] gets a way to read the
 * chosen file's text, to call off the main thread. Backing out calls nothing.
 */
@Composable
expect fun rememberTextFileChooser(title: String, onChosen: (read: suspend () -> String?) -> Unit): () -> Unit

/**
 * Asks the reader for a picture (one to search by, XTA's Pixiv's Search by
 * image), the platform's way: Android's document picker over images, the
 * desktop's open dialog titled [title] showing pictures. The returned
 * function opens it; [onChosen] gets a way to read the chosen file's bytes,
 * to call off the main thread. Backing out calls nothing.
 */
@Composable
expect fun rememberImageChooser(title: String, onChosen: (read: suspend () -> ByteArray?) -> Unit): () -> Unit

/**
 * Saves a text as a file the reader names and places, the platform's way:
 * Android's save dialog (the Files app, Drive, …), the desktop's save dialog
 * titled [title], opened on Downloads. [mime] is the file's type. The returned
 * function takes the suggested name and the text; [onSaved] hears the name the
 * file went under, or null when the reader backed out or it could not be written.
 */
@Composable
expect fun rememberTextFileSaver(title: String, mime: String, onSaved: (String?) -> Unit): (fileName: String, text: String) -> Unit

/**
 * [rememberTextFileSaver] for a caller that tells a failed write from a
 * reader who backed out, as XTA's exports do: [onSaved] hears the name the
 * file went under, [onFailed] that it was placed but could not be written,
 * and backing out of the dialog calls neither.
 */
@Composable
expect fun rememberTextFileSaver(title: String, mime: String, onSaved: (String) -> Unit, onFailed: () -> Unit): (fileName: String, text: String) -> Unit

/** How a save ended, told: nothing when the reader did not place the file, [onSaved] with the [name] it went under, [onFailed] when it has none. */
internal fun textSaveEnded(placed: Boolean, name: String?, onSaved: (String) -> Unit, onFailed: () -> Unit) {
    if (!placed) return
    if (name != null) onSaved(name) else onFailed()
}
