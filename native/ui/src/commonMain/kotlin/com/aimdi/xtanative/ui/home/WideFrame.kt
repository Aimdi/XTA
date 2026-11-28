package com.aimdi.xtanative.ui.home

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aimdi.xtanative.core.backup.FollowGroup
import com.aimdi.xtanative.data.LocalGraph
import com.aimdi.xtanative.ui.common.onSecondaryClick
import com.aimdi.xtanative.ui.settings.AvatarSheet

/** What the column marks: a page of Home's bar, a screen of the menu (its route), or a group (its id). */
data class FrameMark(val page: String? = null, val screen: String? = null, val group: String? = null)

/** Which row of the column is the screen shown, by the route the navigation is on. */
object FrameMarks {
    /**
     * The screens under a menu entry that are not named below its route:
     * one antenna's feed under Antennas, the settings' own screens under
     * Settings, what Saved opens under Saved.
     */
    private val UNDER: List<Pair<String, HomeDestination>> = listOf(
        "antenna/" to HomeDestination.ANTENNAS,
        "speech/" to HomeDestination.SETTINGS,
        "licences" to HomeDestination.SETTINGS,
        "backup" to HomeDestination.SETTINGS,
        "signin" to HomeDestination.SETTINGS,
        "offline" to HomeDestination.SAVED,
        "notes" to HomeDestination.SAVED,
    )

    /**
     * The mark for [route] (a route pattern, its arguments' names in braces):
     * on [home], Home's [homePage]; a group's screen ([group] its id) marks
     * that group, or its page where it is on the [bar] (page ids); a menu
     * entry's screen, or one under it, marks that entry, or the bar's page it
     * is where the bar has it; anything else marks nothing.
     */
    fun of(route: String?, home: String, homePage: String?, group: String?, bar: Set<String>): FrameMark {
        val base = route?.substringBefore('?') ?: return FrameMark()
        if (route == home) return FrameMark(page = homePage)
        if (base == HomeRoutes.GROUP) {
            val pageId = group?.let { BarPage.Group(it).id }
            return if (pageId != null && pageId in bar) FrameMark(page = pageId) else FrameMark(group = group)
        }
        val destination = HomeDestination.entries.firstOrNull { base == it.route || base.startsWith(it.route + "/") }
            ?: UNDER.firstOrNull { (prefix, _) -> base.startsWith(prefix) }?.second
            ?: return FrameMark()
        val page = destination.barPage
        return if (page != null && page in bar) FrameMark(page = page) else FrameMark(screen = destination.route)
    }

    /**
     * The mark for the navigation's [stack] (its entries' routes, the shown
     * last): the screen shown's, else that of the nearest screen below it with
     * one, as a post opened from Home leaves Home's page marked and a profile
     * opened from the search leaves Search marked. A plugin's own pages
     * ([FrameRoute.plugin]) mark the plugin's page where the bar has it, else
     * nothing but Home's page under them: the store or the search they were
     * opened from are not where the reader is.
     */
    fun ofStack(stack: List<FrameRoute>, home: String, homePage: String?, bar: Set<String>): FrameMark {
        var overPlugin = false
        for (entry in stack.asReversed()) {
            val plugin = entry.plugin
            if (plugin != null) {
                val page = BarPage.Network(plugin).id
                if (page in bar) return FrameMark(page = page)
                overPlugin = true
                continue
            }
            val mark = of(entry.route, home, homePage, entry.group, bar)
            if (mark != FrameMark()) return if (!overPlugin || entry.route == home) mark else FrameMark()
        }
        return FrameMark()
    }
}

/**
 * The screens the frame's column or rail opened ([FrameCalls.screen]), by
 * their places' ids in the navigation: they draw no Back of their own
 * ([LocalFrameRoots]), as the navigation beside them leads everywhere, and
 * keep that for as long as they are in the navigation, going out included,
 * so their bar does not change as they slide away. The same screen opened
 * any other way (Search from a hashtag, Settings by Ctrl+comma, Downloads
 * from a snackbar, the deck from Home's button) keeps its Back.
 */
@Stable
class FrameRoots {
    private var opened by mutableStateOf(emptySet<String>())

    /** The place [id] the column opened. */
    fun open(id: String) {
        if (id !in opened) opened = opened + id
    }

    /** Those of [ids] the column opened; read in a composition, followed as it changes. */
    fun among(ids: Collection<String>): Set<String> = ids.filterTo(HashSet()) { it in opened }

