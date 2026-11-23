package com.aimdi.xtanative.ui.home

/** The keys the desktop's navigation answers (XTA's desktop_navigation and desktop_pager). */
enum class DeskKey { ESCAPE, LEFT, RIGHT, F5, R, BACK, UP, DOWN, PAGE_UP, PAGE_DOWN, SPACE, HOME, END, TAB, Q, F, SLASH, PLUS, MINUS, ZERO, F1, QUESTION, COMMA, W, J, K, V, OTHER }

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

    /** The focus goes into the page, to its first control: Tab while the window's frame holds it. */
    ENTER_PAGE,

    /** The page's own search field takes the keyboard, else the search opens ([DesktopActions.find]). */
    FIND,

    /** The reader's text size a step up, a step down, or back to the system's ([DesktopKeys.textScaled]). */
    TEXT_BIGGER,
    TEXT_SMALLER,
    TEXT_RESET,

    /** The list read moves a line, a screen, or to its top or end ([DesktopActions.scroll]). */
    LINE_UP,
    LINE_DOWN,

    /** The list read moves to the start of its next item, or of the one before ([DesktopKeys.itemStep]), as j and k do on X's site. */
    NEXT_ITEM,
    PREVIOUS_ITEM,

    /** A web address on the clipboard opens, as one dropped on the window does (Ctrl+V while nothing is being typed in). */
    OPEN_PASTED_LINK,
    SCREEN_UP,
    SCREEN_DOWN,
    TO_TOP,
    TO_END,

    /** The list of the desktop's keys opens ([DesktopShortcuts]). */
    SHOW_KEYS,

    /** Settings open, as Ctrl+, opens a desktop app's preferences. */
    OPEN_SETTINGS,
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
     * screen does. Tab while the window's frame holds the focus (at launch,
     * after a screen changes) moves it into the page, to its first control,
     * where Tab and Shift+Tab then go on from one to the next. Ctrl+F, even
     * from a field, and / while nothing is being typed in, go to the search,
     * as a desktop app's do: the page's own field, else the app's search.
     * Ctrl with + (or =), - and 0 make the text bigger, smaller, or its
     * size again, as a browser's zoom does, from a field too. F1, and ?
     * while nothing is being typed in, list these keys. Ctrl+, opens the
     * settings, and Ctrl+W closes what is open over the page (a sheet, a
     * menu), as a desktop app closes a document; it never goes back further.
     * j and k move the list read to its next post or the one before, as on
     * X's site, while nothing is being typed in. Ctrl+V, while nothing is
     * being typed in, opens a link copied elsewhere, as dropping it does.
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
        key == DeskKey.W && ctrl && !alt && !shift -> if (somethingOpen) DeskAction.CLOSE else DeskAction.NONE
        key == DeskKey.COMMA && ctrl && !alt && !shift -> if (somethingOpen) DeskAction.NONE else DeskAction.OPEN_SETTINGS
        ctrl && !alt && key in TAB_KEYS -> if (somethingOpen) DeskAction.NONE else tabTurn(key, shift)
        key == DeskKey.TAB && !focusInside && !alt && !ctrl -> DeskAction.ENTER_PAGE
        ctrl && !alt && key in TEXT_KEYS -> textSize(key)
        (key == DeskKey.F1 && !alt && !ctrl) || (key == DeskKey.QUESTION && !focusInside && !alt && !ctrl) ->
            if (somethingOpen) DeskAction.NONE else DeskAction.SHOW_KEYS
        key == DeskKey.V && ctrl && !alt && !shift && !focusInside -> if (somethingOpen) DeskAction.NONE else DeskAction.OPEN_PASTED_LINK
        (key == DeskKey.F && ctrl && !alt && !shift) || (key == DeskKey.SLASH && !focusInside && !alt && !ctrl) ->
            if (somethingOpen) DeskAction.NONE else DeskAction.FIND
        focusInside || alt || ctrl -> DeskAction.NONE
        key in SCROLL_KEYS -> if (somethingOpen) DeskAction.NONE else scroll(key, shift)
        (key == DeskKey.J || key == DeskKey.K) && !shift -> when {
            somethingOpen -> DeskAction.NONE
            key == DeskKey.J -> DeskAction.NEXT_ITEM
            else -> DeskAction.PREVIOUS_ITEM
        }
        key == DeskKey.LEFT -> if (rtl) DeskAction.NEXT_PAGE else DeskAction.PREVIOUS_PAGE
        key == DeskKey.RIGHT -> if (rtl) DeskAction.PREVIOUS_PAGE else DeskAction.NEXT_PAGE
        else -> DeskAction.NONE
    }

    private val SCROLL_KEYS = setOf(DeskKey.UP, DeskKey.DOWN, DeskKey.PAGE_UP, DeskKey.PAGE_DOWN, DeskKey.SPACE, DeskKey.HOME, DeskKey.END)

    private val TAB_KEYS = setOf(DeskKey.TAB, DeskKey.PAGE_UP, DeskKey.PAGE_DOWN)

    private val TEXT_KEYS = setOf(DeskKey.PLUS, DeskKey.MINUS, DeskKey.ZERO)

    private fun textSize(key: DeskKey): DeskAction = when (key) {
        DeskKey.PLUS -> DeskAction.TEXT_BIGGER
        DeskKey.MINUS -> DeskAction.TEXT_SMALLER
        else -> DeskAction.TEXT_RESET
    }

    /** A step of the text size: the Accessibility slider's own. */
    const val TEXT_STEP = 0.05

    /**
     * The text size [scale] after [action], kept in [range] (the setting's)
     * and on the slider's steps; [scale] itself for any other action. Reset
     * is the size the range starts at, the system's.
     */
    fun textScaled(scale: Double, action: DeskAction, range: ClosedFloatingPointRange<Double>): Double = when (action) {
        DeskAction.TEXT_BIGGER -> stepped(scale + TEXT_STEP).coerceIn(range)
        DeskAction.TEXT_SMALLER -> stepped(scale - TEXT_STEP).coerceIn(range)
        DeskAction.TEXT_RESET -> range.start
        else -> scale
    }

    /** On a step, in hundredths as the slider keeps it (1.15, not 1.1500000000000001). */
    private fun stepped(scale: Double): Double = kotlin.math.round(scale / TEXT_STEP) * (TEXT_STEP * 100).toInt() / 100.0

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

    /**
     * Ctrl and the wheel: the wheel's travel [pending] so far plus [delta]
     * (up is negative), and the text-size step a whole notch of it makes:
     * up is bigger, down smaller, as in a browser. A touchpad's small moves
     * add up to a notch first, so one swipe does not jump the size.
     */
    fun wheelText(pending: Float, delta: Float): Pair<Float, DeskAction> {
        // A turn the other way starts afresh rather than first paying back what was left.
        val travel = if (pending != 0f && (pending < 0f) != (delta < 0f)) delta else pending + delta
        return when {
            travel <= -1f -> 0f to DeskAction.TEXT_BIGGER
            travel >= 1f -> 0f to DeskAction.TEXT_SMALLER
            else -> travel to DeskAction.NONE
        }
    }

    /** An item of a list in view: its [index], where it starts ([offset], from the list's start) and its [size]. */
    data class ShownItem(val index: Int, val offset: Int, val size: Int)

    /**
     * Where j ([forward]) or k takes a list of [count] items whose items in
     * view are [shown]: the item to bring up and the offset to leave above it.
     * A header pinned over the list (the items after it in view are not the
     * next ones, or it overlaps them) stays, so
     * the item goes just under it. Forward is the next item starting below
     * that line; back is the start of the item partly read there, else the
     * one before. Null where there is nowhere to go.
     */
    fun itemStep(shown: List<ShownItem>, count: Int, forward: Boolean): Pair<Int, Int>? {
        if (count <= 0 || shown.isEmpty()) return null
        val items = shown.sortedBy { it.index }
        val pinned = pinnedOf(items)
        val line = pinned?.let { it.offset + it.size } ?: 0
        val flowing = items.filter { it !== pinned }
        val reading = flowing.firstOrNull { it.offset + it.size > line } ?: return null
        val target = if (forward) {
            flowing.firstOrNull { it.offset > line + SLACK }?.index ?: (reading.index + 1)
        } else {
            if (reading.offset < line - SLACK) reading.index else reading.index - 1
        }
        if (target !in 0 until count || target == pinned?.index) return null
        return target to -line
    }

    /**
     * How far the start of item [index] sits under a header pinned over the
     * list in [shown], after a step: a header in its own place before the
     * step may have stuck on the way and covered it. 0 when nothing covers it.
     */
    fun coveredAfterStep(shown: List<ShownItem>, index: Int): Int {
        val items = shown.sortedBy { it.index }
        val pinned = pinnedOf(items) ?: return 0
        val item = items.firstOrNull { it.index == index && it !== pinned } ?: return 0
        return (pinned.offset + pinned.size - item.offset).coerceAtLeast(0)
    }

    /** The first item in view when it is pinned over the list: the items after it are not the next ones, or they overlap it. */
    private fun pinnedOf(items: List<ShownItem>): ShownItem? =
        items.getOrNull(1)?.takeIf { second -> second.index != items[0].index + 1 || second.offset < items[0].offset + items[0].size }?.let { items[0] }

    /** Pixels an item may sit off the line and still count as on it. */
    private const val SLACK = 1

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
