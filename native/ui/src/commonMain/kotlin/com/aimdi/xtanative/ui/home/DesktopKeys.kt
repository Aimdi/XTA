package com.aimdi.xtanative.ui.home

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs

/** The keys the desktop's navigation answers (XTA's desktop_navigation and desktop_pager). */
enum class DeskKey { ESCAPE, LEFT, RIGHT, F5, R, BACK, UP, DOWN, PAGE_UP, PAGE_DOWN, SPACE, HOME, END, TAB, Q, F, SLASH, PLUS, MINUS, ZERO, F1, QUESTION, COMMA, W, J, K, V, A, DELETE, ENTER, O, L, B, C, MENU, F10, N, OTHER }

/** A mouse's side buttons: back and forward, as in a browser. */
enum class SideButton { BACK, FORWARD }

/** A step of the keyboard's focus walk ([DesktopKeys.tabStep]). */
enum class TabStep { FORWARD, BACKWARD }

enum class DeskAction {
    /** Out of the text field being typed in, as XTA's first Esc does. */
    LEAVE_FIELD,

    /** What the page has open (a side menu) closes first. */
    CLOSE,
    BACK,

    /** The screen the last back left opens again, as a browser's forward does (Alt+→, the mouse's forward button). */
    FORWARD,
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

    /** Back to Home, as a browser's Alt+Home goes to its home page. */
    GO_HOME,

    /** The reading keys go to the next column of the page's, or the one before ([DesktopActions.stepColumn]): the Deck's. */
    NEXT_COLUMN,
    PREVIOUS_COLUMN,

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

    /** The page's own New opens ([DesktopActions.create]): a note in Saved, a group in Subscriptions, an antenna, as a desktop app's Ctrl+N makes a new document. */
    CREATE,

    /** The page's selection takes every item shown, as a file manager's Ctrl+A does ([DesktopActions.selectAll]). */
    SELECT_ALL,

    /** What the page's selection holds is removed, asking first as its own button does ([DesktopActions.removeSelected]). */
    REMOVE_SELECTED,

    /** The page stops selecting, as Esc does in a file manager ([DesktopActions.leaveSelection]). */
    LEAVE_SELECTION,

    /** The row of a reorderable list the pointer rests on moves a place up or down, as its drag would move it ([DesktopActions.moveRow]). */
    MOVE_ROW_UP,
    MOVE_ROW_DOWN,

    /** The post the reading keys are on ([DesktopActions.currentPost]) opens, as a click on it does. */
    OPEN_POST,

    /** That post liked on this device, or no longer, as its heart does. */
    LIKE_POST,

    /** That post saved on this device, or no longer, as its bookmark does. */
    SAVE_POST,

    /** That post's link copied. */
    COPY_POST_LINK,

