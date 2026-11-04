package com.aimdi.xtanative.ui.offline

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aimdi.xtanative.data.AppGraph
import com.aimdi.xtanative.store.offline.OfflineArticle
import com.aimdi.xtanative.store.offline.OfflineSaved
import com.aimdi.xtanative.store.saved.SavedContent
import com.aimdi.xtanative.ui.common.LocalGraph
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.cancel
import com.aimdi.xtanative.ui.generated.resources.offline_article_text_only
import com.aimdi.xtanative.ui.generated.resources.offline_keep
import com.aimdi.xtanative.ui.generated.resources.offline_media_count
import com.aimdi.xtanative.ui.generated.resources.offline_remove
import com.aimdi.xtanative.ui.generated.resources.offline_sensitive_description
import com.aimdi.xtanative.ui.generated.resources.offline_sensitive_question
import com.aimdi.xtanative.ui.generated.resources.offline_video_only
import com.aimdi.xtanative.ui.generated.resources.retry
import com.aimdi.xtanative.ui.saved.KeepNotices
import com.aimdi.xtanative.ui.saved.SavedIcons
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** Keeping goes on when the sheet that started it closes: a large video takes a while. */
private val keeping = CoroutineScope(SupervisorJob() + Dispatchers.Default)

/**
 * A saved post's row for keeping its media offline (offline_saved_action.dart):
 * what is kept and how much, a tap that keeps it (a sensitive post's once the
 * reader agrees), and a button that removes the copy. Nothing for a post
 * without media to keep.
 */
@Composable
fun OfflineSavedAction(postId: String, content: SavedContent, modifier: Modifier = Modifier) {
    if (!OfflineSaved.hasMedia(content)) return
    val graph = LocalGraph.current
    val state by graph.offline.state.collectAsStateWithLifecycle()
    LaunchedEffect(graph) { graph.offline.load() }
    var asking by remember { mutableStateOf(false) }
    val key = OfflineSaved.idFor(postId)
    val entry = state.entry(key)
    val busy = key in state.busy
    val keep = { keepSaved(graph, postId, content) }
    ListItem(
        modifier = modifier.clickable(enabled = !busy) { if (OfflineSaved.sensitive(content)) asking = true else keep() },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = {
            if (busy) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            else Icon(if (entry?.canOpen == true) SavedIcons.OfflinePin else SavedIcons.OfflinePinOutline, contentDescription = null)
        },
        headlineContent = { Text(if (entry == null) stringResource(Res.string.offline_keep) else statusText(entry.availability)) },
        supportingContent = {
            Text(
                when (val line = SavedOfflineLine.of(content, entry)) {
                    SavedOfflineLine.VideoOnly -> stringResource(Res.string.offline_video_only)
                    is SavedOfflineLine.Kept -> detailText(line.detail)
                    is SavedOfflineLine.NotKept -> stringResource(Res.string.offline_media_count, 0, line.total)
                },
            )
        },
        trailingContent = entry?.let {
            {
                IconButton(onClick = { keeping.launch { graph.offline.remove(key) } }) { Icon(Icons.Filled.Delete, stringResource(Res.string.offline_remove)) }
            }
        },
    )
    if (asking) {
        AlertDialog(
            onDismissRequest = { asking = false },
            title = { Text(stringResource(Res.string.offline_sensitive_question)) },
            text = { Text(stringResource(Res.string.offline_sensitive_description)) },
            confirmButton = { Button(onClick = { asking = false; keep() }) { Text(stringResource(Res.string.offline_keep)) } },
            dismissButton = { TextButton(onClick = { asking = false }) { Text(stringResource(Res.string.cancel)) } },
        )
    }
}

/** Keeps the post's pictures and videos, then says how it went. */
private fun keepSaved(graph: AppGraph, postId: String, content: SavedContent) {
    keeping.launch {
        val key = OfflineSaved.idFor(postId)
        graph.offline.keepMedia(
            id = key,
            title = OfflineSaved.title(content),
            source = OfflineSaved.source(content),
            media = OfflineSaved.media(content),
            url = OfflineSaved.url(content),
            sensitive = OfflineSaved.sensitive(content),
        )
        KeepNotices.show(OfflineRules.afterKeeping(graph.offline.state.value, key))
    }
}

/**
 * The offline button of an article's reader (offline_article_action.dart):
 * keep the article (or keep it again), remove the copy, how much is kept,
 * and that embedded audio and video still need a connection.
 */
@Composable
fun OfflineArticleAction(article: OfflineArticle) {
    val graph = LocalGraph.current
    val state by graph.offline.state.collectAsStateWithLifecycle()
    LaunchedEffect(graph) { graph.offline.load() }
    var open by remember { mutableStateOf(false) }
    val entry = state.entry(article.id)
    val busy = article.id in state.busy
    Box {
        IconButton(onClick = { open = true }) {
            if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else Icon(
                if (entry?.hasArticle == true) SavedIcons.OfflinePin else SavedIcons.OfflinePinOutline,
                contentDescription = if (entry == null) stringResource(Res.string.offline_keep) else statusText(entry.availability),
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(if (entry == null) Res.string.offline_keep else Res.string.retry)) },
                enabled = !busy && article.bodyHtml.isNotBlank(),
                onClick = {
                    open = false
                    keeping.launch {
                        graph.offline.keepArticle(article)
                        KeepNotices.show(OfflineRules.afterKeeping(graph.offline.state.value, article.id))
                    }
                },
            )
            if (entry != null) {
                DropdownMenuItem(text = { Text(stringResource(Res.string.offline_remove)) }, onClick = { open = false; keeping.launch { graph.offline.remove(article.id) } })
                DropdownMenuItem(text = { Text(detailText(OfflineRules.detail(entry))) }, enabled = false, onClick = {})
            }
            DropdownMenuItem(text = { Text(stringResource(Res.string.offline_article_text_only)) }, enabled = false, onClick = {})
        }
    }
}
