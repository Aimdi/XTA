package com.aimdi.xtanative.ui.chrome

/**
 * What `xta-native` is asked on its command line before anything starts:
 * `--help` (`-h`) and `--version` are answered on the terminal and end the
 * process, as command-line programs answer them; anything else starts the
 * app. Terminal text, in English as such answers are, not the app's screens.
 */
object CommandLine {
    /** What a start does with its arguments. */
    enum class Answer { HELP, VERSION, RUN }

    /** The answer [args] ask for: help before the version when both are asked. */
    fun answer(args: Array<String>): Answer = when {
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
        |  --version      print the version and exit
        |  -h, --help     print this help and exit
        |
        |Files:
        |  ${'$'}XDG_DATA_HOME/xta-native    the app's data (~/.local/share/xta-native)
        |  ${'$'}XDG_CACHE_HOME/xta-native   pictures and other caches (~/.cache/xta-native)
        |""".trimMargin()
}
