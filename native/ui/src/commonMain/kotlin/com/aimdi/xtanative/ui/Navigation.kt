package com.aimdi.xtanative.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDeepLink
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.savedstate.read
import com.aimdi.xtanative.core.x.model.XLive
import com.aimdi.xtanative.ui.antenna.AntennaFeedScreen
import com.aimdi.xtanative.ui.antenna.AntennasScreen
import com.aimdi.xtanative.ui.archive.ArchiveScreen
import com.aimdi.xtanative.ui.article.ArticleScreen
import com.aimdi.xtanative.ui.chrome.CardBacks
import com.aimdi.xtanative.ui.chrome.CloseOnBack
import com.aimdi.xtanative.ui.chrome.ConfirmClose
import com.aimdi.xtanative.ui.chrome.SwipeBackCard
import com.aimdi.xtanative.ui.chrome.UpdateCheck
import com.aimdi.xtanative.ui.common.LocalGraph
import com.aimdi.xtanative.ui.common.LocalProblemActions
import com.aimdi.xtanative.ui.common.ProblemActions
import com.aimdi.xtanative.ui.common.LocalReducedMotion
import com.aimdi.xtanative.ui.community.CommunityMembersScreen
import com.aimdi.xtanative.ui.community.CommunityScreen
import com.aimdi.xtanative.ui.downloads.DownloadsScreen
import com.aimdi.xtanative.ui.group.GroupDiscoveryScreen
import com.aimdi.xtanative.ui.group.GroupFeedScreen
import com.aimdi.xtanative.ui.home.DeckScreen
import com.aimdi.xtanative.ui.home.DesktopNavigation
import com.aimdi.xtanative.ui.home.HomeBarScreens
import com.aimdi.xtanative.ui.home.HomeRequests
import com.aimdi.xtanative.ui.home.HomeRoutes
import com.aimdi.xtanative.ui.home.HomeScreen
import com.aimdi.xtanative.ui.importer.ImportScreen
import com.aimdi.xtanative.ui.intro.IntroFlow
import com.aimdi.xtanative.ui.intro.IntroScreen
import com.aimdi.xtanative.ui.links.BindLinkScreens
import com.aimdi.xtanative.ui.links.LinkBrowserScreen
import com.aimdi.xtanative.ui.links.LinkScreens
import com.aimdi.xtanative.ui.links.LocalLinks
import com.aimdi.xtanative.ui.media.Galleries
import com.aimdi.xtanative.ui.media.GalleryViewer
import com.aimdi.xtanative.ui.media.ImageViewer
import com.aimdi.xtanative.ui.media.LivePlayerScreen
import com.aimdi.xtanative.ui.media.LiveRooms
import com.aimdi.xtanative.ui.media.LocalGalleryOpener
import com.aimdi.xtanative.ui.media.LocalLivePlayer
import com.aimdi.xtanative.ui.media.MediaGallery
import com.aimdi.xtanative.ui.media.MediaViewer
import com.aimdi.xtanative.ui.media.ViewerPage
import com.aimdi.xtanative.ui.offline.OfflineLibraryScreen
import com.aimdi.xtanative.ui.offline.OfflineMediaScreen
import com.aimdi.xtanative.ui.pluginstore.AttachPluginNavigation
import com.aimdi.xtanative.ui.pluginstore.LocalPlugins
import com.aimdi.xtanative.ui.pluginstore.PluginSheetHost
import com.aimdi.xtanative.ui.pluginstore.pluginScreens
import com.aimdi.xtanative.ui.post.PostActions
import com.aimdi.xtanative.ui.post.PostScreen
import com.aimdi.xtanative.ui.post.QuotesScreen
import com.aimdi.xtanative.ui.profile.FollowsScreen
import com.aimdi.xtanative.ui.profile.ProfileScreen
import com.aimdi.xtanative.ui.saved.KeepHost
import com.aimdi.xtanative.ui.saved.NoteThreadScreen
import com.aimdi.xtanative.ui.saved.SavedFoldersScreen
import com.aimdi.xtanative.ui.saved.SavedLinks
import com.aimdi.xtanative.ui.saved.SavedScreen
import com.aimdi.xtanative.ui.search.LibrarySearchScreen
import com.aimdi.xtanative.ui.search.LibraryTarget
import com.aimdi.xtanative.ui.search.SearchActions
import com.aimdi.xtanative.ui.search.SearchScreen
import com.aimdi.xtanative.ui.search.SearchTab
import com.aimdi.xtanative.ui.settings.AttachPluginBackups
import com.aimdi.xtanative.ui.settings.BackupScreen
import com.aimdi.xtanative.ui.settings.LicenceScreen
import com.aimdi.xtanative.ui.settings.LicencesScreen
import com.aimdi.xtanative.ui.settings.SettingsRoutes
import com.aimdi.xtanative.ui.settings.SettingsSection
import com.aimdi.xtanative.ui.settings.SettingsScreen
import com.aimdi.xtanative.ui.settings.SettingsSectionScreen
import com.aimdi.xtanative.ui.signin.NotLoggedInDialog
import com.aimdi.xtanative.ui.signin.SignInReminder
import com.aimdi.xtanative.ui.signin.SignInScreen
import com.aimdi.xtanative.ui.speech.LocalSpeech
import com.aimdi.xtanative.ui.speech.VoiceSettingsScreen
import com.aimdi.xtanative.ui.subscriptions.ImportFollowingScreen
import com.aimdi.xtanative.ui.subscriptions.ListImportScreen
import com.aimdi.xtanative.ui.subscriptions.SortUngroupedScreen
import com.aimdi.xtanative.ui.subscriptions.SubscriptionsNavigation
import com.aimdi.xtanative.ui.subscriptions.SubscriptionsScreen
import com.aimdi.xtanative.ui.subscriptions.UngroupedSorterScreen
import com.aimdi.xtanative.ui.trends.TrendsActions
import com.aimdi.xtanative.ui.trends.TrendsScreen
import java.net.URLEncoder