    /** Forgets the places gone from [live] (the navigation's and those still shown, one going out among them). */
    fun keep(live: Collection<String>) {
        val kept = opened.intersect(live.toSet())
        if (kept.size != opened.size) opened = kept
    }
}

/** What the column does when a row is picked: Home's page at an index of its bar, a menu entry's route, a group. */
class FrameCalls(val page: (Int, BarPage) -> Unit, val screen: (String) -> Unit, val group: (FollowGroup) -> Unit)

/** A screen in the navigation: its route pattern, and the group it shows ([group], its id) on a group's screen. */
data class FrameRoute(val route: String?, val group: String? = null, val plugin: String? = null)

/**
 * The column itself: the account menu with Home's pages under the account
 * (as Home's own column has them), the row of where the reader is marked
 * ([stack], the navigation's screens, the shown last; Home's is [homeRoute]).
 * [home] is Home's place in the navigation, whose model it reads: the same
 * one Home reads, so both show one bar and one set of unread marks. Where it
 * opens over the window from the rail ([FramedRail]), [onClose] closes it;
 * [focus], in the window's frame, is where F6 brings the keys.
 */
@Composable
fun FramedSidebar(
    home: ViewModelStoreOwner,
    frame: WideFrame,
    stack: List<FrameRoute>,
    homeRoute: String,
    deckFits: Boolean,
    routes: HomeRoutes,
    calls: FrameCalls,
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null,
    focus: FrameFocus? = null,
) {
    val vm = frameModel(home)
    val networks = LocalHomeNetworks.current
    val loaded by vm.ui.collectAsStateWithLifecycle()
    var avatar by rememberSaveable { mutableStateOf(false) }
    Surface(modifier.width(HomeWide.DESKTOP_SIDEBAR_WIDTH.dp).fillMaxHeight(), color = MaterialTheme.colorScheme.background) {
        val ui = loaded ?: return@Surface
        val unread = unreadKeys(ui, vm, networks)
        val mark = FrameMarks.ofStack(stack, homeRoute, frame.homePage, ui.bar.mapTo(HashSet()) { it.id })
        val marked = focus?.let { Modifier.focusRequester(it.marked) } ?: Modifier
        // The desktop's column, for a pointer: denser rows and a one-line header.
        CompositionLocalProvider(LocalNavCompact provides true) {
            AccountMenu(
                ui = ui,
                routes = routes,
                deckFits = deckFits,
                unread = unread,
                onGo = calls.screen,
                onGroup = calls.group,
                onClose = onClose,
                onAvatar = { avatar = true },
                pages = {
                    itemsIndexed(ui.bar, key = { _, barPage -> "page-" + barPage.id }) { index, barPage ->
                        val selected = barPage.id == mark.page
                        val label = barLabel(barPage, ui)
                        // A network's or a group's page goes on the deck from its menu, or dragged there.
                        val column = deckColumnOfPage(barPage)
                        var menu by remember { mutableStateOf(false) }
                        Box {
                            NavRow(
                                label = label,
                                selected = selected,
                                onClick = { calls.page(index, barPage) },
                                icon = { BarPageIcon(barPage, selected, ui, networks) },
                                modifier = (if (selected) marked else Modifier).then(
                                    if (column == null) Modifier
                                    else Modifier.onSecondaryClick { menu = true }.opensMenuByKey { menu = true }.dragsToDeck(column, label),
                                ),
                            )
                            if (column != null) TimelineRowMenu(menu, column, onDismiss = { menu = false })
                        }
                    }
                },
                current = mark.screen,
                currentGroup = mark.group,
                marked = marked,
            )
        }
    }
    if (avatar) AvatarSheet(onDismiss = { avatar = false })
}

/** Home's model, read from Home's place in the navigation ([home]): the frame shows the bar and the unread marks Home shows. */
@Composable
internal fun frameModel(home: ViewModelStoreOwner): HomeViewModel {
    val graph = LocalGraph.current
    val networks = LocalHomeNetworks.current
    return viewModel(viewModelStoreOwner = home) { HomeViewModel(graph, networks.available, networks.missing) }
}

/**
 * Where F6 puts the keys in the frame's navigation ([FrameRegion]): on the
 * row of where the reader is ([marked]), else on its first control
 * ([whole]); [holds] while something in it has the focus. [page] is the
 * start of the screens' place beside it ([PageStart]), from where Tab from
 * the window's frame goes on to the page's first control.
 */
@Stable
class FrameFocus(private val bar: FocusRequester = FocusRequester()) {
    val marked = FocusRequester()
    val whole = FocusRequester()
    val page = FocusRequester()

