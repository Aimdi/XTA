package com.aimdi.xtanative.ui.search

/** The tabs of X's search, in XTA's order: Popular, Recent, Media, Accounts, Communities. */
enum class SearchTab(
    /** What SearchTimeline is asked for; null for the tabs with a read of their own. */
    val product: String?,
) {
    TOP("Top"),
    LATEST("Latest"),
    MEDIA("Media"),
    PEOPLE(null),
    COMMUNITIES(null),
    ;

    /** How a route names the tab: `search?tab=people`. */
    val routeName: String get() = name.lowercase()

    companion object {
        /** The tab a route names; Popular for none or one this does not know. */
        fun of(routeName: String?): SearchTab = entries.firstOrNull { it.routeName == routeName } ?: TOP
    }
}

/** Typing and searching, as XTA times them. */
object SearchTiming {
    /** A pause in typing this long searches what was typed. */
    const val DEBOUNCE_MILLIS = 750L

    /** Discover's bar settles on what was typed after this pause. */
    const val DISCOVER_DEBOUNCE_MILLIS = 350L
}

/** The recent searches of X's own search, beside the other networks' (`mastodon`, `reddit`…). */
const val SEARCH_SCOPE_X = "x"