private object Routes {
    const val ROOT = "root"
    const val IMPORT = "import"
    const val SIGN_IN = SettingsRoutes.SIGN_IN
    const val HOME = "home"
    const val DECK = "deck"
    const val POST = "post/{id}?media={media}"
    const val PROFILE = "profile/{screenName}"
    const val MEDIA = "media/{postId}/{index}"
    const val GALLERY = "gallery/{key}/{index}?post={post}&at={at}"
    const val SEARCH = "search?q={q}&tab={tab}&focus={focus}"
    const val SETTINGS = "settings"
    const val TRENDS = "trends"
    const val COMMUNITY = "community/{id}"
    const val COMMUNITY_ABOUT = "community/{id}/about"
    const val COMMUNITY_MEMBERS = "community/{id}/members"
    const val LIST = "list/{id}"
    const val ANTENNAS = "antennas"
    const val ANTENNA = "antenna/{id}"
    const val SAVED = "saved"
    const val SAVED_FOLDERS = "saved/folders"
    const val NOTE_THREAD = "notes?root={root}"
    const val DOWNLOADS = "downloads"
    const val OFFLINE = "offline"
    const val OFFLINE_MEDIA = "offline/media?id={id}"
    const val ARCHIVE = "archive?id={id}"
    const val SETTINGS_SECTION = "settings/{section}?focus={focus}"
    const val BACKUP = "backup"
    const val LICENCE = "licences/library?module={module}"
    const val INTRO = "intro"
    const val BROWSER = "browser?url={url}&from={from}"
    const val IMAGE = "image?url={url}&name={name}"
    const val SPEECH_SETTINGS = SettingsRoutes.SPEECH
    const val SUBSCRIPTIONS = "subscriptions"
    const val SUBSCRIPTIONS_IMPORT = "subscriptions/import"
    const val SUBSCRIPTIONS_IMPORT_LIST = "subscriptions/import-list?list={list}"
    const val SUBSCRIPTIONS_SORT = "subscriptions/sort"
    const val SUBSCRIPTIONS_SORT_ONE = "subscriptions/sort-one?ids={ids}"
    const val GROUP = "group/{id}"
    const val GROUP_DISCOVER = "group/{id}/discover"
    const val QUOTES = "post/{id}/quotes"
    const val REPOSTERS = "post/{id}/reposters"
    const val FOLLOWING = "profile/{screenName}/following"
    const val FOLLOWERS = "profile/{screenName}/followers"
    const val ARTICLE = "article/{postId}"
    const val LIBRARY = "library?q={q}"
    const val LIVE = "live?broadcast={broadcast}&space={space}&key={key}&image={image}&ratio={ratio}"

    /** Post [id], its media's row at picture [media]: the one a grid's viewer was on. */
    fun post(id: String, media: Int = 0) = if (media > 0) "post/$id?media=$media" else "post/$id"
    fun profile(screenName: String) = "profile/" + encoded(screenName)
    fun media(postId: String, index: Int) = "media/$postId/$index"

    /** The viewer over the gallery kept as [key], from [index]; [page] is where it falls back to once the gallery is gone. */
    fun gallery(key: String, index: Int, page: ViewerPage?) =
        "gallery/" + encoded(key) + "/$index" + (page?.let { "?post=" + encoded(it.postId) + "&at=" + it.index } ?: "")
    fun search(query: String?) = search(query, null)

