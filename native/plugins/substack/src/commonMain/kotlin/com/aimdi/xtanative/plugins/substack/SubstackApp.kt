package com.aimdi.xtanative.plugins.substack

import com.aimdi.xtanative.networks.substack.SubstackArticleCache
import com.aimdi.xtanative.networks.substack.SubstackClient
import com.aimdi.xtanative.networks.substack.SubstackFeed
import com.aimdi.xtanative.networks.substack.SubstackFeedMemory
import com.aimdi.xtanative.networks.substack.SubstackFollow
import com.aimdi.xtanative.networks.substack.SubstackFollowing
import com.aimdi.xtanative.networks.substack.SubstackFollows
import com.aimdi.xtanative.networks.substack.SubstackHighlights
import com.aimdi.xtanative.networks.substack.SubstackHomeRules
import com.aimdi.xtanative.networks.substack.SubstackHosts
import com.aimdi.xtanative.networks.substack.SubstackLinks
import com.aimdi.xtanative.networks.substack.SubstackNote
import com.aimdi.xtanative.networks.substack.SubstackPins
import com.aimdi.xtanative.networks.substack.SubstackPost
import com.aimdi.xtanative.networks.substack.SubstackPostLink
import com.aimdi.xtanative.networks.substack.SubstackPostShelf
import com.aimdi.xtanative.networks.substack.SubstackPublication
import com.aimdi.xtanative.networks.substack.SubstackReadMarks
import com.aimdi.xtanative.networks.substack.SubstackRememberedPage
import com.aimdi.xtanative.networks.substack.SubstackRoutes
import com.aimdi.xtanative.store.backup.XtaTime
import com.aimdi.xtanative.store.offline.OfflineArticle
import com.aimdi.xtanative.store.settings.SubstackPrefs
import com.aimdi.xtanative.ui.plugins.DrawnPosts
import com.aimdi.xtanative.ui.plugins.FeedKinds
import com.aimdi.xtanative.ui.plugins.FeedReads
import com.aimdi.xtanative.ui.plugins.NetworkPost
import com.aimdi.xtanative.ui.plugins.NetworkPostActions
import com.aimdi.xtanative.ui.plugins.PluginFeed
import com.aimdi.xtanative.ui.plugins.PluginFollowable
import com.aimdi.xtanative.ui.plugins.PluginHost
import com.aimdi.xtanative.ui.plugins.PluginPostArchive
import com.aimdi.xtanative.ui.plugins.PluginPostMenu
import com.aimdi.xtanative.ui.plugins.PluginSubscription
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement

/**
 * What the Substack plugin's screens share while it runs: the client, the
 * publications followed here, what the reader keeps of posts (read marks,
 * likes, saves, highlights, kept bodies, in XTA's preferences and the
 * plugin's folder), the podcast player, the posts, publications and notes
 * its screens were opened with, and the posts the app's shared rows drew,
 * which their bookmark and menu file in the app's Saved.
 */
internal class SubstackApp(val host: PluginHost) {
    /** Where what outlives a screen runs: a follow, a mark, a reader's last place. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val settings = host.settings

    val client = SubstackClient(host.http)

    /** The pane last chosen, kept while the plugin runs as XTA keeps it for the session ('substack/view'). */
    val pane = MutableStateFlow(HomePane.HOME)
    val follows = SubstackFollowStore(host.store.file("follows.json"), settings)
    val pins = SubstackPins(PrefKept(settings, SubstackPrefs.pinnedPublications))
    val read = SubstackReadMarks(PrefKept(settings, SubstackPrefs.readIds))
    val likes = SubstackPostShelf(PrefKept(settings, SubstackPrefs.likedPosts), SubstackPostShelf.LIKES)
    val saves = SubstackPostShelf(PrefKept(settings, SubstackPrefs.savedPosts), SubstackPostShelf.SAVES)
    val highlights = SubstackHighlights(PrefKept(settings, SubstackPrefs.highlights))
    val articles = SubstackArticleCache(PluginFiles(host.store))
    val podcast = SubstackPodcast(settings, scope)
    val opml = OpmlImports()
    private val feedMemory = FileKept(host.store.file("feed.json"))

    /** The follows, pinned first in pin order then by name; null until they have been read. */
    val publications: StateFlow<List<SubstackPublication>?> = combine(follows.state, pins.state) { follows, pins ->
        follows?.let { SubstackFollowing.ordered(it.map { follow -> follow.publication }, pins) }
    }.stateIn(scope, SharingStarted.Eagerly, null)

    private val opened = Opened()

    init {
        scope.launch {
            follows.load()
            pins.load()
            read.load()
            likes.load()
            saves.load()
            highlights.load()
        }
    }

    /** Runs [block] beyond the screen that asked for it: a follow, a mark, a setting. */
    fun launch(block: suspend () -> Unit) {
        scope.launch { block() }
    }

    fun close() {
        scope.launch { podcast.close() }.invokeOnCompletion { scope.cancel() }
    }

