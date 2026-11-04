package com.aimdi.xtanative.ui.home

import java.net.URLEncoder

/**
 * Where the account menu leads, by the name of each screen's route: XTA's
 * drawer buttons, its bar's pages, and the screens it reaches from them.
 */
enum class HomeDestination(val route: String) {
    SEARCH("search"),
    SETTINGS("settings"),
    SUBSCRIPTIONS("subscriptions"),
    TRENDS("trends"),
    SAVED("saved"),
    DOWNLOADS("downloads"),
    ANTENNAS("antennas"),
    PLUGINS("plugins"),
    DECK("deck"),
}

/** The account menu's entries, in one place. */
object HomeMenu {
    /** The two buttons under the account, as XTA's drawer has them. */
    val buttons: List<HomeDestination> = listOf(HomeDestination.SEARCH, HomeDestination.SETTINGS)

    /** The list under them: XTA's bar pages in its order, then the screens beside them. */
    val list: List<HomeDestination> = listOf(
        HomeDestination.SUBSCRIPTIONS,
        HomeDestination.TRENDS,
        HomeDestination.SAVED,
        HomeDestination.DOWNLOADS,
        HomeDestination.ANTENNAS,
        HomeDestination.PLUGINS,
        HomeDestination.DECK,
    )

    /**
     * The entries of [entries] the menu shows: those whose screen the app has
     * ([exists] asks its navigation), the deck only where it fits the window.
     */
    fun shown(entries: List<HomeDestination>, exists: (String) -> Boolean, deckFits: Boolean): List<HomeDestination> =
        entries.filter { exists(it.route) && (it != HomeDestination.DECK || deckFits) }
}

/**
 * The screens Home leads to, by route: [exists] says whether the app has one
 * by that name yet (screens arrive one by one), [open] goes there.
 */
class HomeRoutes(val exists: (String) -> Boolean, val open: (String) -> Unit) {
    /** Opens [route] when the app has it; false when it does not. */
    fun openIfExists(route: String): Boolean = exists(route).also { if (it) open(route) }

    /** A group's own screen, once the app has one; false until then. */
    fun openGroup(groupId: String): Boolean = exists(GROUP).also { if (it) open(group(groupId)) }

    companion object {
        /** The group screen's route pattern. */
        const val GROUP = "group/{id}"
        const val IMPORT = "import"

        fun group(groupId: String): String = "group/" + URLEncoder.encode(groupId, "UTF-8").replace("+", "%20")
    }
}
