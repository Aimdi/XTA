package com.aimdi.xtanative.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import androidx.savedstate.read
import com.aimdi.xtanative.ui.antenna.AntennaFeedScreen
import com.aimdi.xtanative.ui.antenna.AntennasScreen
import com.aimdi.xtanative.ui.common.LocalGraph
import com.aimdi.xtanative.ui.community.CommunityMembersScreen
import com.aimdi.xtanative.ui.community.CommunityScreen
import com.aimdi.xtanative.ui.feed.FeedScreen
import com.aimdi.xtanative.ui.importer.ImportScreen
import com.aimdi.xtanative.ui.lists.ListImportScreen
import com.aimdi.xtanative.ui.media.MediaViewer
import com.aimdi.xtanative.ui.post.PostActions
import com.aimdi.xtanative.ui.post.PostScreen
import com.aimdi.xtanative.ui.profile.ProfileScreen
import com.aimdi.xtanative.ui.search.SearchActions
import com.aimdi.xtanative.ui.search.SearchScreen
import com.aimdi.xtanative.ui.search.SearchTab
import com.aimdi.xtanative.ui.settings.SettingsScreen
import com.aimdi.xtanative.ui.trends.TrendsActions
import com.aimdi.xtanative.ui.trends.TrendsScreen
import java.net.URLEncoder

private object Routes {
    const val ROOT = "root"
    const val IMPORT = "import"
    const val FEED = "feed"
    const val POST = "post/{id}"
    const val PROFILE = "profile/{screenName}"
    const val MEDIA = "media/{postId}/{index}"
    const val SEARCH = "search?q={q}&tab={tab}"
    const val SETTINGS = "settings"
    const val TRENDS = "trends"
    const val COMMUNITY = "community/{id}"
    const val COMMUNITY_ABOUT = "community/{id}/about"
    const val COMMUNITY_MEMBERS = "community/{id}/members"
    const val LIST = "list/{id}"
    const val ANTENNAS = "antennas"
    const val ANTENNA = "antenna/{id}"

    fun post(id: String) = "post/$id"
    fun profile(screenName: String) = "profile/" + encoded(screenName)
    fun media(postId: String, index: Int) = "media/$postId/$index"
    fun search(query: String?) = search(query, null)

    /** The search of [query] on [tab] (a [SearchTab.routeName]); either may be left out. */
    fun search(query: String?, tab: String?): String {
        val params = listOfNotNull(query?.takeIf { it.isNotBlank() }?.let { "q=" + encoded(it) }, tab?.let { "tab=$it" })
        return if (params.isEmpty()) "search" else "search?" + params.joinToString("&")
    }
    fun community(id: String) = "community/" + encoded(id)
    fun communityMembers(id: String) = "community/" + encoded(id) + "/members"
    fun antenna(id: String) = "antenna/" + encoded(id)

    /** [text] escaped for a route, which is matched as a URI: a space as %20, which every platform decodes. */
    private fun encoded(text: String): String = URLEncoder.encode(text, "UTF-8").replace("+", "%20")
}

private const val SLIDE_MS = 280

