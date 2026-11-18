package com.aimdi.xtanative.ui.pluginstore

import androidx.compose.runtime.Composable
import com.aimdi.xtanative.data.plugins.LocalSheetEntries
import com.aimdi.xtanative.ui.plugins.PluginSharedPost
import com.aimdi.xtanative.ui.plugins.currentLanguage
import com.aimdi.xtanative.ui.post.MenuRow

/**
 * The running plugins' entries for [post] in a post sheet, below the app's
 * own, as XTA's share sheet ends with them: each closes the sheet, then runs
 * beyond it.
 */
@Composable
fun PluginSheetEntries(post: PluginSharedPost, onDismiss: () -> Unit) {
    val entries = LocalSheetEntries.current(post, currentLanguage())
    entries.forEach { entry ->
        MenuRow(entry.icon, entry.label) {
            onDismiss()
            entry.run()
        }
    }
}
