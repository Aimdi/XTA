package com.aimdi.xtanative.ui.speech

/**
 * Reading aloud's sleep timer: the reading is held once [endsAt] has come.
 * Every time here is in milliseconds of a clock that only goes forward and
 * is handed in, so the timer itself keeps none and is the same in a test.
 */
data class SleepTimer(val endsAt: Long) {
    fun isDue(now: Long): Boolean = now >= endsAt

    /** The minutes left, a minute begun counted whole: what the bar's button shows, never 0 before the timer is due. */
    fun minutesLeft(now: Long): Int = ((left(now) + MINUTE - 1) / MINUTE).toInt()

    /** How long after [now] the minutes shown change, or the timer is due: how long its keeper may wait. */
    fun nextChange(now: Long): Long = left(now).let { left -> (left % MINUTE).takeIf { it > 0 } ?: minOf(left, MINUTE) }

    private fun left(now: Long): Long = (endsAt - now).coerceAtLeast(0)

    companion object {
        private const val MINUTE = 60_000L

        /** The times the bar offers, in minutes. */
        val CHOICES: List<Int> = listOf(15, 30, 60)

        /** A timer that is due [minutes] after [now]. */
        fun of(minutes: Int, now: Long): SleepTimer = SleepTimer(now + minutes * MINUTE)
    }
}
