package com.aimdi.xtanative.ui.downloads

import android.app.Activity
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.aimdi.xtanative.store.saved.LocalPosts
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The system's "create document" dialog, with the name and type a download suggests. */
private object SaveDocument : ActivityResultContract<Pair<String, String>, Uri?>() {
    override fun createIntent(context: Context, input: Pair<String, String>): Intent =
        Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(input.second)
            .putExtra(Intent.EXTRA_TITLE, input.first)

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? = if (resultCode == Activity.RESULT_OK) intent?.data else null
}

/** Opens the save dialog for each download that asks, one at a time, from the activity the window is in. */
@Composable
internal actual fun PlatformFilesHost() {
    val launcher = rememberLauncherForActivityResult(SaveDocument) { uri -> AndroidSaveDialog.answer(uri) }
    LaunchedEffect(launcher) {
        while (true) {
            val request = AndroidSaveDialog.requests.receive()
            AndroidSaveDialog.open = request
            try {
                launcher.launch(request.name to request.mime)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AndroidSaveDialog.fail(e)
            }
            request.answer.join()
        }
    }
}

/** The system's document picker over the pictures (and videos) a note or a transcription takes. */
@Composable
internal actual fun rememberFilePicker(images: Boolean, title: String): suspend () -> PickedFile? {
    val resolver = LocalContext.current.contentResolver
    val waiting = remember { arrayOfNulls<CompletableDeferred<Uri?>>(1) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        waiting[0]?.complete(uri)
        waiting[0] = null
    }
    return remember(launcher, images) {
        val pick: suspend () -> PickedFile? = {
            val answer = CompletableDeferred<Uri?>()
            waiting[0] = answer
            launcher.launch(if (images) arrayOf("image/png", "image/jpeg") else arrayOf("image/*", "video/*"))
            answer.await()?.let { uri -> withContext(Dispatchers.IO) { read(resolver, uri) } }
        }
        pick
    }
}

private fun read(resolver: ContentResolver, uri: Uri): PickedFile {
    val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }?.takeIf { it.isNotBlank() } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "media"
    val bytes = resolver.openInputStream(uri)?.use { input ->
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > PickedFile.MAX_BYTES) throw IOException("Too large to attach: $name")
            out.write(buffer, 0, read)
        }
        out.toByteArray()
    } ?: throw IOException("Could not read $uri")
    return PickedFile(name, LocalPosts.inferMime(name, resolver.getType(uri)), bytes)
}
