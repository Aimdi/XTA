package com.aimdi.xtanative.plugins.booru

import com.aimdi.xtanative.networks.booru.BooruClient
import com.aimdi.xtanative.networks.booru.BooruPost
import com.aimdi.xtanative.networks.booru.BooruQuery
import com.aimdi.xtanative.networks.booru.BooruSite
import com.aimdi.xtanative.networks.booru.BooruTagCategory
import com.aimdi.xtanative.store.backup.NamedSubscriptionRow
import com.aimdi.xtanative.store.backup.XtaTime
import com.aimdi.xtanative.store.settings.BooruPrefs
import com.aimdi.xtanative.store.settings.Settings
import com.aimdi.xtanative.ui.plugins.DrawnPosts
import com.aimdi.xtanative.ui.plugins.FeedKinds
import com.aimdi.xtanative.ui.plugins.NetworkPost
import com.aimdi.xtanative.ui.plugins.NetworkPostActions
import com.aimdi.xtanative.ui.plugins.PluginFeed
import com.aimdi.xtanative.ui.plugins.PluginHost
import com.aimdi.xtanative.ui.plugins.PluginPostArchive
import com.aimdi.xtanative.ui.plugins.PluginPostMenu
import com.aimdi.xtanative.ui.plugins.PluginSubscription
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement

/**
 * What the Booru plugin's screens share while it runs: the client, the tags
 * followed here, the feeds' copies on the device, the lists the viewer pages
 * through, the reader's choices, and the posts the app's shared rows drew,
 * which their bookmark and menu file in the app's Saved.
 *
 * The choices are the app's settings ([PluginHost.settings]), under XTA's
 * keys ([BooruPrefs]), so XTA's backup and the app's carry them; the followed
 * tags are the plugin's backup section, which a restored XTA backup's rows
 * reach. Saving a post on the device and the downloads are the host's.
 */
internal class BooruApp(val host: PluginHost) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val notices = MutableSharedFlow<String>(extraBufferCapacity = 4)

    /** The app's settings once read; null before. */
    val settings: StateFlow<Settings?> get() = host.settings.state

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

    /** Saves [post]'s file through the app's downloads, as the download setting says, under the reader's file name template. */
    fun download(post: BooruPost) = host.download(BooruDownloads.media(post), BooruDownloads.fileName(post, current()[BooruPrefs.fileNameTemplate]))

    /** [tag]'s groups, followed here first once a group is picked, as XTA's picker follows an account. */
    fun addToGroup(tag: String) {
        val query = BooruQuery.normalise(tag) ?: return
        host.addToGroup(query) { if (query !in follows.load()) follows.add(query) }
    }

    /** [tag] no longer followed here and out of the app's groups, as XTA's BooruTagsStore.remove takes it out of them. */
    suspend fun unfollow(tag: String) {
        follows.remove(tag)
        BooruQuery.normalise(tag)?.let(host::unfollowed)
    }

    /** The tags and searches followed here as the app's Subscriptions list shows them (XTA's Booru subscriptions): "tag:…", a tap searches it. */
    val subscriptions: Flow<List<PluginSubscription>> = follows.rows.onStart { follows.load() }.filterNotNull().map { rows -> rows.map(::subscriptionOf) }

    private fun subscriptionOf(row: NamedSubscriptionRow) = PluginSubscription(
        id = row.id,
        name = row.name,
        subtitle = "tag:${row.name}",
        route = BooruRoutes.search(row.name),
        followedAt = XtaTime.parse(row.createdAt)?.toEpochMilli() ?: 0,
        unfollow = { unfollow(row.name) },
    )

    private val drawn = DrawnPosts<BooruPost> { it.key }
    private val records = DrawnPosts<PluginPostArchive> { it.id }

    /** [post] as the app's shared rows draw it, kept so that what a row asks of it finds the post. */
    fun shown(post: BooruPost): NetworkPost = BooruShared.post(drawn.put(post), Strings.card_title.get(host.language, post.id))

    /** A post Saved kept as XTA's link record, as Saved draws it; null for another network's record. */
    fun shownSaved(content: JsonElement): NetworkPost? {
        val archive = PluginPostArchive.ofLink(content, BooruArchive.SOURCE) ?: return null
        return BooruShared.saved(records.put(archive), BooruSites.known(current())) { id -> Strings.card_title.get(host.language, id) }
    }

    /** Where a post the app draws leads: the viewer, else the page it names; its bookmark and the shared sheet (booru_post_card.dart). */
    val actions = NetworkPostActions(
        open = ::openShown,
        openAuthor = ::openShown,
        openMention = {},
        openTag = { tag -> openSearch(tag) },
        openMedia = { post, _ -> openShown(post) },
        archive = ::archiveOf,
        more = { post -> archiveOf(post)?.let { archive -> host.openPostMenu(PluginPostMenu(archive, post.url.orEmpty())) } },
    )

    private fun archiveOf(post: NetworkPost): PluginPostArchive? = drawn[post.id]?.let(BooruArchive::of) ?: records[post.id]

    private fun openShown(post: NetworkPost) {
        val shown = drawn[post.id]
        if (shown != null) openPost(listOf(shown), 0) else post.url?.let(host::openLink)
    }

    /**
     * The tags followed and the searches saved here, in Home's timelines when
     * the reader asks and in the groups that hold them (booru_interleaved.dart):
     * the newest of each, a few of each, as XTA mixes them in.
     */
    val feed: PluginFeed = object : PluginFeed() {
        override suspend fun followed(): List<String> = follows.load()

        override suspend fun posts(accounts: List<String>, kinds: FeedKinds, force: Boolean): List<NetworkPost> {
            val followed = client.followedPosts(accounts, limitPerQuery = SHARED_PER_TAG)
            followed.error?.takeIf { followed.failed == followed.asked }?.let { throw it }
            return followed.posts.map(::shown)
        }
    }

    private companion object {
        /** Kinds of tag that name someone or something with a page of their own. */
        val PAGE_KINDS = setOf(BooruTagCategory.ARTIST, BooruTagCategory.CHARACTER, BooruTagCategory.COPYRIGHT)

        /** XTA's kBooruInterleavedPageSize. */
        const val SHARED_PER_TAG = 8
    }
}
