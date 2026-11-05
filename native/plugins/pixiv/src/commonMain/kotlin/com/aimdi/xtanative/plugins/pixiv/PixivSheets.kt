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
import com.aimdi.xtanative.networks.pixiv.PixivFailure
import com.aimdi.xtanative.networks.pixiv.PixivImages
import com.aimdi.xtanative.networks.pixiv.PixivNovel
import com.aimdi.xtanative.networks.pixiv.PixivTag
import com.aimdi.xtanative.networks.pixiv.PixivUgoira
import com.aimdi.xtanative.networks.pixiv.PixivUser
import com.aimdi.xtanative.store.settings.PixivPrefs
import com.aimdi.xtanative.ui.plugins.MediaItem
import com.aimdi.xtanative.ui.plugins.PluginDownload
import com.aimdi.xtanative.ui.plugins.PluginProgress
import com.aimdi.xtanative.ui.plugins.currentLanguage
import com.aimdi.xtanative.ui.plugins.pluginPlural
import com.aimdi.xtanative.ui.plugins.pluginString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** What a screen of the plugin has open over it: a sheet of actions, the bookmark editor, a question. One at a time. */
internal sealed interface PixivSheet {
    data class WorkActions(val illust: PixivIllust) : PixivSheet

    data class PageActions(val illust: PixivIllust, val page: Int, val surface: PixivPageSurface) : PixivSheet

    /** Muting from a work's own screen, which then leaves it, since what was muted now hides it. */
    data class Mute(val illust: PixivIllust) : PixivSheet

    data class Tag(val tag: PixivTag, val leaveOnMute: Boolean) : PixivSheet

    data class EditWork(val illust: PixivIllust) : PixivSheet

    /** The follow dialog: follow privately or publicly, change the follow, or unfollow. */
    data class Follow(val user: PixivUser) : PixivSheet

    data class NovelActions(val novel: PixivNovel) : PixivSheet

    data class Resave(val illust: PixivIllust, val pages: List<Int>, val saved: List<Int>, val kind: PixivSaveKind) : PixivSheet
}

