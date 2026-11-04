package com.aimdi.xtanative.plugins.pixiv

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import com.aimdi.xtanative.networks.pixiv.PixivIllust
import com.aimdi.xtanative.networks.pixiv.PixivImages
import com.aimdi.xtanative.networks.pixiv.PixivNovel
import com.aimdi.xtanative.networks.pixiv.PixivTag
import com.aimdi.xtanative.networks.pixiv.PixivUgoira
import com.aimdi.xtanative.store.settings.PixivPrefs
import com.aimdi.xtanative.ui.plugins.MediaItem
import com.aimdi.xtanative.ui.plugins.PluginDownload
import com.aimdi.xtanative.ui.plugins.currentLanguage
import com.aimdi.xtanative.ui.plugins.pluginPlural
import com.aimdi.xtanative.ui.plugins.pluginString
import kotlinx.coroutines.launch

/** What a screen of the plugin has open over it: a sheet of actions, the bookmark editor, a question. One at a time. */
internal sealed interface PixivSheet {
    data class WorkActions(val illust: PixivIllust) : PixivSheet

    data class PageActions(val illust: PixivIllust, val page: Int, val surface: PixivPageSurface) : PixivSheet

    /** Muting from a work's own screen, which then leaves it, since what was muted now hides it. */
    data class Mute(val illust: PixivIllust) : PixivSheet

    data class Tag(val tag: PixivTag, val leaveOnMute: Boolean) : PixivSheet

    data class EditWork(val illust: PixivIllust) : PixivSheet

    data class EditNovel(val novel: PixivNovel) : PixivSheet

    data class NovelActions(val novel: PixivNovel) : PixivSheet

    data class Resave(val illust: PixivIllust, val pages: List<Int>, val saved: List<Int>, val zip: Boolean) : PixivSheet
}

/** What a screen showing a work's pages can be asked to do from its page sheets. */
internal interface PixivPageSurface {
    val offersVertical: Boolean

    fun showPage(page: Int)

    fun changeDirection()

    fun openOverview(selecting: Boolean)
}

/**
 * The sheets, dialogs and saves of one screen, for every part of it: a tile
 * long-pressed, a heart held, a page's actions. Saves run in the plugin's
 * own scope, so they outlive the screen.
 */
@Stable
internal class PixivSheets(private val runtime: PixivRuntime, private val language: () -> String) {
    var open by mutableStateOf<PixivSheet?>(null)
        private set

    val ask = PixivAsk()

    /** Called when what was muted from a work's own screen hides it now: the screen leaves. */
    var onMutedHere: () -> Unit = { runtime.host.back() }

    fun show(sheet: PixivSheet) {
        open = sheet
    }

    fun close() {
        open = null
    }

    /** The heart: bookmarks [illust] on this device with the defaults, or takes the bookmark away. */
    fun toggleBookmark(illust: PixivIllust, haptics: PixivHaptics) = runtime.launch {
        report(illust.userName, runtime.bookmarking.toggle(illust), haptics)?.let { if (it.saveAll) saveAll(illust) }
    }

    fun toggleBookmark(novel: PixivNovel, haptics: PixivHaptics) = runtime.launch {
        report(novel.user.name, runtime.bookmarking.toggle(novel), haptics)
    }

    fun saveBookmark(illust: PixivIllust, tags: List<String>, haptics: PixivHaptics) = runtime.launch {
        report(illust.userName, runtime.bookmarking.bookmark(illust, tags), haptics)?.let { if (it.saveAll) saveAll(illust) }
    }

    fun saveBookmark(novel: PixivNovel, tags: List<String>, haptics: PixivHaptics) = runtime.launch {
        report(novel.user.name, runtime.bookmarking.bookmark(novel, tags), haptics)
    }

    fun removeBookmark(kind: PixivKind, id: Long, haptics: PixivHaptics) = runtime.launch {
        runtime.bookmarking.remove(kind, id)
        haptics.light()
    }

    private fun report(author: String, outcome: PixivBookmarkOutcome, haptics: PixivHaptics): PixivBookmarkOutcome {
        haptics.light()
        if (outcome.followedAuthor) runtime.tell(Strings.bookmark_followed_author.get(language(), author))
        return outcome
    }

    fun saveAll(illust: PixivIllust) = savePages(illust, illust.viewerUrls.indices.toList())

    /** Saves one picture as Pixiv serves it, a profile's avatar or banner, as any plugin's picture is saved (pixiv_download.dart's savePixivImage). */
    fun saveImage(url: String) = runtime.host.download(MediaItem(PixivImages.url(url, runtime.settings[PixivPrefs.imageHost])))

    /** Saves [pages] of [illust], asking first when some were saved before. */
    fun savePages(illust: PixivIllust, pages: List<Int>) {
        val saved = PixivSavedPages.savedAmong(PixivSavedPages.of(runtime.settings.now), illust, pages)
        if (saved.isEmpty()) startSaving(illust, pages) else show(PixivSheet.Resave(illust, pages, saved, zip = false))
    }

    /** Saves an ugoira's frame archive, named as its first page, asking first when it was saved before. */
    fun saveZip(illust: PixivIllust) {
        val saved = PixivSavedPages.savedAmong(PixivSavedPages.of(runtime.settings.now), illust, listOf(0))
        if (saved.isEmpty()) startZip(illust) else show(PixivSheet.Resave(illust, listOf(0), saved, zip = true))
    }

