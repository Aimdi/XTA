package com.aimdi.xtanative.ui.signin

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** XTA's "You are not logged in", asked once a launch (main.dart's checkForAccounts). */
class SignInReminderTest {
    @Test
    fun withoutAnAccountItAsksOnceALaunch() {
        val reminder = SignInReminder()
        assertTrue(reminder.ask(hasAccounts = false, onIntro = false))
        assertFalse(reminder.ask(hasAccounts = false, onIntro = false))
    }

    @Test
    fun anAccountOrTheIntrosCardMeansItDoesNotAsk() {
        assertFalse(SignInReminder().ask(hasAccounts = true, onIntro = false))
        val afterIntro = SignInReminder()
        assertFalse(afterIntro.ask(hasAccounts = false, onIntro = true))
        assertFalse(afterIntro.ask(hasAccounts = false, onIntro = false), "the intro's account card took its place this launch")
    }
}
