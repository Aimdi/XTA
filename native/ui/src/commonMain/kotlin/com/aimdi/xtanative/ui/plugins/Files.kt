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
 * Saves a text as a file the reader names and places, the platform's way:
 * Android's save dialog (the Files app, Drive, …), the desktop's save dialog
 * titled [title], opened on Downloads. [mime] is the file's type. The returned
 * function takes the suggested name and the text; [onSaved] hears the name the
 * file went under, or null when the reader backed out or it could not be written.
 */
@Composable
expect fun rememberTextFileSaver(title: String, mime: String, onSaved: (String?) -> Unit): (fileName: String, text: String) -> Unit
