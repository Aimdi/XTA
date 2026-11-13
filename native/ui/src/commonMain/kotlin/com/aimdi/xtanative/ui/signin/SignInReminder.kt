package com.aimdi.xtanative.ui.signin

/**
 * XTA's "You are not logged in", asked at most once a launch (main.dart's
 * checkForAccounts and _accountDialogShown): the first time the app opens,
 * whatever it finds, and only then when it holds no X account. On a first
 * launch the intro's account card asks instead, and the question is not
 * brought back later.
 */
class SignInReminder {
    private var asked = false

    /** Whether to ask now, with [hasAccounts] and the app opening on the intro's cards ([onIntro]). */
    @Synchronized
    fun ask(hasAccounts: Boolean, onIntro: Boolean): Boolean {
        if (asked) return false
        asked = true
        return !hasAccounts && !onIntro
    }

    companion object {
        /** This launch's. */
        val launch = SignInReminder()
    }
}