    /** The search of [query] on [tab] (a [SearchTab.routeName]); either may be left out. With [focus], the field takes the keyboard, the query ready to add to. */
    fun search(query: String?, tab: String?, focus: Boolean = false): String {
        val params = listOfNotNull(query?.takeIf { it.isNotBlank() }?.let { "q=" + encoded(it) }, tab?.let { "tab=$it" }, "focus=1".takeIf { focus })
        return if (params.isEmpty()) "search" else "search?" + params.joinToString("&")
    }
    fun community(id: String) = "community/" + encoded(id)
    fun communityAbout(id: String) = "community/" + encoded(id) + "/about"
    fun communityMembers(id: String) = "community/" + encoded(id) + "/members"
    fun list(id: String) = "list/" + encoded(id)
    fun antenna(id: String) = "antenna/" + encoded(id)
    fun noteThread(rootId: String) = "notes?root=" + encoded(rootId)
    fun offlineMedia(id: String) = "offline/media?id=" + encoded(id)
    fun archive(id: String) = "archive?id=" + encoded(id)
    fun browser(url: String, from: String?) = "browser?url=" + encoded(url) + (from?.let { "&from=" + encoded(it) } ?: "")
    fun image(url: String, name: String) = "image?url=" + encoded(url) + "&name=" + encoded(name)
    fun group(id: String) = "group/" + encoded(id)
    fun groupDiscover(id: String) = "group/" + encoded(id) + "/discover"
    fun importList(list: String?) = if (list.isNullOrBlank()) "subscriptions/import-list" else "subscriptions/import-list?list=" + encoded(list)
    fun sortOne(ids: List<String>) = "subscriptions/sort-one?ids=" + encoded(ids.joinToString(","))
    fun quotes(id: String) = "post/$id/quotes"
    fun reposters(id: String) = "post/$id/reposters"
    fun follows(screenName: String, followers: Boolean) = "profile/" + encoded(screenName) + if (followers) "/followers" else "/following"
    fun article(postId: String) = "article/$postId"
    fun library(query: String?) = if (query.isNullOrBlank()) "library" else "library?q=" + encoded(query.trim())

    /** The live player on [live]: what X is asked its stream with, and its picture and shape meanwhile. */
    fun live(live: XLive): String {
        val params = listOfNotNull(
            live.broadcastId?.let { "broadcast=" + encoded(it) },
            live.spaceId?.let { "space=" + encoded(it) },
            live.mediaKey?.let { "key=" + encoded(it) },
            live.imageUrl?.let { "image=" + encoded(it) },
            "ratio=" + live.aspectRatio,
        )
        return "live?" + params.joinToString("&")
    }

    /** The screen a result of the library search opens in. */
    fun of(target: LibraryTarget): String = when (target) {
        is LibraryTarget.XPost -> post(target.id)
        LibraryTarget.Saved -> SAVED
        is LibraryTarget.NoteThread -> noteThread(target.rootId)
        is LibraryTarget.Profile -> profile(target.screenName)
        is LibraryTarget.Search -> search(target.query)
        is LibraryTarget.Group -> group(target.id)
        is LibraryTarget.Article -> archive(target.id)
        is LibraryTarget.OfflineMedia -> offlineMedia(target.id)
    }

    /** [text] escaped for a route, which is matched as a URI: a space as %20, which every platform decodes. */
    private fun encoded(text: String): String = URLEncoder.encode(text, "UTF-8").replace("+", "%20")
}

private const val SLIDE_MS = 280
private const val FADE_MS = 180

private typealias Enter = AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition
private typealias Exit = AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition

/**
 * How screens come and go: a slide as one is opened and closed, a fade for
 * the media viewer, and nothing while Settings › Accessibility › Disable
 * animations is on ([reduced]), as XTA then pushes its screens without a
 * transition. [reduced] is read as each change starts, so the switch applies at once.
 * An opened post moves itself as a card over a picture of the screen it
 * came from (SwipeBackCard), so that screen stays still under it.
 */
private class ScreenMotion(private val reduced: State<Boolean>) {
    val enter: Enter = entering { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(SLIDE_MS)) }
    val exit: Exit = leaving {
        if (targetState.isCard()) ExitTransition.None else slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(SLIDE_MS)) { it / 4 }
    }
    val popEnter: Enter = entering {
        if (initialState.isCard()) EnterTransition.None else slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(SLIDE_MS)) { it / 4 }
    }
    val popExit: Exit = leaving { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(SLIDE_MS)) }
    val fadeEnter: Enter = entering { fadeIn(tween(FADE_MS)) }
    val fadeExit: Exit = leaving { fadeOut(tween(FADE_MS)) }

    /** The card comes and goes by itself. */
    val cardEnter: Enter = { EnterTransition.None }
    val cardExit: Exit = { ExitTransition.None }

    private fun entering(moving: Enter): Enter = { if (reduced.value) EnterTransition.None else moving(this) }

    private fun leaving(moving: Exit): Exit = { if (reduced.value) ExitTransition.None else moving(this) }

    private fun NavBackStackEntry.isCard(): Boolean = destination.route == Routes.POST
}

