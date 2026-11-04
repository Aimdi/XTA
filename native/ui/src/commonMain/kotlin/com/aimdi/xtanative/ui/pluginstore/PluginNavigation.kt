package com.aimdi.xtanative.ui.pluginstore

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.savedstate.read
import com.aimdi.xtanative.data.plugins.AppPlugins
import com.aimdi.xtanative.data.plugins.Destination
import com.aimdi.xtanative.data.plugins.NavTarget
import com.aimdi.xtanative.data.plugins.PluginSheet
import com.aimdi.xtanative.data.plugins.RunningPlugin
import com.aimdi.xtanative.ui.home.LocalHomeNetworks
import com.aimdi.xtanative.ui.links.LinkOpener
import com.aimdi.xtanative.ui.links.LinkScreens
import com.aimdi.xtanative.ui.links.LocalLinks
import com.aimdi.xtanative.ui.plugins.MediaPager
import com.aimdi.xtanative.ui.plugins.XtaPlugin
import com.aimdi.xtanative.ui.plugins.currentLanguage
import com.aimdi.xtanative.ui.post.LocalCashtagOpener
import com.aimdi.xtanative.ui.post.LocalCashtagSheet
import com.aimdi.xtanative.ui.post.LocalCashtagStrip
import com.aimdi.xtanative.ui.post.LocalContractNetworks
import com.aimdi.xtanative.ui.settings.SettingsRoutes
import java.net.URLEncoder
import kotlinx.coroutines.flow.first

/** The routes of the plugin store, and of each plugin's client, settings, search and own screens, and the media a plugin opens. */
internal object PluginRoutes {
    /** What Settings' Plugin store row and the account menu open. */
    const val STORE = SettingsRoutes.PLUGINS
    const val CLIENT = "plugin/{pluginId}"
    const val SETTINGS = "plugin/{pluginId}/settings"
    const val SEARCH = "plugin/{pluginId}/search?q={q}"
    const val SCREEN = "plugin/{pluginId}/screen?route={route}"
    const val MEDIA = "plugin-media/{key}/{index}"

    fun client(id: String) = "plugin/$id"
    fun settings(id: String) = "plugin/$id/settings"
    fun search(id: String, query: String?) = if (query.isNullOrBlank()) "plugin/$id/search" else "plugin/$id/search?q=" + encoded(query)
    fun screen(id: String, route: String) = "plugin/$id/screen?route=" + encoded(route)
    fun media(key: String, index: Int) = "plugin-media/" + encoded(key) + "/$index"

    /** [text] escaped for a route, which is matched as a URI: a space as %20, which every platform decodes. */
    private fun encoded(text: String): String = URLEncoder.encode(text, "UTF-8").replace("+", "%20")
}

/** The app's plugins, for the screens: the shell makes them beside the graph (see [PluginsFrame]). */
val LocalPlugins = staticCompositionLocalOf<AppPlugins> { error("No AppPlugins provided") }

/**
 * [plugins] for everything inside [content], their home tabs on Home's
 * strip ([LocalHomeNetworks]), named in the reader's language, what an X
 * post's cashtags do with them ([PluginCashtags]), a long press on one
 * opening its sheet ([PluginSheet.Cashtag]), and the networks they name for
 * the contract addresses in its text ([LocalContractNetworks]).
 */
@Composable
fun PluginsFrame(plugins: AppPlugins, content: @Composable () -> Unit) {
    val language = currentLanguage()
    LaunchedEffect(plugins, language) { plugins.useLanguage(language) }
    val networks = remember(plugins) { PluginHomeNetworks(plugins) }
    val running by plugins.running.collectAsState()
    val cashtags = remember(plugins, running) { PluginCashtags(plugins, running) }
    val cashtagSheet = remember(plugins) { { symbol: String -> plugins.show(PluginSheet.Cashtag(symbol)) } }
    val contracts by plugins.contractNetworks.collectAsState()
    CompositionLocalProvider(
        LocalPlugins provides plugins,
        LocalHomeNetworks provides networks,
        LocalCashtagStrip provides cashtags.strip,
        LocalCashtagOpener provides cashtags.opener,
        LocalCashtagSheet provides cashtagSheet,
        LocalContractNetworks provides contracts,
        content = content,
    )
}

/**
 * What the running plugins do with an X post's cashtags: the first price
 * strip one draws under the post ([XtaPlugin.cashtagStrip]), and the first
 * screen one has for a tapped cashtag ([XtaPlugin.routeForCashtag]). With
 * none, a post has no strip and a cashtag searches X, as without plugins.
 */
private class PluginCashtags(private val plugins: AppPlugins, private val running: List<RunningPlugin>) {
    val strip: (@Composable (List<String>) -> Unit)? = running.firstNotNullOfOrNull { guarded { it.plugin.cashtagStrip } }

    val opener: ((String) -> Boolean)? = if (running.isEmpty()) null else ::open

    private fun open(symbol: String): Boolean {
        val destination = plugins.destinationForCashtag(symbol, running) ?: return false
        plugins.navigator.go(destination)
        return true
    }

    /** A plugin's answer; null when its code fails, or was built against another API. */
    private fun <T> guarded(ask: () -> T): T? = try {
        ask()
    } catch (e: Exception) {
        null
    } catch (e: LinkageError) {
        null
    }
}

