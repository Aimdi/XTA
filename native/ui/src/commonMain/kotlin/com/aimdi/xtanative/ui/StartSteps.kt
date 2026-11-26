package com.aimdi.xtanative.ui

/**
 * The screens' own milestones of a cold start (the root past its wait for
 * the session, Home, the first post), told to whoever is timing it: the
 * Android shell writes them into its line of the log until the first frame
 * (native/PERF.md), then stops listening, after which a milestone costs one
 * read of a field.
 */
object StartSteps {
    /** Hears each milestone by name while a start is timed; null otherwise. */
    @Volatile
    var listener: ((String) -> Unit)? = null

    fun reached(step: String) {
        listener?.invoke(step)
    }
}
