package com.aimdi.xtanative.ui.home

/** The keys the desktop's navigation answers (XTA's desktop_navigation and desktop_pager). */
enum class DeskKey { ESCAPE, LEFT, RIGHT, F5, R, BACK, OTHER }

enum class DeskAction {
    /** Out of the text field being typed in, as XTA's first Esc does. */
    LEAVE_FIELD,

    /** What the page has open (a side menu) closes first. */
    CLOSE,
    BACK,
    REFRESH,
    PREVIOUS_PAGE,
    NEXT_PAGE,

    /** The app closes, asking first as XTA's back from Home does. */
    LEAVE_APP,
    NONE,
}

/** What a key does on the desktop, as XTA's desktop shortcuts decide it. */
object DesktopKeys {
    /**
     * [key], with Alt or Ctrl held, while something inside the page has the
     * focus ([focusInside]: a text field, a button reached with Tab) or not,
     * and while the page has something open that going back closes
     * ([somethingOpen]). Esc leaves the field first, then closes, then goes
     * back; Alt+←, the Back key and the mouse's back button close or go back
     * from anywhere; F5 and Ctrl+R refresh; ← and → turn the page only when
     * nothing in it takes the arrows, in the reading direction ([rtl]).
     */
    fun action(
        key: DeskKey,
        alt: Boolean,
        ctrl: Boolean,
        focusInside: Boolean,
        canGoBack: Boolean,
        rtl: Boolean = false,
        somethingOpen: Boolean = false,
    ): DeskAction = when {
        key == DeskKey.ESCAPE && focusInside -> DeskAction.LEAVE_FIELD
        key == DeskKey.ESCAPE || key == DeskKey.BACK || (key == DeskKey.LEFT && alt) -> back(canGoBack, somethingOpen)
        key == DeskKey.F5 || (key == DeskKey.R && ctrl) -> DeskAction.REFRESH
        focusInside || alt || ctrl -> DeskAction.NONE
        key == DeskKey.LEFT -> if (rtl) DeskAction.NEXT_PAGE else DeskAction.PREVIOUS_PAGE
        key == DeskKey.RIGHT -> if (rtl) DeskAction.PREVIOUS_PAGE else DeskAction.NEXT_PAGE
        else -> DeskAction.NONE
    }

    /** Going back: what is open closes first; the keys never leave the first screen. */
    fun back(canGoBack: Boolean, somethingOpen: Boolean): DeskAction = when {
        somethingOpen -> DeskAction.CLOSE
        canGoBack -> DeskAction.BACK
        else -> DeskAction.NONE
    }

    /** The mouse's back button: as the keys, but on the first screen it closes the app, as XTA's goes back from Home. */
    fun mouseBack(canGoBack: Boolean, somethingOpen: Boolean): DeskAction =
        back(canGoBack, somethingOpen).takeIf { it != DeskAction.NONE } ?: DeskAction.LEAVE_APP

    /** The page [by] away from [page] of [count], or null at either end (XTA's pager does not wrap). */
    fun turned(page: Int, count: Int, by: Int): Int? = (page + by).takeIf { it in 0 until count }
}

/** Options pinned to a network's top bar (XTA's top-bar pins), by the option's id, in pin order. */
object TopBarPins {
    fun toggled(pins: List<String>, id: String): List<String> = if (id in pins) pins - id else pins + id

    /** The options the bar shows: the pinned ones that exist, in pin order. */
    fun <T> shown(options: List<T>, pins: List<String>, idOf: (T) -> String): List<T> {
        val byId = options.associateBy(idOf)
        return pins.distinct().mapNotNull(byId::get)
    }
}
