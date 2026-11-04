package com.aimdi.xtanative.ui.settings

import androidx.compose.runtime.Composable

/**
 * Asks for a picture, the platform's way: Android's picker of images, the
 * desktop's open dialog titled [title] over image files. The returned
 * function opens it; [onChosen] gets a way to read the picture, off the main
 * thread, which gives null for one that cannot be read or is too large.
 * Backing out calls nothing.
 */
@Composable
internal expect fun rememberImageChooser(title: String, onChosen: (read: suspend () -> ByteArray?) -> Unit): () -> Unit