    /** That post's ⋯ menu opens, at the post, as the Menu key opens a desktop app's context menu. */
    POST_MENU,
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
     * as a desktop app's do: the page's own field, else the app's search;
     * Ctrl+K and Ctrl+L, as a browser's go to its search and address bar,
     * do the same.
     * Ctrl with + (or =), - and 0 make the text bigger, smaller, or its
     * size again, as a browser's zoom does, from a field too. F1, and ?
     * while nothing is being typed in, list these keys. Ctrl+, opens the
     * settings, and Ctrl+W closes what is open over the page (a sheet, a
     * menu), as a desktop app closes a document; it never goes back further.
     * j and k move the list read to its next post or the one before, as on
     * X's site, while nothing is being typed in. Ctrl+V, while nothing is
     * being typed in, opens a link copied elsewhere, as dropping it does.
     * Alt+Home goes back to Home, as a browser's goes to its home page.
     * Ctrl+← and Ctrl+→, while nothing is being typed in (where they go a
     * word at a time) and nothing is open over the page, give the reading
     * keys the column before or the next, in the reading direction, on a
     * page of columns side by side (the Deck).
     * On a page that selects (the Archive), Ctrl+A, while nothing is being
     * typed in and nothing is open over the page, selects every item shown;
     * while it is [selecting], Delete removes what is selected and Esc stops
     * selecting before it goes back, as in a file manager.
     * What is open may be a column read beside the page rather than over it
     * ([openIsRead]: the Deck's detail, a post opened there): Esc and Ctrl+W
     * still close it first, but the reading keys, j and k and Ctrl+← and
     * Ctrl+→ go on working, as nothing covers the lists.
     * Alt+↑ and Alt+↓, while nothing is being typed in and nothing is open
     * over the page, move the row the pointer rests on in a list that is
     * otherwise reordered by dragging (a row with the focus moves itself,
     * [RowKeyMoves]).
     * The post the reading keys are on ([DesktopActions.currentPost]) is
     * acted on as on X's site, while nothing is being typed in and nothing is
     * open over the page: Enter and o open it, l likes it on this device (or
     * no longer), b saves it on this device (or no longer), Ctrl+Shift+C
     * copies its link, and the Menu key and Shift+F10 open its ⋯ menu. Enter
     * leaves a button or tab that has the focus to press ([focusHeld]: one
     * the mouse clicked too), as in a browser.
     * Ctrl+N, while nothing is being typed in and nothing is open over the
     * page, makes something new where the page has a New of its own (a note
     * in Saved, a group, an antenna), as a desktop app's makes a document.
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
        selecting: Boolean = false,
        openIsRead: Boolean = false,
        focusHeld: Boolean = focusInside,
    ): DeskAction {
        // Only what is open over the page stops the reading keys; a column opened beside it is read with them.
        val covered = somethingOpen && !openIsRead
        return when {
            key == DeskKey.ESCAPE && focusInside -> DeskAction.LEAVE_FIELD
            key == DeskKey.ESCAPE && selecting && !somethingOpen -> DeskAction.LEAVE_SELECTION
            key == DeskKey.ESCAPE || key == DeskKey.BACK || (key == DeskKey.LEFT && alt) -> back(canGoBack, somethingOpen)
            // Over an opened post (a card the page counts as open) too, as Alt+Home.
            key == DeskKey.RIGHT && alt && !ctrl && !shift -> DeskAction.FORWARD
            key == DeskKey.F5 || (key == DeskKey.R && ctrl) -> DeskAction.REFRESH
            key == DeskKey.Q && ctrl && !alt -> DeskAction.LEAVE_APP
            key == DeskKey.W && ctrl && !alt && !shift -> if (somethingOpen) DeskAction.CLOSE else DeskAction.NONE
            key == DeskKey.COMMA && ctrl && !alt && !shift -> if (somethingOpen) DeskAction.NONE else DeskAction.OPEN_SETTINGS
            ctrl && !alt && key in TAB_KEYS -> if (somethingOpen) DeskAction.NONE else tabTurn(key, shift)
            key == DeskKey.TAB && !focusInside && !alt && !ctrl -> DeskAction.ENTER_PAGE
            ctrl && !alt && key in TEXT_KEYS -> textSize(key)
            (key == DeskKey.F1 && !alt && !ctrl) || (key == DeskKey.QUESTION && !focusInside && !alt && !ctrl) ->
                if (somethingOpen) DeskAction.NONE else DeskAction.SHOW_KEYS
            // Over an opened post (a card the page counts as open) too: going home leaves it with the rest.
            key == DeskKey.HOME && alt && !ctrl && !shift -> DeskAction.GO_HOME
            key == DeskKey.V && ctrl && !alt && !shift && !focusInside -> if (somethingOpen) DeskAction.NONE else DeskAction.OPEN_PASTED_LINK
            (key in FIND_KEYS && ctrl && !alt && !shift) || (key == DeskKey.SLASH && !focusInside && !alt && !ctrl) ->
                if (somethingOpen) DeskAction.NONE else DeskAction.FIND
            ctrl && !alt && !shift && !focusInside && (key == DeskKey.LEFT || key == DeskKey.RIGHT) -> when {
                covered -> DeskAction.NONE
                (key == DeskKey.RIGHT) != rtl -> DeskAction.NEXT_COLUMN
                else -> DeskAction.PREVIOUS_COLUMN
            }
            key == DeskKey.N && ctrl && !alt && !shift && !focusInside -> if (somethingOpen) DeskAction.NONE else DeskAction.CREATE
            // In a field, Ctrl+A selects its text; Delete deletes a character there.
            key == DeskKey.A && ctrl && !alt && !shift && !focusInside -> if (somethingOpen) DeskAction.NONE else DeskAction.SELECT_ALL
            key == DeskKey.DELETE && selecting && !focusInside && !alt && !ctrl && !shift -> if (somethingOpen) DeskAction.NONE else DeskAction.REMOVE_SELECTED
            alt && !ctrl && !shift && !focusInside && (key == DeskKey.UP || key == DeskKey.DOWN) -> when {
                somethingOpen -> DeskAction.NONE
                key == DeskKey.UP -> DeskAction.MOVE_ROW_UP
                else -> DeskAction.MOVE_ROW_DOWN
            }
            key == DeskKey.C && ctrl && shift && !alt && !focusInside -> if (covered) DeskAction.NONE else DeskAction.COPY_POST_LINK
            focusInside || alt || ctrl -> DeskAction.NONE
            (key == DeskKey.MENU && !shift) || (key == DeskKey.F10 && shift) -> if (covered) DeskAction.NONE else DeskAction.POST_MENU
            key in POST_KEYS && !shift -> when {
                covered || (key == DeskKey.ENTER && focusHeld) -> DeskAction.NONE
                else -> POST_KEYS.getValue(key)
            }
            key in SCROLL_KEYS -> if (covered) DeskAction.NONE else scroll(key, shift)
            (key == DeskKey.J || key == DeskKey.K) && !shift -> when {
                covered -> DeskAction.NONE
                key == DeskKey.J -> DeskAction.NEXT_ITEM
                else -> DeskAction.PREVIOUS_ITEM
            }
            key == DeskKey.LEFT -> if (rtl) DeskAction.NEXT_PAGE else DeskAction.PREVIOUS_PAGE
            key == DeskKey.RIGHT -> if (rtl) DeskAction.PREVIOUS_PAGE else DeskAction.NEXT_PAGE
            else -> DeskAction.NONE
        }
    }

