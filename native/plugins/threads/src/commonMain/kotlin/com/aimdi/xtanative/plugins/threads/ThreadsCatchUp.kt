package com.aimdi.xtanative.plugins.threads

/**
 * Home reads the followed accounts twenty at a time (six with a session).
 * While it is shown, the next ones are read a pause after the last load,
 * so a reader following many accounts need not pull again and again; it
 * stops once a load leaves as many unread as before (accounts that fail,
 * or Meta's throttle), and a pull reads on from there.
 */
internal object ThreadsCatchUp {
    /** The pause between two loads, past each request's own pacing. */
    const val PAUSE_MILLIS = 20_000L

    /** Whether to read on: some are still unread, and the last load changed how many (a follow can add some). */
    fun readsOn(before: Int?, pending: Int): Boolean = pending > 0 && pending != before
}
