package com.aimdi.xtanative.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import com.aimdi.xtanative.ui.common.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.focusProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.savedstate.read
import com.aimdi.xtanative.core.session.DeckGroups
import com.aimdi.xtanative.core.x.model.XLive
import com.aimdi.xtanative.data.LocalGraph
import com.aimdi.xtanative.ui.antenna.AntennaFeedScreen
import com.aimdi.xtanative.ui.antenna.AntennasScreen
import com.aimdi.xtanative.ui.archive.ArchiveScreen
import com.aimdi.xtanative.ui.article.ArticleScreen
import com.aimdi.xtanative.ui.chrome.CardBacks
import com.aimdi.xtanative.ui.chrome.CloseOnBack
import com.aimdi.xtanative.ui.chrome.ConfirmClose
import com.aimdi.xtanative.ui.chrome.OfflineFrame
import com.aimdi.xtanative.ui.chrome.SwipeBackCard
import com.aimdi.xtanative.ui.chrome.UpdateCheck
import com.aimdi.xtanative.ui.chrome.push
import com.aimdi.xtanative.ui.common.CenteredPage
import com.aimdi.xtanative.ui.common.LocalProblemActions
import com.aimdi.xtanative.ui.common.LocalReducedMotion
import com.aimdi.xtanative.ui.common.Motion
import com.aimdi.xtanative.ui.common.ProblemActions
import com.aimdi.xtanative.ui.community.CommunityMembersScreen
import com.aimdi.xtanative.ui.community.CommunityScreen
import com.aimdi.xtanative.ui.downloads.DownloadsScreen
import com.aimdi.xtanative.ui.history.HistoryScreen
import com.aimdi.xtanative.ui.group.GroupDiscoveryScreen
import com.aimdi.xtanative.ui.group.GroupFeedScreen
import com.aimdi.xtanative.ui.home.DeckDragPicture
import com.aimdi.xtanative.ui.home.DeckScreen
import com.aimdi.xtanative.ui.home.DesktopNavigation
import com.aimdi.xtanative.ui.home.FindsBackWithKeys
import com.aimdi.xtanative.ui.home.FrameCalls
import com.aimdi.xtanative.ui.home.FrameFocus
import com.aimdi.xtanative.ui.home.FrameKind
import com.aimdi.xtanative.ui.home.FrameRoute
import com.aimdi.xtanative.ui.home.FramedRail
import com.aimdi.xtanative.ui.home.FramedSidebar
import com.aimdi.xtanative.ui.home.FullWindow
import com.aimdi.xtanative.ui.home.HomeBarScreens
import com.aimdi.xtanative.ui.home.HomeDestination
import com.aimdi.xtanative.ui.home.HomeRequests
import com.aimdi.xtanative.ui.home.HomeRoutes
import com.aimdi.xtanative.ui.home.HomeScreen
import com.aimdi.xtanative.ui.home.HomeWide
import com.aimdi.xtanative.ui.home.FrameRoots
import com.aimdi.xtanative.ui.home.LocalFrameRoots
import com.aimdi.xtanative.ui.home.LocalWideFrame
import com.aimdi.xtanative.ui.home.LocalWindowRoot
import com.aimdi.xtanative.ui.home.PageEnd
import com.aimdi.xtanative.ui.home.PageStart
import com.aimdi.xtanative.ui.home.RailMenu
import com.aimdi.xtanative.ui.home.ReachedByF6
import com.aimdi.xtanative.ui.home.WideFrame
import com.aimdi.xtanative.ui.home.frameNavigation
import com.aimdi.xtanative.ui.home.rememberLeaveField
import com.aimdi.xtanative.ui.importer.ImportScreen
import com.aimdi.xtanative.ui.importer.OpenedFileDialogs
import com.aimdi.xtanative.ui.intro.IntroFlow
import com.aimdi.xtanative.ui.intro.IntroScreen
import com.aimdi.xtanative.ui.links.BindLinkScreens
import com.aimdi.xtanative.ui.links.LaunchPlace
import com.aimdi.xtanative.ui.links.LinkBrowserScreen
import com.aimdi.xtanative.ui.links.LinkScreens
import com.aimdi.xtanative.ui.links.LocalLinks
import com.aimdi.xtanative.ui.links.OpenRequests
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
import com.aimdi.xtanative.ui.pluginstore.PluginCards
import com.aimdi.xtanative.ui.pluginstore.PluginRoutes
import com.aimdi.xtanative.ui.pluginstore.PluginSheetHost
import com.aimdi.xtanative.ui.pluginstore.pluginScreens
import com.aimdi.xtanative.ui.post.LocalWindowOpens
import com.aimdi.xtanative.ui.post.PostActions
import com.aimdi.xtanative.ui.post.PostScreen
import com.aimdi.xtanative.ui.post.QuotesScreen
import com.aimdi.xtanative.ui.profile.FollowsScreen
import com.aimdi.xtanative.ui.profile.ProfileScreen
import com.aimdi.xtanative.ui.profile.ProfileTab
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
import com.aimdi.xtanative.ui.settings.SettingsScreen
import com.aimdi.xtanative.ui.settings.SettingsSection
import com.aimdi.xtanative.ui.settings.SettingsSectionScreen
import com.aimdi.xtanative.ui.settings.isDesktop
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first

