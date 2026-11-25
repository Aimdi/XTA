package com.aimdi.xtanative.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CoroutineScope

/**
 * What a hardware keyboard's keys do off the desktop (a tablet with a
 * keyboard, a Chromebook, a phone with one plugged in): a careful part of
 * the desktop's reading keys ([DesktopKeys.action]), on keys the screen
 * itself left unused. Going back stays Android's own (its Esc is Back), and
 * so do Tab, Enter and the arrows' walk from one control to the next.
 */
object HardwareKeys {
    /**
     * [key] with its modifiers held, [repeated] while it is held down, on a
     * page with something open over it or not ([somethingOpen], [openIsRead],
     * as [DesktopKeys.action] takes them). ↑ ↓, Page Up and Down, Space (with
     * Shift, up), Home and End move the list being read, j and k go to its
     * next post and the one before, F5 and Ctrl+R refresh, / goes to the
     * search and Ctrl+F to the page's find bar, else the search. Nothing
     * while a field is being typed in ([typing]), with Alt or the Meta
     * (Search, Windows) key held, with Ctrl on any other key, or for a key
     * not named here; a refresh or a search is not repeated by a key held.
     */
    fun action(
        key: DeskKey,
        alt: Boolean = false,
        ctrl: Boolean = false,
        shift: Boolean = false,
        meta: Boolean = false,
        typing: Boolean = false,
        repeated: Boolean = false,
        somethingOpen: Boolean = false,
        openIsRead: Boolean = false,
    ): DeskAction {
        if (typing || alt || meta || key !in KEYS || (ctrl && key !in WITH_CTRL)) return DeskAction.NONE
        val action = DesktopKeys.action(key, alt = false, ctrl = ctrl, focusInside = false, canGoBack = true, somethingOpen = somethingOpen, shift = shift, openIsRead = openIsRead)
        return when {
            action in MOVES -> action
            action in ONCE && !repeated -> action
            else -> DeskAction.NONE
        }
    }

    private val KEYS = setOf(
        DeskKey.UP, DeskKey.DOWN, DeskKey.PAGE_UP, DeskKey.PAGE_DOWN, DeskKey.SPACE, DeskKey.HOME, DeskKey.END,
        DeskKey.J, DeskKey.K, DeskKey.F5, DeskKey.R, DeskKey.F, DeskKey.SLASH,
    )

    private val WITH_CTRL = setOf(DeskKey.R, DeskKey.F)

    private val MOVES = setOf(
        DeskAction.LINE_UP, DeskAction.LINE_DOWN, DeskAction.SCREEN_UP, DeskAction.SCREEN_DOWN, DeskAction.TO_TOP, DeskAction.TO_END,
        DeskAction.NEXT_ITEM, DeskAction.PREVIOUS_ITEM,
    )

    private val ONCE = setOf(DeskAction.REFRESH, DeskAction.FIND, DeskAction.FIND_IN_PAGE)
}

/**
 * The screens' answers to a hardware keyboard off the desktop: their lists,
 * refresh and search fields, as they give them to the desktop's keys. Null
 * on the desktop (its own are [LocalDesktopActions]) and where no keyboard's
 * keys are listened for. It turns none of the desktop's looks on: those ask
 * [LocalDesktopActions] alone.
 */
val LocalHardwareKeyActions = staticCompositionLocalOf<DesktopActions?> { null }

/** The actions a keyboard's reading keys reach here: the desktop's, else a hardware keyboard's ([LocalHardwareKeyActions]). */
@Composable
internal fun readingKeyActions(): DesktopActions? = LocalDesktopActions.current ?: LocalHardwareKeyActions.current

/**
 * [action] ([HardwareKeys.action]) done on the screen shown: its refresh, its
 * own search field or find bar (else [openSearch]), or the list on screen
 * moved ([DesktopActions.shownList]). False when nothing took it, and the
 * key is left to Android.
 */
internal fun DesktopActions.onHardwareKey(action: DeskAction, scope: CoroutineScope, line: Float, openSearch: () -> Unit): Boolean = when (action) {
    DeskAction.NONE -> false
    DeskAction.REFRESH -> refresh()
    DeskAction.FIND -> {
        if (!find()) openSearch()
        true
    }
    DeskAction.FIND_IN_PAGE -> when {
        findInPage() -> true
        somethingOpen() -> false
        else -> onHardwareKey(DeskAction.FIND, scope, line, openSearch)
    }
    else -> read(action, shownList(), scope, line)
}
