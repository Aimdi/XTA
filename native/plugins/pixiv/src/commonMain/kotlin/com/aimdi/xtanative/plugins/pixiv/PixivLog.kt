package com.aimdi.xtanative.plugins.pixiv

import com.aimdi.xtanative.core.log.LogRedaction
import com.aimdi.xtanative.networks.NetworkFailure
import com.aimdi.xtanative.networks.pixiv.PixivFailure

/**
 * Pixiv's failed reads in the app's log (its terminal, kept on the desktop
 * in `$XDG_STATE_HOME/xta-native/logs`), so a reader reporting an empty grid
 * has something to show: what failed, its kind and the answer's code. Never
 * an address or a message, which could carry a token or what was searched.
 */
internal object PixivLog {
    /** Where the lines go; the app's terminal, which its log keeps. */
    @Volatile
    var write: (String) -> Unit = ::println

    fun failed(what: String, error: Throwable) = write(line(what, error))

    fun line(what: String, error: Throwable): String {
        val code = (error as? NetworkFailure)?.code?.let { " $it" }.orEmpty()
        return LogRedaction.redact("XTA Native: pixiv: $what failed: ${PixivFailure.of(error)} (${error::class.simpleName ?: "error"}$code)")
    }
}
