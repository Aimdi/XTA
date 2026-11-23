package com.aimdi.xtanative.ui.media

import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropTransferAction
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.draganddrop.DragAndDropTransferable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.ui.common.IconTip
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.media_drag_out
import com.aimdi.xtanative.ui.post.PostIcons
import java.awt.Cursor
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

// Compose's desktop drag data still carries @ExperimentalComposeUiApi.
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal actual fun PictureDragHandle(save: MediaSave, shownUrl: String) {
    // The picture as a file once the image cache holds it: the page may still be loading it (a large original on a slow line), so it is looked for until it comes or the page goes.
    val file by produceState<File?>(null, shownUrl, save.fileName) {
        value = null
        var wait = DraggedPictures.RETRY_MS
        while (true) {
            withContext(Dispatchers.IO) { DraggedPictures.file(shownUrl, save.fileName) }?.let { value = it; return@produceState }
            delay(wait)
            wait = (wait * 2).coerceAtMost(DraggedPictures.RETRY_MAX_MS)
        }
    }
    val label = stringResource(Res.string.media_drag_out)
    IconTip(label) {
        Box(
            Modifier.size(48.dp)
                .pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(if (file != null) Cursor.MOVE_CURSOR else Cursor.DEFAULT_CURSOR)))
                .dragAndDropSource { _ ->
                    file?.takeIf { it.isFile }?.let { picture ->
                        DragAndDropTransferData(DragAndDropTransferable(DraggedPictures.FileTransfer(picture)), supportedActions = listOf(DragAndDropTransferAction.Copy))
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(PostIcons.DragIndicator, label, tint = if (file != null) Color.White else Color.White.copy(alpha = 0.4f))
        }
    }
}

/**
 * Pictures dragged out of the viewer, as files another app takes: the
 * copy the image cache already holds (nothing fetched for it), under the
 * name a download saves it by, in the app's cache folder.
 */
internal object DraggedPictures {
    const val RETRY_MS = 500L
    const val RETRY_MAX_MS = 4_000L

    /** Older drags than this are cleared away the first time a picture is put out. */
    private const val KEEP_MS = 24L * 60 * 60 * 1000

    private val folder: File by lazy {
        // The XDG spec has a relative $XDG_CACHE_HOME ignored.
        val cache = System.getenv("XDG_CACHE_HOME")?.let(::File)?.takeIf { it.isAbsolute } ?: File(System.getProperty("user.home"), ".cache")
        File(File(cache, "xta-native"), "dragged-pictures").also { folder ->
            folder.mkdirs()
            val old = System.currentTimeMillis() - KEEP_MS
            folder.listFiles()?.filter { it.isFile && it.lastModified() < old }?.forEach { it.delete() }
        }
    }

    /** [url]'s cached picture as a file named [name] (its last path part only); null while the cache has none. */
    fun file(url: String, name: String): File? = runCatching {
        val cache = coil3.SingletonImageLoader.get(coil3.PlatformContext.INSTANCE).diskCache ?: return null
        // Copied while the snapshot is open, so the cache does not drop or rewrite the file meanwhile.
        cache.openSnapshot(url)?.use { snapshot -> snapshot.data.toFile().takeIf { it.isFile }?.let { put(it, folder, name) } }
    }.getOrNull()

    /** [source] copied into [folder] as [name], made safe as one file's name. */
    fun put(source: File, folder: File, name: String): File {
        val safe = name.substringAfterLast('/').substringAfterLast('\\').replace(Regex("[\\u0000-\\u001f]"), "").trimStart('.').ifEmpty { "picture" }
        val target = File(folder, safe)
        if (!(target.isFile && target.length() == source.length())) {
            val part = File(folder, "$safe.part")
            source.copyTo(part, overwrite = true)
            if (!part.renameTo(target)) {
                target.delete()
                part.renameTo(target)
            }
        }
        return target
    }

    /** A file as a drag hands it over: the file list Java turns into the desktop's `text/uri-list`. */
    class FileTransfer(private val file: File) : Transferable {
        override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(DataFlavor.javaFileListFlavor)

        override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = flavor == DataFlavor.javaFileListFlavor

        override fun getTransferData(flavor: DataFlavor): Any = if (flavor == DataFlavor.javaFileListFlavor) listOf(file) else throw UnsupportedFlavorException(flavor)
    }
}
