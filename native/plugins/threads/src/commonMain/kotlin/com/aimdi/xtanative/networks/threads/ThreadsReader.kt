package com.aimdi.xtanative.networks.threads

import com.aimdi.xtanative.networks.Page
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** Where a reader's Threads reads come from besides Meta's public pages. */
data class ThreadsSetup(
    /** An RSSHub instance the reader runs; blank for none. There is no shared default: one is rate-limited to uselessness. */
    val rssHubInstance: String = "",
    /** The reader's Xy server and its token, for a profile card the public page did not give. */
    val xyBase: String = ThreadsXy.DEFAULT_BASE,
    val xyToken: String = "",
)

/** The tabs of a Threads profile; XTA's fourth, Saved, is the reader's own storage. */
enum class ThreadsProfileTab { POSTS, REPLIES, MEDIA }

/** One profile as far as it has been read: its card, its threads, and its replies page once that was asked for. */
data class ThreadsProfilePage(
    val profile: ThreadsProfile?,
    val posts: List<ThreadsPost> = emptyList(),
    /** What the replies page gave; null until it was asked for. */
    val replies: List<ThreadsPost>? = null,
    /** Where the threads after [posts] start, when the profile's page named a cursor to follow. */
    val next: String? = null,
) {
    val isEmpty: Boolean get() = profile == null && posts.isEmpty()

    /**
     * The posts [tab] shows: the threads that are not replies; the replies of
     * both reads; or the posts of both with media. A reply can be on the profile
     * and on its replies page alike, so these are without repeats, newest first.
     */
    fun tab(tab: ThreadsProfileTab): List<ThreadsPost> = when (tab) {
        ThreadsProfileTab.POSTS -> posts.filter { !it.isReply }
        ThreadsProfileTab.REPLIES -> newestFirst(replies.orEmpty() + posts.filter { it.isReply })
        ThreadsProfileTab.MEDIA -> newestFirst((posts + replies.orEmpty()).filter { it.hasMedia })
    }

    private fun newestFirst(posts: List<ThreadsPost>) =
        posts.distinctBy { it.id }.sortedWith(compareByDescending { it.publishedAt ?: Instant.MIN })
}

/**
 * What a screen reads of Threads, as XTA's Threads stores read it: the
 * followed accounts' merged feed, a profile, and a conversation. Following
 * itself happens in XTA Native: the followed handles are passed in.
 *
 * Each followed account is read once per ten minutes at most, through the
 * source the setup implies; a load asks at most twenty accounts (six with
 * session reads), the ones asked longest ago first, so successive refreshes
 * reach every account. Three are read at once, so one slow answer does not
 * hold the rest; the client still lets requests leave one at a time, its
 * gap apart, so Meta sees no more requests than before, and a throttle
 * stops every read of the load.
 */
