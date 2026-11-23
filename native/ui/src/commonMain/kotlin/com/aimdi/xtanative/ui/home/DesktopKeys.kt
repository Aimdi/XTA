package com.aimdi.xtanative.ui.home

/** The keys the desktop's navigation answers (XTA's desktop_navigation and desktop_pager). */
enum class DeskKey { ESCAPE, LEFT, RIGHT, F5, R, BACK, UP, DOWN, PAGE_UP, PAGE_DOWN, SPACE, HOME, END, TAB, Q, OTHER }

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

    /** The list read moves a line, a screen, or to its top or end ([DesktopActions.scroll]). */
    LINE_UP,
    LINE_DOWN,
    SCREEN_UP,
    SCREEN_DOWN,
    TO_TOP,
    TO_END,
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
     * ↑ ↓, Page Up and Down, Space (with Shift, up), Home and End move the
     * list being read, as in a browser, unless a field is being typed in or
     * something is open over the page. Ctrl+Tab and Ctrl+Page Down turn to
     * the next page, Ctrl+Shift+Tab and Ctrl+Page Up to the one before, as a
     * browser turns its tabs, even from a field. Ctrl+Q closes the app, as
     * Linux apps quit, asking first as the mouse's back button on the first
     * screen does.
     */
    fun action(
        key: DeskKey,
        alt: Boolean,
        ctrl: Boolean,
        focusInside: Boolean,
        canGoBack: Boolean,
        rtl: Boolean = false,
        somethingOpen: Boolean = false,
        shift: Boolean = false,
    ): DeskAction = when {
        key == DeskKey.ESCAPE && focusInside -> DeskAction.LEAVE_FIELD
        key == DeskKey.ESCAPE || key == DeskKey.BACK || (key == DeskKey.LEFT && alt) -> back(canGoBack, somethingOpen)
        key == DeskKey.F5 || (key == DeskKey.R && ctrl) -> DeskAction.REFRESH
        key == DeskKey.Q && ctrl && !alt -> DeskAction.LEAVE_APP
        ctrl && !alt && key in TAB_KEYS -> if (somethingOpen) DeskAction.NONE else tabTurn(key, shift)
        focusInside || alt || ctrl -> DeskAction.NONE
        key in SCROLL_KEYS -> if (somethingOpen) DeskAction.NONE else scroll(key, shift)
        key == DeskKey.LEFT -> if (rtl) DeskAction.NEXT_PAGE else DeskAction.PREVIOUS_PAGE
        key == DeskKey.RIGHT -> if (rtl) DeskAction.PREVIOUS_PAGE else DeskAction.NEXT_PAGE
        else -> DeskAction.NONE
    }

    private val SCROLL_KEYS = setOf(DeskKey.UP, DeskKey.DOWN, DeskKey.PAGE_UP, DeskKey.PAGE_DOWN, DeskKey.SPACE, DeskKey.HOME, DeskKey.END)

    private val TAB_KEYS = setOf(DeskKey.TAB, DeskKey.PAGE_UP, DeskKey.PAGE_DOWN)

    private fun tabTurn(key: DeskKey, shift: Boolean): DeskAction = when {
        key == DeskKey.PAGE_UP || (key == DeskKey.TAB && shift) -> DeskAction.PREVIOUS_PAGE
        else -> DeskAction.NEXT_PAGE
    }

    private fun scroll(key: DeskKey, shift: Boolean): DeskAction = when (key) {
        DeskKey.UP -> DeskAction.LINE_UP
        DeskKey.DOWN -> DeskAction.LINE_DOWN
        DeskKey.PAGE_UP -> DeskAction.SCREEN_UP
        DeskKey.SPACE -> if (shift) DeskAction.SCREEN_UP else DeskAction.SCREEN_DOWN
        DeskKey.HOME -> DeskAction.TO_TOP
        DeskKey.END -> DeskAction.TO_END
        else -> DeskAction.SCREEN_DOWN
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
