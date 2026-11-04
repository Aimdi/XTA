package com.aimdi.xtanative.plugins.tiktok

import com.aimdi.xtanative.networks.tiktok.TikTokLink
import com.aimdi.xtanative.networks.tiktok.TikTokParser
import com.aimdi.xtanative.ui.plugins.PluginRoute

/** The plugin's own screens, as routes on the app's back stack. */
internal sealed interface TikTokRoute {
    data class Profile(val handle: String) : TikTokRoute

    /** The player for video [id] of [handle]; [shelf] names the video as its list had it. */
    data class Video(val id: String, val handle: String?, val shelf: String?) : TikTokRoute

    /** The search, run for [query] when there is one. */
    data class Search(val query: String) : TikTokRoute
}

internal object TikTokRoutes {
    private const val PROFILE = "profile"
    private const val VIDEO = "video"
    private const val SEARCH = "search"

    fun profile(handle: String): String = PluginRoute(PROFILE, "handle" to handle.removePrefix("@")).toString()

    fun video(id: String, handle: String?, shelf: String?): String = PluginRoute(VIDEO, "id" to id, "handle" to handle, "shelf" to shelf).toString()

    fun search(query: String?): String = PluginRoute(SEARCH, "q" to query?.trim()?.ifEmpty { null }).toString()

    /** The screen [route] names; null for one this plugin does not have. */
    fun parse(route: String): TikTokRoute? {
        val opened = PluginRoute.parse(route)
        return when (opened.name) {
            PROFILE -> opened["handle"]?.let(TikTokParser::handle)?.let(TikTokRoute::Profile)
            VIDEO -> opened["id"]?.takeIf { it.all(Char::isDigit) }?.let { TikTokRoute.Video(it, opened["handle"], opened["shelf"]) }
            SEARCH -> TikTokRoute.Search(opened["q"].orEmpty())
            else -> null
        }
    }

    /**
     * The route of the screen for a tiktok.com link (plugin_url.dart): a
     * profile's, and a video's where the app plays videos itself ([inApp]);
     * elsewhere a video's link opens on the site, as XTA's desktop does.
     */
    fun forUrl(url: String, inApp: Boolean): String? = when (val link = TikTokLink.parse(url)) {
        is TikTokLink.Profile -> profile(link.handle)
        is TikTokLink.Video -> if (inApp) video(link.id, link.handle, null) else null
        null -> null
    }
}
