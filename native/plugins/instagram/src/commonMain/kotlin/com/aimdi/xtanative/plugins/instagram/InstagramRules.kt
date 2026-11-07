package com.aimdi.xtanative.plugins.instagram

import com.aimdi.xtanative.data.Problem
import com.aimdi.xtanative.networks.NetworkRequestException
import com.aimdi.xtanative.networks.instagram.InstagramClient
import com.aimdi.xtanative.networks.instagram.InstagramLink
import com.aimdi.xtanative.networks.instagram.InstagramPost
import com.aimdi.xtanative.store.settings.Settings
import com.aimdi.xtanative.store.settings.ThreadsPrefs
import com.aimdi.xtanative.ui.plugins.MediaItem
import com.aimdi.xtanative.ui.plugins.PluginText
import java.net.URLDecoder
import java.net.URLEncoder

/** What a failed Instagram read says, in XTA's words (instagram_errors.dart), by what Instagram answered. */
internal object InstagramFailures {
    fun of(error: Throwable?): PluginText = when (error) {
        is NetworkRequestException -> ofCode(error.code)
        else -> Strings.error_network
    }

    /** The same, for a failure the shared paged lists keep as a [Problem]. */
    fun of(problem: Problem): PluginText = when (problem) {
        Problem.RateLimited -> Strings.error_rate_limited
        is Problem.Refused -> ofCode(problem.code)
        Problem.Offline -> Strings.error_network
        else -> Strings.error_response
    }

    private fun ofCode(code: Int): PluginText = when (code) {
        InstagramClient.NOT_FOUND -> Strings.error_not_found
        InstagramClient.RATE_LIMITED -> Strings.error_rate_limited
        InstagramClient.LOGIN_REQUIRED, 403 -> Strings.error_login
        else -> Strings.error_response
    }
}

/** The plugin's own screens, as routes on the app's back stack: a profile, by its username. */
internal object InstagramRoutes {
    fun profile(handle: String): String = "profile?handle=" + URLEncoder.encode(handle, "UTF-8")

    /** The username [route] names; null for a screen this plugin does not have. */
    fun profileOf(route: String): String? {
        if (route.substringBefore('?') != "profile") return null
        val query = route.substringAfter('?', "").split('&').filter { '=' in it }
            .associate { it.substringBefore('=') to URLDecoder.decode(it.substringAfter('='), "UTF-8") }
        return query["handle"]?.trim()?.lowercase()?.ifEmpty { null }
    }

    /** A profile link opens the profile here; a post, a reel or Instagram's own pages open on the site, as XTA decides. */
    fun forUrl(url: String): String? = InstagramLink.parse(url)?.let { profile(it.handle) }
}

/** A post's pictures for the app's viewer: a carousel's slides, else its cover. Videos play on instagram.com. */
internal object InstagramMedia {
    fun photos(post: InstagramPost): List<MediaItem> = post.displayUrls.map { MediaItem(imageUrl = it, aspectRatio = 1f) }
}

/**
 * The Instagram-family session Threads holds (XTA's importThreadsCookies):
 * the two plugins share the cookies' names, not a client, so Instagram
 * copies the header Threads keeps; null when Threads has none.
 */
internal object InstagramThreadsSession {
    fun of(settings: Settings): String? = settings[ThreadsPrefs.directCookies].trim().ifEmpty { null }
}
