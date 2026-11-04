package com.aimdi.xtanative.ui.speech

import com.aimdi.xtanative.services.speech.VoiceFailure
import com.aimdi.xtanative.services.speech.VoiceInstall
import java.util.Locale

/** What a voice's (or the voice engine's) row offers as it stands now. */
enum class VoiceAction { DOWNLOAD, RETRY, CANCEL, WAIT, DELETE }

/** The line under a download's details that says where it stands; none while it is absent. */
sealed interface VoiceStatus {
    data class Progress(val received: Long, val total: Long) : VoiceStatus

    data object Installing : VoiceStatus

    data class Ready(val bytes: Long) : VoiceStatus

    data class Failed(val reason: VoiceFailure) : VoiceStatus
}

/** The rows of the downloaded voices (offline_voices_section.dart's OfflineDownloadTile). */
object VoiceRows {
    fun action(install: VoiceInstall): VoiceAction = when (install) {
        VoiceInstall.Absent -> VoiceAction.DOWNLOAD
        is VoiceInstall.Failed -> VoiceAction.RETRY
        is VoiceInstall.Downloading -> VoiceAction.CANCEL
        VoiceInstall.Installing -> VoiceAction.WAIT
        is VoiceInstall.Ready -> VoiceAction.DELETE
    }

    /** Where it stands; a download whose size the server has not said counts against [expectedBytes]. */
    fun status(install: VoiceInstall, expectedBytes: Long): VoiceStatus? = when (install) {
        VoiceInstall.Absent -> null
        is VoiceInstall.Downloading -> VoiceStatus.Progress(install.received, install.total ?: expectedBytes)
        VoiceInstall.Installing -> VoiceStatus.Installing
        is VoiceInstall.Ready -> VoiceStatus.Ready(install.bytes)
        is VoiceInstall.Failed -> VoiceStatus.Failed(install.reason)
    }

    /** How far a download is, 0 to 1; null while its size is unknown, and for anything that is not downloading. */
    fun progress(install: VoiceInstall): Float? = (install as? VoiceInstall.Downloading)?.fraction?.toFloat()

    fun downloading(install: VoiceInstall): Boolean = install is VoiceInstall.Downloading

    /** A voice's locale (`de_DE`) by its own name, "Deutsch (Deutschland)", as XTA names locales; the code when the JDK knows none. */
    fun localeName(locale: String): String {
        val tag = Locale.forLanguageTag(locale.replace('_', '-'))
        val name = tag.getDisplayName(tag)
        if (name.isBlank() || tag.language.isEmpty()) return locale
        return name.replaceFirstChar { if (it.isLowerCase()) it.titlecase(tag) else it.toString() }
    }
}
