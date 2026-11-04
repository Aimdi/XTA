package com.aimdi.xtanative.ui.downloads

import com.aimdi.xtanative.core.x.model.XMedia
import com.aimdi.xtanative.store.downloads.MediaHttp
import com.aimdi.xtanative.store.downloads.OkHttpMediaHttp
import com.aimdi.xtanative.store.downloads.downloadRequestHeaders
import com.aimdi.xtanative.store.downloads.mimeTypeFor
import com.aimdi.xtanative.ui.media.MediaSave
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The media viewer's Share, as XTA's (_media.dart): a photo goes to the
 * share sheet as the picture itself, fetched whole into the platform's
 * [PlatformFiles.shareFolder]. A video, or a platform that shares no files,
 * shares the address instead.
 */
object MediaSharing {
    /** Shares [save] as a file; false when it is no photo, the platform cannot, or the host sent nothing. */
    suspend fun share(files: PlatformFiles, save: MediaSave, http: MediaHttp = OkHttpMediaHttp()): Boolean {
        if (save.kind != XMedia.Kind.PHOTO) return false
        val folder = files.shareFolder() ?: return false
        val file = fetch(http, save.url, folder, fileName(save)) ?: return false
        return files.shareFile(file, mimeTypeFor(file.name))
    }

    /** The name a shared photo goes under: its saved name, as a picture when the address named no type. */
    fun fileName(save: MediaSave): String = if ('.' in save.fileName) save.fileName else save.fileName + ".jpg"

    /** [url] fetched whole into [folder] as [name], in place of the photo shared before; null when the host sends no file. */
    suspend fun fetch(http: MediaHttp, url: String, folder: File, name: String): File? = try {
        http.get(url, downloadRequestHeaders(url)).use { response ->
            if (response.code !in 200..299) return null
            withContext(Dispatchers.IO) {
                folder.mkdirs()
                folder.listFiles()?.forEach { it.delete() }
                File(folder, name).also { file -> file.outputStream().use { response.body.copyTo(it) } }
            }
        }
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        null
    }
}
