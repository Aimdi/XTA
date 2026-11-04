package com.aimdi.xtanative.ui.speech

import com.aimdi.xtanative.services.speech.VoiceDownloadCancelled
import com.aimdi.xtanative.services.speech.VoiceTransfer
import com.aimdi.xtanative.store.downloads.MediaHttp
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * A voice's or the voice engine's download over the app's media requests,
 * each into a file of its own in [staging], reporting bytes as they arrive.
 * Redirects are followed (GitHub hands release files out from another host);
 * an answer that is not a whole file throws.
 */
class MediaVoiceTransfer(private val http: MediaHttp, private val staging: File) : VoiceTransfer {
    override suspend fun download(url: String, progress: (received: Long, total: Long?) -> Unit, cancelled: () -> Boolean): File =
        withContext(Dispatchers.IO) {
            staging.mkdirs()
            val file = File.createTempFile("voice-", ".part", staging)
            try {
                http.get(url, emptyMap()).use { response ->
                    if (response.code !in 200..299) throw IOException("HTTP ${response.code}")
                    val total = response.contentLength
                    var received = 0L
                    progress(0, total)
                    file.outputStream().use { out ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            ensureActive()
                            if (cancelled()) throw VoiceDownloadCancelled()
                            val read = response.body.read(buffer)
                            if (read < 0) break
                            out.write(buffer, 0, read)
                            received += read
                            progress(received, total)
                        }
                    }
                    if (total != null && received != total) throw IOException("Incomplete download")
                }
                file
            } catch (error: Throwable) {
                file.delete()
                throw error
            }
        }
}
