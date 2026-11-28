package com.aimdi.xtanative.ui.home

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aimdi.xtanative.core.feed.FeedStrip
import com.aimdi.xtanative.core.session.DeckColumn
import com.aimdi.xtanative.core.session.DeckColumns
import com.aimdi.xtanative.data.AppGraph
import com.aimdi.xtanative.data.LocalGraph
import com.aimdi.xtanative.ui.common.ContextMenuKey
import com.aimdi.xtanative.ui.common.IconTip
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.home_deck_add_to
import com.aimdi.xtanative.ui.generated.resources.home_deck_show_in
import com.aimdi.xtanative.ui.links.LaunchPlace
import com.aimdi.xtanative.ui.links.OpenRequests
import com.aimdi.xtanative.ui.post.KeyedMenuRows
import com.aimdi.xtanative.ui.saved.KeepNotice
import com.aimdi.xtanative.ui.saved.KeepNotices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** The deck's column for a timeline of Home's strip by its id: Following, X's For you, or a network's. */
internal fun deckColumnOfStrip(id: String): DeckColumn = when (id) {
    FeedStrip.FOLLOWING -> DeckColumn.Following
    FeedStrip.FOR_YOU -> DeckColumn.ForYou
    else -> DeckColumn.Network(id)
}

/** The deck's column for one of Home's timelines. */
internal fun deckColumnOfTab(tab: HomeTab): DeckColumn = when (tab) {
    HomeTab.Following -> DeckColumn.Following
    HomeTab.ForYou -> DeckColumn.ForYou
    is HomeTab.Network -> deckColumnOfStrip(tab.id)
    is HomeTab.Group -> DeckColumn.Group(tab.id)
}

/** The deck's column for a page of Home's bar: a network's or a group's; none for the strip and Home's own pages. */
internal fun deckColumnOfPage(page: BarPage): DeckColumn? = when (page) {
    is BarPage.Network -> deckColumnOfStrip(page.id)
    is BarPage.Group -> DeckColumn.Group(page.groupId)
    else -> null
}

/**
 * What the deck is asked from the rest of the app: a column to show
 * ([shown], the Deck bringing it into view and choosing it once open), and
 * the timeline the mouse drags from the account menu onto the deck
 * ([dragged], where it is now, the places it can land).
 */
@Stable
internal object DeckRequests {
    /** The column the Deck is to bring into view and choose, once it shows it; null when none is asked for. */
    var shown: String? by mutableStateOf(null)
        private set

    fun show(columnId: String) {
        shown = columnId
    }

    /** [columnId] has been brought into view: it is asked for no more. */
    fun shownDone(columnId: String) {
        if (shown == columnId) shown = null
    }

    /** What the mouse drags onto the deck now; null while nothing is dragged. */
    var dragged: DeckDrag? by mutableStateOf(null)
        internal set

    /** Where a dragged timeline can be dropped, by who registered it: the Deck's columns, the menu's Deck row. */
    // Kept as plain data: they are told as they are laid out, and read again as the drag moves.
    internal val targets = HashMap<Any, DeckDropTarget>()

    /** The target under [at] (in the window) and the column the drop goes before there; null over none. */
    fun landing(at: Offset): Pair<DeckDropTarget, String?>? =
        targets.values.firstOrNull { it.bounds?.contains(at) == true }?.let { it to it.before(at) }
}

/** A timeline being dragged onto the deck: the column, its name for the picture that follows the mouse, and where it is in the window. */
internal data class DeckDrag(val column: DeckColumn, val label: String, val at: Offset)

/**
 * A place a dragged timeline lands on the deck: [bounds] in the window,
 * [before] the column a drop at a point goes before (null: last), and
 * whether the reader is looking at the deck there ([onDeck]), so no notice
 * says what the reader sees happen.
 */
internal class DeckDropTarget(val bounds: Rect?, val onDeck: Boolean, val before: (Offset) -> String?)

/** Where a timeline dropped among the deck's columns goes, apart from the pointer. */
internal object DeckDrops {
    /**
     * The column a drop at [x] lands before, among columns standing from
     * left to right as [spans] (each column's id with its left and right
     * edges): the first whose middle is past [x]; null past the last one.
     */
    fun before(x: Float, spans: List<Triple<String, Float, Float>>): String? =
        spans.firstOrNull { (_, left, right) -> x < (left + right) / 2f }?.first