class ThreadsReader(
    val direct: ThreadsDirectClient,
    private val rssHub: ThreadsRssHub,
    private val xy: ThreadsXy,
    private val setup: () -> ThreadsSetup = { ThreadsSetup() },
    clock: () -> Long = System::currentTimeMillis,
) {
    private val accounts = AccountPosts<ThreadsPost>(
        dateOf = { it.publishedAt },
        perAccount = ThreadsDirectClient.POSTS_PER_ACCOUNT,
        concurrency = ACCOUNTS_AT_ONCE,
        ttlMillis = CACHE_TTL_MILLIS,
        clock = clock,
        settles = ThreadsTrouble::settles,
    )
    private val extras = AccountPosts<ThreadsPost>(
        dateOf = { it.publishedAt },
        perAccount = EXTRA_POSTS,
        concurrency = 1,
        ttlMillis = EXTRA_TTL_MILLIS,
        clock = clock,
        settles = ThreadsTrouble::settles,
    )
    private var credentials: String? = null

    /**
     * The feed: the [handles] followed here, merged newest first; with none, the
     * pasted Bearer's own timeline; with only cookies or an RSSHub instance,
     * nothing, since a session alone follows no one.
     */
    suspend fun following(
        handles: List<String>,
        force: Boolean = false,
        onPartial: ((List<ThreadsPost>) -> Unit)? = null,
    ): List<ThreadsPost> {
        if (handles.isNotEmpty()) return postsFor(handles, force, onPartial)
        val current = direct.currentSession
        return when {
            current.hasBearer -> direct.followingTimeline().items
            current.hasCookies || setup().rssHubInstance.isNotBlank() -> emptyList()
            else -> throw ThreadsException(ThreadsFailure.NOT_CONFIGURED, "no accounts or session")
        }
    }

    /**
     * [handles]' threads, newest first, at most twenty of each. One account that
     * fails keeps what it said last, and the failure surfaces only when nothing
     * at all could be read. [onPartial] hears the feed grow as each account
     * answers, so a screen paints the first while the rest wait their turn.
     */
    suspend fun postsFor(
        handles: List<String>,
        force: Boolean = false,
        onPartial: ((List<ThreadsPost>) -> Unit)? = null,
    ): List<ThreadsPost> {
        if (handles.isEmpty()) return emptyList()
        forgetOnCredentialChange()
        val current = direct.currentSession
        return accounts.merge(
            accounts.prioritize(handles),
            fetcher(current),
            forceRefresh = force,
            maxFetches = if (current.useSessionApis) SESSION_ACCOUNTS_PER_LOAD else ACCOUNTS_PER_LOAD,
            onPartial = onPartial,
        )
    }

    /**
     * What Home shows beside [handles]' own threads: what they reposted
     * (their public reposts pages; a profile's own page carries none) and the
     * posts of the [topics] followed here (a topic's name, or a community's
     * page address), newest first. Each is read at most
     * once per half hour and [EXTRAS_PER_LOAD] of them per load, the ones
     * asked longest ago first, so they add few requests to a load; never a
     * failure of its own: what was read before stays, else nothing.
     */
    suspend fun extras(handles: List<String>, topics: List<String> = emptyList()): List<ThreadsPost> {
        val keys = extraKeys(handles, topics)
        if (keys.isEmpty()) return emptyList()
        return try {
            extras.merge(extras.prioritize(keys), ::readExtra, maxFetches = EXTRAS_PER_LOAD)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** What [extras] holds for [handles] and [topics] now, asking Meta nothing. */
    suspend fun heldExtras(handles: List<String>, topics: List<String> = emptyList()): List<ThreadsPost> {
        val keys = extraKeys(handles, topics)
        return if (keys.isEmpty()) emptyList() else extras.merge(keys, { emptyList() }, maxFetches = 0)
    }

    /** A topic is its name, read from its topic page; a community's page address (`https://…`) is read as that page. */
    private fun extraKeys(handles: List<String>, topics: List<String>): List<String> =
        topics.map { it.trim() }.filter { it.isNotEmpty() }.map { if (it.startsWith("https://")) PAGE + it else TOPIC + it.removePrefix("#").lowercase() }
            .filter { it != TOPIC }.distinct() + handles.map { REPOSTS + it.trim().lowercase() }

    private suspend fun readExtra(key: String): List<ThreadsPost> = when {
        key.startsWith(REPOSTS) -> direct.guestReposts(key.removePrefix(REPOSTS)).items
        key.startsWith(TOPIC) -> direct.searchFound(key.removePrefix(TOPIC), tag = true).posts
        key.startsWith(PAGE) -> direct.communityPage(key.removePrefix(PAGE)).posts
        else -> emptyList()
    }

    /** [handle]'s reposts page, for when its Reposts tab opens; empty when Meta gave a guest none. */
    suspend fun repostsPage(handle: String): Page<ThreadsPost> = attempt { direct.guestReposts(handle) } ?: Page(emptyList(), null)

    /** How many of [handles] a load would still have to ask Meta for; an account found missing or private is not among them. */
    fun pending(handles: List<String>): Int = accounts.pendingCount(handles)

    /** [handles] whose last read failed, in their order, with why. */
    fun failures(handles: List<String>): List<ThreadsAccountFailure> =
        accounts.failures(handles).map { (handle, failure) -> ThreadsAccountFailure.of(handle, failure.error, Instant.ofEpochMilli(failure.at)) }

    /**
     * [handle] read again now, past its ten minutes and what it failed with
     * last; its threads, or the failure it answered with. The client's
     * spacing paces it as any read.
     */
    suspend fun retry(handle: String): List<ThreadsPost> {
        forgetOnCredentialChange()
        accounts.forgetFailure(handle)
        return accounts.merge(listOf(handle), fetcher(direct.currentSession), forceRefresh = true)
    }

    /** When each account last answered, to keep beside a saved feed. */
    val answeredAt: Map<String, Instant> get() = accounts.answeredAt.mapValues { Instant.ofEpochMilli(it.value) }

    /**
     * Takes what [handle] answered at [at], as a feed saved before a restart
     * says: within ten minutes of [at] it is not asked again.
     */
    fun remember(handle: String, posts: List<ThreadsPost>, at: Instant) {
        forgetOnCredentialChange()
        accounts.seed(handle, posts, at.toEpochMilli())
    }

    /** Forgets every account's threads, and their reposts and the topics' posts. */
    fun forget() {
        accounts.clear()
        extras.clear()
    }

    /**
     * [handle]'s card and threads, read together. The card comes from the public
     * page, else the cookie API when the reader opted in, else the Xy server,
     * else is pieced together from the threads; the read fails only when it
     * found neither a card nor a thread.
     */
    suspend fun profile(handle: String, force: Boolean = false): ThreadsProfilePage {
        if (handle.isEmpty()) throw ThreadsException(ThreadsFailure.NO_SUCH_FEED, "empty handle")
        return coroutineScope {
            val card = async { resolveProfile(handle) }
            val read = async { readPosts(handle, force) }
            val profile = card.await()
            val (posts, error) = read.await()
            val resolved = profile ?: ThreadsProfile.fromPosts(handle, posts)
            if (resolved == null && posts.isEmpty()) throw error ?: ThreadsException(ThreadsFailure.NO_SUCH_FEED, "profile missing")
            ThreadsProfilePage(resolved, posts, next = direct.profileNext(handle))
        }
    }

    /**
     * [handle]'s replies page, for when its Replies tab opens: never on a feed
     * refresh. Empty when Meta did not give it to a guest; the tab then shows
     * the replies the threads already carried.
     */
    suspend fun replies(handle: String): List<ThreadsPost> = repliesPage(handle).items

    /** [replies] with the cursor after them, when the replies page named one. */
    suspend fun repliesPage(handle: String): Page<ThreadsPost> = attempt { direct.guestReplies(handle) } ?: Page(emptyList(), null)

    /** [seed]'s conversation from its public page; a post without a link has none, and asks nothing. */
    suspend fun conversation(seed: ThreadsPost, force: Boolean = false): ThreadsConversation {
        val url = seed.url?.trim()?.ifEmpty { null } ?: return ThreadsConversation(focus = seed, loaded = true)
        return ThreadsConversation.from(direct.postPage(url, force), seed)
    }

    /**
     * Which source reads an account: the cookie API when the reader opted in,
     * else the RSSHub instance with the guest read standing in when it fails or
     * is empty, else the guest read.
     */
    private fun fetcher(current: ThreadsSession): suspend (String) -> List<ThreadsPost> {
        if (current.useSessionApis && current.hasCookies) return { direct.userThreads(it).items }
        val instance = setup().rssHubInstance.trim()
        if (instance.isEmpty()) return { direct.guestAccount(it).items }
        return { handle -> attempt { rssHub.account(instance, handle).items }?.ifEmpty { null } ?: direct.guestAccount(handle).items }
    }

    private suspend fun resolveProfile(handle: String): ThreadsProfile? {
        attempt { direct.guestProfile(handle) }?.let { return it }
        val current = direct.currentSession
        if (current.useSessionApis && current.hasCookies) attempt { direct.sessionProfile(handle) }?.let { return it }
        val where = setup()
        return attempt { xy.profile(where.xyBase, where.xyToken, handle) }
    }

    private suspend fun readPosts(handle: String, force: Boolean): Pair<List<ThreadsPost>, Exception?> = try {
        postsFor(listOf(handle), force) to null
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        emptyList<ThreadsPost>() to e
    }

    /** Another session or instance means another Threads answering: what the old one said is no answer now. */
    private fun forgetOnCredentialChange() {
        val current = direct.currentSession
        val now = listOf(current.cookieHeader, current.bearer, "${current.useSessionApis}", setup().rssHubInstance).joinToString(" ")
        synchronized(this) {
            if (credentials == now) return
            credentials = now
            accounts.clear()
        }
    }

    companion object {
        /** How many followed accounts a guest (or RSSHub) load asks. */
        const val ACCOUNTS_PER_LOAD = 20

        /** Session loads are capped harder: a burst of `text_feed` calls is what Meta's anti-scripting looks for. */
        const val SESSION_ACCOUNTS_PER_LOAD = 6

        /** How many followed accounts are waited on at once. */
        const val ACCOUNTS_AT_ONCE = 3

        /** How many reposts pages and topic pages one Home load asks for at most. */
        const val EXTRAS_PER_LOAD = 3

        /** How many posts of each reposts or topic page reach Home. */
        const val EXTRA_POSTS = 10

        /** How long a reposts or topic page is reused before Meta is asked again. */
        const val EXTRA_TTL_MILLIS = 30 * 60 * 1000L

        private const val REPOSTS = "reposts:"
        private const val TOPIC = "topic:"
        private const val PAGE = "page:"

        /** How long an account's threads are reused before Meta is asked again. */
        const val CACHE_TTL_MILLIS = 10 * 60 * 1000L
    }
}
