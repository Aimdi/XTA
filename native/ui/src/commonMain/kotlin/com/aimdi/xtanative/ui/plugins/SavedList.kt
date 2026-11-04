package com.aimdi.xtanative.ui.plugins

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.store.saved.SavedContent
import com.aimdi.xtanative.ui.common.Hairline
import com.aimdi.xtanative.ui.common.LoadingRow
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.home_no_results
import com.aimdi.xtanative.ui.generated.resources.saved_notice_saves
import com.aimdi.xtanative.ui.generated.resources.saved_title
import com.aimdi.xtanative.ui.saved.KeptCard
import com.aimdi.xtanative.ui.saved.KeptCardView
import com.aimdi.xtanative.ui.saved.SavedNoteRow
import org.jetbrains.compose.resources.stringResource

/**
 * A plugin's own list of its posts in Saved (XTA's plugin bookmarks,
 * plugin_bookmarks.dart), in Saved's words: that saves stay on this device,
 * then the posts newest first ([PluginHost.savedPosts]), each as [row] draws
 * the post [read] makes of its record (Saved's own card for one it does not
 * read, as Saved draws it), with the reader's note under it (a tap edits it,
 * else Add a note) and Keep offline where it has media; No results when there
 * are none. [row] draws its post without the line under it, which the list
 * draws under the note.
 */
@Composable
fun <T : Any> PluginSavedScreen(host: PluginHost, read: (PluginPostArchive) -> T?, row: @Composable (T) -> Unit) {
    val saved by host.savedPosts.collectAsState()
    val rows = remember(saved) { saved?.mapNotNull { kept -> ListedSave.of(kept, read) } }
    PluginScaffold(title = stringResource(Res.string.saved_title), onBack = host::back) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + 32.dp),
            modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()),
        ) {
            item(key = "notice") { SavedNotice(empty = rows?.isEmpty() == true) }
            if (rows == null) item(key = "loading") { LoadingRow() }
            items(rows.orEmpty(), key = { it.kept.post.id }, contentType = { "saved" }) { listed ->
                val post = listed.post
                if (post != null) row(post) else listed.card?.let { KeptCardView(it) }
                SavedNoteRow(listed.kept.post.id, listed.kept.note, listed.content) { host.editNote(listed.kept.post) }
                Hairline()
            }
        }
    }
}

/** One saved post of the list: the post its plugin read from its record, else Saved's own card of it. */
private class ListedSave<T : Any>(val kept: PluginSavedPost, val content: SavedContent, val post: T?, val card: KeptCard?) {
    companion object {
        /** [kept] as the list draws it; null when neither its plugin nor Saved can draw it. */
        fun <T : Any> of(kept: PluginSavedPost, read: (PluginPostArchive) -> T?): ListedSave<T>? {
            val content = SavedContent.of(kept.post.content)
            val post = runCatching { read(kept.post) }.getOrNull()
            val card = if (post == null) KeptCard.of(content) else null
            return if (post == null && card == null) null else ListedSave(kept, content, post, card)
        }
    }
}

/** That saves stay on this device, and No results under it when there are none. */
@Composable
private fun SavedNotice(empty: Boolean) {
    Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(Res.string.saved_notice_saves), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        if (empty) Text(stringResource(Res.string.home_no_results), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 24.dp))
    }
}