    /** Where the line marking the drop stands: the left edge of the column it goes before, else the right edge of the last. */
    fun lineAt(before: String?, spans: List<Triple<String, Float, Float>>): Float? =
        spans.firstOrNull { it.first == before }?.second ?: spans.lastOrNull()?.third
}

/**
 * Puts timelines on the deck from where the reader is (a row of the account
 * menu, a group's or a network's screen): added last, or before the column
 * a drop names; off the deck itself it says so at the window's foot with
 * Open, which shows the deck with the column chosen there.
 */
@Stable
internal class DeckAdder(private val graph: AppGraph, private val scope: CoroutineScope) {
    fun add(column: DeckColumn, before: String? = null, onDeck: Boolean = false) {
        scope.launch {
            graph.sessions.update { it.copy(deck = DeckColumns.placed(it.deck, column, before)) }
            if (onDeck) DeckRequests.show(column.id) else KeepNotices.show(KeepNotice.AddedToDeck { show(column) })
        }
    }

    /** The deck opened (as its menu row opens it) and [column] brought into view on it. */
    fun show(column: DeckColumn) {
        DeckRequests.show(column.id)
        OpenRequests.place(LaunchPlace.DECK)
    }
}

/**
 * The window's [DeckAdder]. Its work outlives the menu row or the dragged
 * row it was asked from, which go as the menu closes or the list moves.
 */
@Composable
internal fun rememberDeckAdder(): DeckAdder {
    val graph = LocalGraph.current
    return remember(graph) { DeckAdder(graph, DeckAdds) }
}

/** Where a [DeckAdder]'s writes run: on the main thread, as long as the app does. */
private val DeckAdds = CoroutineScope(SupervisorJob() + Dispatchers.Main)

/** Whether [column] is on the deck now. */
@Composable
internal fun onDeck(column: DeckColumn): Boolean {
    val session by LocalGraph.current.sessions.state.collectAsStateWithLifecycle()
    return session?.deck.orEmpty().contains(column.id)
}

/** A menu's Add to Deck, or Show in Deck where [column] is on it already; [close] closes the menu first. */
@Composable
internal fun DeckMenuItem(column: DeckColumn, close: () -> Unit) {
    val adder = rememberDeckAdder()
    val there = onDeck(column)
    DropdownMenuItem(
        text = { Text(stringResource(if (there) Res.string.home_deck_show_in else Res.string.home_deck_add_to)) },
        leadingIcon = { Icon(if (there) HomeIcons.ViewColumn else HomeIcons.ViewColumnOutlined, contentDescription = null) },
        onClick = {
            close()
            if (there) adder.show(column) else adder.add(column)
        },
    )
}

/** A top bar's button for [column]: Add to Deck, or Show in Deck once it is there, its mark filled. */
@Composable
fun DeckButton(column: DeckColumn) {
    val adder = rememberDeckAdder()
    val there = onDeck(column)
    val label = stringResource(if (there) Res.string.home_deck_show_in else Res.string.home_deck_add_to)
    IconTip(label) {
        IconButton(onClick = { if (there) adder.show(column) else adder.add(column) }) {
            Icon(if (there) HomeIcons.ViewColumn else HomeIcons.ViewColumnOutlined, label)
        }
    }
}

/**
 * A row's own menu, as a right click, the Menu key or Shift+F10 opens it:
 * Mark all as read while [onMarkRead] is there, and Add to Deck (or Show in
 * Deck) for [column].
 */
@Composable
internal fun TimelineRowMenu(expanded: Boolean, column: DeckColumn, onDismiss: () -> Unit, onMarkRead: (() -> Unit)? = null) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        KeyedMenuRows {
            if (onMarkRead != null) {
                MarkReadItem {
                    onDismiss()
                    onMarkRead()
                }
            }
            DeckMenuItem(column, onDismiss)
        }
    }
}

