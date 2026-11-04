package com.aimdi.xtanative.ui.subscriptions

import com.aimdi.xtanative.core.backup.FollowedAccount
import com.aimdi.xtanative.core.session.AccountLookup
import com.aimdi.xtanative.core.session.FollowCheck
import com.aimdi.xtanative.core.session.FollowHealth
import com.aimdi.xtanative.core.session.HealthCheck
import com.aimdi.xtanative.core.x.XAnswerException
import com.aimdi.xtanative.core.x.XRateLimitedException
import com.aimdi.xtanative.core.x.XUserUnavailableException
import com.aimdi.xtanative.core.x.model.XUser
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class CleanupScanTest {
    private fun account(id: String, screenName: String) = FollowedAccount(id, screenName, screenName, null, false, true, 0)

    @Test
    fun xsAnswersNameTheAccountGoneOrSuspendedAndAnythingElseIsNoAnswer() {
        assertEquals(AccountLookup.Suspended, CleanupScan.lookupOf(XUserUnavailableException("a", "Suspended")))
        assertEquals(AccountLookup.Missing, CleanupScan.lookupOf(XUserUnavailableException("a", null)))
        assertEquals(AccountLookup.Missing, CleanupScan.lookupOf(XAnswerException(50, "User not found.")))
        assertEquals(AccountLookup.Suspended, CleanupScan.lookupOf(XAnswerException(63, "User has been suspended.")))
        assertNull(CleanupScan.lookupOf(XAnswerException(88, "Rate limit")))
        assertNull(CleanupScan.lookupOf(IOException("offline")))
    }

    @Test
    fun aRenamedAccountIsFoundByIdAndRepairedNotRemoved(): Unit = runBlocking {
        val fresh = XUser("1", "newname", "New")
        val health = FollowCheck.check<XUser>(byName = { AccountLookup.Missing }, byId = { AccountLookup.Found(fresh) })
        assertEquals(FollowHealth.RENAMED, health.health)
        val check = CleanupScan.checkOf(health)
        assertEquals(CleanupCheck.Renamed(fresh), check)
        val state = CleanupScan.after(CleanupState(total = 2), account("1", "oldname"), check)!!
        assertEquals(listOf(Rename("oldname", "newname")), state.renamed)
        assertEquals(1, state.checked)
    }

    @Test
    fun onlyWhatXConfirmedGoneIsOfferedForRemoval() {
        var state = CleanupState(total = 4)
        state = CleanupScan.after(state, account("1", "gone"), CleanupScan.checkOf(HealthCheck(FollowHealth.MISSING)))!!
        state = CleanupScan.after(state, account("2", "banned"), CleanupScan.checkOf(HealthCheck(FollowHealth.SUSPENDED)))!!
        state = CleanupScan.after(state, account("3", "flaky"), CleanupScan.checkOf(HealthCheck(FollowHealth.UNREACHABLE)))!!
        assertEquals(listOf("gone" to BrokenReason.MISSING, "banned" to BrokenReason.SUSPENDED), state.broken.map { it.first.screenName to it.second })
        assertEquals(1, state.unreachable)
        assertEquals(0.75f, state.fraction)
        val end = CleanupScan.finished(state)
        assertTrue(end.done)
        assertEquals(2, end.unreachable, "the account never reached is kept as not checked")
    }

    @Test
    fun aRateLimitStopsTheScan() {
        assertNull(CleanupScan.after(CleanupState(total = 3), account("1", "a"), CleanupScan.checkOf(HealthCheck(FollowHealth.RATE_LIMITED))))
        assertEquals(CleanupCheck.RateLimited, CleanupScan.checkOf(HealthCheck(FollowHealth.RATE_LIMITED)))
        runBlocking {
            val limited = FollowCheck.check<XUser>(byName = { throw XRateLimitedException() }, byId = { AccountLookup.Missing })
            assertEquals(FollowHealth.RATE_LIMITED, limited.health)
        }
    }
}