    /**
     * Which way a Tab press walks the controls: forward, or back with Shift;
     * null for anything else (Ctrl+Tab and Alt+Tab turn tabs or windows).
     *
     * Compose walks them in the order they are placed, and a Material
     * scaffold places its page before its top bar: Tab from the bar's last
     * button runs off the end and wraps to the window's frame. The frame
     * then hands the same step on ([DesktopNavigation]), into the page, so
     * the walk goes bar, page, bar, as the eye reads it, with no stop that
     * shows nothing.
     */
    fun tabStep(key: DeskKey, shift: Boolean, ctrl: Boolean, alt: Boolean): TabStep? = when {
        key != DeskKey.TAB || ctrl || alt -> null
        shift -> TabStep.BACKWARD
        else -> TabStep.FORWARD
    }

    /**
     * Whether what has the focus inside the page ([focusInside]) keeps the
     * keys from [action]: a field being typed in ([typing]), or anything the
     * keyboard moved the focus to (Tab), where Space and Enter press it. A
     * button, tab or chip the mouse last [clicked] has the focus too (the
     * mouse's press gives it), but holds no claim to them: the reading keys,
     * j and k, Ctrl+← / Ctrl+→ and Esc go on working at once, as in a
     * browser, where only a field keeps the keys after a click.
     */
    fun keysInside(focusInside: Boolean, typing: Boolean, clicked: Boolean): Boolean = focusInside && (typing || !clicked)

    /**
     * Whether the scrolling [action] goes first to the control the mouse
     * clicked, which holds the focus ([focusHeld]), and moves the page only
     * when that control leaves the key unused: on a page that scrolls as a
     * whole ([wholePage]: Settings and its sections), a slider takes its
     * arrows, Home and End, a switch's row its Space. On a lazy list (a
     * timeline, the Deck) a clicked button, tab or chip leaves the reading
     * keys to the list at once ([keysInside]).
     */
    fun controlFirst(action: DeskAction, focusHeld: Boolean, wholePage: Boolean): Boolean =
        focusHeld && wholePage && action in PAGE_SCROLLS

    private val PAGE_SCROLLS = setOf(
        DeskAction.LINE_UP, DeskAction.LINE_DOWN, DeskAction.SCREEN_UP, DeskAction.SCREEN_DOWN, DeskAction.TO_TOP, DeskAction.TO_END,
    )

    private val SCROLL_KEYS = setOf(DeskKey.UP, DeskKey.DOWN, DeskKey.PAGE_UP, DeskKey.PAGE_DOWN, DeskKey.SPACE, DeskKey.HOME, DeskKey.END)

    private val POST_KEYS = mapOf(
        DeskKey.ENTER to DeskAction.OPEN_POST,
        DeskKey.O to DeskAction.OPEN_POST,
        DeskKey.L to DeskAction.LIKE_POST,
        DeskKey.B to DeskAction.SAVE_POST,
    )

