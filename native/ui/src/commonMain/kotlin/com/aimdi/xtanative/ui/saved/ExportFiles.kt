package com.aimdi.xtanative.ui.saved

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.downloads_saved_to
import com.aimdi.xtanative.ui.plugins.rememberTextFileSaver
import com.aimdi.xtanative.store.offline.OfflineSaved
import com.aimdi.xtanative.store.offline.OfflineStore
import com.aimdi.xtanative.store.saved.SavedFolder
import com.aimdi.xtanative.store.saved.SavedLibrary
import com.aimdi.xtanative.store.saved.SavedPost
import java.io.File
import org.jetbrains.compose.resources.stringResource

/** What kind of file an export writes. */
enum class ExportFormat(val extension: String, val mime: String) {
    MARKDOWN("md", "text/markdown"),
    HTML("html", "text/html"),
}

/** Where an export landed, as its notice says it: a file on the desktop opens from the notice (a printable page in the browser). */
internal object ExportNotices {
    private const val NAME = "\u0000"

    /** The notice for a file saved at [where] (a path on the desktop, a document's name elsewhere); [savedTo] is "Saved to %1$s" with [NAME] in its place. */
    fun of(where: String, savedTo: String): KeepNotice {
        val file = File(where)
        return if (file.isAbsolute) {
            KeepNotice.SavedTo(file.parent ?: where, file.toURI().toString(), file.name)
        } else {
            KeepNotice.Said(savedTo.replace(NAME, where))
        }
    }

    @Composable
    fun savedToTemplate(): String = stringResource(Res.string.downloads_saved_to, NAME)
}

/** The pictures of [postIds] the offline collection keeps, by the address they came from, as `file:` links; nothing is fetched. */
internal suspend fun keptPictures(offline: OfflineStore, postIds: Collection<String>): Map<String, String> {
    offline.load()
    val state = offline.state.value
    return postIds.flatMap { id ->
        val entry = state.entry(OfflineSaved.idFor(id)) ?: return@flatMap emptyList()
        entry.files.mapNotNull { file -> offline.mediaFile(entry, file)?.let { file.url to it.toURI().toString() } }
    }.toMap()
}

/**
 * Saves what [text] makes as [fileName] through the save dialog as soon as
 * it is shown, says where it went, then [onDone]; the reader closing the
 * dialog ends it too. Nothing it writes is fetched: [text] reads what the app holds.
 */
@Composable
internal fun ExportOnce(title: String, fileName: String, format: ExportFormat, text: suspend () -> String, onDone: () -> Unit) {
    val savedTo = ExportNotices.savedToTemplate()
    val save = rememberTextFileSaver(title, format.mime) { where ->
        where?.let { KeepNotices.show(ExportNotices.of(it, savedTo)) }
        onDone()
    }
    LaunchedEffect(Unit) { save(fileName, text()) }
}

/** [posts] as one [format] file under [title]: what Saved kept of each, in the order given; posts with nothing readable kept are left out. */
internal fun exportedText(title: String?, posts: List<SavedPost>, format: ExportFormat, kept: Map<String, String> = emptyMap()): String {
    val exported = posts.mapNotNull { PostExport.of(it, kept::get) }
    return when (format) {
        ExportFormat.MARKDOWN -> PostExport.markdown(title, exported)
        ExportFormat.HTML -> PostExport.html(title, exported)
    }
}

/** Saves [folder]'s posts of [library], newest saved first, as [format] through the save dialog, then [onDone]. */
@Composable
internal fun ExportFolder(folder: SavedFolder, library: SavedLibrary, format: ExportFormat, title: String, onDone: () -> Unit) {
    val keeper = LocalKeeper.current
    val posts = library.posts.filter { it.folderId == folder.id }.sortedByDescending { it.savedAt }
    ExportOnce(title, PostExport.fileName(folder.name, format.extension), format, text = {
        exportedText(folder.name, posts, format, keptPictures(keeper.offline, posts.map { it.id }))
    }, onDone = onDone)
}
