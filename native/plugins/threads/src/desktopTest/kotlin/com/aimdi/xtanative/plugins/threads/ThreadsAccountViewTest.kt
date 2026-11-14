package com.aimdi.xtanative.plugins.threads

import com.aimdi.xtanative.networks.threads.ThreadsAccountState
import com.aimdi.xtanative.networks.threads.ThreadsAccountStatus
import com.aimdi.xtanative.networks.threads.ThreadsException
import com.aimdi.xtanative.networks.threads.ThreadsFailure
import com.aimdi.xtanative.networks.threads.ThreadsGuardKept
import com.aimdi.xtanative.networks.threads.ThreadsKept
import com.aimdi.xtanative.networks.threads.ThreadsSession
import com.aimdi.xtanative.networks.threads.ThreadsSessionHeaders
import com.aimdi.xtanative.store.settings.PrefValue
import com.aimdi.xtanative.store.settings.Settings
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** What the reader is told of their account: on the screens that use it, in Settings, and where a read was held. */
class ThreadsAccountViewTest {
    private val now = Instant.parse("2026-11-14T13:00:00Z")
    private val until = Instant.parse("2026-11-14T14:32:00Z")

    @Test
    fun aScreenThatReadsWithTheAccountSaysSoOrWhyItIsPausedAndUntilWhen() {
        assertEquals("Using your Threads account", ThreadsAccountView.line(ThreadsAccountStatus(), "en", now, ZoneOffset.UTC))
        val resting = ThreadsAccountStatus(ThreadsAccountState.RESTING, until, 15, 300)
        val english = ThreadsAccountView.line(resting, "en", now, ZoneOffset.UTC)
        assertTrue(english.startsWith("Paused to protect your account — continues at ") && "2:32" in english, english)
        assertTrue("14:32" in ThreadsAccountView.line(resting, "de", now, ZoneOffset.UTC), "the time in the reader's language")
        assertEquals("Using your Threads account", ThreadsAccountView.line(resting, "en", until, ZoneOffset.UTC), "a rest whose end has passed is over")
        assertEquals("Threads signed this session out — sign in again", ThreadsAccountView.line(ThreadsAccountStatus(ThreadsAccountState.SIGNED_OUT), "en", now))
    }

    @Test
    fun settingsShowTheStateAndTodaysReadsAgainstTheBudget() {
        val resting = ThreadsAccountStatus(ThreadsAccountState.RESTING, until, 42, 300)
        assertTrue(ThreadsAccountView.settingsLine(resting, "de", now, ZoneOffset.UTC).endsWith("14:32"))
        assertEquals("Active — used only when you open something that needs it", ThreadsAccountView.settingsLine(ThreadsAccountStatus(), "en", now))
        assertEquals("Account reads in the last 24 hours: 42 of 300", ThreadsAccountView.usage(resting, "en"))
    }

    @Test
    fun aReadTheGuardHeldSaysTheAccountIsPausedAndWhenItGoesOn() {
        val held = ThreadsException(ThreadsFailure.ACCOUNT_RESTING, "budget", retryAt = until)
        assertTrue(ThreadsFailures.profile(held, "de", ZoneOffset.UTC).endsWith("14:32"))
        assertTrue(ThreadsFailures.nextPage(held, "en", ZoneOffset.UTC).startsWith("Paused to protect your account"))
        val parked = ThreadsException(ThreadsFailure.SESSION_SUSPENDED, "cooling down", retryAt = until)
        assertTrue(ThreadsFailures.ownList(parked, "en", ZoneOffset.UTC).startsWith("Paused to protect your account"))
        assertEquals("Paused to protect your account", ThreadsFailures.settings(ThreadsException(ThreadsFailure.ACCOUNT_RESTING, "budget")).get("en"))
    }

    @Test
    fun theGuardsCountsAreKeptInTheSettingsAndNoSecretWithThem() {
        val guard = ThreadsGuardKept(
            sent = listOf(1_700_000_000_000, 1_700_000_004_000),
            signals = listOf(1_700_000_005_000),
            signedOut = true,
            headers = ThreadsSessionHeaders("web agent", "app agent", "238260118697367", "129477"),
            rung = 1,
            restEnd = 1_700_021_605_000,
        )
        val settings = ThreadsSettingsMemory.written(Settings(), ThreadsKept(cooldownUntil = 1_700_021_605_000, guard = guard))
        assertEquals(guard, ThreadsSettingsMemory.read(settings).guard, "what a restart reads back")
        assertEquals(1_700_021_605_000, ThreadsSettingsMemory.read(settings).cooldownUntil)
        val stored = (settings.values[ThreadsSettingsMemory.GUARD_KEY] as PrefValue.Text).value
        assertFalse(listOf("sessionid", "csrftoken", "IGT:2", "Bearer").any { it in stored }, stored)
        assertEquals(ThreadsGuardKept(), ThreadsSettingsMemory.read(Settings()).guard, "nothing kept reads as nothing")
    }

    @Test
    fun wordsSearchPostsAsAGuestUnlessTheReaderLetTheSessionSearchFirst() {
        val cookies = "sessionid=s; csrftoken=c; ds_user_id=1; mid=m; ig_did=g"
        assertFalse(ThreadsSearchRules.listsPeopleFirst(ThreadsSession(cookies)))
        assertTrue(ThreadsSearchRules.listsPeopleFirst(ThreadsSession(cookies, useSessionApis = true)))
        assertFalse(ThreadsSearchRules.listsPeopleFirst(ThreadsSession(bearer = "IGT:2:x", useSessionApis = true)))
    }
}
