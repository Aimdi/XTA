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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.ImeAction
import com.aimdi.xtanative.networks.booru.BooruPost
import com.aimdi.xtanative.store.settings.BooruPrefs
import com.aimdi.xtanative.ui.plugins.currentLanguage
import com.aimdi.xtanative.ui.plugins.pluginString

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
 * Asks before downloading the [posts] a feed has loaded, then has the app's
 * downloads queue those they do not have yet, and says how many were queued
 * and how many were there already (booru_download.dart).
 */
@Composable
internal fun DownloadAllDialog(app: BooruApp, posts: List<BooruPost>, onDone: () -> Unit) {
    if (posts.isEmpty()) {
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
                app.launch { downloadAll(app, posts, language) }
            }) { Text(pluginString(Strings.download)) }
        },
        dismissButton = { TextButton(onClick = onDone) { Text(pluginString(Strings.cancel)) } },
    )
}

/** Queues [posts] where the download setting says, named by the reader's template; a setting that asks where each file goes has the app say a batch needs a folder. */
private suspend fun downloadAll(app: BooruApp, posts: List<BooruPost>, language: String) {
    val queued = app.host.queueDownloads(BooruDownloads.files(posts, app.current()[BooruPrefs.fileNameTemplate])) ?: return
    app.tell(Strings.download_all_result.get(language, queued.queued, queued.skipped))
}
