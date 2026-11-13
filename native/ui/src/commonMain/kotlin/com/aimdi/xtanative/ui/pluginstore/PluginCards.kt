package com.aimdi.xtanative.ui.pluginstore

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import com.aimdi.xtanative.ui.chrome.CardBacks
import com.aimdi.xtanative.ui.chrome.SwipeBackCard
import com.aimdi.xtanative.ui.plugins.XtaPlugin
import kotlinx.coroutines.CoroutineScope

/**
 * A plugin's opened posts as the app opens its own (XTA's
 * card_page_route.dart): a screen the plugin says opens as a card
 * ([XtaPlugin.opensAsCard]) slides in over a picture of the page it came
 * from and is dragged, thrown or sent back as a card. [backs] are the
 * app's pictures, taken in [scope] before the card is opened.
 */
class PluginCards(internal val backs: CardBacks, private val scope: CoroutineScope) {
    internal val sliding = SlidingBacks()

    /** Pictures the screens, then [navigate]s to the card. */
    internal fun open(navigate: () -> Unit) = backs.open(scope, navigate)

    companion object {
        /** The route of a plugin's card, for the app's transitions. */
        const val ROUTE = PluginRoutes.CARD
    }
}

/** The back of each card on screen, by its screen's id: what a plugin's own back runs while its card is on top, so the card slides out. */
internal class SlidingBacks {
    private val backs = HashMap<String, () -> Unit>()

    fun put(id: String, back: () -> Unit) {
        backs[id] = back
    }

    /** Forgets [id]'s back, unless a newer one took its place. */
    fun remove(id: String, back: () -> Unit) {
        if (backs[id] === back) backs.remove(id)
    }

    /** The back of the card whose screen is [id]; null when that screen is no card. */
    fun of(id: String?): (() -> Unit)? = id?.let(backs::get)
}

/** Whether [plugin] opens its screen named [route] as a card; not when its code fails. */
internal fun opensAsCard(plugin: XtaPlugin?, route: String): Boolean = try {
    plugin?.opensAsCard(route) == true
} catch (e: Exception) {
    false
} catch (e: LinkageError) {
    false
}

/** The screen of plugin [id] named [route], as a card over the page it was opened from. */
@Composable
internal fun PluginCardScreen(cards: PluginCards, nav: NavHostController, entry: NavBackStackEntry, id: String, route: String) {
    val canLeave = remember(entry.id) { nav.previousBackStackEntry != null }
    SwipeBackCard(cards.backs.claim(entry), canLeave, leave = { if (nav.currentBackStackEntry?.id == entry.id) nav.popBackStack() }) { back ->
        DisposableEffect(cards, entry.id, back) {
            cards.sliding.put(entry.id, back)
            onDispose { cards.sliding.remove(entry.id, back) }
        }
        PluginRouteScreen(id, route, onBack = back)
    }
}
