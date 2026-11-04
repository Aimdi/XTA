package com.aimdi.xtanative.plugins.booru

import com.aimdi.xtanative.data.AppGraph
import com.aimdi.xtanative.networks.booru.BooruClient
import com.aimdi.xtanative.networks.booru.BooruPost
import com.aimdi.xtanative.networks.booru.BooruSite
import com.aimdi.xtanative.networks.booru.BooruTagCategory
import com.aimdi.xtanative.store.settings.BooruPrefs
import com.aimdi.xtanative.store.settings.Settings
import com.aimdi.xtanative.ui.plugins.PluginHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * What the Booru plugin's screens share while it runs: the client, the tags
 * followed here, the feeds' copies on the device, the lists the viewer pages
 * through, and the reader's choices.
 *
 * The choices are the app's settings ([PluginHost.settings]), under XTA's
 * keys ([BooruPrefs]), so XTA's backup and the app's carry them. What the
 * plugin API has no member for (saving a post on the device, the downloads,
 * the followed tags an XTA backup restored) is the app's graph's, which the
 * first of the plugin's screens to be drawn hands over ([bind]).
 */
internal class BooruApp(val host: PluginHost) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val bound = MutableStateFlow<AppGraph?>(null)
    private val notices = MutableSharedFlow<String>(extraBufferCapacity = 4)

    /** The app's settings once read; null before. */
    val settings: StateFlow<Settings?> get() = host.settings.state

    /** The app's graph, once a screen handed it over. */
    val graph: StateFlow<AppGraph?> = bound.asStateFlow()

    /** What a screen has to tell the reader at its foot: copied, saved, a test's answer. */
    val messages: SharedFlow<String> = notices.asSharedFlow()

    val client = BooruClient(host.http, settings = { BooruConfig.client(current()) })
    val follows = BooruFollows(host.store.file("follows.json"))
    val cache = BooruFeedCache(host.store)
    val viewers = BooruViewers()

    /** Each site's newest post Following had shown before this visit: read once while the plugin runs, as XTA's session keeps it. */
    var seenBefore: Map<String, Long>? = null

    /** The settings now, the defaults until they are read. */
    fun current(): Settings = host.settings.state.value ?: Settings()

    /** What the app does to XTA's Booru preferences as it starts: credentials saved before they had a host get the host in use. */
    fun start() = update(BooruConfig::withCredentialsHost)

    /** Takes the app's [graph] from the first screen drawn, and the followed tags an XTA backup restored with it. */
    fun bind(graph: AppGraph) {
        if (bound.compareAndSet(null, graph)) adoptRestored()
    }

    /** The followed tags and saved searches a restored XTA backup left with the app, taken as XTA's import takes them. */
    fun adoptRestored() {
        val graph = bound.value ?: return
        launch { graph.kept.get().booruSubscriptions?.let { follows.adopt(it) } }
    }

    /** The settings changed by [edit] (only Booru's own: `plugin.booru.`), written and announced. */
    fun update(edit: (Settings) -> Settings) = launch { host.settings.update(edit) }

    /** Runs [block] beyond the screen that asked for it: a follow, a setting, a download. A failure (a full disk) ends it quietly. */
    fun launch(block: suspend () -> Unit) {
        scope.launch { attempt(block) }
    }

    fun tell(message: String) {
        notices.tryEmit(message)
    }

    fun close() = scope.cancel()

    /** The site [post] came from, with the credentials only if they belong there. */
    fun siteOf(post: BooruPost): SiteRef = SiteRef.of(client.siteOf(post))

    /** [ref]'s site, or the site in use. */
    fun site(ref: SiteRef?): BooruSite = ref?.let { client.siteAt(it.host, it.engine) } ?: client.site

    /** Opens the viewer over [posts] at [index], swiping on into what [feed] loads next; [search] is the search they came from. */
    fun openPost(posts: List<BooruPost>, index: Int, feed: BooruFeed? = null, search: BooruSearch? = null) {
        val post = posts.getOrNull(index) ?: return
        host.open(BooruRoutes.viewer(viewers.put(ViewerSource(posts, feed, search)), index, post))
    }

    /** Opens [tag]'s page when it names an artist, character or series, and a search for it otherwise. */
    fun openTag(tag: String, category: BooruTagCategory?, site: SiteRef?) {
        host.open(if (category in PAGE_KINDS) BooruRoutes.tag(tag, category, site) else BooruRoutes.search(tag))
    }

    /** The tag search, on [query]; [edit] goes back into the field to be changed. */
    fun openSearch(query: String? = null, edit: String? = null) = host.open(BooruRoutes.search(query, edit))

    private companion object {
        /** Kinds of tag that name someone or something with a page of their own. */
        val PAGE_KINDS = setOf(BooruTagCategory.ARTIST, BooruTagCategory.CHARACTER, BooruTagCategory.COPYRIGHT)
    }
}