    /** A post's body as this device keeps it: its offline copy first, then the article cache. Nothing is fetched. */
    suspend fun keptBody(post: SubstackPost): String? =
        offlineCopy(post)?.bodyHtml?.takeIf { it.isNotBlank() } ?: articles.get(post.publication, post.slug)?.bodyHtml

    /** [post] as the offline library keeps it, found by its id, then by its address. */
    suspend fun offlineCopy(post: SubstackPost): OfflineArticle? =
        host.offline.article(SubstackOffline.idOf(post), post.canonicalUrl ?: "${post.publicationBaseUrl}/p/${post.slug}")

    suspend fun recallFeed(): Map<String, SubstackRememberedPage> = SubstackFeedMemory.decode(feedMemory.read())

    suspend fun rememberFeed(pages: Map<String, SubstackRememberedPage>) = feedMemory.write(SubstackFeedMemory.encode(pages))

    /** The follow [publication] is, if it is followed at all. */
    fun followOf(publication: SubstackPublication): SubstackPublication? = SubstackFollows.followOf(publications.value.orEmpty(), publication)

    fun follow(publication: SubstackPublication) = launch { follows.follow(publication) }

    fun unfollow(publication: SubstackPublication) = launch { unfollowNow(publication) }

    /** [publication] no longer followed here, unpinned and out of the app's groups, as XTA's SubstackPublicationsStore.remove. */
    private suspend fun unfollowNow(publication: SubstackPublication) {
        val id = followOf(publication)?.id ?: publication.id
        follows.unfollow(id)
        pins.forget(id)
        host.unfollowed(id)
    }

    /** The publications followed here as the app's Subscriptions list shows them (XTA's SubstackSubscription): their site under their name, a tap opens their page. */
    val subscriptions: Flow<List<PluginSubscription>> = follows.state.onStart { follows.load() }.filterNotNull().map { list -> list.map(::subscriptionOf) }

    private fun subscriptionOf(follow: SubstackFollow): PluginSubscription {
        val publication = follow.publication
        return PluginSubscription(
            id = publication.id,
            name = publication.displayName,
            subtitle = PluginSubscription.siteOf(publication.baseUrl),
            avatarUrl = publication.logoUrl,
            route = SubstackRoutes.publication(publication),
            followedAt = XtaTime.parse(follow.createdAt)?.toEpochMilli() ?: 0,
            unfollow = { unfollowNow(publication) },
        )
    }

    /** Writes [fresh], read from its page, over the follow it is; a pin moves with it when its id does. */
    fun refreshFollow(fresh: SubstackPublication) = launch {
        val left = follows.refresh(fresh) ?: return@launch
        val pinned = pins.isPinned(left)
        pins.forget(left)
        val moved = SubstackFollows.followOf(follows.load().map { it.publication }, fresh)?.id
        if (pinned && moved != null && !pins.isPinned(moved)) pins.toggle(moved)
    }

    // ---- where things open ----

    fun openPost(post: SubstackPost) {
        opened.posts.keep(post.key, post)
        host.open(SubstackRoutes.post(post))
    }

    /** The reader's route for a post the app's offline collection keeps ([article]), its kept copy at hand there; null for another's. */
    fun routeForKept(article: OfflineArticle): String? = SubstackOffline.postOf(article)?.let { post ->
        opened.posts.keep(post.key, post)
        SubstackRoutes.post(post)
    }

    fun openComments(post: SubstackPost) {
        opened.posts.keep(post.key, post)
        host.open(SubstackRoutes.comments(post))
    }

    fun openPublication(publication: SubstackPublication) {
        opened.publications.keep(publication.baseUrl, publication)
        host.open(SubstackRoutes.publication(publication))
    }

    fun openNote(note: SubstackNote) {
        opened.notes.keep(note.id, note)
        host.open(SubstackRoutes.note(note))
    }

    fun openAdd() = host.open(SubstackRoutes.ADD)

    fun openDiscover() = host.openSearch(null)

    /**
     * A link tapped in an article or a discussion: another Substack post opens
     * in the reader, a jump inside this article is the reader's, anything else
     * goes where the app sends links.
     */
    fun openLink(url: String) {
        val link = SubstackLinks.postLink(url, publications.value.orEmpty().map { it.baseUrl })
        if (link != null) host.open(SubstackRoutes.post(link)) else host.openLink(url)
    }

    /** The post a screen was opened for: the copy it was opened with, else what its address says until it is read. */
    fun postFor(link: SubstackPostLink, key: String?): SubstackPost {
        key?.let { opened.posts.get(it) }?.let { return it }
        val host = SubstackHosts.hostOf(link.publicationBase)
        val name = publications.value.orEmpty().firstOrNull { SubstackHosts.same(SubstackHosts.hostOf(it.baseUrl), host) }?.name
        return SubstackLinks.stub(link, name)
    }

    fun publicationFor(publication: SubstackPublication): SubstackPublication = opened.publications.get(publication.baseUrl) ?: publication

