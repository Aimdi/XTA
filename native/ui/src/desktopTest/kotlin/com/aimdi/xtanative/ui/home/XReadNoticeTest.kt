package com.aimdi.xtanative.ui.home

import com.aimdi.xtanative.core.x.XAccount
import com.aimdi.xtanative.core.x.XAccountHealth
import com.aimdi.xtanative.core.x.XAccountPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class XReadNoticeTest {
    private val now = 1_800_000_000_000L
    private val accounts = listOf(XAccount("a", "amy", emptyMap()), XAccount("b", "bo", emptyMap()), XAccount("c", null, emptyMap()))

    @Test
    fun nothingToSayWhileEveryAccountReads() {
        assertNull(XReadNotice.of(accounts, { XAccountHealth(2) }, null, now))
        assertNull(XReadNotice.of(accounts, { XAccountHealth() }, now - 1, now), "a pause that ended says nothing")
    }

    @Test
    fun aPauseOfEveryReadComesFirst() {
        val notice = XReadNotice.of(accounts, { XAccountHealth(3, now) }, now + 60_000, now)
        assertEquals(XReadNotice.Paused(now + 60_000), notice)
        assertEquals(now + 60_000, XReadNotice.endsAt(notice))
    }

    @Test
    fun flaggedAccountsAndOnesAwaitingTheirProbeLookSignedOut() {
        val health = mapOf(
            "a" to XAccountHealth(3, now - 60_000),
            "b" to XAccountHealth(1),
            "c" to XAccountHealth(3, now - XAccountPolicy.NOT_FOUND_COOLDOWN_MILLIS - 1),
        )
        assertEquals(XReadNotice.SignedOut(listOf("@amy", "@?")), XReadNotice.of(accounts, { health.getValue(it) }, null, now))
        assertNull(XReadNotice.endsAt(XReadNotice.SignedOut(listOf("@amy"))))
    }

    @Test
    fun anAccountPausedOnItsOwnIsShownInSettingsNotHere() {
        assertNull(XReadNotice.of(accounts, { XAccountHealth(pausedUntil = now + 1) }, null, now))
    }
}