    fun answerResave(sheet: PixivSheet.Resave, choice: PixivResave?) {
        close()
        val pages = pagesToSave(sheet.pages, sheet.saved, choice)
        if (pages.isEmpty()) return
        if (sheet.zip) startZip(sheet.illust) else startSaving(sheet.illust, pages)
    }

    private fun startSaving(illust: PixivIllust, pages: List<Int>) {
        runtime.launch {
            val naming = PixivSaveNaming.of(runtime.settings.now)
            val saved = runtime.host.downloadAll(PixivDownloads.pageFiles(runtime, illust, pages), naming.subfolder(illust))
            afterSave(illust, pages.filterIndexed { index, _ -> saved.getOrElse(index) { false } })
        }
    }

    private fun startZip(illust: PixivIllust) {
        runtime.launch {
            val archive: PixivUgoira = runCatching { runtime.client.ugoiraMetadata(illust.id) }.getOrNull() ?: return@launch runtime.tell(Strings.ugoira_export_failed.get(language()))
            val naming = PixivSaveNaming.of(runtime.settings.now)
            val file = PluginDownload(archive.archiveUrl(runtime.settings[PixivPrefs.imageHost]), naming.workName(illust, ".zip"))
            val saved = runtime.host.downloadAll(listOf(file), naming.subfolder(illust))
            afterSave(illust, if (saved.firstOrNull() == true) listOf(0) else emptyList())
        }
    }

    /** Remembers the pages saved, then bookmarks the work when the reader asked for bookmark-after-save. */
    private suspend fun afterSave(illust: PixivIllust, saved: List<Int>) {
        if (saved.isEmpty()) return
        runtime.settings.update { PixivSavedPages.recorded(it, illust.id, saved) }
        val outcome = runtime.bookmarking.afterSave(illust) ?: return
        if (outcome.followedAuthor) runtime.tell(Strings.bookmark_followed_author.get(language(), illust.userName))
    }
}

internal val LocalPixivSheets = staticCompositionLocalOf<PixivSheets> { error("No Pixiv sheets here") }

/** A screen of the plugin: [runtime] for every part of it, its sheets and dialogs, and its notices at the foot. */
@Composable
internal fun PixivRoot(runtime: PixivRuntime, content: @Composable () -> Unit) {
    val language = rememberUpdatedState(currentLanguage())
    val sheets = remember(runtime) { PixivSheets(runtime) { language.value } }
    LaunchedEffect(runtime) { runtime.ready() }
    CompositionLocalProvider(LocalPixiv provides runtime, LocalPixivSheets provides sheets) {
        Box(Modifier.fillMaxSize()) {
            content()
            PixivSheetHost(sheets)
            AskDialog(sheets.ask)
            PixivNotices()
        }
    }
}

@Composable
private fun PixivSheetHost(sheets: PixivSheets) {
    when (val sheet = sheets.open) {
        is PixivSheet.WorkActions -> WorkActionsSheet(sheet.illust, sheets)
        is PixivSheet.PageActions -> PageActionsSheet(sheet, sheets)
        is PixivSheet.Mute -> MuteSheet(sheet.illust, sheets)
        is PixivSheet.Tag -> TagSheet(sheet, sheets)
        is PixivSheet.EditWork -> BookmarkEditor(PixivKind.WORK, sheet.illust.id, sheet.illust.title, sheet.illust.tags.map { it.name }, sheets) { tags, haptics ->
            sheets.saveBookmark(sheet.illust, tags, haptics)
        }
        is PixivSheet.EditNovel -> BookmarkEditor(PixivKind.NOVEL, sheet.novel.id, sheet.novel.title, sheet.novel.tags.map { it.name }, sheets) { tags, haptics ->
            sheets.saveBookmark(sheet.novel, tags, haptics)
        }
        is PixivSheet.NovelActions -> NovelActionsSheet(sheet.novel, sheets)
        is PixivSheet.Resave -> ResaveDialog(sheet, sheets)
        null -> Unit
    }
}

/** Asks before saving pages again: some of those about to be saved are on the device already. */
@Composable
private fun ResaveDialog(sheet: PixivSheet.Resave, sheets: PixivSheets) {
    val total = sheet.pages.size
    val saved = sheet.saved.size
    AlertDialog(
        onDismissRequest = { sheets.answerResave(sheet, null) },
        title = { Text(pluginString(Strings.already_saved)) },
        text = { Text(if (total == 1) pluginString(Strings.already_saved_page) else pluginPlural(Strings.already_saved_pages, saved, saved, total)) },
        confirmButton = { Button(onClick = { sheets.answerResave(sheet, PixivResave.ALL) }) { Text(pluginString(Strings.save_again)) } },
        dismissButton = {
            if (saved < total) TextButton(onClick = { sheets.answerResave(sheet, PixivResave.NEW_ONLY) }) { Text(pluginString(Strings.save_new_only)) }
            TextButton(onClick = { sheets.answerResave(sheet, null) }) { Text(pluginString(Strings.cancel)) }
        },
    )
}
