package com.aimdi.xtanative.ui.home

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalFocusManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.navigation.NavHostController

/**
 * What the screen on top lets the desktop's keys do (XTA's DesktopRefreshKeys
 * and DesktopPager): F5 and Ctrl+R refresh it, ← and → turn its pages, and
 * going back first closes what it has open. One screen holds them at a time,
 * the one resumed; see [DesktopShortcuts]. [refocus] gives the keys back
 * to the frame unless something in the page holds them; [takeKeys] takes
 * them from whatever in the page holds them. [screen] is the screen shown
 * now (the back stack's top entry), so a sheet knows whether it closes over
 * the screen it opened on.
 */
class DesktopActions(
    private val refocus: (FocusRequester?) -> Unit = {},
    private val takeKeys: () -> Unit = {},
    private val screen: () -> Any? = { null },
) {
    private var owner: Any? = null
    private var refreshing: (() -> Unit)? = null
    private var turning: ((Int) -> Unit)? = null
    private var jumping: ((Int) -> Unit)? = null
    private var open: (() -> Boolean)? = null
    private var openRead: (() -> Boolean)? = null
    private var closing: (() -> Unit)? = null

    internal fun take(
        owner: Any,
        refresh: () -> Unit,
        turn: (Int) -> Unit,
        isOpen: () -> Boolean,
        close: () -> Unit,
        jump: (Int) -> Unit = {},
        readsOpen: () -> Boolean = { false },
    ) {
        this.owner = owner
        refreshing = refresh
        turning = turn
        jumping = jump
        open = isOpen
        openRead = readsOpen
        closing = close
    }

    internal fun release(owner: Any) {
        if (this.owner !== owner) return
        this.owner = null
        refreshing = null
        turning = null
        jumping = null
        open = null
        openRead = null
        closing = null
    }

    /** False when no screen answers. */
    fun refresh(): Boolean = refreshing?.let { it(); true } ?: false

    /** The screen's pages turned [by]; the post marked on the page left is marked no more. */
    fun turn(by: Int): Boolean = turning?.let { markedPost = null; it(by); true } ?: false

    /** Ctrl and a digit: the screen's tab [digit] ([DesktopKeys.pageAt]); false when no screen answers. */
    fun jump(digit: Int): Boolean = jumping?.let { markedPost = null; it(digit); true } ?: false

    fun somethingOpen(): Boolean = open?.invoke() ?: false

    /**
     * Whether what is open is read with the reading keys ([DesktopKeys.action]'s
     * openIsRead): a column opened beside the page (the Deck's detail), while
     * no sheet is open over it.
     */
    fun openIsRead(): Boolean = openRead?.invoke() == true && !sheetOpen()

    fun close() {
        closing?.invoke()
    }

    /** The search fields that Ctrl+F and / go to, the last shown on top ([FindsWithKeys]). */
    private val finders = mutableListOf<Pair<Any, () -> Unit>>()

    internal fun addFinder(owner: Any, find: () -> Unit) {
        finders.removeAll { it.first === owner }
        finders += owner to find
    }

    internal fun removeFinder(owner: Any) {
        finders.removeAll { it.first === owner }
    }

    /** The search field shown on top takes the keyboard; false when the page has none. */
    fun find(): Boolean = finders.lastOrNull()?.second?.let { it(); true } ?: false

    /** The find bars Ctrl+F and F3 work, the last shown on top ([FindsInPageWithKeys]). */
    private val pageFinders = mutableListOf<Pair<Any, PageFinder>>()

    internal fun addPageFinder(owner: Any, finder: PageFinder) {
        pageFinders.removeAll { it.first === owner }
        pageFinders += owner to finder
    }

    internal fun removePageFinder(owner: Any) {
        pageFinders.removeAll { it.first === owner }
    }

    /** The find bar of the page shown opens and takes the keyboard; false when the page has none. */
    fun findInPage(): Boolean = pageFinders.lastOrNull()?.second?.let { it.open(); true } ?: false

    /** F3: the open find bar goes to its next match, or [by] -1 the one before; false when none is open. */
    fun findStep(by: Int): Boolean = pageFinders.lastOrNull()?.second?.step?.invoke(by) ?: false

    /** The New actions Ctrl+N opens, the last shown on top ([CreatesWithKeys]). */
    private val creators = mutableListOf<Pair<Any, () -> Boolean>>()

    internal fun addCreator(owner: Any, create: () -> Boolean) {
        creators.removeAll { it.first === owner }
        creators += owner to create
    }

    internal fun removeCreator(owner: Any) {
        creators.removeAll { it.first === owner }
    }

    /** The New of the page shown opens; false when it has none, or none now (its editor already open). */
    fun create(): Boolean = creators.lastOrNull()?.second?.invoke() ?: false

    /** The selections Ctrl+A, Delete and Esc work on, the last shown on top ([SelectsWithKeys]). */
    private val selections = mutableListOf<Pair<Any, KeySelection>>()

    internal fun addSelection(owner: Any, selection: KeySelection) {
        selections.removeAll { it.first === owner }
        selections += owner to selection
    }

    internal fun removeSelection(owner: Any) {
        selections.removeAll { it.first === owner }
    }

    /** Whether the page shown is selecting now. */
    fun selecting(): Boolean = selections.lastOrNull()?.second?.selecting?.invoke() ?: false

    /** Every item the page shows selected; false when it has nothing to select. */
    fun selectAll(): Boolean = selections.lastOrNull()?.second?.selectAll?.invoke() ?: false

    /** What is selected removed, the page asking first; false when nothing is. */
    fun removeSelected(): Boolean = selections.lastOrNull()?.second?.remove?.invoke() ?: false

    /** The page stops selecting; false when it was not. */
    fun leaveSelection(): Boolean = selections.lastOrNull()?.second?.leave?.invoke() ?: false

    /** The reorderable lists Alt+↑ and Alt+↓ move a row of, the last shown on top ([RowKeyMoves]). */
    private val rowMovers = mutableListOf<Pair<Any, (up: Boolean) -> Boolean>>()

    internal fun addRowMover(owner: Any, move: (up: Boolean) -> Boolean) {
        rowMovers.removeAll { it.first === owner }
        rowMovers += owner to move
    }

    internal fun removeRowMover(owner: Any) {
        rowMovers.removeAll { it.first === owner }
    }

    /** The row the pointer rests on, in a list shown, a place up or down; false when no list has one there. */
    fun moveRow(up: Boolean): Boolean = rowMovers.asReversed().any { it.second(up) }

    /** What a field being typed in drops at Esc before leaving it (a recent search it has highlighted), the last added on top. */
    private val fieldEscapes = mutableListOf<Pair<Any, () -> Boolean>>()

    internal fun addFieldEscape(owner: Any, drop: () -> Boolean) {
        fieldEscapes.removeAll { it.first === owner }
        fieldEscapes += owner to drop
    }

    internal fun removeFieldEscape(owner: Any) {
        fieldEscapes.removeAll { it.first === owner }
    }

    /** Esc in a field: true when the field dropped something of its own, and keeps the keyboard for now. */
    internal fun escapeInField(): Boolean = fieldEscapes.asReversed().any { it.second() }

    /**
     * The lists on screen, in the order they came, the one the pointer was
     * last over, and the column each is in ([ReadColumn]). A list is a lazy
     * one ([LazyListState]) or a page that scrolls as a whole ([ScrollState]).
     */
    private val lists = mutableListOf<ScrollableState>()
    private var pointed: ScrollableState? = null
    private val listColumns = mutableMapOf<ScrollableState, Any>()

    internal fun addList(list: ScrollableState, column: Any? = null) {
        lists += list
        if (column != null) listColumns[list] = column
    }

    internal fun removeList(list: ScrollableState) {
        lists -= list
        listColumns -= list
        listPlaces -= list
        if (pointed === list) pointed = null
    }

    /** Where each list on screen is laid out, so the posts drawn in it are placed against it ([currentPost]). */
    private val listPlaces = mutableMapOf<ScrollableState, LayoutCoordinates>()

    internal fun placeList(list: ScrollableState, place: LayoutCoordinates) {
        listPlaces[list] = place
    }

    /** The posts drawn in the lists on screen, each with what the keys do to it ([keyedPost]). */
    private val posts = mutableListOf<KeyedPost>()

    internal fun addPost(post: KeyedPost) {
        posts += post
    }

    internal fun removePost(post: KeyedPost) {
        posts -= post
        if (markedPost === post) markedPost = null
    }

    /**
     * The post a step of j or k landed on, marked while the keyboard reads
     * the list; null once the pointer moves or the list moves otherwise.
     */
    var markedPost: KeyedPost? by mutableStateOf(null)
        private set

    /**
     * The post the reading keys are on: the one j or k last landed on while
     * it is still marked, else the first whose start shows in the list they
     * move, under a header pinned over it ([DesktopKeys.currentPost]).
     */
    fun currentPost(): KeyedPost? {
        // Only a lazy list holds posts; a page that scrolls as a whole has none to act on.
        val list = readList() as? LazyListState ?: return null
        markedIn(list)?.let { return it }
        val (drawn, tops) = drawnPosts(list) ?: return null
        val info = list.layoutInfo
        val shown = info.visibleItemsInfo.map { DesktopKeys.ShownItem(it.index, it.offset, it.size) }
        return DesktopKeys.currentPost(shown, tops, info.viewportEndOffset)?.let(drawn::get)
    }

    /**
     * The post marked while it is still drawn in [list]: one marked in
     * another column, or on a page turned away from, is not what the reading
     * keys are on now.
     */
    internal fun markedIn(list: ScrollableState?): KeyedPost? =
        markedPost?.takeIf { it.list === list && it in posts && it.place?.isAttached == true }

    /** The posts drawn in [list] and where each starts, in its items' offsets; null while the list is not laid out. */
    private fun drawnPosts(list: LazyListState): Pair<List<KeyedPost>, List<Int>>? {
        val frame = listPlaces[list]?.takeIf { it.isAttached } ?: return null
        val drawn = posts.filter { it.list === list && it.place?.isAttached == true }
        // From the window to the items' own offsets, which start under the list's padding.
        val top = frame.positionInWindow().y - list.layoutInfo.viewportStartOffset
        return drawn to drawn.map { (it.place!!.positionInWindow().y - top).toInt() }
    }

    /**
     * After a step of j or k to item [index] of [list]: its first post is
     * marked, even where the list could not bring it to the top (near its
     * end); else the post the keys are on now.
     */
    internal fun markPostIn(list: LazyListState, index: Int?) {
        markedPost = null
        val item = list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
        val inItem = item?.let { shown -> drawnPosts(list)?.let { (drawn, tops) -> DesktopKeys.firstIn(shown.offset, shown.size, tops)?.let(drawn::get) } }
        markedPost = inItem ?: currentPost()
    }

    /**
     * Where j ([forward]) or k takes [list] ([DesktopKeys.keyStep]): from
     * the post marked in it, so near its end or start, where the list moves
     * no further, the mark still goes on post by post. A step to a post drawn
     * already names it ([KeyedStep.post]), to be marked once the list moved.
     */
    internal fun keyStep(list: LazyListState, forward: Boolean): KeyedStep? {
        val info = list.layoutInfo
        val shown = info.visibleItemsInfo.map { DesktopKeys.ShownItem(it.index, it.offset, it.size) }
        val (drawn, tops) = drawnPosts(list) ?: (emptyList<KeyedPost>() to emptyList())
        val marked = markedIn(list)?.let { drawn.indexOf(it).takeIf { at -> at >= 0 } }
        return when (val step = DesktopKeys.keyStep(shown, info.totalItemsCount, tops, marked, forward)) {
            is DesktopKeys.KeyStep.ToItem -> KeyedStep(step, null)
            is DesktopKeys.KeyStep.ToPost -> KeyedStep(step, drawn[step.post])
            null -> null
        }
    }

    /** [post] marked: j or k went on to it ([keyStep]). */
    internal fun markPost(post: KeyedPost) {
        markedPost = post
    }

    /** The keyboard or the mouse moved the list some other way: no post is marked. */
    internal fun unmarkPost() {
        markedPost = null
    }

    /** [act] done to the post the reading keys are on; false when there is none. */
    fun onPost(act: (PostKeys) -> Unit): Boolean = currentPost()?.let { act(it.keys); true } ?: false

    /** Where in the window the pointer last was: over a list ([point]), or anywhere ([pointerAt]). */
    private var pointer: Offset? = null

    /**
     * The pointer over [list], [at] that place in the window: the keys move
     * it, and a column chosen from the keyboard elsewhere is chosen no more.
     * A pointer that has not moved ([DesktopKeys.pointerMoved]) points at
     * nothing: the event is the window's own, sent as what is under the
     * pointer moved (the Deck scrolled sideways to a chosen column).
     */
    internal fun point(list: ScrollableState, at: Offset? = null) {
        if (!DesktopKeys.pointerMoved(pointer, at)) return
        if (pointer != null) markedPost = null
        pointer = at
        if (pointed !== list) markedPost = null
        pointed = list
        if (listColumns[list] != chosenColumn) chosenColumn = null
        if (listColumns[list] != opened) opened = null
    }

    /** The page's columns side by side ([ReadColumns]), in their order on screen, and the page that shows them. */
    private var columnsOwner: Any? = null
    private var columnOrder: () -> List<Any> = { emptyList() }

    /**
     * The column Ctrl+← and Ctrl+→ chose ([stepColumn]), whose list the
     * reading keys move until the pointer points at another; null while
     * none is. Read in composition, so the column can be marked.
     */
    var chosenColumn: Any? by mutableStateOf(null)
        private set

    internal fun takeColumns(owner: Any, order: () -> List<Any>) {
        columnsOwner = owner
        columnOrder = order
    }

    internal fun releaseColumns(owner: Any) {
        if (columnsOwner !== owner) return
        columnsOwner = null
        columnOrder = { emptyList() }
        chosenColumn = null
        opened = null
        markedPost = null
    }

    /** The column just opened beside the others ([readOpened]); null once the pointer or Ctrl+← and Ctrl+→ go elsewhere. */
    private var opened: Any? = null

    /**
     * [column] just opened (the Deck's detail showing a post): the reading
     * keys move its list, unmarked, until the pointer moves over another list
     * or Ctrl+← and Ctrl+→ choose a column; a column chosen before is chosen
     * no more. The frame takes the keys from the post or button clicked to
     * open it, which would otherwise keep them: the reading keys would do
     * nothing and the first Esc only let go of it.
     */
    internal fun readOpened(column: Any) {
        opened = column
        chosenColumn = null
        markedPost = null
        takeKeys()
    }

    /**
     * The reading keys to the column [by] away ([DesktopKeys.columnStep])
     * from the one they move now; false when the page shown has no columns.
     */
    fun stepColumn(by: Int): Boolean {
        val order = columnOrder()
        val current = chosen() ?: readList()?.let { listColumns[it] }
        val index = DesktopKeys.columnStep(order.size, order.indexOf(current).takeIf { it >= 0 }, by) ?: return false
        chosenColumn = order[index]
        opened = null
        markedPost = null
        return true
    }

    /** The chosen column while the page still shows it; one removed since (its menu's Remove) counts as none, so the keys never go dead. */
    internal fun chosen(): Any? = chosenColumn?.takeIf { it in columnOrder() }

    /** A chosen column no longer on the page is forgotten, so one coming back (added again, turned back to) is not chosen by itself. */
    internal fun forgetGoneColumn() {
        if (chosenColumn != null && chosen() == null) chosenColumn = null
    }

    /** The page scrolled under the pointer (the wheel, a touchpad): the next list it rests over is pointed at, as after a move. */
    internal fun scrolled() {
        pointer = null
        markedPost = null
    }

    /**
     * Where the pointer is in the window, from any of its moves, not only over
     * a list: told after the lists had theirs ([point]), so a move onto a list
     * still counts as one, while a list sliding under a pointer resting off any
     * list (a column's error page, a divider) does not take the keys.
     */
    internal fun pointerAt(at: Offset) {
        if (pointer != null && DesktopKeys.pointerMoved(pointer, at)) markedPost = null
        pointer = at
    }

    /** What is drawn over the screens and holds the keys for its own (the media viewer, a video full screen), the last on top. */
    private val holders = mutableListOf<FocusRequester>()

    internal fun holdKeys(holder: FocusRequester) {
        holders += holder
    }

    /** Whether an overlay holds the keys: its own Esc and arrows then come before the window's. */
    fun overlayHoldsKeys(): Boolean = holders.isNotEmpty()

    /** [holder] going: the keys to the one under it, else back to the window's frame, so they always have a taker. */
    internal fun releaseKeys(holder: FocusRequester) {
        holders -= holder
        refocus(holders.lastOrNull())
    }

    /** The sheets open now ([GivesKeysBack]); one over another hands nothing back as it goes. */
    private var sheets = 0

    /** The screen the first of the sheets open now opened over. */
    private var sheetScreen: Any? = null

    internal fun sheetOpened() {
        if (sheets == 0) sheetScreen = screen()
        sheets++
    }

    /** Whether a sheet is open now: one opened in place of another as it closed takes back the keys it handed back. */
    internal fun sheetOpen(): Boolean = sheets > 0

    /**
     * A sheet going: once the last is gone, the keys back to the overlay under
     * it, else the window's frame. One still open (a sheet that opened
     * another) keeps its own field's focus. Back on the screen it opened
     * over, the frame takes the keys from the page too: the button clicked to
     * open the sheet (Add column's +) still holds them, and would keep the
     * reading keys and Ctrl+← / Ctrl+→ dead until Esc or a click elsewhere.
     * On a screen the sheet opened (a pick that went somewhere), a field it
     * focuses keeps the keys.
     */
    internal fun sheetClosed() {
        sheets = (sheets - 1).coerceAtLeast(0)
        if (sheets > 0) return
        val under = holders.lastOrNull()
        if (under == null && screen() == sheetScreen) takeKeys() else refocus(under)
        sheetScreen = null
    }

    /** The keys back to the window's frame (or the overlay holding them), once a field has let go of them ([rememberLeaveField]). */
    internal fun keysToFrame() = refocus(holders.lastOrNull())

    /**
     * The list the keys move: the chosen column's ([chosenColumn]; none while
     * it shows no list, such as its error), else the column's just opened
     * ([readOpened]) once its list is shown, else the one the pointer was
     * last over, else the first on screen (the page's own, before any column
     * beside it). A column gone from the page (the Deck's detail closed)
     * counts as none chosen or opened.
     */
    internal fun readList(): ScrollableState? {
        chosen()?.let { column -> return lists.lastOrNull { listColumns[it] == column } }
        opened?.takeIf { it in columnOrder() }?.let { column -> lists.lastOrNull { listColumns[it] == column }?.let { return it } }
        return pointed ?: lists.firstOrNull()
    }
}