/** The app's screens. The first is the import until XTA's backup has given it an account. */
@Composable
fun XtaNativeRoot() {
    val graph = LocalGraph.current
    val session by graph.sessions.state.collectAsStateWithLifecycle()
    val loaded = session
    if (loaded == null) {
        // The session is read in a few milliseconds; until then, the theme's background.
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        return
    }
    val start = remember { if (loaded.accounts.isEmpty()) Routes.IMPORT else Routes.FEED }
    val nav = rememberNavController()
    val actions = remember(nav) {
        PostActions(
            openPost = { id -> nav.navigate(Routes.post(id)) },
            openProfile = { name -> nav.navigate(Routes.profile(name)) },
            openMedia = { postId, index -> nav.navigate(Routes.media(postId, index)) },
            search = { query -> nav.navigate(Routes.search(query)) },
        )
    }
    val searchActions = remember(nav) {
        SearchActions(openCommunity = { id -> nav.navigate(Routes.community(id)) }, openAntennas = { nav.navigate(Routes.ANTENNAS) })
    }
    val trendsActions = remember(nav) {
        TrendsActions(
            openSearch = { query -> nav.navigate(Routes.search(query)) },
            openPeople = { nav.navigate(Routes.search(null, SearchTab.PEOPLE.routeName)) },
            openAntennas = { nav.navigate(Routes.ANTENNAS) },
        )
    }

    NavHost(
        navController = nav,
        startDestination = start,
        route = Routes.ROOT,
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(SLIDE_MS)) },
        exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(SLIDE_MS)) { it / 4 } },
        popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(SLIDE_MS)) { it / 4 } },
        popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(SLIDE_MS)) },
    ) {
        composable(Routes.IMPORT) {
            ImportScreen(onImported = { nav.startOver(Routes.FEED) })
        }
        composable(Routes.FEED) {
            FeedScreen(actions, openSearch = { nav.navigate(Routes.search(null)) }, openSettings = { nav.navigate(Routes.SETTINGS) })
        }
        composable(
            Routes.POST,
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
            deepLinks = xLinks(".*/status/{id}"),
        ) { entry ->
            PostScreen(entry.text("id").orEmpty(), actions, onBack = { nav.popBackStack() })
        }
        composable(
            Routes.PROFILE,
            arguments = listOf(navArgument("screenName") { type = NavType.StringType }),
            deepLinks = xLinks("{screenName}"),
        ) { entry ->
            ProfileScreen(entry.text("screenName").orEmpty(), actions, onBack = { nav.popBackStack() })
        }
        composable(
            Routes.MEDIA,
            arguments = listOf(navArgument("postId") { type = NavType.StringType }, navArgument("index") { type = NavType.IntType }),
            enterTransition = { fadeIn(tween(180)) },
            exitTransition = { fadeOut(tween(180)) },
            popEnterTransition = { fadeIn(tween(180)) },
            popExitTransition = { fadeOut(tween(180)) },
        ) { entry ->
            MediaViewer(entry.text("postId").orEmpty(), entry.arguments?.read { getIntOrNull("index") } ?: 0, onClose = { nav.popBackStack() })
        }
        composable(
            Routes.SEARCH,
            arguments = listOf(
                navArgument("q") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("tab") { type = NavType.StringType; nullable = true; defaultValue = null },
            ),
            deepLinks = xLinks("search?q={q}"),
        ) { entry ->
            SearchScreen(entry.text("q"), SearchTab.of(entry.text("tab")), actions, searchActions, onBack = { nav.popBackStack() })
        }
        composable(Routes.TRENDS) {
            TrendsScreen(trendsActions, onBack = { nav.popBackStack() })
        }
        composable(Routes.COMMUNITY, arguments = listOf(navArgument("id") { type = NavType.StringType }), deepLinks = xLinks("i/communities/{id}")) { entry ->
            CommunityScreen(entry.text("id").orEmpty(), about = false, actions, openMembers = { id -> nav.navigate(Routes.communityMembers(id)) }, onBack = { nav.popBackStack() })
        }
        composable(Routes.COMMUNITY_ABOUT, arguments = listOf(navArgument("id") { type = NavType.StringType }), deepLinks = xLinks("i/communities/{id}/about")) { entry ->
            CommunityScreen(entry.text("id").orEmpty(), about = true, actions, openMembers = { id -> nav.navigate(Routes.communityMembers(id)) }, onBack = { nav.popBackStack() })
        }
        composable(Routes.COMMUNITY_MEMBERS, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
            CommunityMembersScreen(entry.text("id").orEmpty(), actions, onBack = { nav.popBackStack() })
        }
        composable(Routes.LIST, arguments = listOf(navArgument("id") { type = NavType.StringType }), deepLinks = xLinks("i/lists/{id}")) { entry ->
            ListImportScreen(entry.text("id"), onBack = { nav.popBackStack() })
        }
        composable(Routes.ANTENNAS) {
            AntennasScreen(openAntenna = { id -> nav.navigate(Routes.antenna(id)) }, onBack = { nav.popBackStack() })
        }
        composable(Routes.ANTENNA, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
            AntennaFeedScreen(entry.text("id").orEmpty(), actions, onBack = { nav.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { nav.popBackStack() },
                onImport = { nav.navigate(Routes.IMPORT) },
                onCleared = { nav.startOver(Routes.IMPORT) },
            )
        }
    }
}

/** An x.com or twitter.com link opens here, as it opens XTA. Only Android is handed links. */
private fun xLinks(path: String) =
    listOf("x.com", "www.x.com", "twitter.com", "www.twitter.com", "mobile.twitter.com", "mobile.x.com").map { host ->
        navDeepLink { uriPattern = "https://$host/$path" }
    }

/** A route's text argument; null where the route has none. */
private fun NavBackStackEntry.text(name: String): String? = arguments?.read { getStringOrNull(name) }

/** To [route] with nothing to go back to: the feed after an import, the import after removing everything. */
private fun NavHostController.startOver(route: String) {
    navigate(route) { popUpTo(Routes.ROOT) { inclusive = true } }
}