internal object Routes {
    const val ROOT = "root"
    const val IMPORT = "import"
    const val SIGN_IN = SettingsRoutes.SIGN_IN
    const val HOME = "home"
    const val DECK = "deck"
    const val POST = "post/{id}?media={media}"
    const val PROFILE = "profile/{screenName}?id={id}&tab={tab}"
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
    const val HISTORY = "history"
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
    fun post(id: String, media: Int = 0) = "post/" + encoded(id) + if (media > 0) "?media=$media" else ""
    /**
     * [screenName]'s profile, read by [id] when known, opening on [tab] when one is named.
     * An account X sent without a handle goes by its id: the route needs a name.
     */
    fun profile(screenName: String, id: String? = null, tab: ProfileTab? = null): String {
        val params = listOfNotNull(id?.takeIf { it.isNotBlank() }?.let { "id=" + encoded(it) }, tab?.let { "tab=" + it.routeName })
        return "profile/" + encoded(screenName.ifEmpty { id.orEmpty() }) + if (params.isEmpty()) "" else "?" + params.joinToString("&")
    }
    fun media(postId: String, index: Int) = "media/" + encoded(postId) + "/$index"

    /** The viewer over the gallery kept as [key], from [index]; [page] is where it falls back to once the gallery is gone. */
    fun gallery(key: String, index: Int, page: ViewerPage?) =
        "gallery/" + encoded(key) + "/$index" + (page?.let { "?post=" + encoded(it.postId) + "&at=" + it.index } ?: "")

    /** What names the viewer [gallery] opens at [index] and [page] whatever its key: the same tile tapped twice. */
    fun galleryOpening(index: Int, page: ViewerPage?) =
        "gallery/$index" + (page?.let { "/" + encoded(it.postId) + "/" + it.index } ?: "")
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
    fun quotes(id: String) = "post/" + encoded(id) + "/quotes"
    fun reposters(id: String) = "post/" + encoded(id) + "/reposters"
    fun follows(screenName: String, followers: Boolean) = "profile/" + encoded(screenName) + if (followers) "/followers" else "/following"
    fun article(postId: String) = "article/" + encoded(postId)
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
        is LibraryTarget.Profile -> profile(target.screenName, target.id)
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

/** How one screen gives way to the next. */
internal enum class ScreenChange {
    /** At once: motion is reduced. */
    NONE,

    /** Sliding across, as a phone pushes its screens. */
    SLIDE,

    /** A short crossfade, as a desktop app changes its view in place. */
    CROSSFADE,

    ;

    companion object {
        /** The change on the desktop ([desktop]) or a phone, still while motion is [reduced]. */
        fun of(desktop: Boolean, reduced: Boolean): ScreenChange = when {
            reduced -> NONE
            desktop -> CROSSFADE
            else -> SLIDE
        }
    }
}

/**
 * How screens come and go: a slide as one is opened and closed on a phone, a
 * short crossfade on the desktop ([desktop]), whose screens change in place
 * beside the navigation, a fade for the media viewer, and nothing while
 * Settings › Accessibility › Disable animations is on ([reduced]), as XTA then
 * pushes its screens without a transition ([ScreenChange]). [reduced] is read
 * as each change starts, so the switch applies at once.
 * An opened post moves itself as a card over a picture of the screen it
 * came from (SwipeBackCard), so that screen stays still under it.
 */
private class ScreenMotion(private val reduced: State<Boolean>, private val desktop: Boolean = isDesktop) {
    val enter: Enter = moving(
        slide = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(SLIDE_MS)) },
        fade = { fadeIn(tween(Motion.CROSSFADE_MS)) },
    )
    val exit: Exit = going(
        slide = { if (targetState.isCard()) ExitTransition.None else slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(SLIDE_MS)) { it / 4 } },
        fade = { if (targetState.isCard()) ExitTransition.None else fadeOut(tween(Motion.CROSSFADE_MS)) },
    )
    val popEnter: Enter = moving(
        slide = { if (initialState.isCard()) EnterTransition.None else slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(SLIDE_MS)) { it / 4 } },
        fade = { if (initialState.isCard()) EnterTransition.None else fadeIn(tween(Motion.CROSSFADE_MS)) },
    )
    val popExit: Exit = going(
        slide = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(SLIDE_MS)) },
        fade = { fadeOut(tween(Motion.CROSSFADE_MS)) },
    )
    val fadeEnter: Enter = entering { fadeIn(tween(FADE_MS)) }
    val fadeExit: Exit = leaving { fadeOut(tween(FADE_MS)) }

    /** The card comes and goes by itself. */
    val cardEnter: Enter = { EnterTransition.None }
    val cardExit: Exit = { ExitTransition.None }

    private fun entering(moving: Enter): Enter = { if (reduced.value) EnterTransition.None else moving(this) }

    private fun leaving(moving: Exit): Exit = { if (reduced.value) ExitTransition.None else moving(this) }

    private fun moving(slide: Enter, fade: Enter): Enter = {
        when (ScreenChange.of(desktop, reduced.value)) {
            ScreenChange.NONE -> EnterTransition.None
            ScreenChange.SLIDE -> slide(this)
            ScreenChange.CROSSFADE -> fade(this)
        }
    }

    private fun going(slide: Exit, fade: Exit): Exit = {
        when (ScreenChange.of(desktop, reduced.value)) {
            ScreenChange.NONE -> ExitTransition.None
            ScreenChange.SLIDE -> slide(this)
            ScreenChange.CROSSFADE -> fade(this)
        }
    }

    private fun NavBackStackEntry.isCard(): Boolean = destination.route == Routes.POST || destination.route == PluginCards.ROUTE
}