/**
 * The app's screens, from Home, or the intro's cards on a first launch. With
 * no X account, XTA's "You are not logged in" asks once a launch; Cancel
 * leaves the app to X as a guest and the plugins.
 */
@Composable
fun XtaNativeRoot() {
    val graph = LocalGraph.current
    val session by graph.sessions.state.collectAsStateWithLifecycle()
    val settings by graph.settings.state.collectAsStateWithLifecycle()
    val loaded = session
    val prefs = settings
    if (loaded == null || prefs == null) {
        // The session and settings are read in a few milliseconds; until then, the theme's background.
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        return
    }
    val start = remember { if (IntroFlow.opensOn(prefs, hasAccounts = loaded.accounts.isNotEmpty())) Routes.INTRO else Routes.HOME }
    var askSignIn by rememberSaveable { mutableStateOf(SignInReminder.launch.ask(loaded.accounts.isNotEmpty(), onIntro = start == Routes.INTRO)) }
    val nav = rememberNavController()
    val links = LocalLinks.current
    val backs = remember { CardBacks() }
    val opening = rememberCoroutineScope()
    val actions = remember(nav, links) {
        PostActions(
            // A post opens as a card over a picture of the screen it is opened from.
            openPost = { id -> backs.open(opening) { nav.navigate(Routes.post(id)) } },
            openProfile = { name -> nav.navigate(Routes.profile(name)) },
            openMedia = { postId, index -> nav.navigate(Routes.media(postId, index)) },
            search = { query -> nav.navigate(Routes.search(query)) },
            openQuotes = { id -> nav.navigate(Routes.quotes(id)) },
            openReposters = { id -> nav.navigate(Routes.reposters(id)) },
            openArticle = { postId -> nav.navigate(Routes.article(postId)) },
            openFollows = { name, followers -> nav.navigate(Routes.follows(name, followers)) },
            openLink = { url -> links?.open(url) },
            openInBrowser = { url -> links?.openOutside(url) },
            openImage = { url, name -> nav.navigate(Routes.image(url, name)) },
        )
    }
    val searchActions = remember(nav) {
        SearchActions(
            openCommunity = { id -> nav.navigate(Routes.community(id)) },
            openAntennas = { nav.navigate(Routes.ANTENNAS) },
            openLibrary = { query -> nav.navigate(Routes.library(query)) },
        )
    }
    val trendsActions = remember(nav) {
        TrendsActions(
            openSearch = { query -> nav.navigate(Routes.search(query)) },
            openPeople = { nav.navigate(Routes.search(null, SearchTab.PEOPLE.routeName)) },
            openAntennas = { nav.navigate(Routes.ANTENNAS) },
            openLibrary = { query -> nav.navigate(Routes.library(query)) },
        )
    }
    val livePlayer = remember(nav) { { live: XLive -> nav.navigate(Routes.live(live)) } }
    val homeRoutes = remember(nav) { HomeRoutes(exists = nav::hasRoute, open = { route -> nav.navigate(route) }) }
    val plugins = LocalPlugins.current
    val linkScreens = remember(nav, actions, plugins) {
        LinkScreens(
            post = actions.openPost,
            profile = actions.openProfile,
            search = actions.search,
            browser = { url, from -> nav.navigate(Routes.browser(url, from)) },
            community = { id, about -> true.also { nav.navigate(if (about) Routes.communityAbout(id) else Routes.community(id)) } },
            list = { id -> true.also { nav.navigate(Routes.list(id)) } },
            live = { link -> true.also { LiveRooms.open(LiveRooms.of(link), livePlayer, browser = { url -> links?.openOutside(url) }) } },
            plugin = plugins::openOnScreen,
        )
    }
    BindLinkScreens(linkScreens)
    // Where a plugin asks to go lands here too (PLUGINS.md).
    AttachPluginNavigation(nav, linkScreens, Routes.DOWNLOADS)
    AttachPluginBackups()
    OpenVoiceSettingsWhenAsked(nav)

    // A grid's tiles open the viewer over everything it has loaded (media_grid_lightbox.dart).
    val galleries = remember(nav) { { gallery: MediaGallery, at: Int -> nav.navigate(Routes.gallery(Galleries.put(gallery), at, gallery.pages().getOrNull(at))) } }
    // A read failure's Add account opens the sign-in, and its Diagnostics the settings page.
    val problems = remember(nav) {
        ProblemActions(addAccount = { nav.navigate(Routes.SIGN_IN) }, diagnostics = { nav.navigate(SettingsRoutes.of(SettingsSection.DIAGNOSTICS)) })
    }
    CompositionLocalProvider(LocalGalleryOpener provides galleries, LocalProblemActions provides problems, LocalLivePlayer provides livePlayer) {
        DesktopNavigation(nav) { Screens(nav, start, ScreenActions(actions, searchActions, trendsActions, homeRoutes, backs)) }
    }
    if (askSignIn) {
        NotLoggedInDialog(onImportBackup = { nav.navigate(Routes.IMPORT) }, onLogin = { nav.navigate(Routes.SIGN_IN) }, onDismiss = { askSignIn = false })
    }
    ConfirmClose()
    UpdateCheck()
}

