package com.aimdi.xtanative.services.speech

import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CancellationException

/** Where a download for reading aloud (a voice, or the engine that runs voices) stands on this device. */
sealed interface VoiceInstall {
    data object Absent : VoiceInstall

    data class Downloading(val received: Long, val total: Long?) : VoiceInstall {
        /** 0–1, or null while the size is unknown. */
        val fraction: Double? get() = total?.takeIf { it != 0L }?.let { (received.toDouble() / it).coerceIn(0.0, 1.0) }
    }

    /** Downloaded; being checked against its pin and unpacked. */
    data object Installing : VoiceInstall

    data class Ready(val path: File, val bytes: Long) : VoiceInstall

    data class Failed(val reason: VoiceFailure) : VoiceInstall
}

enum class VoiceFailure { NETWORK, CHECKSUM, ARCHIVE }

/** A download the reader can call off; what listens is told once, when it is. */
class VoiceCancellation {
    @Volatile
    var cancelled = false
        private set
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    fun cancel() {
        if (cancelled) return
        cancelled = true
        listeners.forEach { it() }
    }

    /** Runs [listener] when this is cancelled, at once if it already is. */
    fun onCancel(listener: () -> Unit) {
        listeners.add(listener)
        if (cancelled) listener()
    }
}

/** Thrown by a [VoiceTransfer] that stopped because its download was cancelled. */
class VoiceDownloadCancelled : Exception("cancelled")

/**
 * Downloads one file to a staging place of its own and answers it,
 * reporting bytes as they arrive; it stops with [VoiceDownloadCancelled]
 * once [cancelled] says so, and throws when the network fails. The
 * downloads module provides it.
 */
fun interface VoiceTransfer {
    suspend fun download(url: String, progress: (received: Long, total: Long?) -> Unit, cancelled: () -> Boolean): File
}

/**
 * Downloads [url] and installs it with [install], reporting each step, and
 * answers where it ended: ready, absent when cancelled, or failed
 * (voice_install.dart). What a voice and the voice engine share. The staged
 * download is removed whatever happens, so a file that did not match is
 * never resumed; [discard] removes what [install] put in place when the
 * download was cancelled while it ran.
 */
suspend fun downloadArchive(
    transfer: VoiceTransfer,
    url: String,
    expectedBytes: Long,
    cancellation: VoiceCancellation,
    install: suspend (File) -> VoiceInstall.Ready?,
    discard: suspend () -> Unit,
    report: (VoiceInstall) -> Unit,
): VoiceInstall {
    var staged: File? = null
    return try {
        staged = transfer.download(url, wholePercents(report, expectedBytes, cancellation)) { cancellation.cancelled }
        if (cancellation.cancelled) throw VoiceDownloadCancelled()
        report(VoiceInstall.Installing)
        val ready = try {
            install(staged)
        } finally {
            staged.delete()
        }
        if (cancellation.cancelled) {
            discard()
            VoiceInstall.Absent
        } else {
            ready ?: VoiceInstall.Failed(VoiceFailure.ARCHIVE)
        }
    } catch (error: CancellationException) {
        throw error
    } catch (_: VoiceDownloadCancelled) {
        VoiceInstall.Absent
    } catch (_: VoiceChecksumMismatch) {
        VoiceInstall.Failed(VoiceFailure.CHECKSUM)
    } catch (_: VoiceArchiveInvalid) {
        VoiceInstall.Failed(VoiceFailure.ARCHIVE)
    } catch (_: Exception) {
        if (cancellation.cancelled) VoiceInstall.Absent else VoiceInstall.Failed(VoiceFailure.NETWORK)
    } finally {
        staged?.delete()
    }
}

/**
 * Progress, but only when the whole percent changes (each megabyte while the
 * size is unknown): a large download would otherwise redraw the settings
 * list thousands of times.
 */
private fun wholePercents(report: (VoiceInstall) -> Unit, expectedBytes: Long, cancellation: VoiceCancellation): (Long, Long?) -> Unit {
    var shown = -1L
    return { received, total ->
        val percent = if (total == null || total == 0L) received shr 20 else received * 100 / total
        if (percent != shown && !cancellation.cancelled) {
            shown = percent
            report(VoiceInstall.Downloading(received, total ?: expectedBytes))
        }
    }
}
