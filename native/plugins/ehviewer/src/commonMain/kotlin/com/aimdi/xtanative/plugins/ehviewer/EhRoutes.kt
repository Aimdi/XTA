package com.aimdi.xtanative.plugins.ehviewer

import com.aimdi.xtanative.networks.ehviewer.EhGallery
import com.aimdi.xtanative.networks.ehviewer.EhPreview
import com.aimdi.xtanative.ui.plugins.PluginRoute

/** The plugin's own screens, as routes on the app's back stack. */
internal sealed interface EhRoute {
    /** A gallery's page; [shelf] names the gallery as its list had it. */
    data class Gallery(val gid: Long, val token: String, val shelf: String?) : EhRoute

    /** The reader on [page] of a gallery; [shelf] names the gallery and the page tokens already known. */
    data class Reader(val gid: Long, val token: String, val page: Int, val shelf: String?) : EhRoute

    /** The search, run for [query] when there is one. */
    data class Search(val query: String) : EhRoute
}

/** What the gallery's page hands the reader: the gallery as it knows it and the preview sheets it read. */
internal class EhReading(val gallery: EhGallery, val previews: List<EhPreview>)

internal object EhRoutes {
    private const val GALLERY = "gallery"
    private const val READER = "reader"
    private const val SEARCH = "search"

    fun gallery(gallery: EhGallery, shelf: String?): String =
        PluginRoute(GALLERY, "gid" to "${gallery.gid}", "token" to gallery.token, "shelf" to shelf).toString()

    fun reader(gallery: EhGallery, page: Int, shelf: String?): String =
        PluginRoute(READER, "gid" to "${gallery.gid}", "token" to gallery.token, "page" to "$page", "shelf" to shelf).toString()

    fun search(query: String?): String = PluginRoute(SEARCH, "q" to query?.trim()?.ifEmpty { null }).toString()

    /** The screen [route] names; null for one this plugin does not have. */
    fun parse(route: String): EhRoute? {
        val opened = PluginRoute.parse(route)
        val gid = opened["gid"]?.toLongOrNull()?.takeIf { it > 0 }
        val token = opened["token"]
        return when (opened.name) {
            GALLERY -> if (gid != null && token != null) EhRoute.Gallery(gid, token, opened["shelf"]) else null
            READER -> if (gid != null && token != null) EhRoute.Reader(gid, token, opened["page"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1, opened["shelf"]) else null
            SEARCH -> EhRoute.Search(opened["q"].orEmpty())
            else -> null
        }
    }
}