/** The reading-aloud bar's "Voice settings" opens them over whatever screen is up. */
@Composable
private fun OpenVoiceSettingsWhenAsked(nav: NavHostController) {
    val speech = LocalSpeech.current ?: return
    LaunchedEffect(speech, nav) { speech.voiceSettingsAsked.collect { nav.navigate(Routes.SPEECH_SETTINGS) } }
}

/** What the screens are given to reach one another, and the pictures the opened posts are dragged off. */
private class ScreenActions(val posts: PostActions, val search: SearchActions, val trends: TrendsActions, val home: HomeRoutes, val backs: CardBacks)

/** Every screen, by route; [start] is the first. */
@Composable
private fun Screens(nav: NavHostController, start: String, all: ScreenActions) {
    val reduced = rememberUpdatedState(LocalReducedMotion.current)
    val motion = remember(reduced) { ScreenMotion(reduced) }
    all.backs.Recorded { ScreenHost(nav, start, all, motion) }
    // The foot-of-window notices of saves, likes and downloads, and the platform's save dialog (ui/saved).
    KeepHost()
    // The sheets plugins open over the screens: a post's folders, its note, its menu, an account's groups.
    PluginSheetHost()
}

@Composable
private fun ScreenHost(nav: NavHostController, start: String, all: ScreenActions, motion: ScreenMotion) {
    val graph = LocalGraph.current
    val actions = all.posts
    val searchActions = all.search
    val trendsActions = all.trends
    val homeRoutes = all.home
    NavHost(
        navController = nav,
        startDestination = start,
        route = Routes.ROOT,
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        enterTransition = motion.enter,
        exitTransition = motion.exit,
        popEnterTransition = motion.popEnter,
        popExitTransition = motion.popExit,
    ) {
        composable(Routes.IMPORT) {
            ImportScreen(
                onImported = {
                    // A restored backup is a returning reader's: the first-launch cards are done.
                    graph.keep { graph.settings.update(IntroFlow::finished) }
                    nav.startOver(Routes.HOME)
                },
                onSignIn = { nav.navigate(Routes.SIGN_IN) },
            )
        }
        composable(Routes.SIGN_IN) {
            SignInScreen(
                onBack = { nav.popBackStack() },
                onImportFollowing = { nav.navigate(Routes.SUBSCRIPTIONS_IMPORT) { popUpTo(Routes.SIGN_IN) { inclusive = true } } },
            )
        }
        composable(Routes.INTRO) {
            IntroScreen(
                onFinished = { nav.startOver(Routes.HOME) },
                onImport = { nav.navigate(Routes.IMPORT) },
                onOpen = { route -> nav.navigate(route) },
            )
        }
        composable(Routes.HOME) {
            val screens = remember(nav, actions, trendsActions) {
                HomeBarScreens(
                    subscriptions = { SubscriptionsScreen(subscriptionsNavigation(nav, actions)) },
                    trends = { TrendsScreen(trendsActions) },
                    saved = { SavedScreen(actions, savedLinks(nav)) },
                )
            }
            // Back on Home closes the app, asking first as XTA does; Home's own sheets and menus close before.
            CloseOnBack(enabled = nav.previousBackStackEntry == null)
            HomeScreen(actions, homeRoutes, screens)
        }
        composable(Routes.DECK) {
            DeckScreen(actions, onBack = { nav.popBackStack() })
        }
        composable(
            Routes.POST,
            arguments = listOf(navArgument("id") { type = NavType.StringType }, navArgument("media") { type = NavType.IntType; defaultValue = 0 }),
            enterTransition = motion.cardEnter,
            popExitTransition = motion.cardExit,
        ) { entry ->
            val canLeave = remember(entry.id) { nav.previousBackStackEntry != null }
            SwipeBackCard(all.backs.claim(entry), canLeave, leave = { nav.popIfOn(entry) }) { back ->
                PostScreen(entry.text("id").orEmpty(), actions, onBack = back, startMedia = entry.arguments?.read { getIntOrNull("media") } ?: 0)
            }
        }
        composable(
            Routes.PROFILE,
            arguments = listOf(navArgument("screenName") { type = NavType.StringType }),
            deepLinks = xLinks("{screenName}"),
        ) { entry ->
            ProfileScreen(entry.text("screenName").orEmpty(), actions, onBack = { nav.popBackStack() }) { query ->
                nav.navigate(Routes.search(query, SearchTab.LATEST.routeName, focus = true))
            }
        }
        composable(
            Routes.MEDIA,
            arguments = listOf(navArgument("postId") { type = NavType.StringType }, navArgument("index") { type = NavType.IntType }),
            enterTransition = motion.fadeEnter,
            exitTransition = motion.fadeExit,
            popEnterTransition = motion.fadeEnter,
            popExitTransition = motion.fadeExit,
        ) { entry ->
            MediaViewer(entry.text("postId").orEmpty(), entry.arguments?.read { getIntOrNull("index") } ?: 0, onClose = { nav.popBackStack() })
        }
        composable(
            Routes.GALLERY,
            arguments = listOf(
                navArgument("key") { type = NavType.StringType },
                navArgument("index") { type = NavType.IntType },
                navArgument("post") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("at") { type = NavType.IntType; defaultValue = 0 },
            ),
            enterTransition = motion.fadeEnter,
            exitTransition = motion.fadeExit,
            popEnterTransition = motion.fadeEnter,
            popExitTransition = motion.fadeExit,
        ) { entry ->
            GalleryViewer(
                entry.text("key").orEmpty(),
                entry.arguments?.read { getIntOrNull("index") } ?: 0,
                fallbackPostId = entry.text("post"),
                fallbackIndex = entry.arguments?.read { getIntOrNull("at") } ?: 0,
                openPost = { id, media -> nav.navigate(Routes.post(id, media)) },
                onClose = { nav.popBackStack() },
            )
        }
        composable(
            Routes.SEARCH,
            arguments = listOf(
                navArgument("q") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("tab") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("focus") { type = NavType.StringType; nullable = true; defaultValue = null },
            ),
        ) { entry ->
            SearchScreen(entry.text("q"), SearchTab.of(entry.text("tab")), actions, searchActions, onBack = { nav.popBackStack() }, focusOnOpen = entry.text("focus") == "1")
        }
        composable(Routes.TRENDS) {
            TrendsScreen(trendsActions, onBack = { nav.popBackStack() })
        }
        composable(Routes.COMMUNITY, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
            CommunityScreen(entry.text("id").orEmpty(), about = false, actions, openMembers = { id -> nav.navigate(Routes.communityMembers(id)) }, onBack = { nav.popBackStack() })
        }
        composable(Routes.COMMUNITY_ABOUT, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
            CommunityScreen(entry.text("id").orEmpty(), about = true, actions, openMembers = { id -> nav.navigate(Routes.communityMembers(id)) }, onBack = { nav.popBackStack() })
        }
        composable(Routes.COMMUNITY_MEMBERS, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
            CommunityMembersScreen(entry.text("id").orEmpty(), actions, onBack = { nav.popBackStack() })
        }
        composable(Routes.LIST, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
            ListImportScreen(entry.text("id"), onBack = { nav.popBackStack() })
        }
        composable(Routes.ANTENNAS) {
            AntennasScreen(openAntenna = { id -> nav.navigate(Routes.antenna(id)) }, onBack = { nav.popBackStack() })
        }
        composable(Routes.ANTENNA, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
            AntennaFeedScreen(entry.text("id").orEmpty(), actions, onBack = { nav.popBackStack() })
        }
        composable(
            Routes.BROWSER,
            arguments = listOf(
                navArgument("url") { type = NavType.StringType },
                navArgument("from") { type = NavType.StringType; nullable = true; defaultValue = null },
            ),
        ) { entry ->
            LinkBrowserScreen(entry.text("url").orEmpty(), entry.text("from"), onClose = { nav.popBackStack() })
        }
        composable(
            Routes.IMAGE,
            arguments = listOf(
                navArgument("url") { type = NavType.StringType },
                navArgument("name") { type = NavType.StringType; nullable = true; defaultValue = null },
            ),
            enterTransition = motion.fadeEnter,
            exitTransition = motion.fadeExit,
            popEnterTransition = motion.fadeEnter,
            popExitTransition = motion.fadeExit,
        ) { entry ->
            ImageViewer(entry.text("url").orEmpty(), entry.text("name").orEmpty(), onClose = { nav.popBackStack() })
        }
        composable(Routes.SPEECH_SETTINGS) {
            VoiceSettingsScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { nav.popBackStack() },
                onOpen = { route -> nav.navigate(route) },
                onIntroAgain = { nav.startOver(Routes.INTRO) },
            )
        }
        composable(Routes.SAVED) {
            SavedScreen(actions, savedLinks(nav), onBack = { nav.popBackStack() })
        }
        composable(Routes.LIBRARY, arguments = listOf(navArgument("q") { type = NavType.StringType; nullable = true; defaultValue = null })) { entry ->
            LibrarySearchScreen(
                entry.text("q"),
                open = { target -> if (target is LibraryTarget.XPost) actions.openPost(target.id) else nav.navigate(Routes.of(target)) },
                onBack = { nav.popBackStack() },
            )
        }
        composable(Routes.SUBSCRIPTIONS) {
            SubscriptionsScreen(subscriptionsNavigation(nav, actions), onBack = { nav.popBackStack() })
        }
        composable(Routes.SAVED_FOLDERS) { SavedFoldersScreen(onBack = { nav.popBackStack() }) }
        composable(
            Routes.NOTE_THREAD,
            arguments = listOf(navArgument("root") { type = NavType.StringType; nullable = true; defaultValue = null }),
        ) { entry ->
            NoteThreadScreen(entry.text("root").orEmpty(), actions, onBack = { nav.popBackStack() })
        }
        composable(Routes.DOWNLOADS) { DownloadsScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.OFFLINE) {
            val plugins = LocalPlugins.current
            OfflineLibraryScreen(
                onBack = { nav.popBackStack() },
                openMedia = { id -> nav.navigate(Routes.offlineMedia(id)) },
                // In the reader of the plugin it came from (RSS's, Substack's), else the archive's page of the copy.
                openArticle = { article -> if (!plugins.openOffline(article)) nav.navigate(Routes.archive(article.id)) },
            )
        }
        composable(
            Routes.OFFLINE_MEDIA,
            arguments = listOf(navArgument("id") { type = NavType.StringType; nullable = true; defaultValue = null }),
        ) { entry ->
            OfflineMediaScreen(entry.text("id").orEmpty(), onBack = { nav.popBackStack() })
        }
        composable(
            Routes.ARCHIVE,
            arguments = listOf(navArgument("id") { type = NavType.StringType; nullable = true; defaultValue = null }),
        ) { entry ->
            ArchiveScreen(entry.text("id").orEmpty(), onBack = { nav.popBackStack() })
        }
        composable(
            Routes.SETTINGS_SECTION,
            arguments = listOf(
                navArgument("section") { type = NavType.StringType },
                navArgument("focus") { type = NavType.StringType; nullable = true; defaultValue = null },
            ),
        ) { entry ->
            SettingsSectionScreen(
                entry.text("section").orEmpty(),
                entry.text("focus"),
                onBack = { nav.popBackStack() },
                onOpen = { route -> nav.navigate(route) },
                onStartOver = { nav.startOver(Routes.IMPORT) },
            )
        }
        composable(Routes.BACKUP) {
            BackupScreen(onBack = { nav.popBackStack() })
        }
        composable(SettingsRoutes.LICENCES) {
            LicencesScreen(onOpen = { module -> nav.navigate(SettingsRoutes.licence(module)) }, onBack = { nav.popBackStack() })
        }
        composable(Routes.LICENCE, arguments = listOf(navArgument("module") { type = NavType.StringType; nullable = true; defaultValue = null })) { entry ->
            LicenceScreen(entry.text("module").orEmpty(), onBack = { nav.popBackStack() })
        }
        composable(Routes.SUBSCRIPTIONS_IMPORT) {
            ImportFollowingScreen(onBack = { nav.popBackStack() }, onSortUngrouped = { nav.navigate(Routes.SUBSCRIPTIONS_SORT) })
        }
        composable(
            Routes.SUBSCRIPTIONS_IMPORT_LIST,
            arguments = listOf(navArgument("list") { type = NavType.StringType; nullable = true; defaultValue = null }),
        ) { entry ->
            ListImportScreen(entry.text("list"), onBack = { nav.popBackStack() })
        }
        composable(Routes.SUBSCRIPTIONS_SORT) {
            SortUngroupedScreen(
                onBack = { nav.popBackStack() },
                onSortOneByOne = { ids -> nav.navigate(Routes.sortOne(ids)) },
                onApplied = { leftovers ->
                    if (leftovers.isEmpty()) {
                        nav.popBackStack()
                    } else {
                        nav.navigate(Routes.sortOne(leftovers)) { popUpTo(Routes.SUBSCRIPTIONS_SORT) { inclusive = true } }
                    }
                },
            )
        }
        composable(
            Routes.SUBSCRIPTIONS_SORT_ONE,
            arguments = listOf(navArgument("ids") { type = NavType.StringType; nullable = true; defaultValue = null }),
        ) { entry ->
            UngroupedSorterScreen(
                entry.text("ids").orEmpty().split(',').filter { it.isNotEmpty() },
                openProfile = actions.openProfile,
                onDone = { nav.popBackStack() },
            )
        }
        composable(Routes.GROUP, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
            GroupFeedScreen(entry.text("id").orEmpty(), actions, onBack = { nav.popBackStack() }, onFeed = { tabKey ->
                HomeRequests.show(tabKey)
                nav.backToHome()
            })
        }
        composable(Routes.GROUP_DISCOVER, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
            GroupDiscoveryScreen(entry.text("id").orEmpty(), actions, onBack = { nav.popBackStack() })
        }
        composable(Routes.QUOTES, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
            QuotesScreen(entry.text("id").orEmpty(), reposters = false, actions, onBack = { nav.popBackStack() })
        }
        composable(Routes.REPOSTERS, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
            QuotesScreen(entry.text("id").orEmpty(), reposters = true, actions, onBack = { nav.popBackStack() })
        }
        composable(Routes.FOLLOWING, arguments = listOf(navArgument("screenName") { type = NavType.StringType })) { entry ->
            FollowsScreen(entry.text("screenName").orEmpty(), followers = false, actions, onBack = { nav.popBackStack() })
        }
        composable(Routes.FOLLOWERS, arguments = listOf(navArgument("screenName") { type = NavType.StringType })) { entry ->
            FollowsScreen(entry.text("screenName").orEmpty(), followers = true, actions, onBack = { nav.popBackStack() })
        }
        composable(Routes.ARTICLE, arguments = listOf(navArgument("postId") { type = NavType.StringType })) { entry ->
            ArticleScreen(entry.text("postId").orEmpty(), actions, onBack = { nav.popBackStack() })
        }
        composable(
            Routes.LIVE,
            arguments = listOf("broadcast", "space", "key", "image", "ratio").map { name ->
                navArgument(name) { type = NavType.StringType; nullable = true; defaultValue = null }
            },
        ) { entry ->
            val live = remember(entry.id) {
                XLive(
                    mediaKey = entry.text("key"),
                    broadcastId = entry.text("broadcast"),
                    spaceId = entry.text("space"),
                    imageUrl = entry.text("image"),
                    aspectRatio = entry.text("ratio")?.toFloatOrNull()?.takeIf { it > 0f } ?: (16f / 9f),
                )
            }
            LivePlayerScreen(live, onBack = { nav.popBackStack() })
        }
        // A plugin's media viewer fades as the app's does, and not at all while animations are off.
        pluginScreens(nav, motion.fadeEnter, motion.fadeExit)
    }
}

