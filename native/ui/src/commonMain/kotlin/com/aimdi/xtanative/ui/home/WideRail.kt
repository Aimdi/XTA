package com.aimdi.xtanative.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aimdi.xtanative.ui.common.BackHandling
import com.aimdi.xtanative.ui.common.IconTip
import com.aimdi.xtanative.ui.common.TipSide
import com.aimdi.xtanative.ui.common.LocalReducedMotion
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.home_open_menu
import com.aimdi.xtanative.ui.generated.resources.keys_menu
import org.jetbrains.compose.resources.stringResource

/**
 * The rail of a desktop window too narrow for the column ([FrameKind.RAIL]):
 * Home's pages as icons down the start edge, named by a tip, the one where
 * the reader is marked as the column marks its row ([NavRowLook]), and above
 * them the menu, which opens the whole account menu over the window
 * ([onMenu]) and is marked while a screen of it is shown; at its foot
 * Search and Settings, where the app has them ([routes]). It reads Home's
 * model from Home's place in the navigation ([home]), as the column does.
 */
@Composable
fun FramedRail(
    home: ViewModelStoreOwner,
    frame: WideFrame,
    stack: List<FrameRoute>,
    homeRoute: String,
    routes: HomeRoutes,
    calls: FrameCalls,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
    focus: FrameFocus? = null,
) {
    val vm = frameModel(home)
    val networks = LocalHomeNetworks.current
    val loaded by vm.ui.collectAsStateWithLifecycle()
    Surface(modifier.width(HomeWide.RAIL_WIDTH.dp).fillMaxHeight(), color = MaterialTheme.colorScheme.background) {
        val ui = loaded ?: return@Surface
        val mark = FrameMarks.ofStack(stack, homeRoute, frame.homePage, ui.bar.mapTo(HashSet()) { it.id })
        val marked = focus?.let { Modifier.focusRequester(it.marked) } ?: Modifier
        val pinned = HomeMenu.shown(HomeMenu.buttons, routes.exists, deckFits = false)
        Column(Modifier.fillMaxSize().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val inMenu = RailMarks.inMenu(mark, pinned)
                val button = focus?.let { Modifier.focusRequester(it.menu) } ?: Modifier
                RailItem(stringResource(Res.string.home_open_menu), selected = inMenu, onClick = onMenu, modifier = button.then(if (inMenu) marked else Modifier)) {
                    Icon(Icons.Filled.Menu, contentDescription = null)
                }
                Spacer(Modifier.height(8.dp))
                ui.bar.forEachIndexed { index, page ->
                    val selected = page.id == mark.page
                    RailItem(barLabel(page, ui), selected, onClick = { calls.page(index, page) }, modifier = if (selected) marked else Modifier) {
                        BarPageIcon(page, selected, ui, networks)
                    }
                }
            }
            // Search and Settings pinned at the foot, as a desktop app's rail keeps them, a click away without the menu.
            Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                pinned.forEach { destination ->
                    val selected = destination.route == mark.screen
                    RailItem(menuLabel(destination), selected, onClick = { calls.screen(destination.route) }, modifier = if (selected) marked else Modifier) {
                        Icon(menuIcon(destination, selected), contentDescription = null)
                    }
                }
            }
        }
    }
}

/** What the rail marks beside the pages: its menu button while a screen of the menu is shown, but not one pinned at the rail's foot. */
internal object RailMarks {
    fun inMenu(mark: FrameMark, pinned: List<HomeDestination>): Boolean =
        mark.group != null || (mark.screen != null && pinned.none { it.route == mark.screen })
}

