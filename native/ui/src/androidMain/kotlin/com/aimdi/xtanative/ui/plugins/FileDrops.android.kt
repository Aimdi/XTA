package com.aimdi.xtanative.ui.plugins

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Android's screens take no dropped files: the plugin's chooser stays the way in. */
@Composable
actual fun Modifier.pluginFileDrop(accept: (name: String, mime: String) -> Boolean, onFiles: (List<PluginDroppedFile>) -> Unit): Modifier = this
