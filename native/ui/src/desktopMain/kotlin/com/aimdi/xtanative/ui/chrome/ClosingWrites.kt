package com.aimdi.xtanative.ui.chrome

/**
 * The writes the app makes as it ends (the window's place, the reading place
 * and whatever else is under way), done once: by the normal quit, or by the
 * JVM's shutdown hook when the session ends the app with SIGTERM (logout or
 * shutdown while it waits in the tray), where `application {}` never returns.
 * The quit's own `exitProcess` runs the hook too; it finds them done. An end
 * that asks while the other is writing waits for it, so the JVM does not
 * halt halfway through.
 */
class ClosingWrites(private val write: () -> Unit) {
    private var done = false

    /** Runs the writes unless they ran already (waiting for them while they run); true when this call ran them. */
    @Synchronized
    fun run(): Boolean {
        if (done) return false
        done = true
        runCatching(write)
        return true
    }

    /** Has the JVM's end run them as well, for an end the app is not asked about. */
    fun onShutdown(): ClosingWrites = apply {
        runCatching { Runtime.getRuntime().addShutdownHook(Thread({ run() }, "xta-native-closing-writes")) }
    }
}