/**
 * The app's screens, from Home, or the intro's cards on a first launch. With
 * no X account, XTA's "You are not logged in" asks once a launch; Cancel
 * leaves the app to X as a guest and the plugins.
 * In a [SecondaryWindow], from that window's screen, with none of what the
 * main window alone does (asking to sign in or before closing, the update
 * check, the plugins' navigation and backups, the voice settings asked).
 */
@Composable
fun XtaNativeRoot() {
    val graph = LocalGraph.current
    // Only whether both are read yet: what is read of them below is read once, so a later write redraws what shows it, not the root.
    val ready by remember(graph) { combine(graph.sessions.state, graph.settings.state) { session, settings -> session != null && settings != null }.distinctUntilChanged() }
        .collectAsStateWithLifecycle(graph.sessions.state.value != null && graph.settings.state.value != null)
    val loaded = graph.sessions.state.value
    val prefs = graph.settings.state.value
    if (!ready || loaded == null || prefs == null) {
        // The session and settings are read in a few milliseconds; until then, the theme's background.
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        return
    }
    val secondary = LocalSecondaryWindow.current
    val start = remember {
        secondary?.route ?: if (IntroFlow.opensOn(prefs, hasAccounts = loaded.accounts.isNotEmpty())) Routes.INTRO else Routes.HOME
    }
    var askSignIn by rememberSaveable {
        mutableStateOf(secondary == null && SignInReminder.launch.ask(loaded.accounts.isNotEmpty(), onIntro = start == Routes.INTRO))
    }
    val nav = rememberNavController()
    val links = LocalLinks.current
    val backs = remember { CardBacks() }
    val opening = rememberCoroutineScope()
    val pluginCards = remember(backs, opening) { PluginCards(backs, opening) }
    val actions = remember(nav, links) {
        PostActions(
            // A post opens as a card over a picture of the screen it is opened from.
            openPost = { id -> backs.open(opening) { nav.push(Routes.post(id)) } },
            openProfile = { name -> nav.push(Routes.profile(name)) },
            openMedia = { postId, index -> nav.push(Routes.media(postId, index)) },
            // A search with no words yet (the desktop entry's "New search") opens with its field ready to type in.
            search = { query -> nav.push(Routes.search(query, null, focus = query.isBlank())) },
            openQuotes = { id -> nav.push(Routes.quotes(id)) },
            openReposters = { id -> nav.push(Routes.reposters(id)) },
            openArticle = { postId -> nav.push(Routes.article(postId)) },
            openFollows = { name, followers -> nav.push(Routes.follows(name, followers)) },
            openLink = { url -> links?.open(url) },
            openInBrowser = { url -> links?.openOutside(url) },
            openImage = { url, name -> nav.push(Routes.image(url, name)) },
            openUser = { name, id -> nav.push(Routes.profile(name, id)) },
        )
    }
    val searchActions = remember(nav) {
        SearchActions(
            openCommunity = { id -> nav.push(Routes.community(id)) },
            openAntennas = { nav.push(Routes.ANTENNAS) },
            openLibrary = { query -> nav.push(Routes.library(query)) },
        )
    }
    // Beside the wide frame's column, the screens its rows lead to open as those rows open them ([FrameOpens]).
    val frameOpens = remember(nav) { FrameOpens() }
    val trendsActions = remember(nav, frameOpens) {
        TrendsActions(
            openSearch = { query -> nav.push(Routes.search(query)) },
            // Discover's "Find people" is Search on its Accounts tab, opened as the column's Search row opens it: no Back.
            openPeople = { Routes.search(null, SearchTab.PEOPLE.routeName).let { route -> frameOpens.open(route) { nav.push(route) } } },
            openAntennas = { nav.push(Routes.ANTENNAS) },
            openLibrary = { query -> nav.push(Routes.library(query)) },
        )
    }
    val livePlayer = remember(nav) { { live: XLive -> nav.push(Routes.live(live)); Unit } }
    val homeRoutes = remember(nav) { HomeRoutes(exists = nav::hasRoute, open = { route -> nav.push(route) }) }
    val plugins = LocalPlugins.current
    val linkScreens = remember(nav, actions, plugins) {
        LinkScreens(
            post = actions.openPost,
            profile = { name, tab -> nav.push(Routes.profile(name, tab = ProfileTab.of(tab))) },
            search = actions.search,
            searchOn = { query, tab -> nav.push(Routes.search(query, tab.routeName, focus = query.isBlank())) },
            browser = { url, from -> nav.push(Routes.browser(url, from)) },
            community = { id, about -> true.also { nav.push(if (about) Routes.communityAbout(id) else Routes.community(id)) } },
            list = { id -> true.also { nav.push(Routes.list(id)) } },
            live = { link -> true.also { LiveRooms.open(LiveRooms.of(link), livePlayer, browser = { url -> links?.openOutside(url) }) } },
            plugin = plugins::openOnScreen,
            mightAskPlugin = plugins::mightOpenLater,
            pluginLater = plugins::openLater,
        )
    }
    // What is handed in from outside (`xta-native <url>` at a fresh start, a shared link) waits for the navigation's
    // graph and first screen: the screens' host is composed after this, and nothing can be opened before it is.
    val screensUp by produceState(false, nav) {
        nav.currentBackStackEntryFlow.first()
        value = true
    }
    if (screensUp) BindLinkScreens(linkScreens)
    if (secondary == null) {
        // Where a plugin asks to go lands here too (PLUGINS.md).
        val showMain = LocalShowMainWindow.current
        AttachPluginNavigation(nav, linkScreens, Routes.DOWNLOADS, actions, pluginCards, landed = showMain)
        AttachPluginBackups()
        OpenVoiceSettingsWhenAsked(nav, showMain)
        ShowWhenPluginSheetOpens(showMain)
    }
    // Ctrl+click and a middle click on a post, an author's picture, a person or a link to an X screen, and the menus'
    // Open in new window, open it in a window of its own.
    val postWindows = remember(links) { windowOpener()?.let { open -> WindowRoutes.opens(open) { url -> links?.xScreenOf(url) } } }

    // A grid's tiles open the viewer over everything it has loaded (media_grid_lightbox.dart).
    // Its route names the gallery by a new key each time; a double-tap is known by the page it opens on.
    val galleries = remember(nav) {
        { gallery: MediaGallery, at: Int ->
            val page = gallery.pages().getOrNull(at)
            nav.push(Routes.galleryOpening(at, page)) { Routes.gallery(Galleries.put(gallery), at, page) }
            Unit
        }
    }
    // A read failure's Add account opens the sign-in, and its Diagnostics the settings page.
    val problems = remember(nav) {
        ProblemActions(addAccount = { nav.push(Routes.SIGN_IN) }, diagnostics = { nav.push(SettingsRoutes.of(SettingsSection.DIAGNOSTICS)) })
    }
    CompositionLocalProvider(
        LocalGalleryOpener provides galleries,
        LocalProblemActions provides problems,
        LocalLivePlayer provides livePlayer,
        LocalWindowOpens provides postWindows,
    ) {
        // Beside the wide frame's column, / and Ctrl+, open Search and Settings as its rows do ([FrameOpens]).
        // Ctrl+F and / on a screen with no search of its own open the search, its field ready: not over the
        // intro, the sign-in or an import, not over a plugin's pages (they keep their own keys), and not a
        // second search over one still opening.
        val openSearch = remember(nav, frameOpens) {
            {
                if (SearchKeys.opensSearchOver(nav.currentDestination?.route)) frameOpens.open(HomeDestination.SEARCH.route) { nav.push(Routes.search(null)) }
            }
        }
        // Ctrl+, goes back to the settings where they are open below (Read aloud, the licences, the plugin store),
        // else opens them, unless a screen that must be finished first is shown.
        val openSettings = remember(nav, frameOpens) {
            {
                if (!nav.popBackStack(Routes.SETTINGS, inclusive = false) && SearchKeys.opensSettingsOver(nav.currentDestination?.route)) {
                    frameOpens.open(HomeDestination.SETTINGS.route) { nav.push(Routes.SETTINGS) }
                }
            }
        }
        // `xta-native --saved`, `--settings` and `--deck` (the desktop entry's actions), as the column's rows open them;
        // a file opened with the app (a backup, an OPML list) is asked about first.
        if (secondary == null && screensUp) {
            LaunchedEffect(nav, frameOpens) {
                OpenRequests.shownPlaces.collect { place ->
                    when (place) {
                        LaunchPlace.SAVED -> frameOpens.open(HomeDestination.SAVED.route) { nav.push(Routes.SAVED) }
                        LaunchPlace.DECK -> frameOpens.open(HomeDestination.DECK.route) { nav.push(Routes.DECK) }
                        LaunchPlace.SETTINGS -> openSettings()
                    }
                }
            }
            OpenedFileDialogs()
        }
        // Alt+Home goes back to Home, as a browser's goes to its home page, and to Home's start page there (not
        // Saved or another page last picked on its bar); nothing where Home is not below.
        val openHome = remember(nav) {
            {
                if (runCatching { nav.getBackStackEntry(Routes.HOME) }.isSuccess) {
                    nav.popBackStack(Routes.HOME, inclusive = false)
                    HomeRequests.toStart()
                }
            }
        }
        // Alt+→ and the mouse's forward button open again what going back left, as a browser's forward does.
        val forward = remember(nav) { ForwardRoutes() }
        LaunchedEffect(nav, forward) {
            nav.currentBackStackEntryFlow.collect { entry ->
                forward.shown(entry.id, entry.destination.route.orEmpty(), forwardStep(entry), nav.previousBackStackEntry?.id)
            }
        }
        val goForward = remember(nav, forward, backs, opening) {
            {
                forward.next()?.let { step ->
                    val open = { runCatching { nav.push(step.route) }.onFailure { forward.clear() }.getOrDefault(false) }
                    // A post opens as a card over a picture of the screen, as it does when clicked; one that
                    // does not open (another still opening) leaves the step to go forward to.
                    if (step.pattern == Routes.POST) backs.open(opening, abandoned = { forward.putBack(step) }, navigate = open) else open()
                    true
                } ?: false
            }
        }
        // Ctrl+H opens History as a browser's does, beside the wide frame's column as its row does.
        val openHistory = remember(nav, frameOpens) {
            {
                if (nav.currentDestination?.route.let { it != null && it !in setOf(Routes.INTRO, Routes.SIGN_IN, Routes.IMPORT) }) {
                    frameOpens.open(HomeDestination.HISTORY.route) { nav.push(Routes.HISTORY) }
                }
            }
        }
        DesktopNavigation(nav, openSearch, openSettings, openHome, goForward, openHistory) { OfflineFrame { Screens(nav, start, ScreenActions(actions, searchActions, trendsActions, homeRoutes, backs, pluginCards, frameOpens)) } }
    }
    if (askSignIn) {
        NotLoggedInDialog(onImportBackup = { nav.push(Routes.IMPORT) }, onLogin = { nav.push(Routes.SIGN_IN) }, onDismiss = { askSignIn = false })
    }
    if (secondary == null) {
        ConfirmClose()
        UpdateCheck()
    }
}

