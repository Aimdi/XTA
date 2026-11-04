package com.aimdi.xtanative.plugins.threads

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.threads.ThreadsPost
import com.aimdi.xtanative.ui.plugins.currentLanguage
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.rememberOpenOutside
import com.aimdi.xtanative.ui.plugins.rememberShareText
import com.aimdi.xtanative.ui.post.PostIcons

/**
 * A post's actions (plugin_post_actions.dart): its link shared, or opened on
 * the web. Saving it, its note and its author's groups are the app's.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ThreadsPostActions(app: ThreadsApp, post: ThreadsPost, onDismiss: () -> Unit) {
    val url = post.url ?: post.id
    val share = rememberShareText()
    val outside = rememberOpenOutside()
    val copied = Strings.link_copied.get(currentLanguage())
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 8.dp)) {
            SheetRow(Icons.Filled.Share, pluginString(Strings.share_link)) {
                onDismiss()
                if (!share(url)) app.notices.tell(copied)
            }
            SheetRow(PostIcons.OpenInNew, pluginString(Strings.open_in_browser)) {
                onDismiss()
                outside(url)
            }
        }
    }
}

@Composable
internal fun SheetRow(icon: ImageVector, label: String, supporting: String? = null, onClick: () -> Unit) {
    ListItem(
        leadingContent = { Icon(icon, contentDescription = null) },
        headlineContent = { Text(label) },
        supportingContent = supporting?.let { { Text(it) } },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

/** A short line under or between rows, in the muted colour. */
@Composable
internal fun NoteText(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = modifier.fillMaxWidth())
}
