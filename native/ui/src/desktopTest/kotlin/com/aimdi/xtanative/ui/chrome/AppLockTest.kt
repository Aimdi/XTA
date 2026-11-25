package com.aimdi.xtanative.ui.chrome

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppLockTest {
    @BeforeTest
    fun newRun() = AppLockSession.reset()

    @Test
    fun aNewRunLocksOnlyWhenAskedToOnAPhoneWithAScreenLock() {
        assertTrue(AppLockRules.locks(enabled = true, deviceSecure = true, awayMillis = null))
        assertFalse(AppLockRules.locks(enabled = false, deviceSecure = true, awayMillis = null))
        assertFalse(AppLockRules.locks(enabled = true, deviceSecure = false, awayMillis = null))
    }

    @Test
    fun aGlanceAwayDoesNotAskAgainButAMinuteDoes() {
        assertFalse(AppLockRules.locks(enabled = true, deviceSecure = true, awayMillis = AppLockRules.GRACE_MILLIS - 1))
        assertTrue(AppLockRules.locks(enabled = true, deviceSecure = true, awayMillis = AppLockRules.GRACE_MILLIS))
    }

    @Test
    fun theAppOpensLockedAndStaysSoUntilUnlocked() {
        assertTrue(AppLockSession.arrive(enabled = true, deviceSecure = true, now = 0))
        // The lock page covers the screens, which go out of sight and come back behind it.
        AppLockSession.leave(now = 10)
        assertTrue(AppLockSession.arrive(enabled = true, deviceSecure = true, now = 20))
        AppLockSession.unlock()
        assertFalse(AppLockSession.arrive(enabled = true, deviceSecure = true, now = 30))
    }

    @Test
    fun comingBackSoonStaysOpenAndLaterLocks() {
        AppLockSession.arrive(enabled = true, deviceSecure = true, now = 0)
        AppLockSession.unlock()
        AppLockSession.leave(now = 1_000)
        assertFalse(AppLockSession.arrive(enabled = true, deviceSecure = true, now = 1_000 + AppLockRules.GRACE_MILLIS - 1))
        AppLockSession.leave(now = 100_000)
        assertTrue(AppLockSession.arrive(enabled = true, deviceSecure = true, now = 100_000 + AppLockRules.GRACE_MILLIS))
    }

    @Test
    fun leavingWhileLockedDoesNotStartTheMinuteAgainAfterUnlocking() {
        AppLockSession.arrive(enabled = true, deviceSecure = true, now = 0)
        AppLockSession.leave(now = 5)
        AppLockSession.unlock()
        // Unlocked just now: the screens come back from behind the lock page without asking again.
        assertFalse(AppLockSession.arrive(enabled = true, deviceSecure = true, now = 500_000))
    }

    @Test
    fun theLockTurnedOnWhileOpenWaitsForTheNextTimeAway() {
        assertFalse(AppLockSession.arrive(enabled = false, deviceSecure = true, now = 0))
        AppLockSession.leave(now = 10)
        assertFalse(AppLockSession.arrive(enabled = true, deviceSecure = true, now = 20))
        AppLockSession.leave(now = 30)
        assertTrue(AppLockSession.arrive(enabled = true, deviceSecure = true, now = 30 + AppLockRules.GRACE_MILLIS))
    }

    @Test
    fun aLockTurnedOffLetsBackInWithoutAsking() {
        AppLockSession.arrive(enabled = true, deviceSecure = true, now = 0)
        AppLockSession.unlock()
        AppLockSession.leave(now = 10)
        assertFalse(AppLockSession.arrive(enabled = false, deviceSecure = true, now = 10 + 10 * AppLockRules.GRACE_MILLIS))
    }
}