    /** The letters Ctrl takes to the search: F as a desktop app's find, K and L as a browser's search and address bar. */
    private val FIND_KEYS = setOf(DeskKey.F, DeskKey.K, DeskKey.L)

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

    /**
     * Where a page that scrolls as a whole (a verticalScroll's ScrollState)
     * stands after [action], from [value] of [max]: a [line] for ↑ and ↓, a
     * [viewport] less [kept] of it for a screen (some of what was read stays
     * in view, as a browser's Page Down leaves), its start or end for Home
     * and End, never past either. Null when [action] does not move such a
     * page (j and k: it has no items).
     */
    fun scrolledTo(action: DeskAction, value: Int, max: Int, viewport: Int, line: Float, kept: Float = SCREEN_KEPT): Int? {
        val screen = (viewport * (1 - kept)).toInt().coerceAtLeast(1)
        val target = when (action) {
            DeskAction.LINE_UP -> value - line.toInt()
            DeskAction.LINE_DOWN -> value + line.toInt()
            DeskAction.SCREEN_UP -> value - screen
            DeskAction.SCREEN_DOWN -> value + screen
            DeskAction.TO_TOP -> 0
            DeskAction.TO_END -> max
            else -> return null
        }
        return target.coerceIn(0, max.coerceAtLeast(0))
    }

    /** How much of a screen's move stays in view after it ([scrolledTo]), as a browser's Page Down keeps a little. */
    const val SCREEN_KEPT = 0.12f

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

    /** Where j or k takes a list ([keyStep]): to an item's start ([itemStep]), or on to a post already drawn, the list moving [by] that far as it can. */
    sealed interface KeyStep {
        data class ToItem(val index: Int, val offset: Int) : KeyStep
        data class ToPost(val post: Int, val by: Int) : KeyStep
    }