/** What opens [entry] again going forward: its route with its arguments filled in; null where they cannot be, or it is a step of setting up. */
private fun forwardStep(entry: NavBackStackEntry): ForwardRoutes.Step? {
    val pattern = entry.destination.route?.takeIf { it !in NOT_FORWARD } ?: return null
    val args = entry.arguments?.read { toMap() }.orEmpty()
    return ForwardRoutes.routeOf(pattern, args)?.let { ForwardRoutes.Step(it, pattern) }
}

/**
 * Screens going forward never opens again: the app's setting up, what only
 * leads to the first screen, and what does something as it is shown: the
 * browser screen hands its page to the desktop's browser again, and a live
 * room starts playing again.
 */
internal val NOT_FORWARD = setOf(Routes.ROOT, Routes.INTRO, Routes.IMPORT, Routes.SIGN_IN, Routes.BROWSER, Routes.LIVE)

/** The reading-aloud bar's "Voice settings" opens them over whatever screen is up. */
@Composable
private fun OpenVoiceSettingsWhenAsked(nav: NavHostController, shown: () -> Unit) {
    val speech = LocalSpeech.current ?: return
    LaunchedEffect(speech, nav) {
        speech.voiceSettingsAsked.collect {
            nav.push(Routes.SPEECH_SETTINGS)
            shown()
        }
    }
}

