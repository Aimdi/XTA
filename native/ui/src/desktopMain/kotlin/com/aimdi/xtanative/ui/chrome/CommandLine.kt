package com.aimdi.xtanative.ui.chrome

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

    /** The options a start knows; every other argument beginning with `-` is refused. */
    val options: Set<String> = setOf("--help", "-h", "--version", "--minimized", "--verbose")

    /** The first argument of [args] that looks like an option but is none this app knows, or null. */
    fun unknownOption(args: Array<String>): String? = args.firstOrNull { it.startsWith("-") && it !in options }

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
        |Usage: xta-native [OPTION]... [URL]
        |
        |XTA Native, a private, read-only reader for X and other networks.
        |
        |  URL            an http:// or https:// link to open in the app; when the
        |                 app is running already, its window opens the link and
        |                 this start ends
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
