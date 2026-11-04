package com.aimdi.xtanative.ui.downloads

import android.annotation.TargetApi
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import com.aimdi.xtanative.store.downloads.DownloadCancellation
import com.aimdi.xtanative.store.downloads.DownloadEntry
import com.aimdi.xtanative.store.downloads.SharedDownloadFolder
import com.aimdi.xtanative.store.downloads.mimeTypeFor
import com.aimdi.xtanative.store.downloads.sharedFolderOf
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext

/**
 * Android's files, as XTA saves them: a download saved without asking goes
 * into MediaStore (Pictures/XTA, Movies/XTA or Download/XTA, by its type), one
 * for a picked folder into that folder's document tree, and any other through
 * the system's save dialog, which the window's [PlatformFilesHost] opens.
 */
class AndroidFiles(context: Context) : PlatformFiles {
    private val app = context.applicationContext

    /** MediaStore takes a folder of the reader's choosing from Android 10 on; before that a download asks instead. */
    override suspend fun sharedStorageAvailable(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    override suspend fun save(entry: DownloadEntry, file: File, cancellation: DownloadCancellation): String? {
        val tree = entry.treeUri
        return when {
            tree != null -> withContext(Dispatchers.IO) { intoTree(Uri.parse(tree), entry, file, cancellation) }
            entry.background && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                withContext(Dispatchers.IO) { intoShared(entry, file, cancellation) }
            else -> {
                val chosen = AndroidSaveDialog.ask(entry.fileName, mimeTypeFor(entry.fileName)) ?: return null
                withContext(Dispatchers.IO) {
                    try {
                        write(chosen, file, cancellation)
                    } catch (e: Exception) {
                        runCatching { DocumentsContract.deleteDocument(app.contentResolver, chosen) }
                        throw e
                    }
                    chosen.toString()
                }
            }
        }
    }

    override fun folderOf(entry: DownloadEntry): String? = sharedFolderOf(entry)

    override suspend fun open(savedUri: String, fileName: String): Boolean = withContext(Dispatchers.Main) {
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(Uri.parse(savedUri), mimeTypeFor(fileName))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            app.startActivity(intent)
        }.isSuccess
    }

    /** The app's own files cannot be handed to another app without a file provider, which XTA Native does not declare. */
    override suspend fun openFile(file: File, mime: String): Boolean = false

    @TargetApi(Build.VERSION_CODES.Q)
    private fun intoShared(entry: DownloadEntry, file: File, cancellation: DownloadCancellation): String {
        val folder = SharedDownloadFolder.of(entry.fileName)
        val collection = when (folder) {
            SharedDownloadFolder.PICTURES -> MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            SharedDownloadFolder.MOVIES -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            SharedDownloadFolder.DOWNLOADS -> MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, entry.fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeTypeFor(entry.fileName))
            put(MediaStore.MediaColumns.RELATIVE_PATH, folder.relativePath + entry.subfolder?.let { "/$it" }.orEmpty())
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = app.contentResolver
        val uri = resolver.insert(collection, values) ?: throw IOException("MediaStore refused ${entry.fileName}")
        try {
            write(uri, file, cancellation)
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
        return uri.toString()
    }

    private fun intoTree(tree: Uri, entry: DownloadEntry, file: File, cancellation: DownloadCancellation): String {
        val resolver = app.contentResolver
        var parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        entry.subfolder?.split('/')?.filter { it.isNotEmpty() }?.forEach { name -> parent = childFolder(tree, parent, name) }
        val document = DocumentsContract.createDocument(resolver, parent, mimeTypeFor(entry.fileName), entry.fileName)
            ?: throw IOException("The download folder refused ${entry.fileName}")
        try {
            write(document, file, cancellation)
        } catch (e: Exception) {
            runCatching { DocumentsContract.deleteDocument(resolver, document) }
            throw e
        }
        return document.toString()
    }

    /** The folder [name] in [parent], made when it is not there yet. */
    private fun childFolder(tree: Uri, parent: Uri, name: String): Uri {
        val resolver = app.contentResolver
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getDocumentId(parent))
        val columns = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
        resolver.query(children, columns, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.getString(1) == name && cursor.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR) {
                    return DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(0))
                }
            }
        }
        return DocumentsContract.createDocument(resolver, parent, DocumentsContract.Document.MIME_TYPE_DIR, name)
            ?: throw IOException("Could not make the folder $name")
    }

    private fun write(uri: Uri, file: File, cancellation: DownloadCancellation) {
        val output = app.contentResolver.openOutputStream(uri, "w") ?: throw IOException("Could not write $uri")
        output.use { copyCancellable(file, it, cancellation) }
    }
}

/**
 * The system's save dialog, which only an activity can open: a download asks
 * through here, and [PlatformFilesHost] in the window opens the dialog for
 * each request in turn. A request asked while no window is shown waits for one.
 */
internal object AndroidSaveDialog {
    class Request(val name: String, val mime: String) {
        val answer = CompletableDeferred<Uri?>()
    }

    val requests = Channel<Request>(Channel.UNLIMITED)

    /** The request whose dialog is open; its answer may reach a window made again since it opened. */
    @Volatile
    var open: Request? = null

    suspend fun ask(name: String, mime: String): Uri? {
        val request = Request(name, mime)
        requests.send(request)
        return request.answer.await()
    }

    fun answer(uri: Uri?) {
        open?.answer?.complete(uri)
        open = null
    }

    fun fail(error: Throwable) {
        open?.answer?.completeExceptionally(error)
        open = null
    }
}