/**
 * A place on the rail: [icon] on a pill ([label] its tip, at its end, and
 * what a screen reader says; the tip draws the keyboard's ring). The pointer
 * washes it as the column's rows are ([NavRowLook]); the place shown is lit
 * in the accent, so it never passes for the one under the pointer, on a dark
 * page too.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RailItem(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, icon: @Composable () -> Unit) {
    val interactions = remember { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()
    val colors = MaterialTheme.colorScheme
    val dark = colors.background.luminance() < 0.5f
    val fill = if (selected) colors.primary.copy(alpha = RailLook.selectedAlpha(hovered)) else colors.onSurface.copy(alpha = NavRowLook.fillAlpha(false, hovered, dark))
    CompositionLocalProvider(LocalRippleConfiguration provides PressOnly, LocalContentColor provides if (selected) colors.primary else colors.onSurface) {
        IconTip(label, TipSide.END) {
            Box(
                modifier
                    .size(width = RAIL_ITEM_WIDTH, height = RAIL_ITEM_HEIGHT)
                    .clip(CircleShape)
                    .background(fill)
                    .selectable(selected, interactionSource = interactions, indication = ripple(), role = Role.Tab, onClick = onClick)
                    .semantics { contentDescription = label },
                contentAlignment = Alignment.Center,
            ) { icon() }
        }
    }
}

/** How strongly the accent lights the rail's place shown: clearly more than the pointer's wash, a little more under the pointer. */
internal object RailLook {
    const val SELECTED_ALPHA = 0.20f
    const val SELECTED_HOVER_ALPHA = 0.26f

    fun selectedAlpha(hovered: Boolean): Float = if (hovered) SELECTED_HOVER_ALPHA else SELECTED_ALPHA
}

private val RAIL_ITEM_WIDTH = 64.dp
private val RAIL_ITEM_HEIGHT = 48.dp

/**
 * The whole account menu ([content]) over the window from the rail, while
 * [open]: it slides in from the start edge over a scrim, as a drawer does,
 * edged with a line where the page is as dark as its shadow (Lights out);
 * a click on the scrim, Esc and the back keys close it ([onClose]). Where
 * motion is reduced it shows and goes at once. Open, it holds the keys as a
 * dialog does ([HoldsKeys]): they go into it, its own Esc closes it, F6
 * and the window's keys wait, and a screen reader hears it as a pane it can
 * dismiss.
 */
@Composable
fun RailMenu(open: Boolean, onClose: () -> Unit, content: @Composable () -> Unit) {
    val reduced = LocalReducedMotion.current
    BackHandling(enabled = open, onBack = onClose)
    AnimatedVisibility(open, enter = fadeIn(if (reduced) snap() else tween(MENU_MS)), exit = fadeOut(if (reduced) snap() else tween(MENU_MS))) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = SCRIM_ALPHA)).pointerInput(onClose) { detectTapGestures { onClose() } })
    }
    AnimatedVisibility(
        open,
        enter = slideInHorizontally(if (reduced) snap() else tween(MENU_MS)) { -it },
        exit = slideOutHorizontally(if (reduced) snap() else tween(MENU_MS)) { -it },
    ) {
        val focus = remember { FocusRequester() }
        HoldsKeys(focus)
        // Its rows are laid out a frame or two after it is composed: the keys go in once one can take them.
        LaunchedEffect(focus) {
            repeat(FOCUS_TRIES) {
                withFrameNanos { }
                if (runCatching { focus.requestFocus(FocusDirection.Enter) }.getOrDefault(false)) return@LaunchedEffect
            }
        }
        val title = stringResource(Res.string.keys_menu)
        Surface(
            Modifier
                .fillMaxHeight()
                .onPreviewKeyEvent { event -> (event.key == Key.Escape && event.type == KeyEventType.KeyDown).also { if (it) onClose() } }
                .semantics {
                    paneTitle = title
                    dismiss { onClose(); true }
                }
                .focusRequester(focus)
                .focusGroup(),
            shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp),
            color = MaterialTheme.colorScheme.background,
            shadowElevation = 6.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            content()
        }
    }
}

private const val FOCUS_TRIES = 5
private const val MENU_MS = 200
private const val SCRIM_ALPHA = 0.32f