/**
 * Hooks the app's navigation [nav] up for the plugins: where a plugin asks
 * to go is opened there, X's screens through [x], a link the way the app
 * opens links, the Downloads screen at its route [downloads]; and the screen
 * the shell asked to open at start is opened once.
 */
@Composable
fun AttachPluginNavigation(nav: NavHostController, x: LinkScreens, downloads: String) {
    val plugins = LocalPlugins.current
    val links = LocalLinks.current
    val uri = LocalUriHandler.current
    val target = remember(nav, x, links, uri, downloads) { PluginNavTarget(nav, x, links, uri, downloads) }
    DisposableEffect(plugins, target) {
        plugins.navigator.attach(target)
        onDispose { plugins.navigator.detach(target) }
    }
    LaunchedEffect(plugins, target) {
        val start = plugins.openAtStart ?: return@LaunchedEffect
        // Once the navigation has its graph and its first screen.
        nav.currentBackStackEntryFlow.first()
        plugins.openAtStart = null
        target.go(start)
    }
}

/** The plugin store and each plugin's screens, in the app's navigation graph. */
fun NavGraphBuilder.pluginScreens(
    nav: NavHostController,
    mediaEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = { fadeIn(tween(180)) },
    mediaExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = { fadeOut(tween(180)) },
) {
    val pluginId = navArgument("pluginId") { type = NavType.StringType }
    composable(PluginRoutes.STORE) {
        PluginStoreScreen(
            onBack = { nav.popBackStack() },
            onOpen = { id -> nav.navigate(PluginRoutes.client(id)) },
            onSettings = { id -> nav.navigate(PluginRoutes.settings(id)) },
        )
    }
    composable(PluginRoutes.CLIENT, arguments = listOf(pluginId)) { entry ->
        val id = entry.text("pluginId").orEmpty()
        PluginClientScreen(
            id,
            onBack = { nav.popBackStack() },
            onSearch = { nav.navigate(PluginRoutes.search(id, null)) },
            onSettings = { nav.navigate(PluginRoutes.settings(id)) },
        )
    }
    composable(PluginRoutes.SETTINGS, arguments = listOf(pluginId)) { entry ->
        PluginSettingsScreen(entry.text("pluginId").orEmpty(), onBack = { nav.popBackStack() })
    }
    composable(
        PluginRoutes.SEARCH,
        arguments = listOf(pluginId, navArgument("q") { type = NavType.StringType; nullable = true; defaultValue = null }),
    ) { entry ->
        PluginSearchScreen(entry.text("pluginId").orEmpty(), entry.text("q"), onBack = { nav.popBackStack() })
    }
    composable(
        PluginRoutes.SCREEN,
        arguments = listOf(pluginId, navArgument("route") { type = NavType.StringType; nullable = true; defaultValue = null }),
    ) { entry ->
        PluginRouteScreen(entry.text("pluginId").orEmpty(), entry.text("route").orEmpty(), onBack = { nav.popBackStack() })
    }
    composable(
        PluginRoutes.MEDIA,
        arguments = listOf(navArgument("key") { type = NavType.StringType }, navArgument("index") { type = NavType.IntType }),
        enterTransition = mediaEnter,
        exitTransition = mediaExit,
        popEnterTransition = mediaEnter,
        popExitTransition = mediaExit,
    ) { entry ->
        val plugins = LocalPlugins.current
        val key = entry.text("key").orEmpty()
        val shelved = remember(key) { plugins.media.get(key) }
        val openPost = shelved?.openPost
        MediaPager(
            shelved?.items.orEmpty(),
            entry.arguments?.read { getIntOrNull("index") } ?: 0,
            onClose = { nav.popBackStack() },
            onDownload = shelved?.let { media -> { item -> plugins.download(media.source, item) } },
            onOpenPost = openPost?.let { open -> { runCatching { open() } } },
        )
    }
}

/** Where the plugins' navigation lands: the app's navigation controller, its X screens and its way of opening links. */
private class PluginNavTarget(
    private val nav: NavHostController,
    private val x: LinkScreens,
    private val links: LinkOpener?,
    private val uri: UriHandler,
    private val downloads: String,
) : NavTarget {
    override fun go(destination: Destination) {
        when (destination) {
            is Destination.Link -> links?.open(destination.url) ?: runCatching { uri.openUri(destination.url) }
            is Destination.XPost -> x.post(destination.id)
            is Destination.XProfile -> x.profile(destination.screenName)
            is Destination.XSearch -> x.search(destination.query)
            Destination.PluginStore -> nav.navigate(PluginRoutes.STORE)
            is Destination.PluginClient -> nav.navigate(PluginRoutes.client(destination.pluginId))
            is Destination.PluginRoute -> nav.navigate(PluginRoutes.screen(destination.pluginId, destination.route))
            is Destination.PluginSearch -> nav.navigate(PluginRoutes.search(destination.pluginId, destination.query))
            is Destination.PluginSettings -> nav.navigate(PluginRoutes.settings(destination.pluginId))
            is Destination.Media -> nav.navigate(PluginRoutes.media(destination.key, destination.start))
            Destination.Downloads -> nav.navigate(downloads)
            Destination.Back -> nav.popBackStack()
        }
    }
}

/** A route's text argument; null where the route has none. */
private fun NavBackStackEntry.text(name: String): String? = arguments?.read { getStringOrNull(name) }
