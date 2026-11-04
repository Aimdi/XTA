package com.aimdi.xtanative.plugins.substack

import com.aimdi.xtanative.networks.substack.SubstackArticleCache
import com.aimdi.xtanative.networks.substack.SubstackClient
import com.aimdi.xtanative.networks.substack.SubstackFeedMemory
import com.aimdi.xtanative.networks.substack.SubstackFollowing
import com.aimdi.xtanative.networks.substack.SubstackFollows
import com.aimdi.xtanative.networks.substack.SubstackHighlights
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
import com.aimdi.xtanative.store.offline.OfflineArticle
import com.aimdi.xtanative.store.settings.SubstackPrefs
import com.aimdi.xtanative.ui.plugins.PluginHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * What the Substack plugin's screens share while it runs: the client, the
 * publications followed here, what the reader keeps of posts (read marks,
 * likes, saves, highlights, kept bodies, in XTA's preferences and the
 * plugin's folder), the podcast player, and the posts, publications and
 * notes its screens were opened with.
 */
internal class SubstackApp(val host: PluginHost) {
    /** Where what outlives a screen runs: a follow, a mark, a reader's last place. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val settings = host.settings

    val client = SubstackClient(host.http)
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

    fun unfollow(publication: SubstackPublication) = launch {
        val id = followOf(publication)?.id ?: publication.id
        follows.unfollow(id)
        pins.forget(id)
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