/** [shown] each time a plugin's sheet opens: it shows over the main window's screens, wherever it was asked from ([LocalShowMainWindow]). */
@Composable
private fun ShowWhenPluginSheetOpens(shown: () -> Unit) {
    val plugins = LocalPlugins.current
    LaunchedEffect(plugins) { plugins.sheet.collect { if (it != null) shown() } }
}

/** What the screens are given to reach one another, and the pictures the opened posts are dragged off. */
private class ScreenActions(
    val posts: PostActions,
    val search: SearchActions,
    val trends: TrendsActions,
    val home: HomeRoutes,
    val backs: CardBacks,
    val pluginCards: PluginCards,
    val frameOpens: FrameOpens,
)

/** Every screen, by route; [start] is the first. */
@Composable
private fun Screens(nav: NavHostController, start: String, all: ScreenActions) {
    val reduced = rememberUpdatedState(LocalReducedMotion.current)
    val motion = remember(reduced) { ScreenMotion(reduced) }
    // In a wide desktop window the navigation column stands beside every screen; only the screens are pictured under a card.
    WideFramed(nav, all.home, all.frameOpens) {
        all.backs.Recorded { ScreenHost(nav, start, all, motion) }
        // The foot-of-window notices of saves, likes and downloads, and the platform's save dialog (ui/saved):
        // beside the screens, so a notice centres under the reading column, not under the navigation too.
        KeepHost()
    }
    // The sheets plugins open over the screens: a post's folders, its note, its menu, an account's groups. The main window's alone, so each shows once.
    if (LocalSecondaryWindow.current == null) PluginSheetHost()
}