/**
 * None: no route takes a link another app opens here by itself, or the link
 * would open twice. It goes through the app's link opener
 * (ui/links' IncomingLinks), which also knows the live player, short links
 * and plugins.
 */
@Suppress("UNUSED_PARAMETER")
private fun xLinks(path: String): List<NavDeepLink> = emptyList()

/** Whether the app has a screen at [route] yet: Home leads only to those. */
private fun NavHostController.hasRoute(route: String): Boolean = runCatching { graph.findNode(route) != null }.getOrDefault(false)

/** A route's text argument; null where the route has none. */
private fun NavBackStackEntry.text(name: String): String? = arguments?.read { getStringOrNull(name) }

/** To [route] with nothing to go back to: the feed after an import, the import after removing everything. */
private fun NavHostController.startOver(route: String) {
    navigate(route) { popUpTo(Routes.ROOT) { inclusive = true } }
}

/** [entry] taken off, when it is still the screen on top: a card that has gone leaves once. */
private fun NavHostController.popIfOn(entry: NavBackStackEntry) {
    if (currentBackStackEntry?.id == entry.id) popBackStack()
}

/** Back to Home, whatever lies between (a group's title menu picking one of Home's feeds); Home again when it was not under. */
private fun NavHostController.backToHome() {
    if (!popBackStack(Routes.HOME, inclusive = false)) startOver(Routes.HOME)
}

