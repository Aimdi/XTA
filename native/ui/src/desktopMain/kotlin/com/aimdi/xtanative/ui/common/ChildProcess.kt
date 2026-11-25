package com.aimdi.xtanative.ui.common

/**
 * Starts the desktop's helper programs (xdg-open, a file manager) with the
 * environment the reader started XTA with. The launcher
 * (packaging/linux/xta-native-launch) puts its class-data archive into
 * JAVA_TOOL_OPTIONS, which every child would inherit: a Java program opened
 * from XTA would try XTA's archive. The launcher keeps the reader's own value
 * in [ORIGINAL], and whether there was one in [WAS_SET]; a child gets that
 * back ([restore]).
 */
object ChildProcess {
    /** The reader's own JAVA_TOOL_OPTIONS, as the launcher found it. */
    const val ORIGINAL = "XTA_NATIVE_ORIG_JTO"

    /** "1" when the reader had JAVA_TOOL_OPTIONS set, "0" when not; absent when the launcher left it alone. */
    const val WAS_SET = "XTA_NATIVE_ORIG_JTO_SET"

    private const val OPTIONS = "JAVA_TOOL_OPTIONS"

    /** A [ProcessBuilder] for [command] whose environment is the reader's ([restore]). */
    fun builder(vararg command: String): ProcessBuilder = ProcessBuilder(*command).also { restore(it.environment()) }

    /** Starts [command] with the reader's environment. */
    fun start(vararg command: String): Process = builder(*command).start()

    /**
     * Puts the reader's JAVA_TOOL_OPTIONS back into [environment] (or takes it
     * out, when they had none) and drops the launcher's two marks. Without the
     * marks (XTA started without the launcher) nothing changes.
     */
    fun restore(environment: MutableMap<String, String>) {
        val wasSet = environment.remove(WAS_SET) ?: return
        val original = environment.remove(ORIGINAL).orEmpty()
        when (wasSet) {
            "1" -> environment[OPTIONS] = original
            else -> environment.remove(OPTIONS)
        }
    }
}