@Composable
private fun ScreenHost(nav: NavHostController, start: String, all: ScreenActions, motion: ScreenMotion) {
    val graph = LocalGraph.current
    val actions = all.posts
    val searchActions = all.search
    val trendsActions = all.trends
    val homeRoutes = all.home
    WindowTitleFollows(nav)
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
                onSignIn = { nav.push(Routes.SIGN_IN) },
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
                onImport = { nav.push(Routes.IMPORT) },
                onOpen = { route -> nav.push(route) },
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
            // The post a window of its own was opened on: its back arrow closes that window.
            val closeWindow = LocalSecondaryWindow.current?.close?.takeIf { !canLeave }
            SwipeBackCard(all.backs.claim(entry), canLeave, leave = { nav.popIfOn(entry) }, readsOpen = true) { back ->
                PostScreen(entry.text("id").orEmpty(), actions, onBack = closeWindow ?: back, startMedia = entry.arguments?.read { getIntOrNull("media") } ?: 0)
            }
        }
        composable(
            Routes.PROFILE,
            arguments = listOf(
                navArgument("screenName") { type = NavType.StringType },
                navArgument("id") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("tab") { type = NavType.StringType; nullable = true; defaultValue = null },
            ),
        ) { entry ->
            ProfileScreen(
                entry.text("screenName").orEmpty(),
                actions,
                onBack = { nav.popBackStack() },
                userId = entry.text("id"),
                openingTab = ProfileTab.ofRoute(entry.text("tab")),
            ) { query ->
                nav.push(Routes.search(query, SearchTab.LATEST.routeName, focus = true))
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
            FullWindow { MediaViewer(entry.text("postId").orEmpty(), entry.arguments?.read { getIntOrNull("index") } ?: 0, onClose = { nav.popBackStack() }) }
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
            FullWindow {
                GalleryViewer(
                    entry.text("key").orEmpty(),
                    entry.arguments?.read { getIntOrNull("index") } ?: 0,
                    fallbackPostId = entry.text("post"),
                    fallbackIndex = entry.arguments?.read { getIntOrNull("at") } ?: 0,
                    openPost = { id, media -> nav.push(Routes.post(id, media)) },
                    onClose = { nav.popBackStack() },
                )
            }
        }
        composable(
            Routes.SEARCH,
            arguments = listOf(
                navArgument("q") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("tab") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("focus") { type = NavType.StringType; nullable = true; defaultValue = null },
            ),
        ) { entry ->
            // Its search field, tabs and rows in one column in a wide window, as Discover.
            CenteredPage { SearchScreen(entry.text("q"), SearchTab.of(entry.text("tab")), actions, searchActions, onBack = { nav.popBackStack() }, focusOnOpen = entry.text("focus") == "1") }
        }
        composable(Routes.TRENDS) {
            TrendsScreen(trendsActions, onBack = { nav.popBackStack() })
        }
        composable(Routes.COMMUNITY, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
            CommunityScreen(entry.text("id").orEmpty(), about = false, actions, openMembers = { id -> nav.push(Routes.communityMembers(id)) }, onBack = { nav.popBackStack() })
        }
        composable(Routes.COMMUNITY_ABOUT, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
            CommunityScreen(entry.text("id").orEmpty(), about = true, actions, openMembers = { id -> nav.push(Routes.communityMembers(id)) }, onBack = { nav.popBackStack() })
        }
        composable(Routes.COMMUNITY_MEMBERS, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
            CommunityMembersScreen(entry.text("id").orEmpty(), actions, onBack = { nav.popBackStack() })
        }
        composable(Routes.LIST, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
            ListImportScreen(entry.text("id"), onBack = { nav.popBackStack() })
        }
        composable(Routes.ANTENNAS) {
            AntennasScreen(openAntenna = { id -> nav.push(Routes.antenna(id)) }, onBack = { nav.popBackStack() })
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
            FullWindow { ImageViewer(entry.text("url").orEmpty(), entry.text("name").orEmpty(), onClose = { nav.popBackStack() }) }
        }
        composable(Routes.SPEECH_SETTINGS) {
            FindsInSettings(nav)
            VoiceSettingsScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { nav.popBackStack() },
                onOpen = { route -> nav.push(route) },
                onIntroAgain = { nav.startOver(Routes.INTRO) },
                onStartOver = { nav.startOver(Routes.IMPORT) },
            )
        }
        composable(Routes.SAVED) {
            SavedScreen(actions, savedLinks(nav), onBack = { nav.popBackStack() })
        }
        composable(Routes.LIBRARY, arguments = listOf(navArgument("q") { type = NavType.StringType; nullable = true; defaultValue = null })) { entry ->
            CenteredPage {
                LibrarySearchScreen(
                    entry.text("q"),
                    open = { target -> if (target is LibraryTarget.XPost) actions.openPost(target.id) else nav.push(Routes.of(target)) },
                    onBack = { nav.popBackStack() },
                )
            }
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
        composable(Routes.HISTORY) { HistoryScreen(actions, onBack = { nav.popBackStack() }) }
        composable(Routes.OFFLINE) {
            val plugins = LocalPlugins.current
            OfflineLibraryScreen(
                onBack = { nav.popBackStack() },
                openMedia = { id -> nav.push(Routes.offlineMedia(id)) },
                // In the reader of the plugin it came from (RSS's, Substack's), else the archive's page of the copy.
                openArticle = { article -> if (!plugins.openOffline(article)) nav.push(Routes.archive(article.id)) },
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
            FindsInSettings(nav)
            SettingsSectionScreen(
                entry.text("section").orEmpty(),
                entry.text("focus"),
                onBack = { nav.popBackStack() },
                onOpen = { route -> nav.push(route) },
                onStartOver = { nav.startOver(Routes.IMPORT) },
            )
        }
        composable(Routes.BACKUP) {
            FindsInSettings(nav)
            BackupScreen(onBack = { nav.popBackStack() })
        }
        composable(SettingsRoutes.LICENCES) {
            FindsInSettings(nav)
            LicencesScreen(onOpen = { module -> nav.push(SettingsRoutes.licence(module)) }, onBack = { nav.popBackStack() })
        }
        composable(Routes.LICENCE, arguments = listOf(navArgument("module") { type = NavType.StringType; nullable = true; defaultValue = null })) { entry ->
            LicenceScreen(entry.text("module").orEmpty(), onBack = { nav.popBackStack() })
        }
        composable(Routes.SUBSCRIPTIONS_IMPORT) {
            ImportFollowingScreen(onBack = { nav.popBackStack() }, onSortUngrouped = { nav.push(Routes.SUBSCRIPTIONS_SORT) })
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
                onSortOneByOne = { ids -> nav.push(Routes.sortOne(ids)) },
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
        pluginScreens(nav, motion.fadeEnter, motion.fadeExit, all.pluginCards)
    }
}

/** The screens over the whole window, the wide frame's column included: the media viewers ([FullWindow]). */
private val FULL_WINDOW = setOf(Routes.MEDIA, Routes.GALLERY, Routes.IMAGE, PluginRoutes.MEDIA)

/**
 * The screens ([content]) beside the navigation of a desktop window
 * ([WideFrame]), once Home is in the navigation: the column in a wide
 * window, a rail of icons in a narrower one ([HomeWide.frame]), whose menu
 * opens the whole column over the window. It stays put while the screens
 * change, and the screens slide within their own place. A media viewer
 * grows over it; while one shows, or comes or goes, the screens' place lets
 * it, and Tab passes the navigation by. F6 moves the keys between it and the
 * page. Off the desktop, in a post's own window and in a window narrower
 * still, the screens alone, as before.
 */
@Composable
private fun WideFramed(nav: NavHostController, routes: HomeRoutes, opens: FrameOpens, content: @Composable () -> Unit) {
    if (!isDesktop) return content()
    if (LocalSecondaryWindow.current != null) return WindowRooted(nav, content)
    val frame = remember { WideFrame() }
    val focus = remember(frame) { FrameFocus(frame.pageBar) }
    var menu by remember { mutableStateOf(false) }
    val stack by nav.currentBackStack.collectAsStateWithLifecycle()
    val visible by nav.visibleEntries.collectAsStateWithLifecycle()
    // Home's place as it goes (popped, its model cleared) is no longer one the frame reads Home's model from.
    val home = stack.lastOrNull { it.destination.route == Routes.HOME }?.takeIf { it.lifecycle.currentState != Lifecycle.State.DESTROYED }
    // A row clicked keeps no focus: the keys go back to the screen beside the column, its reading keys at work at once.
    val leaveRow = rememberLeaveField()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val kind = if (home == null) FrameKind.NONE else HomeWide.frame(maxWidth.value, rail = true)
        val viewer = visible.any { it.destination.route in FULL_WINDOW }
        val deckFits = DeckGroups.fits(maxWidth.value, minOf(maxWidth, maxHeight).value)
        val screens = stack.map { entry -> FrameRoute(entry.destination.route, entry.text("id")?.takeIf { entry.destination.route == Routes.GROUP }) }
        val opened = remember { FrameRoots() }
        val calls = remember(nav, frame, routes, leaveRow) { frameCalls(nav, frame, routes, leaveRow, opened) }
        val railMenu = menu && kind == FrameKind.RAIL
        SideEffect {
            frame.inset = HomeWide.inset(kind)
            frame.openMenu = if (kind == FrameKind.RAIL) ({ menu = true }) else null
            opens.screen = calls.screen.takeIf { kind != FrameKind.NONE }
            // Read now, not as collected: a place the column has just opened is in the navigation already.
            opened.keep((nav.currentBackStack.value + nav.visibleEntries.value).map { it.id })
        }
        DisposableEffect(opens) { onDispose { opens.screen = null } }
        // A menu open over a rail is closed once the window has the column, or nothing beside its screens.
        LaunchedEffect(kind) { if (kind != FrameKind.RAIL) menu = false }
        if (kind != FrameKind.NONE) ReachedByF6(focus.region)
        // Under the rail's open menu nothing takes the keys: they stay in the menu, as in a dialog.
        Row(Modifier.fillMaxSize().focusProperties { onEnter = { if (menu) cancelFocusChange() } }.focusGroup()) {
            if (home != null && kind == FrameKind.SIDEBAR) {
                FramedSidebar(home, frame, screens, Routes.HOME, deckFits, routes, calls, Modifier.frameNavigation(focus, covered = viewer), focus = focus)
                VerticalDivider()
            }
            if (home != null && kind == FrameKind.RAIL) {
                FramedRail(home, frame, screens, Routes.HOME, routes, calls, onMenu = { menu = true }, Modifier.frameNavigation(focus, covered = viewer), focus)
                VerticalDivider()
            }
            Box(Modifier.weight(1f).fillMaxHeight().then(if (kind != FrameKind.NONE && !viewer) Modifier.clipToBounds() else Modifier)) {
                PageStart(focus)
                // The screen going out among them, so its bar stays as it was while it slides away.
                val roots = if (kind == FrameKind.NONE) {
                    emptySet()
                } else {
                    val shown = (stack + visible).distinctBy { it.id }
                    val ids = opened.among(shown.map { it.id })
                    shown.filterTo(HashSet<Any>()) { it.id in ids }
                }
                CompositionLocalProvider(LocalWideFrame provides frame.takeIf { kind != FrameKind.NONE }, LocalFrameRoots provides roots, content = content)
                PageEnd(focus)
            }
        }
        // A group or a network dragged from the column onto the deck, its picture following the mouse.
        DeckDragPicture()
        if (home != null) {
            // Closed by Esc, its X or the scrim, the keys go back to the rail's menu button; a row picked leads on to its screen.
            val hide = remember { { menu = false } }
            val close = {
                hide()
                runCatching { focus.menu.requestFocus() }
                Unit
            }
            RailMenu(railMenu, onClose = close) {
                FramedSidebar(home, frame, screens, Routes.HOME, deckFits, routes, remember(calls) { calls.after(hide) }, onClose = close)
            }
        }
    }
}