/**
 * Lets the desktop's keys move [list] (DesktopKeys: ↑ ↓, Page Up and Down,
 * Space, Home, End) while it is on screen, and makes it the one they move
 * while the pointer is over it. In a [ReadColumn], Ctrl+← and Ctrl+→ choose
 * it with its column. Nothing off the desktop.
 */
@Composable
fun Modifier.movedByKeys(list: LazyListState): Modifier = keyMoved(list)

/**
 * A page that scrolls as a whole ([verticalScroll]) moved by the desktop's
 * keys as a list is ([movedByKeys]): ↑ ↓, Page Up and Down, Space, Home and
 * End, while nothing in it keeps the keys (a field, a slider, a dialog).
 * Put it before the [verticalScroll] of the same [scroll]; or use
 * [verticalScrollByKeys], which does both. Nothing off the desktop.
 */
@Composable
fun Modifier.movedByKeys(scroll: ScrollState): Modifier = keyMoved(scroll)

/** [verticalScroll] over [scroll], moved by the desktop's keys too ([movedByKeys]). */
@Composable
fun Modifier.verticalScrollByKeys(scroll: ScrollState): Modifier = movedByKeys(scroll).verticalScroll(scroll)

@Composable
private fun Modifier.keyMoved(list: ScrollableState): Modifier {
    val actions = LocalDesktopActions.current ?: return this
    val column = LocalReadColumn.current
    DisposableEffect(actions, list, column) {
        actions.addList(list, column)
        onDispose { actions.removeList(list) }
    }
    // Where the list stands in the window, so the pointer's place is known there and not only in the list.
    val origin = remember { arrayOf(Offset.Zero) }
    return onGloballyPositioned {
        origin[0] = it.positionInWindow()
        actions.placeList(list, it)
    }.pointerInput(actions, list) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.type == PointerEventType.Enter || event.type == PointerEventType.Move) {
                    actions.point(list, event.changes.firstOrNull()?.let { origin[0] + it.position })
                }
            }
        }
    }
}

