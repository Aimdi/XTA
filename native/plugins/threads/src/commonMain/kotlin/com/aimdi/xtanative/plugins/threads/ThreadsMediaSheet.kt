package com.aimdi.xtanative.plugins.threads

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.threads.ThreadsPost
import com.aimdi.xtanative.ui.common.rememberCopyText
import com.aimdi.xtanative.ui.plugins.MediaItem
import com.aimdi.xtanative.ui.plugins.PluginMenuRow
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.rememberShareText
import com.aimdi.xtanative.ui.post.PostIcons

/**
 * What a long press on one of a post's pictures, videos or GIFs offers, as
 * Threads' own app and X's viewer do: its description, saving it or every
 * one of the post's under names that say whose and which they are
 * ([ThreadsMediaFiles]), the post, and its link copied or shared. Only what
 * applies is listed: Download all for several, Open post where a tap on the
 * card opens it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ThreadsMediaSheet(app: ThreadsApp, post: ThreadsPost, index: Int, items: List<MediaItem>, opens: Boolean, onDescribe: () -> Unit, onDismiss: () -> Unit) {
    val item = items.getOrNull(index) ?: return
    val copy = rememberCopyText()
    val share = rememberShareText()
    val copied = pluginString(Strings.link_copied)
    val done = { action: () -> Unit -> { onDismiss(); action() } }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 8.dp)) {
            for (entry in ThreadsMediaActions.of(post, index, items, opens)) {
                when (entry) {
                    ThreadsMediaAction.DESCRIPTION -> PluginMenuRow(Icons.Filled.Info, pluginString(Strings.media_description), done(onDescribe))
                    ThreadsMediaAction.DOWNLOAD -> PluginMenuRow(ThreadsMediaIcons.Download, pluginString(Strings.media_download), done { app.saveMedia(post, index, item) })
                    ThreadsMediaAction.DOWNLOAD_ALL -> PluginMenuRow(ThreadsMediaIcons.Collections, pluginString(Strings.media_download_all), done { app.saveAllMedia(post, items) })
                    ThreadsMediaAction.OPEN_POST -> PluginMenuRow(PostIcons.Forum, pluginString(Strings.open_post), done { app.openPost(post) })
                    ThreadsMediaAction.COPY_LINK -> PluginMenuRow(ThreadsMediaIcons.Copy, pluginString(Strings.media_copy_link), done { post.url?.let { copy(it); app.host.tell(copied) } })
                    ThreadsMediaAction.SHARE_LINK -> PluginMenuRow(Icons.Filled.Share, pluginString(Strings.share_link), done { post.url?.let { if (!share(it)) app.host.tell(copied) } })
                }
            }
        }
    }
}

internal enum class ThreadsMediaAction { DESCRIPTION, DOWNLOAD, DOWNLOAD_ALL, OPEN_POST, COPY_LINK, SHARE_LINK }

/** Which entries the sheet lists for the [index]th of [post]'s [items]. */
internal object ThreadsMediaActions {
    fun of(post: ThreadsPost, index: Int, items: List<MediaItem>, opens: Boolean): List<ThreadsMediaAction> {
        val item = items.getOrNull(index) ?: return emptyList()
        val saved = items.count { ThreadsMediaFiles.url(it) != null }
        val linked = !post.url.isNullOrBlank()
        return listOfNotNull(
            ThreadsMediaAction.DESCRIPTION.takeIf { item.description != null },
            ThreadsMediaAction.DOWNLOAD.takeIf { ThreadsMediaFiles.url(item) != null },
            ThreadsMediaAction.DOWNLOAD_ALL.takeIf { saved > 1 },
            ThreadsMediaAction.OPEN_POST.takeIf { opens && linked },
            ThreadsMediaAction.COPY_LINK.takeIf { linked },
            ThreadsMediaAction.SHARE_LINK.takeIf { linked },
        )
    }
}

/** [item], the [index]th of [post]'s, saved through the app's downloads under its name. */
internal fun ThreadsApp.saveMedia(post: ThreadsPost, index: Int, item: MediaItem) =
    host.download(item, fileName = ThreadsMediaFiles.name(post, index, item))

/** Every one of [post]'s [items] that has a file, saved as one batch with one notice, beyond the screen that asked. */
internal fun ThreadsApp.saveAllMedia(post: ThreadsPost, items: List<MediaItem>) {
    val files = ThreadsMediaFiles.all(post, items)
    if (files.isNotEmpty()) beyondScreen { host.downloadAll(files) }
}

/** The glyphs of the media sheet, from Material's (Apache 2.0) paths. */
internal object ThreadsMediaIcons {
    val Download: ImageVector by lazy { icon("Download", "M19,9h-4V3H9v6H5l7,7 7,-7zM5,18v2h14v-2H5z") }
    val Collections: ImageVector by lazy {
        icon("Collections", "M22,16V4c0,-1.1 -0.9,-2 -2,-2H8c-1.1,0 -2,0.9 -2,2v12c0,1.1 0.9,2 2,2h12c1.1,0 2,-0.9 2,-2zM11,12l2.03,2.71L16,11l4,5H8l3,-4zM2,6v14c0,1.1 0.9,2 2,2h14v-2H4V6H2z")
    }
    val Copy: ImageVector by lazy {
        icon("ContentCopy", "M16,1H4c-1.1,0 -2,0.9 -2,2v14h2V3h12V1zM19,5H8c-1.1,0 -2,0.9 -2,2v14c0,1.1 0.9,2 2,2h11c1.1,0 2,-0.9 2,-2V7c0,-1.1 -0.9,-2 -2,-2zM19,21H8V7h11v14z")
    }

    /** Every glyph, for the test that draws them all. */
    internal val all: List<ImageVector> get() = listOf(Download, Collections, Copy)

    private fun icon(name: String, path: String): ImageVector = ThreadsIcons.icon(name, path)
}