/**
 * The screens of a window of its own ([content]), its first one known as
 * the window's root ([LocalWindowRoot]): its bar draws no Back, which would
 * only close the window.
 */
@Composable
private fun WindowRooted(nav: NavHostController, content: @Composable () -> Unit) {
    val stack by nav.currentBackStack.collectAsStateWithLifecycle()
    // The navigation's graph stands first in the stack, with no graph of its own around it.
    val root = stack.firstOrNull { it.destination.parent != null }
    CompositionLocalProvider(LocalWindowRoot provides root, content = content)
}

/**
 * What the column's rows do: a page of Home's bar turns Home to it (Home's
 * own tap on Home, else back to Home on that page); a screen of the menu or
 * a group opens over Home, what was open over Home closed, as a desktop
 * app's sidebar leads from one place to the next; the one shown stays.
 */
private fun frameCalls(nav: NavHostController, frame: WideFrame, routes: HomeRoutes, leaveRow: () -> Unit, opened: FrameRoots) = FrameCalls(
    page = { index, page ->
        leaveRow()
        val tap = frame.homeTap
        if (nav.currentDestination?.route == Routes.HOME && tap != null) {
            tap(index)
        } else {
            HomeRequests.showPage(page.id)
            nav.popBackStack(Routes.HOME, inclusive = false)
        }
    },
    screen = { route ->
        leaveRow()
        if (nav.currentDestination?.route?.substringBefore('?') != route) {
            nav.popBackStack(Routes.HOME, inclusive = false)
            val under = nav.currentBackStackEntry?.id
            // The screen the column opens has no Back beside it ([FrameRoots]); one opened any other way keeps it.
            if (routes.openIfExists(route)) nav.currentBackStackEntry?.id?.takeIf { it != under }?.let(opened::open)
        } else if (nav.previousBackStackEntry?.destination?.route == Routes.HOME) {
            // Shown already, right over Home (Settings by Ctrl+comma): the column's own from now on.
            nav.currentBackStackEntry?.id?.let(opened::open)
        }
    },
    group = { group ->
        leaveRow()
        val shown = nav.currentDestination?.route == Routes.GROUP && nav.currentBackStackEntry?.text("id") == group.id
        if (!shown) {
            nav.popBackStack(Routes.HOME, inclusive = false)
            routes.openGroup(group.id)
        }
    },
)