/** What a save writes: the pages themselves, or an ugoira's frames as a ZIP or as a GIF made here, each named as the first page. */
internal enum class PixivSaveKind { PAGES, ZIP, GIF }

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

    /** The heart: bookmarks [illust] on Pixiv with the defaults, or takes the bookmark away. */
    fun toggleBookmark(illust: PixivIllust, haptics: PixivHaptics) = write(haptics, { runtime.bookmarking.toggle(illust) }) { landed(illust, it) }

    /** A novel's heart: bookmarks it on Pixiv with the default visibility, or takes the bookmark away. */
    fun toggleBookmark(novel: PixivNovel, haptics: PixivHaptics) = write(haptics, { runtime.bookmarking.toggle(novel) }) {}

    /** A novel's long press: bookmarks it privately on Pixiv. */
    fun bookmarkPrivately(novel: PixivNovel, haptics: PixivHaptics) = write(haptics, { runtime.bookmarking.bookmarkPrivately(novel) }) {}

    /**
     * One of the bookmark editor's writes: [done] hears null once it landed,
     * else why it failed while the editor is still open; once the editor has
     * gone, the reader hears why at the foot of the screen.
     */
    fun editBookmark(sheet: PixivSheet.EditWork, haptics: PixivHaptics, edit: suspend (PixivBookmarking) -> PixivBookmarkOutcome?, done: (Throwable?) -> Unit) = runtime.launch {
        val outcome = try {
            edit(runtime.bookmarking)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (open === sheet) done(e) else runtime.tell(failureText(PixivFailure.of(e)).get(language()))
            return@launch
        }
        if (outcome != null) {
            haptics.light()
            landed(sheet.illust, outcome)
        }
        done(null)
    }

    /** A follow button's tap: follows [user] publicly on Pixiv, or unfollows. */
    fun toggleFollow(user: PixivUser, haptics: PixivHaptics) = write(haptics, { runtime.following.toggle(user) }) {}

    /** What the follow dialog was closed with: follow with [restrict], or unfollow when it is null. */
    fun answerFollow(user: PixivUser, restrict: String?, haptics: PixivHaptics) {
        close()
        write(haptics, { if (restrict == null) runtime.following.unfollow(user) else runtime.following.follow(user, restrict) }) {}
    }

    /** Runs a write to Pixiv, a light buzz once it landed, the reason at the foot when it fails; null from [send] means nothing was written. */
    private fun <T : Any> write(haptics: PixivHaptics, send: suspend () -> T?, after: (T) -> Unit) = runtime.launch {
        val outcome = try {
            send()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            runtime.tell(failureText(PixivFailure.of(e)).get(language()))
            return@launch
        } ?: return@launch
        haptics.light()
        after(outcome)
    }

    /** Says whom the bookmark followed, and saves the work when the reader asked for that. */
    private fun landed(illust: PixivIllust, outcome: PixivBookmarkOutcome) {
        if (outcome.followedAuthor) runtime.tell(Strings.bookmark_followed_author.get(language(), illust.userName))
        if (outcome.saveAll) saveAll(illust)
    }

    fun saveAll(illust: PixivIllust) = savePages(illust, illust.viewerUrls.indices.toList())

    /** Saves one picture as Pixiv serves it, a profile's avatar or banner, as any plugin's picture is saved (pixiv_download.dart's savePixivImage). */
    fun saveImage(url: String) = runtime.host.download(MediaItem(PixivImages.url(url, runtime.settings[PixivPrefs.imageHost])))

    /** Saves [pages] of [illust], asking first when some were saved before. */
    fun savePages(illust: PixivIllust, pages: List<Int>) = save(illust, pages, PixivSaveKind.PAGES)

    /** Saves an ugoira's frame archive, named as its first page, asking first when it was saved before. */
    fun saveZip(illust: PixivIllust) = save(illust, listOf(0), PixivSaveKind.ZIP)

    /** Saves an ugoira as a GIF made here (pixiv_ugoira_save.dart), named as its first page, asking first when it was saved before. */
    fun saveGif(illust: PixivIllust) = save(illust, listOf(0), PixivSaveKind.GIF)

    private fun save(illust: PixivIllust, pages: List<Int>, kind: PixivSaveKind) {
        val saved = PixivSavedPages.savedAmong(PixivSavedPages.of(runtime.settings.now), illust, pages)
        if (saved.isEmpty()) start(illust, pages, kind) else show(PixivSheet.Resave(illust, pages, saved, kind))
    }

    fun answerResave(sheet: PixivSheet.Resave, choice: PixivResave?) {
        close()
        val pages = pagesToSave(sheet.pages, sheet.saved, choice)
        if (pages.isNotEmpty()) start(sheet.illust, pages, sheet.kind)
    }

    private fun start(illust: PixivIllust, pages: List<Int>, kind: PixivSaveKind) = when (kind) {
        PixivSaveKind.PAGES -> startSaving(illust, pages)
        PixivSaveKind.ZIP -> startZip(illust)
        PixivSaveKind.GIF -> startGif(illust)
    }

    /** One page as any picture is saved; several together, the foot of the window saying which page is on its way, with Cancel (pixiv_download.dart). */
    private fun startSaving(illust: PixivIllust, pages: List<Int>) {
        runtime.launch {
            val naming = PixivSaveNaming.of(runtime.settings.now)
            val files = PixivDownloads.pageFiles(runtime, illust, pages)
            val language = language()
            val saved = if (files.size > 1) {
                runtime.host.downloadAll(files, naming.subfolder(illust)) { current, total -> Strings.downloading_page.get(language, current, total) }
            } else {
                runtime.host.downloadAll(files, naming.subfolder(illust))
            }
            afterSave(illust, pages.filterIndexed { index, _ -> saved.getOrElse(index) { false } })
        }
    }

    /** The GIF fetched, encoded and saved under the foot of the window's progress, with Cancel until it is being saved. */
    private fun startGif(illust: PixivIllust) {
        runtime.launch {
            val language = language()
            val saved = try {
                runtime.host.withProgress(PluginProgress(Strings.ugoira_fetching.get(language))) { say -> PixivUgoiraExport.gif(runtime, illust, language, say) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                runtime.host.tell(Strings.ugoira_export_failed.get(language))
                null
            }
            if (saved == true) {
                runtime.host.tell(Strings.ugoira_saved_gif.get(language))
                afterSave(illust, listOf(0))
            }
        }
    }

    private fun startZip(illust: PixivIllust) {
        runtime.launch {
            val archive: PixivUgoira = runCatching { runtime.client.ugoiraMetadata(illust.id) }.getOrNull() ?: return@launch runtime.tell(Strings.ugoira_export_failed.get(language()))
            val naming = PixivSaveNaming.of(runtime.settings.now)
            val file = PluginDownload(archive.archiveUrl(runtime.settings[PixivPrefs.imageHost]), naming.workName(illust, ".zip"))
            val saved = runtime.host.downloadAll(listOf(file), naming.subfolder(illust)).firstOrNull() == true
            if (saved) runtime.host.tell(Strings.ugoira_saved_zip.get(language()))
            afterSave(illust, if (saved) listOf(0) else emptyList())
        }
    }

    /** Remembers the pages saved, then bookmarks the work when the reader asked for bookmark-after-save. */
    private suspend fun afterSave(illust: PixivIllust, saved: List<Int>) {
        if (saved.isEmpty()) return
        runtime.settings.update { PixivSavedPages.recorded(it, illust.id, saved) }
        val outcome = try {
            runtime.bookmarking.afterSave(illust)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            runtime.tell(failureText(PixivFailure.of(e)).get(language()))
            null
        } ?: return
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
        is PixivSheet.EditWork -> BookmarkEditor(sheet, sheets)
        is PixivSheet.Follow -> FollowDialog(sheet.user, sheets)
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
