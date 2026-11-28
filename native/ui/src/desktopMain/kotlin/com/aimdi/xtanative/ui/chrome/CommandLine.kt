package com.aimdi.xtanative.ui.chrome

import com.aimdi.xtanative.ui.links.LaunchPlace
import java.io.File
import java.net.URI

/**
 * What `xta-native` is asked on its command line before anything starts:
 * `--help` (`-h`) and `--version` are answered on the terminal and end the
 * process, as command-line programs answer them; an option it does not know
 * (`--bogus`, `-x`) is refused there with the usage and exit status 2, as
 * getopt programs refuse one, instead of starting the app; anything else (a
 * link, `--minimized`, `--verbose`) starts the app. A link never begins with `-`. Terminal text, in English as such answers are, not the app's screens.
 */
object CommandLine {
    /** What a start does with its arguments. */
    enum class Answer { HELP, VERSION, RUN, UNKNOWN }

    /** The option whose next argument (or `=` value) is a file to open: `--open <file>`. */
    const val OPEN = "--open"

    /** The options that open a place of the app, as the desktop entry's actions start it. */
    val places: Map<String, LaunchPlace> = LaunchPlace.entries.associateBy { "--" + it.key }

    /** The options a start knows; every other argument beginning with `-` is refused. */
    val options: Set<String> = setOf("--help", "-h", "--version", "--minimized", "--verbose", OPEN) + places.keys

    /** The first argument of [args] that looks like an option but is none this app knows, or null; `--open`'s file is no option. */
    fun unknownOption(args: Array<String>): String? = args.withIndex().firstOrNull { (index, arg) ->
        arg.startsWith("-") && arg !in options && !arg.startsWith("$OPEN=") && args.getOrNull(index - 1) != OPEN
    }?.value

    /**
     * The files [args] ask to open, as absolute paths from [cwd]: each
     * `--open <file>` (or `--open=<file>`), and each other argument that is
     * no option and no web link (a path, or a `file:` URI as a file manager's
     * "Open with" passes through %U). Whether a file is there, and what it
     * is, the running copy finds out.
     */
    fun filesIn(args: Array<String>, cwd: File = File(System.getProperty("user.dir"))): List<String> = args.withIndex().mapNotNull { (index, arg) ->
        when {
            arg.startsWith("$OPEN=") -> arg.removePrefix("$OPEN=")
            args.getOrNull(index - 1) == OPEN -> arg
            arg.startsWith("-") || arg == OPEN -> null
            arg.startsWith("http://", ignoreCase = true) || arg.startsWith("https://", ignoreCase = true) -> null
            else -> arg
        }
    }.mapNotNull { pathOf(it, cwd) }.distinct()

    /** [arg] as an absolute, normalised path: a `file:` URI of this machine, or a path from [cwd]; null for anything else. */
    internal fun pathOf(arg: String, cwd: File): String? {
        if (arg.isBlank()) return null
        val file = when {
            arg.startsWith("file:", ignoreCase = true) -> runCatching { URI(arg) }.getOrNull()
                ?.takeIf { it.host.isNullOrEmpty() || it.host == "localhost" }
                ?.let { runCatching { File(URI("file", null, it.path, null)) }.getOrNull() }
            arg.contains("://") -> null
            else -> File(arg).let { if (it.isAbsolute) it else File(cwd, arg) }
        }
        return file?.toPath()?.normalize()?.toString()
    }

    /** The place [args] ask for (`--saved`, `--settings`, `--deck`), the first one named; null for none. */
    fun placeIn(args: Array<String>): LaunchPlace? = args.firstNotNullOfOrNull { places[it] }

    /** What the terminal is told for [option] before the usage, as getopt programs word it. */
    fun unknownLine(option: String): String = "xta-native: unknown option '$option'"

    /** Exit status of a start refused for an unknown option (2: wrong usage, as getopt programs end). */
    const val USAGE_ERROR = 2

    /**
     * The answer [args] ask for: an unknown option first (nothing else is
     * done then), help before the version when both are asked.
     */
    fun answer(args: Array<String>): Answer = when {
        unknownOption(args) != null -> Answer.UNKNOWN
        args.any { it == "--help" || it == "-h" } -> Answer.HELP
        "--version" in args -> Answer.VERSION
        else -> Answer.RUN
    }

    /** `xta-native --version`'s line: the name, and [version] where the build has one. */
    fun versionLine(version: String): String = "xta-native $version".trim()

    /** `xta-native --help`'s text. */
    val usage: String = """
        |Usage: xta-native [OPTION]... [URL | FILE]
        |
        |XTA Native, a private, read-only reader for X and other networks.
        |
        |  URL            an http:// or https:// link to open in the app; when the
        |                 app is running already, its window opens the link and
        |                 this start ends (also the pixiv:// address Pixiv's
        |                 sign-in in the browser hands back)
        |  FILE, --open FILE
        |                 an XTA backup to restore, or an OPML list of feeds to
        |                 follow, each asked first in the app's window
        |  --saved, --settings, --deck
        |                 open Saved, the settings or the deck
        |  --minimized    start hidden in the tray (with Settings > General >
        |                 Close to tray on, while a tray shows the icon)
        |  --verbose      also log the system's facts and every request the app
        |                 makes (where to, not what: no query, header or body)
        |  --version      print the version and exit
        |  -h, --help     print this help and exit
        |
        |Files:
        |  ${'$'}XDG_DATA_HOME/xta-native    the app's data (~/.local/share/xta-native)
        |  ${'$'}XDG_CACHE_HOME/xta-native   pictures and other caches (~/.cache/xta-native)
        |  ${'$'}XDG_STATE_HOME/xta-native/logs
        |                             the app's log, what it would print here, without
        |                             cookies or tokens (~/.local/state/xta-native/logs)
        |""".trimMargin()
}