/** These calls, each after [first] (the rail's menu closing as a row of it is picked). */
private fun FrameCalls.after(first: () -> Unit) = FrameCalls(
    page = { index, page ->
        first()
        page(index, page)
    },
    screen = { route ->
        first()
        screen(route)
    },
    group = { group ->
        first()
        group(group)
    },
)

/** Whether the app has a screen at [route] yet: Home leads only to those. */
/**
 * Ctrl+F on a page Settings opened (a section, Backup, the licences) goes
 * back to Settings with the keyboard in its search ([FindsBackWithKeys]);
 * a page opened from anywhere else leaves the keys to the app's search.
 * Before the page itself, so a search the page has (a plugin's) stays on top.
 */
@Composable
private fun FindsInSettings(nav: NavHostController) {
    val overSettings = remember { runCatching { nav.getBackStackEntry(Routes.SETTINGS) }.isSuccess }
    if (overSettings) FindsBackWithKeys { nav.popBackStack(Routes.SETTINGS, inclusive = false) }
}

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
    openProfile = actions.openUser,
    search = actions.search,
    openGroup = { id -> nav.push(Routes.group(id)) },
    importFollowing = { nav.push(Routes.SUBSCRIPTIONS_IMPORT) },
    importList = { nav.push(Routes.importList(null)) },
    sortUngrouped = { nav.push(Routes.SUBSCRIPTIONS_SORT) },
    settings = { nav.push(Routes.SETTINGS) },
    antennas = { nav.push(Routes.ANTENNAS) },
    deck = { nav.push(Routes.DECK) },
    discoverGroup = { id -> nav.push(Routes.groupDiscover(id)) },
)

/** Where Saved leads, whether it is a page of Home's bar or a screen of its own. */
private fun savedLinks(nav: NavHostController) = SavedLinks(
    openFolders = { nav.push(Routes.SAVED_FOLDERS) },
    openThread = { rootId -> nav.push(Routes.noteThread(rootId)) },
    openDownloads = { nav.push(Routes.DOWNLOADS) },
    openOffline = { nav.push(Routes.OFFLINE) },
    openSettings = { nav.push(Routes.SETTINGS) },
    searchLibrary = { nav.push(Routes.library(null)) },
)

/**
 * How the keys that open a screen of the wide frame's column (/ for Search,
 * Ctrl+, for Settings) open it: as its row does ([screen], what was over Home
 * closed and no Back beside the screen) while the window has the column or
 * rail; otherwise over the screen shown.
 */
internal class FrameOpens {
    /** The column's own way to open a screen by its route; null where the window has none. */
    var screen: ((String) -> Unit)? = null

    /** [route] opened as the column's row opens it, or by [otherwise] where there is no column. */
    fun open(route: String, otherwise: () -> Unit) {
        screen?.invoke(route) ?: otherwise()
    }
}

/** Where Ctrl+F and / open the app's search, on a screen with no search field of its own. */
internal object SearchKeys {
    private val KEPT = setOf(Routes.INTRO, Routes.SIGN_IN, Routes.IMPORT, Routes.SEARCH)

    /** Over [route], the screen shown: not over those that keep the reader where they are, nor a plugin's pages. */
    fun opensSearchOver(route: String?): Boolean = route != null && route !in KEPT && !route.startsWith("plugin")

    /**
     * Ctrl+, over [route], with no settings open below it: not over the
     * intro, the sign-in, an import or the plugin store the intro opens,
     * nor over the settings themselves.
     */
    fun opensSettingsOver(route: String?): Boolean =
        route != null && route !in setOf(Routes.INTRO, Routes.SIGN_IN, Routes.IMPORT, SettingsRoutes.PLUGINS) && !route.startsWith(Routes.SETTINGS)
}