/** The column of a page of columns ([ReadColumn]) that what is drawn here is in; null outside one. */
private val LocalReadColumn = staticCompositionLocalOf<Any?> { null }

/**
 * [content] as the column [key] of a page of columns side by side
 * ([ReadColumns]): the list it shows is the one the reading keys move once
 * Ctrl+← or Ctrl+→ chooses the column.
 */
@Composable
fun ReadColumn(key: Any, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalReadColumn provides key, content = content)
}

/**
 * Lets Ctrl+← and Ctrl+→ choose among the page's columns, [order] being
 * their keys ([ReadColumn]) in their order on screen, while the page is the
 * one shown. Nothing off the desktop.
 */
@Composable
fun ReadColumns(order: List<Any>) {
    val actions = LocalDesktopActions.current ?: return
    val latest by rememberUpdatedState(order)
    val owner = remember { Any() }
    LaunchedEffect(actions, order) { actions.forgetGoneColumn() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { actions.takeColumns(owner) { latest } }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { actions.releaseColumns(owner) }
    DisposableEffect(actions) { onDispose { actions.releaseColumns(owner) } }
}

/** The column Ctrl+← and Ctrl+→ chose ([ReadColumn]'s key), for the page to mark; null while none is, and off the desktop. */
@Composable
fun chosenReadColumn(): Any? = LocalDesktopActions.current?.chosen()

/** The desktop's actions; null off the desktop, where only touch and Android's back exist. */
val LocalDesktopActions = staticCompositionLocalOf<DesktopActions?> { null }

/**
 * Where the window's [DesktopActions] are, for what is drawn above the frame
 * that provides them (a video full screen, over every screen). The app's
 * root provides one; [DesktopNavigation] fills it in on the desktop.
 */
class DesktopActionsHost {
    var actions: DesktopActions? = null
}

val LocalDesktopActionsHost = staticCompositionLocalOf { DesktopActionsHost() }

/**
 * Going back the way a PC does it (XTA's DesktopNavigation): the mouse's back
 * button, Alt+← and Esc go back as Android's back gesture does, the first Esc
 * leaving a text field being typed in, and the screen on top answers
 * [DesktopActions]; Alt+→ and the mouse's forward button call [goForward],
 * false when there is nothing to go forward to. Off the desktop it is
 * [content] alone.
 */
@Composable
expect fun DesktopNavigation(
    nav: NavHostController,
    openSearch: () -> Unit,
    openSettings: () -> Unit,
    openHome: () -> Unit = {},
    goForward: () -> Boolean = { false },
    content: @Composable () -> Unit,
)

/**
 * Lets Ctrl+F and / ([DesktopKeys]) go to the screen's own search while it is
 * the one shown: [find] puts the keyboard in its field (or opens its search
 * first). Where no screen shown has one, they open the app's search. Nothing
 * off the desktop.
 */
@Composable
fun FindsWithKeys(find: () -> Unit) {
    val actions = LocalDesktopActions.current ?: return
    val latest by rememberUpdatedState(find)
    val owner = remember { Any() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { actions.addFinder(owner) { latest() } }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { actions.removeFinder(owner) }
    DisposableEffect(actions) { onDispose { actions.removeFinder(owner) } }
}

/** A page's find bar as the desktop's keys reach it: [open] it (Ctrl+F), or [step] it on (F3; false while it is closed). */
class PageFinder(val open: () -> Unit, val step: (Int) -> Boolean)

/**
 * Lets Ctrl+F and F3 ([DesktopKeys]) work the screen's find bar while it is
 * the one shown, as a browser's find in page: [open] opens it with the
 * keyboard in its field, [step] goes to the next match or (-1) the one
 * before, answering false while it is closed. / keeps going to the search.
 * Nothing off the desktop.
 */
@Composable
fun FindsInPageWithKeys(open: () -> Unit, step: (Int) -> Boolean) {
    val actions = LocalDesktopActions.current ?: return
    val latestOpen by rememberUpdatedState(open)
    val latestStep by rememberUpdatedState(step)
    val owner = remember { Any() }
    val finder = remember { PageFinder({ latestOpen() }, { latestStep(it) }) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { actions.addPageFinder(owner, finder) }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { actions.removePageFinder(owner) }
    DisposableEffect(actions) { onDispose { actions.removePageFinder(owner) } }
}

/**
 * Lets Ctrl+N ([DesktopKeys]) open the screen's own New while it is the one
 * shown, as a desktop app's makes a new document: [create] opens it (a new
 * note's editor, a new group's) and answers false when it did nothing, so the
 * key goes on. Nothing off the desktop.
 */
@Composable
fun CreatesWithKeys(create: () -> Boolean) {
    val actions = LocalDesktopActions.current ?: return
    val latest by rememberUpdatedState(create)
    val owner = remember { Any() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { actions.addCreator(owner) { latest() } }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { actions.removeCreator(owner) }
    DisposableEffect(actions) { onDispose { actions.removeCreator(owner) } }
}

/**
 * A page's selection as the desktop's keys reach it ([SelectsWithKeys]):
 * whether it is [selecting], and Ctrl+A ([selectAll]), Delete ([remove])
 * and Esc ([leave]) on it, each answering false when it did nothing.
 */
class KeySelection(
    val selecting: () -> Boolean,
    val selectAll: () -> Boolean,
    val remove: () -> Boolean,
    val leave: () -> Boolean,
)

/**
 * Lets Ctrl+A, Delete and Esc ([DesktopKeys]) work on the screen's selection
 * while it is the one shown, as in a file manager: [selectAll] selects every
 * item shown (starting a selection where the page does), [remove] asks to
 * remove what is selected, [leave] stops selecting; each answers false when
 * it did nothing, and the key goes on. Nothing off the desktop.
 */
@Composable
fun SelectsWithKeys(selecting: Boolean, selectAll: () -> Boolean, remove: () -> Boolean, leave: () -> Boolean) {
    val actions = LocalDesktopActions.current ?: return
    val latestSelecting by rememberUpdatedState(selecting)
    val latestSelectAll by rememberUpdatedState(selectAll)
    val latestRemove by rememberUpdatedState(remove)
    val latestLeave by rememberUpdatedState(leave)
    val owner = remember { Any() }
    val selection = remember { KeySelection({ latestSelecting }, { latestSelectAll() }, { latestRemove() }, { latestLeave() }) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { actions.addSelection(owner, selection) }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { actions.removeSelection(owner) }
    DisposableEffect(actions) { onDispose { actions.removeSelection(owner) } }
}

/** A search field that Ctrl+F and / put the keyboard in while it is shown ([FindsWithKeys]). Nothing off the desktop. */
@Composable
fun Modifier.foundWithKeys(): Modifier {
    if (LocalDesktopActions.current == null) return this
    val focus = remember { FocusRequester() }
    FindsWithKeys { runCatching { focus.requestFocus() } }
    return focusRequester(focus)
}

/**
 * Lets the screen answer the desktop's keys while it is the one shown: [refresh]
 * for F5 and Ctrl+R, [turn] for ← and →, and [isOpen] with [close] for what
 * going back closes first; [readsOpen] when what is open is a column beside
 * the page that the reading keys move rather than something over it (the
 * Deck's detail). Nothing off the desktop.
 */
@Composable
internal fun DesktopShortcuts(
    refresh: () -> Unit,
    turn: (Int) -> Unit,
    isOpen: () -> Boolean,
    close: () -> Unit,
    jump: (Int) -> Unit = {},
    readsOpen: () -> Boolean = { false },
) {
    val actions = LocalDesktopActions.current ?: return
    val latestRefresh by rememberUpdatedState(refresh)
    val latestTurn by rememberUpdatedState(turn)
    val latestJump by rememberUpdatedState(jump)
    val latestIsOpen by rememberUpdatedState(isOpen)
    val latestClose by rememberUpdatedState(close)
    val latestReadsOpen by rememberUpdatedState(readsOpen)
    val owner = remember { Any() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        actions.take(owner, { latestRefresh() }, { latestTurn(it) }, { latestIsOpen() }, { latestClose() }, { latestJump(it) }, { latestReadsOpen() })
    }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { actions.release(owner) }
    DisposableEffect(actions) { onDispose { actions.release(owner) } }
}

/**
 * For something drawn over the screens that keeps no keys of its own (a
 * sheet): when it goes, the keys go back to what it covered, the overlay
 * under it (a video full screen) or the window's frame, so they never
 * wait for a click. Nothing off the desktop.
 */
@Composable
fun GivesKeysBack() {
    val actions = LocalDesktopActions.current ?: LocalDesktopActionsHost.current.actions ?: return
    DisposableEffect(actions) {
        actions.sheetOpened()
        onDispose { actions.sheetClosed() }
    }
}

/**
 * For something drawn over the screens that takes the keys with [focus] (the
 * media viewer, a video full screen): when it goes, the keys go back to what
 * it covered, the overlay under it or the window's frame. Without that its
 * going leaves nothing with the focus and every desktop key without a
 * taker. Nothing off the desktop.
 */
@Composable
fun HoldsKeys(focus: FocusRequester) {
    val actions = LocalDesktopActions.current ?: LocalDesktopActionsHost.current.actions ?: return
    DisposableEffect(actions, focus) {
        actions.holdKeys(focus)
        onDispose { actions.releaseKeys(focus) }
    }
}

/**
 * Out of the field being typed in, as a search sent leaves it: the keyboard
 * hides, and on the desktop the window's frame takes the keys back, so ←, →
 * and the reading keys work at once rather than after a click.
 */
@Composable
fun rememberLeaveField(): () -> Unit {
    val focusManager = LocalFocusManager.current
    val actions = LocalDesktopActions.current
    return remember(focusManager, actions) {
        {
            focusManager.clearFocus()
            actions?.keysToFrame()
        }
    }
}

/** A step of j or k ([DesktopActions.keyStep]), and the post it goes to when that is one drawn already. */
internal class KeyedStep(val step: DesktopKeys.KeyStep, val post: KeyedPost?)
