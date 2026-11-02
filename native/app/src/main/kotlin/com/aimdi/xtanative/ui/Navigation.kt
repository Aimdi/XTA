package com.aimdi.xtanative.ui

import android.net.Uri
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
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.aimdi.xtanative.ui.common.LocalGraph
import com.aimdi.xtanative.ui.feed.FeedScreen
import com.aimdi.xtanative.ui.importer.ImportScreen
import com.aimdi.xtanative.ui.media.MediaViewer
import com.aimdi.xtanative.ui.post.PostActions
import com.aimdi.xtanative.ui.post.PostScreen
import com.aimdi.xtanative.ui.profile.ProfileScreen
import com.aimdi.xtanative.ui.search.SearchScreen
import com.aimdi.xtanative.ui.settings.SettingsScreen

private object Routes {
    const val IMPORT = "import"
    const val FEED = "feed"
    const val POST = "post/{id}"
    const val PROFILE = "profile/{screenName}"
    const val MEDIA = "media/{postId}/{index}"
    const val SEARCH = "search?q={q}"
    const val SETTINGS = "settings"

    fun post(id: String) = "post/$id"
    fun profile(screenName: String) = "profile/" + Uri.encode(screenName)
    fun media(postId: String, index: Int) = "media/$postId/$index"
    fun search(query: String?) = if (query.isNullOrBlank()) "search" else "search?q=" + Uri.encode(query)
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

    NavHost(
        navController = nav,
        startDestination = start,
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(SLIDE_MS)) },
        exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(SLIDE_MS)) { it / 4 } },
        popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(SLIDE_MS)) { it / 4 } },
        popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(SLIDE_MS)) },
    ) {
        composable(Routes.IMPORT) {
            ImportScreen(onImported = { nav.goHome() })
        }
        composable(Routes.FEED) {
            FeedScreen(actions, openSearch = { nav.navigate(Routes.search(null)) }, openSettings = { nav.navigate(Routes.SETTINGS) })
        }
        composable(
            Routes.POST,
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
            deepLinks = xLinks(".*/status/{id}"),
        ) { entry ->
            PostScreen(entry.arguments?.getString("id").orEmpty(), actions, onBack = { nav.popBackStack() })
        }
        composable(
            Routes.PROFILE,
            arguments = listOf(navArgument("screenName") { type = NavType.StringType }),
            deepLinks = xLinks("{screenName}"),
        ) { entry ->
            ProfileScreen(entry.arguments?.getString("screenName").orEmpty(), actions, onBack = { nav.popBackStack() })
        }
        composable(
            Routes.MEDIA,
            arguments = listOf(navArgument("postId") { type = NavType.StringType }, navArgument("index") { type = NavType.IntType }),
            enterTransition = { fadeIn(tween(180)) },
            exitTransition = { fadeOut(tween(180)) },
            popEnterTransition = { fadeIn(tween(180)) },
            popExitTransition = { fadeOut(tween(180)) },
        ) { entry ->
            MediaViewer(entry.arguments?.getString("postId").orEmpty(), entry.arguments?.getInt("index") ?: 0, onClose = { nav.popBackStack() })
        }
        composable(
            Routes.SEARCH,
            arguments = listOf(navArgument("q") { type = NavType.StringType; nullable = true; defaultValue = null }),
            deepLinks = xLinks("search?q={q}"),
        ) { entry ->
            SearchScreen(entry.arguments?.getString("q"), actions, onBack = { nav.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { nav.popBackStack() },
                onImport = { nav.navigate(Routes.IMPORT) },
                onCleared = { nav.navigate(Routes.IMPORT) { popUpTo(nav.graph.id) { inclusive = true } } },
            )
        }
    }
}

/** An x.com or twitter.com link opens here, as it opens XTA. */
private fun xLinks(path: String) =
    listOf("x.com", "www.x.com", "twitter.com", "www.twitter.com", "mobile.twitter.com", "mobile.x.com").map { host ->
        navDeepLink { uriPattern = "https://$host/$path" }
    }

/** To the feed, with nothing to go back to: after an import. */
private fun NavHostController.goHome() {
    navigate(Routes.FEED) { popUpTo(graph.id) { inclusive = true } }
}
