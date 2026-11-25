package com.aimdi.xtanative.ui.home

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aimdi.xtanative.core.backup.FollowGroup
import com.aimdi.xtanative.data.LocalGraph
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
     * opened from the search leaves Search marked.
     */
    fun ofStack(stack: List<FrameRoute>, home: String, homePage: String?, bar: Set<String>): FrameMark =
        stack.asReversed().asSequence().map { of(it.route, home, homePage, it.group, bar) }.firstOrNull { it != FrameMark() } ?: FrameMark()
}

/** What the column does when a row is picked: Home's page at an index of its bar, a menu entry's route, a group. */
class FrameCalls(val page: (Int, BarPage) -> Unit, val screen: (String) -> Unit, val group: (FollowGroup) -> Unit)

/** A screen in the navigation: its route pattern, and the group it shows ([group], its id) on a group's screen. */
data class FrameRoute(val route: String?, val group: String? = null)

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
    Surface(modifier.width(HomeWide.SIDEBAR_WIDTH.dp).fillMaxHeight(), color = MaterialTheme.colorScheme.background) {
        val ui = loaded ?: return@Surface
        val unread = unreadKeys(ui, vm, networks)
        val mark = FrameMarks.ofStack(stack, homeRoute, frame.homePage, ui.bar.mapTo(HashSet()) { it.id })
        val marked = focus?.let { Modifier.focusRequester(it.marked) } ?: Modifier
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
                    NavRow(
                        label = barLabel(barPage, ui),
                        selected = selected,
                        onClick = { calls.page(index, barPage) },
                        icon = { BarPageIcon(barPage, selected, ui, networks) },
                        modifier = if (selected) marked else Modifier,
                    )
                }
            },
            current = mark.screen,
            currentGroup = mark.group,
            marked = marked,
        )
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
 * ([whole]); [holds] while something in it has the focus.
 */
@Stable
class FrameFocus {
    val marked = FocusRequester()
    val whole = FocusRequester()
    var holds = false

    /** The keys into the navigation; false where nothing in it took them. */
    fun enter(): Boolean = runCatching { marked.requestFocus() }.isSuccess || runCatching { whole.requestFocus() }.isSuccess

    val region = FrameRegion(enter = ::enter, holds = { holds })
}

/**
 * The frame's navigation as one place for the keys ([focus]): F6 reaches it
 * whole, and while something is [covered] by a media viewer over the whole
 * window, Tab passes it by, as nothing in it can be seen.
 */
fun Modifier.frameNavigation(focus: FrameFocus, covered: Boolean): Modifier =
    onFocusChanged { focus.holds = it.hasFocus }
        .focusRequester(focus.whole)
        .focusProperties { onEnter = { if (covered) cancelFocusChange() } }
        .focusGroup()

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