    /** The end of the screens' place ([PageEnd]), from where Shift+Tab steps back to the page's last control. */
    val pageEnd = FocusRequester()

    /** The rail's menu button ([FramedRail]), where the keys go back as the menu it opened closes. */
    val menu = FocusRequester()
    var holds = false

    /** Whether the page's start ([PageStart]) takes the focus now: only on the way to the page's first control, never for Tab's own walk. */
    internal var entering = false

    /** On the way back to the page's last control ([toPageEnd]): the page's start is then no stop either. */
    internal var endward = false

    /** A step of the keyboard's focus from where it is ([PageStart] sets it). */
    internal var move: (FocusDirection) -> Boolean = { false }

    /** The keys into the navigation; false where nothing in it took them. */
    fun enter(): Boolean = marked.enters() || whole.enters()

    /**
     * The keys onto the page's first control, the one at its top start (a
     * screen's Back): into the [bar] of a page that puts its bar first
     * ([WideFrame.pageBar], Home's), whose first control stands flush with
     * the page's top edge, where the step down below would pass it by for the
     * first post. Else onto the page's start ([PageStart]), at its top start
     * corner, then down from there to the nearest control; where that finds
     * none in the page, the next in Tab's own order. A plain Enter would pick
     * the start-most control instead (a list's row before the bar above it),
     * and Tab's order has a screen's bar last. The navigation takes no focus
     * meanwhile. False where nothing in the page took the keys.
     */
    fun toPage(): Boolean {
        entering = true
        try {
            return bar.enters() || page.enters() && (move(FocusDirection.Down) || move(FocusDirection.Next))
        } finally {
            entering = false
        }
    }

    /**
     * The keys onto the page's last control: onto the page's end ([PageEnd]),
     * then one step back. Where nothing in the page takes them, into the
     * navigation, never left on the end, which shows nothing.
     */
    fun toPageEnd(): Boolean {
        entering = true
        endward = true
        val moved = try {
            pageEnd.enters() && move(FocusDirection.Previous)
        } finally {
            entering = false
            endward = false
        }
        return moved || enter()
    }

    val region = FrameRegion(enter = ::enter, holds = { holds }, page = ::toPage, pageEnd = ::toPageEnd)
}

/** Whether the focus went into what this is on, false too where it is on nothing (not composed). */
private fun FocusRequester.enters(): Boolean = runCatching { requestFocus(FocusDirection.Enter) }.getOrDefault(false)

/**
 * The frame's navigation as one place for the keys ([focus]): F6 reaches it
 * whole, and while something is [covered] by a media viewer over the whole
 * window, Tab passes it by, as nothing in it can be seen; nor does the
 * keyboard's way into the page ([FrameFocus.toPage]) stop in it.
 */
fun Modifier.frameNavigation(focus: FrameFocus, covered: Boolean): Modifier =
    onFocusChanged { focus.holds = it.hasFocus }
        .focusRequester(focus.whole)
        .focusProperties { onEnter = { if (covered || focus.entering) cancelFocusChange() } }
        .focusGroup()

/**
 * The start of the screens' place beside the frame's navigation, put first
 * in it: Tab from the window's frame comes here ([FrameFocus.toPage]) and
 * steps on at once to the page's first control. It takes no focus otherwise
 * and shows nothing.
 */
@Composable
fun PageStart(focus: FrameFocus) {
    val manager = LocalFocusManager.current
    SideEffect { focus.move = manager::moveFocus }
    Box(Modifier.size(1.dp).focusRequester(focus.page).focusProperties { canFocus = focus.entering && !focus.endward }.focusable())
}

/**
 * The end of the screens' place, put last in it: Shift+Tab from the
 * navigation's first control comes here ([FrameFocus.toPageEnd]) and steps
 * back at once to the page's last control. It takes no focus otherwise and
 * shows nothing.
 */
@Composable
fun PageEnd(focus: FrameFocus) {
    Box(Modifier.size(1.dp).focusRequester(focus.pageEnd).focusProperties { canFocus = focus.entering }.focusable())
}

/**
 * Home in the wide window's frame: tells the frame the bar's page shown and
 * how it turns to one of its pages, for as long as Home is composed.
 */
@Composable
internal fun FollowedByFrame(frame: WideFrame, page: BarPage, tap: (Int) -> Unit) {
    SideEffect { frame.homePage = page.id }
    val latest by rememberUpdatedState(tap)
    DisposableEffect(frame) {
        val own: (Int) -> Unit = { latest(it) }
        frame.homeTap = own
        onDispose { if (frame.homeTap === own) frame.homeTap = null }
    }
}
