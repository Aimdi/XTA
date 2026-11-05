package com.aimdi.xtanative.plugins.pixiv

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.pixiv.PixivNovel
import com.aimdi.xtanative.networks.pixiv.PixivNovelContent
import com.aimdi.xtanative.networks.pixiv.PixivNovelNeighbour
import com.aimdi.xtanative.networks.pixiv.pixivNovelSeriesUrl
import com.aimdi.xtanative.networks.pixiv.pixivNovelUrl
import com.aimdi.xtanative.networks.pixiv.pixivUserUrl
import com.aimdi.xtanative.store.reading.ReadingAppearance
import com.aimdi.xtanative.ui.plugins.PluginScaffold
import com.aimdi.xtanative.ui.plugins.PluginText
import com.aimdi.xtanative.ui.plugins.currentLanguage
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.rememberTextFileSaver
import com.aimdi.xtanative.ui.saved.SavedIcons
import kotlinx.coroutines.launch

/** The reader's sheet: the creator's novels, the chapters beside this one, the reading appearance, Export as text once the text is read, sharing and Pixiv's own page. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NovelMenu(novel: PixivNovel, content: PixivNovelContent?, onAppearance: () -> Unit, onExport: () -> Unit, onClose: () -> Unit) {
    val runtime = LocalPixiv.current
    val outside = rememberOutside()
    val run = { action: () -> Unit ->
        onClose()
        action()
    }
    ModalBottomSheet(onDismissRequest = onClose) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 8.dp)) {
            val user = novel.user
            ListItem(
                headlineContent = { Text(user.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = { Text(pluginString(Strings.novel_author_novels)) },
                leadingContent = { PixivAvatar(user.avatarUrl, user.name) },
                trailingContent = { IconButton(onClick = { run { outside.share(pixivUserUrl(user.id)) } }, enabled = user.id != 0L) { Icon(Icons.Filled.Share, pluginString(Strings.novel_share_author)) } },
                modifier = Modifier.clickable(enabled = user.id != 0L) { run { runtime.openUser(user.id, "novels") } },
            )
            HorizontalDivider()
            content?.previous?.let { ChapterRow(it, Strings.novel_previous, Icons.AutoMirrored.Filled.KeyboardArrowLeft) { run { runtime.open(PixivRoute.Novel(it.id)) } } }
            content?.next?.let { ChapterRow(it, Strings.novel_next, Icons.AutoMirrored.Filled.KeyboardArrowRight) { run { runtime.open(PixivRoute.Novel(it.id)) } } }
            MenuRow(PixivIcons.TextFields, Strings.article_reader_appearance) { run(onAppearance) }
            if (content != null) MenuRow(PixivIcons.Description, Strings.novel_export) { run(onExport) }
            MenuRow(Icons.Filled.Share, Strings.share_link) { run { outside.share(pixivNovelUrl(novel.id)) } }
            novel.series?.let { series -> MenuRow(PixivIcons.Collections, Strings.novel_share_series) { run { outside.share(pixivNovelSeriesUrl(series.id)) } } }
            MenuRow(SavedIcons.OpenInNew, Strings.open_on_pixiv) { run { outside.open(pixivNovelUrl(novel.id)) } }
        }
    }
}

@Composable
private fun ChapterRow(chapter: PixivNovelNeighbour, label: PluginText, icon: ImageVector, onClick: () -> Unit) {
    val name = chapterName(chapter)
    ListItem(
        headlineContent = { Text(pluginString(label)) },
        supportingContent = name.takeIf { it.isNotEmpty() }?.let { { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis) } },
        leadingContent = { Icon(icon, null) },
        modifier = Modifier.clickable(enabled = chapter.viewable, onClick = onClick),
    )
}

@Composable
private fun MenuRow(icon: ImageVector, label: PluginText, hint: PluginText? = null, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(pluginString(label)) },
        supportingContent = hint?.let { { Text(pluginString(it)) } },
        leadingContent = { Icon(icon, null) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

/** The two ways to export a novel as text, each saying what the file will hold (pixiv_novel_export.dart's sheet). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NovelExportSheet(onPick: (PixivNovelExportFormat) -> Unit, onClose: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onClose) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 8.dp)) {
            ListItem(headlineContent = { Text(pluginString(Strings.novel_export), style = MaterialTheme.typography.titleMedium) })
            MenuRow(PixivIcons.Notes, Strings.novel_export_plain, Strings.novel_export_plain_hint) { onPick(PixivNovelExportFormat.PLAIN) }
            MenuRow(PixivIcons.Code, Strings.novel_export_markup, Strings.novel_export_markup_hint) { onPick(PixivNovelExportFormat.MARKUP) }
        }
    }
}

/**
 * Export as text: the novel's text as the format picked, in a file the
 * reader names and places through the system's save dialog, saying where it
 * went, or that it could not be exported when there is no text to write. Remembered by the reader's screen, so the dialog's answer outlives the sheet.
 */
@Composable
internal fun rememberNovelExport(novel: PixivNovel, reading: PixivNovelReading): (PixivNovelExportFormat) -> Unit {
    val runtime = LocalPixiv.current
    val language = currentLanguage()
    val save = rememberTextFileSaver(pluginString(Strings.novel_export), PixivNovelExport.MIME) { saved ->
        if (saved != null) runtime.tell(Strings.novel_exported.get(language, saved))
    }
    val markup = reading.content?.text
    return { format ->
        val text = markup?.let { runCatching { PixivNovelExport.text(it, reading.blocks, format) }.getOrNull() }
        if (text == null) runtime.tell(Strings.novel_export_failed.get(language)) else save(PixivNovelExport.fileName(novel.title, novel.id), text)
    }
}

/** The app's reading appearance, which its article reader shares: text size and line spacing. */
@Composable
internal fun AppearanceDialog(onClose: () -> Unit) {
    val store = LocalPixiv.current.host.readingAppearance
    val scope = rememberCoroutineScope()
    var look by remember { mutableStateOf<ReadingAppearance?>(null) }
    LaunchedEffect(store) { look = store.get() }
    val shown = look ?: return
    val change = { next: ReadingAppearance ->
        look = next.bounded()
        scope.launch { store.set(next) }
        Unit
    }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(pluginString(Strings.article_reader_appearance)) },
        text = {
            Column {
                Text(pluginString(Strings.article_reader_text_size))
                Slider(value = shown.fontSize.toFloat(), onValueChange = { change(shown.copy(fontSize = it.toDouble())) }, valueRange = 16f..28f, steps = 11)
                Text(pluginString(Strings.article_reader_line_spacing))
                Slider(value = shown.lineHeight.toFloat(), onValueChange = { change(shown.copy(lineHeight = it.toDouble())) }, valueRange = 1.4f..2.2f, steps = 7)
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(pluginString(Strings.close)) } },
    )
}

/** One picture full screen, on black: pinch or double-tap to zoom, a long press saves it. */
@Composable
internal fun PixivPictureScreen(url: String) {
    val runtime = LocalPixiv.current
    val sheets = LocalPixivSheets.current
    PluginScaffold(title = "", onBack = runtime.host::back) { padding ->
        Box(Modifier.padding(padding).fillMaxSize().background(Color.Black)) {
            ZoomablePage(url) { sheets.saveImage(url) }
        }
    }
}