/** [open] at the Menu key or Shift+F10 while the row this is on has the focus, as a right click opens its menu. */
internal fun Modifier.opensMenuByKey(open: () -> Unit): Modifier = onKeyEvent { event ->
    val menu = event.type == KeyEventType.KeyDown && ((event.key == ContextMenuKey && !event.isShiftPressed) || (event.key == Key.F10 && event.isShiftPressed))
    menu.also { if (it) open() }
}

/**
 * A row the mouse drags onto the deck: past the touch slop the press is a
 * drag (the row's click cancelled), a picture of [label] follows the
 * pointer ([DeckDragPicture]), and let go over the deck's columns or the
 * menu's Deck row [column] goes there. Touch is left alone, its drag
 * scrolls the list.
 */
internal fun Modifier.dragsToDeck(column: DeckColumn, label: String): Modifier = composed {
    val adder = rememberDeckAdder()
    val latest by rememberUpdatedState(DeckDrag(column, label, Offset.Zero))
    val place = remember { arrayOfNulls<LayoutCoordinates>(1) }
    onGloballyPositioned { place[0] = it }.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (down.type != PointerType.Mouse || !currentEvent.buttons.isPrimaryPressed) return@awaitEachGesture
            val start = awaitTouchSlopOrCancellation(down.id) { change, _ -> change.consume() } ?: return@awaitEachGesture
            val origin = { place[0]?.takeIf { it.isAttached }?.positionInWindow() ?: Offset.Zero }
            DeckRequests.dragged = latest.copy(at = origin() + start.position)
            try {
                val ended = drag(start.id) { change ->
                    change.consume()
                    DeckRequests.dragged = latest.copy(at = origin() + change.position)
                }
                if (ended) DeckRequests.dragged?.let { dragged ->
                    DeckRequests.landing(dragged.at)?.let { (target, before) -> adder.add(dragged.column, before, target.onDeck) }
                }
            } finally {
                DeckRequests.dragged = null
            }
        }
    }
}

/** [this] as a place a dragged timeline lands ([DeckRequests.targets]) while composed, outlined in the accent while one is over it. */
@Composable
internal fun Modifier.deckDropTarget(onDeck: Boolean = false, before: (Offset) -> String? = { null }): Modifier {
    val key = remember { Any() }
    val latest by rememberUpdatedState(before)
    DisposableEffect(key) { onDispose { DeckRequests.targets.remove(key) } }
    val color = MaterialTheme.colorScheme.primary
    return onGloballyPositioned { DeckRequests.targets[key] = DeckDropTarget(it.boundsInWindow(), onDeck) { at -> latest(at) } }
        .drawWithContent {
            drawContent()
            // Read as it is drawn: a drag moving redraws the outline, not what it is on.
            val over = DeckRequests.dragged?.let { dragged -> DeckRequests.targets[key]?.bounds?.contains(dragged.at) } == true
            if (over && !onDeck) drawRoundRect(color, cornerRadius = CornerRadius(20.dp.toPx()), style = Stroke(2.dp.toPx()))
        }
}

/** The picture of the timeline being dragged onto the deck, beside the pointer, over the whole window. */
@Composable
internal fun DeckDragPicture() {
    val dragged = DeckRequests.dragged
    val origin = remember { arrayOfNulls<Offset>(1) }
    Box(Modifier.fillMaxSize().onGloballyPositioned { origin[0] = it.positionInWindow() }) {
        if (dragged == null) return@Box
        val at = dragged.at - (origin[0] ?: Offset.Zero)
        val gap = with(LocalDensity.current) { 12.dp.roundToPx() }
        Surface(
            Modifier.offset { IntOffset(at.x.toInt() + gap, at.y.toInt() + gap) }.widthIn(max = 240.dp),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.inverseSurface,
            shadowElevation = 6.dp,
        ) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(HomeIcons.ViewColumnOutlined, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(dragged.label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

/** The line where a timeline dragged over the deck would land: [at] in the window, drawn over [this] (whose left is at [left]); both read as it is drawn. */
internal fun Modifier.deckDropLine(at: () -> Float?, left: () -> Float, color: Color): Modifier = drawWithContent {
    drawContent()
    val x = at()?.minus(left())
    if (x != null) {
        val width = 3.dp.toPx()
        drawRect(color, topLeft = Offset(x - width / 2, 0f), size = Size(width, size.height))
    }
}