    fun noteFor(id: String): SubstackNote? = opened.notes.get(id)

    // ---- the app's shared features ----

    /** [publication]'s groups, followed here first once a group is picked (substack_group.dart). */
    fun addToGroup(publication: SubstackPublication) = host.addToGroup(publication.id) { followHere(publication) }

    /**
     * [text] as a newsletter for a group's add box (its site's host): found as
     * XTA's box finds it, so that a site that is none says so, then followed
     * here and named as the groups hold it.
     */
    fun followable(text: String): PluginFollowable? {
        val base = SubstackHosts.base(text) ?: return null
        if (SubstackHosts.subdomainOf(base.host).isEmpty()) return null
        return PluginFollowable(base.host) {
            val publication = client.resolve(text)
            followHere(publication)
            (SubstackFollows.followOf(follows.load().map { it.publication }, publication) ?: publication).id
        }
    }

    private suspend fun followHere(publication: SubstackPublication) {
        if (SubstackFollows.followOf(follows.load().map { it.publication }, publication) == null) follows.follow(publication)
    }

    /** The shared post sheet for [post], as its card's long press opens it (substack_post_card.dart). */
    fun openMenu(post: SubstackPost) = host.openPostMenu(PluginPostMenu(SubstackShared.archive(post), SubstackShared.urlOf(post)))

    /** The shared post sheet for [note] (substack_note_content.dart): its publication's groups too; nothing for a note without its own page. */
    fun openMenu(note: SubstackNote) {
        val archive = SubstackShared.archive(note) ?: return
        val publication = note.publication
        host.openPostMenu(PluginPostMenu(archive, SubstackLinks.webUrl(note.url).orEmpty(), author = publication?.id, follow = { publication?.let { followHere(it) } }))
    }

    private val drawn = DrawnPosts<SubstackPost> { it.key }
    private val records = DrawnPosts<PluginPostArchive> { it.id }

    /** [post] as the app's shared rows draw it, with its publication's [logoUrl], kept so that what a row asks of it finds the post. */
    fun shown(post: SubstackPost, logoUrl: String?): NetworkPost = SubstackShared.post(drawn.put(post), logoUrl)

    /** A post or note Saved kept as XTA's link record, as Saved draws it; null for another network's record. */
    fun shownSaved(content: JsonElement): NetworkPost? = PluginPostArchive.ofLink(content, SubstackShared.SOURCE)?.let { SubstackShared.saved(records.put(it)) }

    /** The post [shown] names, as the app drew it lately; null for one not drawn lately. */
    fun drawnPost(shown: NetworkPost): SubstackPost? = drawn[shown.id]

    /** Where a post the app draws leads: the reader, its publication's page, else its link; its bookmark and the shared sheet. */
    val actions = NetworkPostActions(
        open = ::openShown,
        openAuthor = { post -> drawn[post.id]?.let { openPublication(SubstackHomeRules.publicationOf(it, post.avatarUrl)) } ?: openShown(post) },
        openMention = {},
        openTag = {},
        openMedia = { post, _ -> openShown(post) },
        archive = { post -> drawn[post.id]?.let(SubstackShared::archive) ?: records[post.id] },
        more = { post -> drawn[post.id]?.let(::openMenu) ?: records[post.id]?.let { host.openPostMenu(PluginPostMenu(it, post.url.orEmpty())) } },
    )

    private fun openShown(post: NetworkPost) {
        val shown = drawn[post.id]
        if (shown != null) openPost(shown) else post.url?.let(::openLink)
    }

    /**
     * The publications followed here in the groups that hold them
     * (substack_interleaved.dart): each one's newest page, three read at a
     * time, the posts that have a date; Home's timelines have no switch for
     * them in XTA.
     */
    val feed: PluginFeed = object : PluginFeed() {
        override suspend fun followed(): List<String> = follows.load().map { it.publication.id }

        override suspend fun posts(accounts: List<String>, kinds: FeedKinds, force: Boolean): List<NetworkPost> {
            val publications = follows.load().map { it.publication }.filter { it.id in accounts }.associateBy { it.id }
            return FeedReads.each(publications.keys.toList(), atOnce = SubstackFeed.CONCURRENCY) { id ->
                val publication = publications.getValue(id)
                client.posts(publication, limit = SubstackFeed.PAGE_SIZE).items.filter { it.publishedAt != null }.map { shown(it, publication.logoUrl) }
            }
        }
    }

    /** What the plugin's screens were opened with, the last few of each, while it runs. */
    private class Opened {
        val posts = Recent<SubstackPost>()
        val publications = Recent<SubstackPublication>()
        val notes = Recent<SubstackNote>()
    }

    private class Recent<T> {
        private val values = LinkedHashMap<String, T>()

        @Synchronized
        fun keep(key: String, value: T) {
            values.remove(key)
            values[key] = value
            while (values.size > LIMIT) values.remove(values.keys.first())
        }

        @Synchronized
        fun get(key: String): T? = values[key]
    }

    private companion object {
        const val LIMIT = 64
    }
}
