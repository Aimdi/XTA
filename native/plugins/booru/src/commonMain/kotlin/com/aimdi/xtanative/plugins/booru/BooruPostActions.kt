package com.aimdi.xtanative.plugins.booru

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import com.aimdi.xtanative.data.AppGraph
import com.aimdi.xtanative.networks.booru.BooruPost
import com.aimdi.xtanative.store.settings.BooruPrefs
import com.aimdi.xtanative.ui.common.XIcons
import com.aimdi.xtanative.ui.downloads.Downloading
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.subscriptions.GroupIcons
import kotlinx.coroutines.launch

/** What the viewer's menu offers for a post (booru_post_actions.dart's _PostMenu). */
private enum class PostMenu { SHARE, COPY_LINK, COPY_TAGS, COPY_PROMPT, HOST, SOURCE, FILE }

/**
 * Download, save on this device and the rest of a post's actions, for the
 * viewer's bar. Nothing is favourited or voted on at the booru: saving is the
 * app's own.
 */
@Composable
internal fun PostActions(app: BooruApp, viewer: ViewerState, post: BooruPost, graph: AppGraph) {
    IconButton(onClick = { download(app, graph, post) }) { Icon(GroupIcons.FileDownloadOutlined, pluginString(Strings.download)) }
    SaveButton(app, graph, post)
    PostMenuButton(app, viewer, post)
}

/** Saves [post]'s file as the download setting says, under the reader's file name template. */
private fun download(app: BooruApp, graph: AppGraph, post: BooruPost) {
    Downloading.save(graph, BooruDownloads.url(post), BooruDownloads.fileName(post, app.current()[BooruPrefs.fileNameTemplate]))
}

@Composable
private fun SaveButton(app: BooruApp, graph: AppGraph, post: BooruPost) {
    val library by graph.saved.state.collectAsState()
    LaunchedEffect(graph) { graph.saved.library() }
    val id = BooruArchive.id(post)
    val saved = library?.isSaved(id) == true
    IconButton(onClick = {
        app.launch { if (saved) graph.saved.remove(id) else graph.saved.file(id, BooruArchive.authorId(post), BooruArchive.content(post)) }
    }) {
        Icon(
            if (saved) BooruIcons.Bookmark else BooruIcons.BookmarkBorder,
            pluginString(if (saved) Strings.unsave_from_this_device else Strings.save_on_this_device),
        )
    }
}

@Composable
private fun PostMenuButton(app: BooruApp, viewer: ViewerState, post: BooruPost) {
    var open by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val outside = rememberOutside()
    val copied = copiedNotice(app)
    val source = post.source?.trim()?.ifEmpty { null }
    val run = { action: PostMenu ->
        open = false
        when (action) {
            PostMenu.SHARE -> outside.share(BooruArchive.shareUrl(post), copied)
            PostMenu.COPY_LINK -> outside.copy(BooruArchive.shareUrl(post), copied)
            PostMenu.COPY_TAGS -> outside.copy(post.tagLine, copied)
            PostMenu.COPY_PROMPT -> scope.launch { outside.copy(prompt(app, viewer, post), copied) }
            PostMenu.HOST -> post.hostPageUrl?.let(outside::open)
            PostMenu.SOURCE -> source?.let(outside::open)
            PostMenu.FILE -> outside.open(post.originalUrl)
        }
    }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Filled.MoreVert, pluginString(Strings.more_options)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            MenuRow(Icons.Filled.Share, pluginString(Strings.share_link)) { run(PostMenu.SHARE) }
            MenuRow(XIcons.Link, pluginString(Strings.copy_link)) { run(PostMenu.COPY_LINK) }
            if (post.tags.isNotEmpty()) {
                MenuRow(BooruIcons.Sell, pluginString(Strings.copy_tags)) { run(PostMenu.COPY_TAGS) }
                MenuRow(GroupIcons.AutoAwesomeOutlined, pluginString(Strings.copy_prompt)) { run(PostMenu.COPY_PROMPT) }
            }
            if (post.hostPageUrl != null) MenuRow(GroupIcons.Language, pluginString(Strings.open_on_host)) { run(PostMenu.HOST) }
            if (source != null) MenuRow(GroupIcons.OpenInNew, pluginString(Strings.open_source)) { run(PostMenu.SOURCE) }
            MenuRow(BooruIcons.File, pluginString(Strings.open_file)) { run(PostMenu.FILE) }
        }
    }
}

@Composable
private fun MenuRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(label) }, leadingIcon = { Icon(icon, contentDescription = null) }, onClick = onClick)
}

/**
 * The post's tags as a prompt in the reader's order. Kinds come from the tag
 * lookup the viewer has usually made already; without it, the kinds the post
 * carries are used and the rest count as general.
 */
private suspend fun prompt(app: BooruApp, viewer: ViewerState, post: BooruPost): String {
    val info = viewer.tagInfo(post)
    val kinds = post.tagCategories + info.mapNotNull { (tag, tagInfo) -> tagInfo.category?.let { tag to it } }
    return BooruPrompt.text(post.tags, kinds, BooruPrompt.order(app.current()[BooruPrefs.promptKinds]))
}
