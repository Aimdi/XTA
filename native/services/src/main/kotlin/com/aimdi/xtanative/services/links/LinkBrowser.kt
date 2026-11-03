package com.aimdi.xtanative.services.links

import com.aimdi.xtanative.services.util.WebLink
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * What XTA's browser screen shows about the page under it: the address, and
 * how far it loaded ([progress] 0–1 while [loading]).
 */
data class LinkBrowserState(val url: String, val loading: Boolean = true, val progress: Double = 0.0) {
    /** The padlock: an https page. */
    val secure: Boolean get() = WebLink.parse(url)?.scheme == "https"

    /** The host the top bar names, as a link preview names it. */
    val domain: String get() = LinkPreviews.domain(url)

    /** The page started loading [next]; an empty address keeps the one shown. */
    fun started(next: String) = copy(url = next.ifEmpty { url }, loading = true, progress = 0.0)

    fun progressed(percent: Int) = copy(progress = (percent / 100.0).coerceIn(0.0, 1.0))

    fun finished(next: String) = copy(url = next.ifEmpty { url }, loading = false, progress = 1.0)

    /** The page failed to load. */
    fun stopped() = copy(loading = false)
}

/**
 * The page events of one browser screen. A web view keeps reporting for a
 * moment after its screen is gone, so every event after [close] is dropped
 * rather than written to a screen that is no more (link_browser_store.dart).
 */
class LinkBrowserSession(url: String) {
    private val current = MutableStateFlow(LinkBrowserState(url))
    val state: StateFlow<LinkBrowserState> = current.asStateFlow()

    @Volatile
    private var closed = false

    fun started(url: String) = apply { it.started(url) }

    fun progressed(percent: Int) = apply { it.progressed(percent) }

    fun finished(url: String) = apply { it.finished(url) }

    fun stopped() = apply { it.stopped() }

    fun close() {
        closed = true
    }

    private fun apply(change: (LinkBrowserState) -> LinkBrowserState) {
        if (!closed) current.update(change)
    }
}

/** What the browser screen does with a page the web view is about to navigate to. */
enum class LinkNavigation {
    /** Ask whether an XTA screen reads it; if one does, it opens there and the page stays. */
    ASK_APP,

    /** Let the web view go there: a page's own `about:`, `data:` and `blob:` frames. */
    ALLOW,

    /** Stay: no web view should follow `intent:`, `market:`, `javascript:` or the like. */
    BLOCK,
}

/** The rule link_browser_page.dart navigates by. */
fun linkNavigation(url: String): LinkNavigation = when (WebLink.parse(url)?.scheme) {
    "http", "https" -> LinkNavigation.ASK_APP
    "about", "data", "blob" -> LinkNavigation.ALLOW
    else -> LinkNavigation.BLOCK
}

/** The actions in the browser screen's ⋯ menu, in its order. */
enum class LinkBrowserAction { OPEN_EXTERNALLY, COPY_LINK, SHARE, RELOAD }