/** Where Subscriptions leads, whether it is a page of Home's bar or a screen of its own. */
private fun subscriptionsNavigation(nav: NavHostController, actions: PostActions) = SubscriptionsNavigation(
    openProfile = actions.openProfile,
    search = actions.search,
    openGroup = { id -> nav.navigate(Routes.group(id)) },
    importFollowing = { nav.navigate(Routes.SUBSCRIPTIONS_IMPORT) },
    importList = { nav.navigate(Routes.importList(null)) },
    sortUngrouped = { nav.navigate(Routes.SUBSCRIPTIONS_SORT) },
    settings = { nav.navigate(Routes.SETTINGS) },
    antennas = { nav.navigate(Routes.ANTENNAS) },
    deck = { nav.navigate(Routes.DECK) },
    discoverGroup = { id -> nav.navigate(Routes.groupDiscover(id)) },
)

/** Where Saved leads, whether it is a page of Home's bar or a screen of its own. */
private fun savedLinks(nav: NavHostController) = SavedLinks(
    openFolders = { nav.navigate(Routes.SAVED_FOLDERS) },
    openThread = { rootId -> nav.navigate(Routes.noteThread(rootId)) },
    openDownloads = { nav.navigate(Routes.DOWNLOADS) },
    openOffline = { nav.navigate(Routes.OFFLINE) },
    openSettings = { nav.navigate(Routes.SETTINGS) },
    searchLibrary = { nav.navigate(Routes.library(null)) },
)
