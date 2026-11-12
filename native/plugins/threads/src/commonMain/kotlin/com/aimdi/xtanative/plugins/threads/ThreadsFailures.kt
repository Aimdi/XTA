package com.aimdi.xtanative.plugins.threads

import com.aimdi.xtanative.networks.threads.ThreadsAccountFailure
import com.aimdi.xtanative.networks.threads.ThreadsException
import com.aimdi.xtanative.networks.threads.ThreadsFailure
import com.aimdi.xtanative.networks.threads.ThreadsTrouble
import com.aimdi.xtanative.networks.threads.ThreadsXyException
import com.aimdi.xtanative.ui.plugins.PluginText
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** What a failed Threads read says, in XTA's words (threads_screen.dart, threads_settings.dart, threads_profile_screen.dart). */
internal object ThreadsFailures {
    /** The tab's and the search sheet's sentence for [error]. */
    fun feed(error: Throwable?): PluginText = when ((error as? ThreadsException)?.failure) {
        ThreadsFailure.NOT_CONFIGURED -> Strings.not_configured
        ThreadsFailure.NO_SUCH_FEED -> Strings.error_no_feed
        else -> common(error)
    }

    /** [feed]'s sentence, a throttle naming the time Meta's wait ends. */
    fun feedText(error: Throwable?, language: String, zone: ZoneId = ZoneId.systemDefault()): String =
        throttledUntil(error, language, zone) ?: feed(error).get(language)

    /** The For you section's sentence for [error]: no account is named there. */
    fun forYou(error: Throwable?): PluginText = common(error)

    /** The settings' sentence for a test that failed: there a missing feed is an instance without the route. */
    fun settings(error: Throwable?): PluginText = when ((error as? ThreadsException)?.failure) {
        ThreadsFailure.NOT_CONFIGURED -> Strings.not_configured
        ThreadsFailure.NO_SUCH_FEED -> Strings.error_no_route
        else -> common(error)
    }

    /** A profile's sentence: a profile Meta has no page for is an account that could not be read. */
    fun profile(error: Throwable?, language: String, zone: ZoneId = ZoneId.systemDefault()): String =
        page(error, language, Strings.error_no_feed, zone)

    /** A conversation's sentence: a post Meta has no page for is a post that could not be read. */
    fun post(error: Throwable?, language: String, zone: ZoneId = ZoneId.systemDefault()): String =
        page(error, language, Strings.error_no_post, zone)

    /** A list's next page's sentence: Meta refusing for now names when its wait ends. */
    fun nextPage(error: Throwable?, language: String, zone: ZoneId = ZoneId.systemDefault()): String =
        page(error, language, Strings.error_unreachable, zone)

    /** A search's sentence. */
    fun search(error: Throwable?, language: String, zone: ZoneId = ZoneId.systemDefault()): String =
        page(error, language, Strings.error_unreachable, zone)

    /**
     * What a page says when its read failed, past XTA's words (which named an
     * RSSHub route for any missing page): what the Xy server itself wrote when
     * it wrote it; [missing] for a page Meta does not have; a throttle with
     * the time Meta's wait ends, in [zone]; Meta's sign-in page as such;
     * otherwise what the settings say.
     */
    private fun page(error: Throwable?, language: String, missing: PluginText, zone: ZoneId): String {
        if (error is ThreadsXyException) return error.serverMessage?.trim()?.ifEmpty { null } ?: xy(error).get(language)
        if ((error as? ThreadsException)?.failure == ThreadsFailure.NO_SUCH_FEED) return missing.get(language)
        if ((error as? ThreadsException)?.failure == ThreadsFailure.PRIVATE_ACCOUNT) return Strings.account_private.get(language)
        return throttledUntil(error, language, zone) ?: settings(error).get(language)
    }

    /** Why a followed account could not be read, as its row in the failed accounts' list says it. */
    fun trouble(failure: ThreadsAccountFailure, language: String, zone: ZoneId = ZoneId.systemDefault()): String = when (failure.trouble) {
        ThreadsTrouble.MISSING -> Strings.account_missing.get(language)
        ThreadsTrouble.PRIVATE -> Strings.account_private.get(language)
        ThreadsTrouble.THROTTLED -> failure.retryAt?.let { Strings.error_throttled_until.get(language, time(it, language, zone)) } ?: Strings.error_throttled.get(language)
        ThreadsTrouble.SIGN_IN -> Strings.error_sign_in.get(language)
        ThreadsTrouble.NETWORK -> Strings.error_unreachable.get(language)
    }

    /** When Retry may be pressed again after [error]: the end of a throttle Meta named, while it is ahead of [now]; null otherwise. */
    fun retryBlockedUntil(error: Throwable?, now: Instant = Instant.now()): Instant? =
        (error as? ThreadsException)?.takeIf { it.failure == ThreadsFailure.THROTTLED }?.retryAt?.takeIf { it.isAfter(now) }

    /** A throttle's sentence with the time Meta's wait ends; null for anything else, or a throttle that named no end. */
    private fun throttledUntil(error: Throwable?, language: String, zone: ZoneId): String? {
        val throttle = (error as? ThreadsException)?.takeIf { it.failure == ThreadsFailure.THROTTLED } ?: return null
        return throttle.retryAt?.let { Strings.error_throttled_until.get(language, time(it, language, zone)) }
    }

    /** [at] as a short time of day in [language], where the reader is. */
    fun time(at: Instant, language: String, zone: ZoneId = ZoneId.systemDefault()): String =
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.forLanguageTag(language)).withZone(zone).format(at)

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
        ThreadsFailure.SIGN_IN_REQUIRED -> Strings.error_sign_in
        else -> Strings.error_unreachable
    }
}
