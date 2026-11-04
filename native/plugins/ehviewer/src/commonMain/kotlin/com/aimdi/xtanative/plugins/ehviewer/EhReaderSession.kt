package com.aimdi.xtanative.plugins.ehviewer

import com.aimdi.xtanative.networks.ehviewer.EhGallery
import com.aimdi.xtanative.networks.ehviewer.EhPageResolver
import com.aimdi.xtanative.ui.plugins.Fetch
import kotlinx.coroutines.CoroutineScope

/** A gallery open in the reader: its pages, found as the site links them, and where the reader is. */
internal class EhOpenReader(val gallery: EhGallery, val resolver: EhPageResolver, val model: EhReaderModel)

/**
 * The reader of one route (eh_reader_screen.dart), kept while its screen is:
 * opened at once with what the gallery's page handed it ([handed]), else
 * after reading the gallery's page again, as after the app was started anew.
 * Each page the reader settles on goes into the history, and the pages around
 * it are fetched ahead ([EhPageResolver.preloadPages]).
 */
internal class EhReaderSession(private val app: EhApp, private val scope: CoroutineScope, private val route: EhRoute.Reader, handed: EhReading?) {
    /** The gallery once open; reading its page again can be tried again. */
    val reader = Fetch(scope, handed?.let(::open)) { _ ->
        app.ready()
        val detail = app.client.galleryDetail(route.gid, route.token)
        open(EhReading(detail.gallery, detail.previews))
    }

    init {
        if (handed == null) reader.load()
    }

    private fun open(reading: EhReading): EhOpenReader {
        val total = EhPageResolver.totalOf(reading.gallery, reading.previews, route.page)
        val resolver = EhPageResolver(app.client, reading.gallery, total, reading.previews, scope)
        lateinit var opened: EhOpenReader
        val model = EhReaderModel(total, route.page, EhConfig.readingMode(app.current())) { page -> settled(opened, page) }
        opened = EhOpenReader(reading.gallery, resolver, model)
        settled(opened, model.state.value.page)
        return opened
    }

    private fun settled(open: EhOpenReader, page: Int) {
        app.launch { app.history.remember(open.gallery, page) }
        EhPageResolver.preloadPages(page, open.resolver.total).forEach { open.resolver.resolve(it) }
    }
}