    /**
     * Where j ([forward]) or k goes from the post [marked] among those drawn
     * at [tops] (the items' offsets) in a list of [count] items, [shown] in
     * view. A marked post at the line (under any pinned header) steps by
     * items ([itemStep]), except to the next post of its own item (a thread's
     * next post). One the list could not bring to the line, being at its end
     * or its start, steps to the next drawn post, or the one before, so every
     * post there is reached one by one while the list moves as far as it can.
     * Null where there is nowhere to go: the mark stays. Without a mark, the
     * step goes from the list's place ([itemStep]).
     */
    fun keyStep(shown: List<ShownItem>, count: Int, tops: List<Int>, marked: Int?, forward: Boolean): KeyStep? {
        val item = itemStep(shown, count, forward)?.let { (index, offset) -> KeyStep.ToItem(index, offset) }
        val top = marked?.let(tops::getOrNull) ?: return item
        val items = shown.sortedBy { it.index }
        val line = pinnedOf(items)?.let { it.offset + it.size } ?: 0
        val target = tops.withIndex().let { all ->
            if (forward) all.filter { it.value > top + SLACK }.minByOrNull { it.value } else all.filter { it.value < top - SLACK }.maxByOrNull { it.value }
        }
        fun itemOf(at: Int) = items.firstOrNull { at >= it.offset - SLACK && at < it.offset + it.size }?.index
        val home = itemOf(top)
        val atLine = abs(top - line) <= SLACK
        if (target != null && (!atLine || item == null || (home != null && itemOf(target.value) == home))) {
            return KeyStep.ToPost(target.index, target.value - line)
        }
        if (item == null) return null
        // An item step that would take the mark back the way it came (the list at its end) is none.
        if (!atLine && home != null && (if (forward) item.index <= home else item.index >= home)) return null
        return item
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

    /**
     * Which of the posts drawn in a list is the one the reading keys are on,
     * the list's items in view being [shown] and the posts starting at
     * [tops] (in the items' offsets), the list's view ending at [end]: the
     * first starting at or below the line a pinned header leaves (j and k
     * leave a post's start there), else the one partly read across it. Null
     * when no post is in view.
     */
    fun currentPost(shown: List<ShownItem>, tops: List<Int>, end: Int): Int? {
        val pinned = pinnedOf(shown.sortedBy { it.index })
        val line = pinned?.let { it.offset + it.size } ?: 0
        val inView = tops.withIndex().filter { it.value < end }
        return (inView.filter { it.value >= line - SLACK }.minByOrNull { it.value } ?: inView.filter { it.value < line }.maxByOrNull { it.value })?.index
    }

    /** Which of the posts starting at [tops] is the first in the item at [offset], [size] long; null when none is in it. */
    fun firstIn(offset: Int, size: Int, tops: List<Int>): Int? =
        tops.withIndex().filter { it.value >= offset - SLACK && it.value < offset + size }.minByOrNull { it.value }?.index

    /** The first item in view when it is pinned over the list: the items after it are not the next ones, or they overlap it. */
    private fun pinnedOf(items: List<ShownItem>): ShownItem? =
        items.getOrNull(1)?.takeIf { second -> second.index != items[0].index + 1 || second.offset < items[0].offset + items[0].size }?.let { items[0] }

    /** Pixels an item may sit off the line and still count as on it. */
    private const val SLACK = 1

    /**
     * The tab Ctrl and [digit] go to of [count], as a browser's Ctrl+1…9 go
     * to its tabs: 1 to 8 that tab, where there is one; 9 the last.
     */
    fun pageAt(digit: Int, count: Int): Int? = when {
        count <= 0 -> null
        digit == 9 -> count - 1
        digit in 1..8 -> (digit - 1).takeIf { it < count }
        else -> null
    }

    /**
     * The side button an AWT mouse [button] is, where Compose does not see it:
     * on X11, X's buttons 8 and 9 (back, forward) come as AWT's 6 and 7, after
     * the wheel's 4 to 7. Null for any other button.
     */
    fun sideButton(button: Int): SideButton? = when (button) {
        X11_BACK -> SideButton.BACK
        X11_FORWARD -> SideButton.FORWARD
        else -> null
    }

    /**
     * The side button a press is: from AWT's [awtButton] where the press
     * came through AWT, and only from it, since on X11 a sideways scroll
     * (X's buttons 6 and 7: a touchpad, a tilting wheel) comes as AWT's 4
     * and 5, which Compose reports as back and forward ([composeBack],
     * [composeForward]); those count only where there is no AWT event.
     */
    fun pressedSide(awtButton: Int?, composeBack: Boolean, composeForward: Boolean): SideButton? = when {
        awtButton != null -> sideButton(awtButton)
        composeBack -> SideButton.BACK
        composeForward -> SideButton.FORWARD
        else -> null
    }

    private const val X11_BACK = 6
    private const val X11_FORWARD = 7

    /**
     * Whether [key] closes a window of the app's own beside its main one
     * (a post opened in a new window) where it does nothing else ([action]
     * none): Ctrl+W, as a browser's closes its window's last tab, and Esc on
     * its first screen. The main window never closes so.
     */
    fun closesWindow(key: DeskKey, alt: Boolean, ctrl: Boolean, shift: Boolean, action: DeskAction): Boolean =
        action == DeskAction.NONE && !alt && !shift && ((key == DeskKey.ESCAPE && !ctrl) || (key == DeskKey.W && ctrl))

    /** The mouse's back button: as the keys, but on the first screen it closes the app, as XTA's goes back from Home. */
    fun mouseBack(canGoBack: Boolean, somethingOpen: Boolean): DeskAction =
        back(canGoBack, somethingOpen).takeIf { it != DeskAction.NONE } ?: DeskAction.LEAVE_APP

    /**
     * The column of [count] that Ctrl+← or Ctrl+→ ([by] -1 or 1) gives the
     * reading keys, from the one they move now ([current]; null when none of
     * the columns, such as before any list was read): the next or the one
     * before, no further than either end, as the pager does not wrap; from
     * none, the first going on, the last going back. Null when there are no
     * columns.
     */
    fun columnStep(count: Int, current: Int?, by: Int): Int? = when {
        count <= 0 -> null
        current == null || current !in 0 until count -> if (by > 0) 0 else count - 1
        else -> (current + by).coerceIn(0, count - 1)
    }

    /**
     * Whether the pointer, now at [at] in the window, has moved since it was
     * last at [last]: an event at the same place (within half a pixel) is the
     * window's own, sent as what lies under a still pointer moved. Unknown
     * places count as moves.
     */
    fun pointerMoved(last: Offset?, at: Offset?): Boolean =
        last == null || at == null || (at - last).getDistance() >= POINTER_STILL

    /** How far, in pixels, a pointer may seem to move while it stands still: the rounding of the places it is measured from. */
    private const val POINTER_STILL = 0.5f

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
