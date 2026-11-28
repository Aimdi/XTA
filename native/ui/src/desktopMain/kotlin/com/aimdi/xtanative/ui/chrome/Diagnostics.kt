package com.aimdi.xtanative.ui.chrome

import com.aimdi.xtanative.core.net.RequestTrace
import com.aimdi.xtanative.ui.media.MpvEngine
import com.aimdi.xtanative.ui.media.MpvEngineState
import com.aimdi.xtanative.ui.media.MpvEngines
import com.aimdi.xtanative.ui.plugins.OLDEST_PLUGIN_API
import com.aimdi.xtanative.ui.plugins.PLUGIN_API_VERSION
import java.io.File
import java.util.Locale

/**
 * What a bug report needs to know of this app and machine: Settings › App
 * info's "Copy diagnostics", and the first lines `--verbose` writes into the
 * log. Only versions and names: the app's, the system's, the desktop's, the
 * renderer's, Java's and the plugin API's; never an account, a token, a
 * server or proxy address, or a path (the home folder and the user's name
 * are taken out of anything that would carry them).
 */
object Diagnostics {
    /** The main window's renderer (skiko's API: OPENGL, SOFTWARE_FAST, …), once it is made; asked when the text is made. */
    @Volatile
    var renderer: () -> String? = { null }

    /** The facts the text is made of; each null when unknown. */
    data class Facts(
        val version: String,
        val releaseTag: String?,
        val system: String?,
        val kernel: String,
        val desktop: String?,
        val session: String?,
        val renderer: String?,
        val java: String,
        val locale: String,
        val video: String,
    )

    /** The lines of [facts], in English as a bug report is read. */
    fun text(facts: Facts): String = listOfNotNull(
        "XTA Native ${facts.version.ifEmpty { "dev" }}" + (facts.releaseTag?.let { " ($it)" } ?: ""),
        "System: ${facts.system ?: "unknown"}; ${facts.kernel}",
        "Desktop: ${facts.desktop ?: "unknown"}; session ${facts.session ?: "unknown"}",
        "Renderer: ${facts.renderer ?: "unknown"}",
        "Java: ${facts.java}",
        "Plugin API: $PLUGIN_API_VERSION (oldest $OLDEST_PLUGIN_API)",
        "Language: ${facts.locale}",
        "Video: ${facts.video}",
    ).joinToString("\n") { scrubbed(it) }

    /** This machine's facts, from [env] and [property]; the system's name from [osRelease]'s PRETTY_NAME. */
    fun facts(
        env: (String) -> String? = System::getenv,
        property: (String) -> String? = System::getProperty,
        osRelease: File = File("/etc/os-release"),
        video: () -> String = ::videoEngine,
    ): Facts = Facts(
        version = property("xtanative.version").orEmpty(),
        releaseTag = property("xtanative.release_tag")?.takeIf { it.isNotBlank() },
        system = prettyName(runCatching { osRelease.readText() }.getOrNull()),
        kernel = listOfNotNull(property("os.name"), property("os.version"), property("os.arch")).joinToString(" "),
        desktop = env("XDG_CURRENT_DESKTOP")?.takeIf { it.isNotBlank() },
        session = env("XDG_SESSION_TYPE")?.takeIf { it.isNotBlank() },
        renderer = runCatching { renderer() }.getOrNull(),
        java = listOfNotNull(property("java.runtime.version") ?: property("java.version"), property("java.vendor")).joinToString(", "),
        locale = Locale.getDefault().toLanguageTag(),
        video = video(),
    )

    /** os-release's PRETTY_NAME (quotes taken off), or its NAME. */
    fun prettyName(osRelease: String?): String? {
        val values = osRelease.orEmpty().lines().mapNotNull { line ->
            line.substringBefore('=', "").takeIf { it.isNotEmpty() }?.let { it to line.substringAfter('=').trim().trim('"', '\'') }
        }.toMap()
        return (values["PRETTY_NAME"] ?: values["NAME"])?.takeIf { it.isNotBlank() }
    }

    /** [line] without the home folder or the user's name, should anything have carried them. */
    internal fun scrubbed(line: String, home: String = System.getProperty("user.home").orEmpty(), user: String = System.getProperty("user.name").orEmpty()): String {
        val homeless = if (home.length > 1) line.replace(home, "~") else line
        return if (user.length > 2) homeless.replace(user, "[user]") else homeless
    }

    /** `--verbose`: the facts first, then every request's line ([RequestTrace]), into the log through stdout. */
    fun verbose() {
        println("XTA Native: verbose\n" + text(facts(video = ::videoEngineSoFar)).lines().joinToString("\n") { "XTA Native: $it" })
        RequestTrace.sink = { println(it) }
    }

    /** The engine as far as it is known without opening it: the log's first lines come before any video, which opens it and says so. */
    private fun videoEngineSoFar(): String = when {
        MpvEngine.opened -> videoEngine()
        MpvEngine.expected -> "libmpv, opened at the first video"
        else -> "no mpv (videos open in the system's player)"
    }

    private fun videoEngine(): String = when (val state = MpvEngine.state) {
        is MpvEngineState.Ready -> "${state.library}, mpv client API ${MpvEngines.versionText(state.apiVersion)}"
        is MpvEngineState.Missing -> "no mpv (videos open in the system's player)"
    }
}
