package com.aimdi.xtanative.plugins.booru

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.ImeAction
import com.aimdi.xtanative.data.AppGraph
import com.aimdi.xtanative.networks.booru.BooruPost
import com.aimdi.xtanative.store.downloads.DownloadQueue
import com.aimdi.xtanative.store.downloads.DownloadStatus
import com.aimdi.xtanative.store.settings.BooruPrefs
import com.aimdi.xtanative.ui.downloads.Downloading
import com.aimdi.xtanative.ui.downloads.downloadDestination
import com.aimdi.xtanative.ui.plugins.currentLanguage
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.saved.KeepNotice
import com.aimdi.xtanative.ui.saved.KeepNotices

/**
 * Asks the reader to confirm being 18 or older and wanting adult posts
 * (booru_adult_consent.dart); [site] names a host whose posts are mostly
 * explicit. Anything but the confirm button, back included, is a no.
 */
@Composable
internal fun AdultConsentDialog(site: String?, onAnswer: (Boolean) -> Unit) {
    val body = listOfNotNull(site?.let { pluginString(Strings.adult_site, it) }, pluginString(Strings.adult_body)).joinToString("\n\n")
    AlertDialog(
        onDismissRequest = { onAnswer(false) },
        title = { Text(pluginString(Strings.adult_title)) },
        text = { Text(body) },
        confirmButton = { Button(onClick = { onAnswer(true) }) { Text(pluginString(Strings.adult_confirm)) } },
        dismissButton = { TextButton(onClick = { onAnswer(false) }) { Text(pluginString(Strings.cancel)) } },
    )
}

/** The whole search as text, to be edited as typed and searched again. */
@Composable
internal fun RawQueryDialog(initial: String, onSearch: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(pluginString(Strings.edit_as_text)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(pluginString(Strings.search_hint)) },
                minLines = 1,
                maxLines = 4,
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch(text) }),
            )
        },
        confirmButton = { Button(onClick = { onSearch(text) }) { Text(pluginString(Strings.search)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(pluginString(Strings.cancel)) } },
    )
}

/**
 * Asks before downloading the [posts] a feed has loaded, then queues those
 * the downloads do not have yet, and says how many were queued and how many
 * were there already (booru_download.dart).
 */
@Composable
internal fun DownloadAllDialog(app: BooruApp, posts: List<BooruPost>, onDone: () -> Unit) {
    val graph by app.graph.collectAsState()
    val ready = graph
    if (posts.isEmpty() || ready == null) {
        LaunchedEffect(Unit) { onDone() }
        return
    }
    val language = currentLanguage()
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(pluginString(Strings.download_all)) },
        text = { Text(pluginString(Strings.download_all_body, posts.size)) },
        confirmButton = {
            Button(onClick = {
                onDone()
                app.launch { downloadAll(app, ready, posts, language) }
            }) { Text(pluginString(Strings.download)) }
        },
        dismissButton = { TextButton(onClick = onDone) { Text(pluginString(Strings.cancel)) } },
    )
}

/** Queues [posts] where the download setting says; one that asks where to save each file needs a folder for a batch, as the app's other batches do. */
private suspend fun downloadAll(app: BooruApp, graph: AppGraph, posts: List<BooruPost>, language: String) {
    val settings = graph.settings.load()
    val destination = downloadDestination(settings)
    if (destination.asks) return KeepNotices.show(KeepNotice.NeedsDownloadFolder)
    graph.downloads.initialize()
    val entries = graph.downloads.state.value.entries
    val known = entries.filter { it.active || it.status == DownloadStatus.COMPLETED }.mapTo(HashSet()) { it.url }
    val plan = BooruDownloads.plan(posts, known, DownloadQueue.MAX_ACTIVE - entries.count { it.active }, settings[BooruPrefs.fileNameTemplate], destination)
    if (plan.requests.isNotEmpty()) Downloading.batch(graph, plan.requests)
    app.tell(Strings.download_all_result.get(language, plan.requests.size, plan.skipped))
}
