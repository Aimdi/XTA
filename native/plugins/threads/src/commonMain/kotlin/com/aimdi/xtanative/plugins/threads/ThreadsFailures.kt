package com.aimdi.xtanative.plugins.threads

import com.aimdi.xtanative.networks.threads.ThreadsException
import com.aimdi.xtanative.networks.threads.ThreadsFailure
import com.aimdi.xtanative.networks.threads.ThreadsXyException
import com.aimdi.xtanative.ui.plugins.PluginText

/** What a failed Threads read says, in XTA's words (threads_screen.dart, threads_settings.dart, threads_profile_screen.dart). */
internal object ThreadsFailures {
    /** The tab's and the search sheet's sentence for [error]. */
    fun feed(error: Throwable?): PluginText = when ((error as? ThreadsException)?.failure) {
        ThreadsFailure.NOT_CONFIGURED -> Strings.not_configured
        ThreadsFailure.NO_SUCH_FEED -> Strings.error_no_feed
        else -> common(error)
    }

    /** The For you section's sentence for [error]: no account is named there. */
    fun forYou(error: Throwable?): PluginText = common(error)

    /** The settings' sentence for a test that failed: there a missing feed is an instance without the route. */
    fun settings(error: Throwable?): PluginText = when ((error as? ThreadsException)?.failure) {
        ThreadsFailure.NOT_CONFIGURED -> Strings.not_configured
        ThreadsFailure.NO_SUCH_FEED -> Strings.error_no_route
        else -> common(error)
    }

    /**
     * A profile's, a conversation's or a search's sentence: what the Xy server
     * itself wrote for a person when it wrote it, else XTA's words for its
     * kind; a Meta failure says what the settings say of it.
     */
    fun page(error: Throwable?, language: String): String {
        if (error !is ThreadsXyException) return settings(error).get(language)
        error.serverMessage?.trim()?.ifEmpty { null }?.let { return it }
        return xy(error).get(language)
    }

    /** A profile's sentence for [error]. */
    fun profile(error: Throwable?, language: String): String = page(error, language)

    /** The settings' sentence for the Xy server's own failure. */
    fun xy(error: ThreadsXyException): PluginText = when (error.kind) {
        ThreadsXyException.Kind.NOT_CONFIGURED -> Strings.api_not_configured
        ThreadsXyException.Kind.UNAUTHORIZED -> Strings.api_unauthorized
        ThreadsXyException.Kind.NOT_FOUND -> Strings.error_no_feed
        else -> Strings.error_unreachable
    }

    private fun common(error: Throwable?): PluginText = when ((error as? ThreadsException)?.failure) {
        ThreadsFailure.THROTTLED -> Strings.error_throttled
        ThreadsFailure.UNAUTHORIZED -> Strings.error_unauthorized
        ThreadsFailure.SESSION_SUSPENDED -> Strings.error_session_suspended
        else -> Strings.error_unreachable
    }
}
