package com.aimdi.xtanative.networks.threads

import com.aimdi.xtanative.core.repairs.RepairKind
import com.aimdi.xtanative.core.repairs.RepairSlot
import kotlin.random.Random
import kotlinx.coroutines.delay

/**
 * A Threads session the reader pasted, if any: a browser's Cookie header for
 * threads.com, and an `IGT:2` Bearer for the app API. Optional: public pages
 * are read without one, and what needs the session (a people list, Activity)
 * switches to it when the reader opens it. [useSessionApis] is the reader's
 * opt-in to spend it in the background too (Home, For you, the accounts
 * followed here) and to list people before posts in a search: the one
 * setting that can cost them their Threads login.
 */
data class ThreadsSession(
    val cookieHeader: String = "",
    val bearer: String = "",
    val useSessionApis: Boolean = false,
) {
    val cookies: Map<String, String> get() = parseCookieHeader(cookieHeader)

    /** Every cookie the cookie API needs is there. */
    val hasCookies: Boolean get() = cookies.let { pasted -> REQUIRED_COOKIES.all { !pasted[it].isNullOrEmpty() } }

    /** The Bearer token without its `Bearer ` prefix; null unless it is an `IGT:2` token. */
    val bearerToken: String? get() = normaliseBearer(bearer)

    val hasBearer: Boolean get() = bearerToken != null

    val hasDirectAuth: Boolean get() = hasCookies || hasBearer

    companion object {
        /** The cookies a browser session must carry for the cookie API, in the order they are sent. */
        val REQUIRED_COOKIES = listOf("sessionid", "csrftoken", "ds_user_id", "mid", "ig_did")

        /** A pasted Cookie header (`name=value; …`) as a map. */
        fun parseCookieHeader(raw: String): Map<String, String> = raw.split(';')
            .mapNotNull { part ->
                val equals = part.indexOf('=')
                if (equals <= 0) return@mapNotNull null
                val name = part.substring(0, equals).trim()
                val value = part.substring(equals + 1).trim()
                (name to value).takeIf { name.isNotEmpty() && value.isNotEmpty() }
            }
            .toMap()

        /** [raw] as a Bearer token, with or without its `Bearer ` prefix; null unless it is an `IGT:2` token. */
        fun normaliseBearer(raw: String): String? {
            val value = raw.trim()
            if (value.isEmpty()) return null
            val token = if (value.lowercase().startsWith("bearer ")) value.substring(7).trim() else value
            return token.takeIf { it.startsWith("IGT:2:") }
        }
    }
}

/**
 * What the client keeps between launches, as XTA keeps it in preferences, so
 * a restart does not ask Meta again what it already answered: the device id
 * the app API is sent, each handle's numeric id, the last guest LSD token and
 * when it was taken, when a parked session may talk to Meta again, and what
 * the account guard counts ([ThreadsGuardKept]). Times are epoch
 * milliseconds. Nothing secret: no token, cookie or session value.
 */
data class ThreadsKept(
    val deviceId: String? = null,
    val userIds: Map<String, String> = emptyMap(),
    val guestLsd: String? = null,
    val guestLsdAt: Long? = null,
    val cooldownUntil: Long? = null,
    val guard: ThreadsGuardKept = ThreadsGuardKept(),
)

/** Where [ThreadsKept] lives: the app's own storage, or memory alone. */
interface ThreadsMemory {
    var kept: ThreadsKept

    /** Kept for as long as the process lives. */
    class InMemory(initial: ThreadsKept = ThreadsKept()) : ThreadsMemory {
        @Volatile
        override var kept: ThreadsKept = initial
    }
}

/**
 * How far apart requests leave: looking like one person reading is the whole
 * defence a reader has against Meta's ban on scripts. A session read waits
 * [sessionGapMillis] after the last departure, a guest read, which has no
 * account to lose, [guestGapMillis]; each with up to its jitter on top.
 *
 * A session read also keeps its distance from the last session read
 * ([sessionSpacing]): after every [longPauseEvery] in a row a longer pause
 * of [longPauseMillis], as a person stops to read, and a page after a
 * list's first [pagingExtraMillis] more, as a person scrolls.
 */
class ThreadsPacing(
    val sessionGapMillis: Long = 3_000,
    val guestGapMillis: Long = 300,
    val sessionJitterMillis: Int = 750,
    val guestJitterMillis: Int = 200,
    private val random: Random = Random.Default,
    internal val sleep: suspend (Long) -> Unit = { delay(it) },
    val longPauseEvery: Int = 5,
    val longPauseMillis: LongRange = 8_000L..20_000L,
    val pagingExtraMillis: LongRange = 2_000L..4_000L,
) {
    /** The gap before the next departure. */
    internal fun gap(asGuest: Boolean): Long {
        val jitter = if (asGuest) guestJitterMillis else sessionJitterMillis
        return (if (asGuest) guestGapMillis else sessionGapMillis) + (if (jitter > 0) random.nextInt(jitter) else 0)
    }

    /**
     * How long after the last session read the next leaves, [streak] reads
     * having left in a row before it: the session gap, a page of a long list
     * ([paging]) more, and after every [longPauseEvery]th a long pause.
     */
    internal fun sessionSpacing(streak: Int, paging: Boolean): Long {
        val paced = gap(asGuest = false) + if (paging) within(pagingExtraMillis) else 0
        val resting = longPauseEvery > 0 && streak > 0 && streak % longPauseEvery == 0
        return if (resting) maxOf(paced, within(longPauseMillis)) else paced
    }

    private fun within(range: LongRange): Long = if (range.last <= range.first) range.first.coerceAtLeast(0) else random.nextLong(range.first, range.last + 1)

    companion object {
        /** No gap at all: for tests. */
        val NONE = ThreadsPacing(0, 0, 0, 0, longPauseMillis = 0L..0L, pagingExtraMillis = 0L..0L)
    }
}

/**
 * Values Meta may change, each with the one XTA ships. XTA's published
 * repairs (`repairs.json`) can replace one with a value of the same kind, so a
 * rotated doc id is fixed without a release; nothing here names a host.
 *
 * The reads asked by a doc id that only threads.com's bundles name
 * ([ThreadsDocIds]) ship with none: [inlineRepliesDocId],
 * [aboutProfileDocId], [trendingTopicsDocId], [searchUsersDocId] and
 * [recommendedUsersDocId] are empty, and each read is off, asking Meta
 * nothing, until its id is shipped or published. The variable such a
 * query takes its id or words under is a value too, since only a bundle
 * says its name.
 *
 * The refetch queries a list's next pages fall back on ([ThreadsRefetch])
 * ship with the ids CI's probe read: they are asked only where the page's
 * own query names no doc id or brings nothing, so an empty id is today's
 * paging and nothing else.
 */
data class ThreadsRepairs(
    val guestDocId: String = GUEST_DOC_ID.shipped,
    val guestQueryName: String = GUEST_QUERY_NAME.shipped,
    val appId: String = APP_ID.shipped,
    val asbdId: String = ASBD_ID.shipped,
    val appUserAgent: String = APP_USER_AGENT.shipped,
    val webUserAgent: String = WEB_USER_AGENT.shipped,
    val inlineRepliesDocId: String = INLINE_REPLIES_DOC_ID.shipped,
    val inlineRepliesVariable: String = INLINE_REPLIES_VARIABLE.shipped,
    val aboutProfileDocId: String = ABOUT_PROFILE_DOC_ID.shipped,
    val aboutProfileVariable: String = ABOUT_PROFILE_VARIABLE.shipped,
    val trendingTopicsDocId: String = TRENDING_TOPICS_DOC_ID.shipped,
    val inlineRepliesExpansion: String = INLINE_REPLIES_EXPANSION.shipped,
    val searchUsersDocId: String = SEARCH_USERS_DOC_ID.shipped,
    val searchUsersVariable: String = SEARCH_USERS_VARIABLE.shipped,
    val recommendedUsersDocId: String = RECOMMENDED_USERS_DOC_ID.shipped,
    val repliesRefetchDocId: String = REPLIES_REFETCH_DOC_ID.shipped,
    val searchRefetchDocId: String = SEARCH_REFETCH_DOC_ID.shipped,
    val profileRefetchDocId: String = PROFILE_REFETCH_DOC_ID.shipped,
    val repostsRefetchDocId: String = REPOSTS_REFETCH_DOC_ID.shipped,
    val feedRefetchDocId: String = FEED_REFETCH_DOC_ID.shipped,
) {
    /**
     * The doc id [refetch]'s query is asked by; empty when that fallback is
     * off. An id of zeros is off too: `repairs.json` can publish no empty
     * value, so `"0"` is how it withdraws a refetch without a release.
     */
    fun refetchDocId(refetch: ThreadsRefetch): String = when (refetch) {
        ThreadsRefetch.REPLIES -> repliesRefetchDocId
        ThreadsRefetch.SEARCH -> searchRefetchDocId
        ThreadsRefetch.PROFILE_THREADS -> profileRefetchDocId
        ThreadsRefetch.PROFILE_REPOSTS -> repostsRefetchDocId
        ThreadsRefetch.FRONT_PAGE -> feedRefetchDocId
    }.takeIf { id -> id.any { it != '0' } }.orEmpty()

    companion object {
        val GUEST_DOC_ID = RepairSlot("threads.guest_doc_id", "6232751443445612", RepairKind.DIGITS)
        val GUEST_QUERY_NAME = RepairSlot("threads.guest_query_name", "BarcelonaProfileThreadsTabQuery", RepairKind.TOKEN)
        val APP_ID = RepairSlot("threads.app_id", "238260118697367", RepairKind.DIGITS)
        val ASBD_ID = RepairSlot("threads.asbd_id", "129477", RepairKind.DIGITS)
        val APP_USER_AGENT = RepairSlot("threads.app_user_agent", "Barcelona 289.0.0.77.109 Android", RepairKind.HEADER)
        val WEB_USER_AGENT = RepairSlot(
            "threads.web_user_agent",
            "Mozilla/5.0 (iPhone; CPU iPhone OS 16_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) " +
                "Version/16.6 Mobile/15E148 Safari/604.1",
            RepairKind.HEADER,
        )

        /** `BarcelonaInlineRepliesExpansionQuery`'s doc id, for the replies under a reply: empty, and the read off, until a bundle told it. */
        val INLINE_REPLIES_DOC_ID = RepairSlot("threads.inline_replies_doc_id", "", RepairKind.DIGITS)

        /** The variable that query takes the reply's media id under. */
        val INLINE_REPLIES_VARIABLE = RepairSlot("threads.inline_replies_variable", "postID", RepairKind.TOKEN)

        /** `BarcelonaAboutThisProfileDialogQuery`'s doc id, for when an account joined: empty, and the read off, until a bundle told it. */
        val ABOUT_PROFILE_DOC_ID = RepairSlot("threads.about_profile_doc_id", "", RepairKind.DIGITS)

        /** The variable that query takes the account's id under. */
        val ABOUT_PROFILE_VARIABLE = RepairSlot("threads.about_profile_variable", "userID", RepairKind.TOKEN)

        /** `BarcelonaSearchTrendingTopicsSectionQuery`'s doc id, for the trends when the search page carries none: empty, and the read off, until a bundle told it. */
        val TRENDING_TOPICS_DOC_ID = RepairSlot("threads.trending_topics_doc_id", "", RepairKind.DIGITS)

        /**
         * The `expansionType` that query is asked with: empty, and sent as null, since only its caller's
         * code names the values and no probe has read them.
         */
        val INLINE_REPLIES_EXPANSION = RepairSlot("threads.inline_replies_expansion", "", RepairKind.TOKEN)

        /** `BarcelonaSearchUserResultsRefetchableQuery`'s doc id, for a guest's people search: empty, and the read off, until its variables are known. */
        val SEARCH_USERS_DOC_ID = RepairSlot("threads.search_users_doc_id", "", RepairKind.DIGITS)

        /** The variable that query takes the words under. */
        val SEARCH_USERS_VARIABLE = RepairSlot("threads.search_users_variable", "query", RepairKind.TOKEN)

        /** `BarcelonaSearchRecommendedUsersRefetchableQuery`'s doc id, for the accounts Threads recommends a guest: empty, and the read off, until its variables are known. */
        val RECOMMENDED_USERS_DOC_ID = RepairSlot("threads.recommended_users_doc_id", "", RepairKind.DIGITS)

        /** `BarcelonaPostPageDirectRepliesRefetchQuery`'s doc id: a conversation's next replies where the page's own query gives none. */
        val REPLIES_REFETCH_DOC_ID = RepairSlot("threads.replies_refetch_doc_id", "28965099446515427", RepairKind.DIGITS)

        /** `BarcelonaSearchResultsRefetchableQuery`'s doc id: a search's next page where the page's own query gives none. */
        val SEARCH_REFETCH_DOC_ID = RepairSlot("threads.search_refetch_doc_id", "29180318631617452", RepairKind.DIGITS)

        /** `BarcelonaProfileThreadsTabRefetchableDirectQuery`'s doc id: a profile's next threads where the page's own query gives none. */
        val PROFILE_REFETCH_DOC_ID = RepairSlot("threads.profile_refetch_doc_id", "29222947497321867", RepairKind.DIGITS)

        /** `BarcelonaProfileRepostsTabRefetchableDirectQuery`'s doc id: a profile's next reposts where the page's own query gives none. */
        val REPOSTS_REFETCH_DOC_ID = RepairSlot("threads.reposts_refetch_doc_id", "30054019074236536", RepairKind.DIGITS)

        /** `BarcelonaLoggedOutFeedPaginationQuery`'s doc id: the front page's next threads where the page's own query gives none. */
        val FEED_REFETCH_DOC_ID = RepairSlot("threads.feed_refetch_doc_id", "41291939227071792", RepairKind.DIGITS)

        /** Every Threads value `repairs.json` may replace. */
        val SLOTS = listOf(
            GUEST_DOC_ID, GUEST_QUERY_NAME, APP_ID, ASBD_ID, APP_USER_AGENT, WEB_USER_AGENT,
            INLINE_REPLIES_DOC_ID, INLINE_REPLIES_VARIABLE, ABOUT_PROFILE_DOC_ID, ABOUT_PROFILE_VARIABLE, TRENDING_TOPICS_DOC_ID,
            INLINE_REPLIES_EXPANSION, SEARCH_USERS_DOC_ID, SEARCH_USERS_VARIABLE, RECOMMENDED_USERS_DOC_ID,
            REPLIES_REFETCH_DOC_ID, SEARCH_REFETCH_DOC_ID, PROFILE_REFETCH_DOC_ID, REPOSTS_REFETCH_DOC_ID, FEED_REFETCH_DOC_ID,
        )

        /**
         * The slots that ship empty: a read waiting for its doc id, or a value no probe has read yet,
         * which `repairs.json` names only once it is known.
         */
        val WAITING = SLOTS.filter { it.shipped.isEmpty() }

        /**
         * The shipped values with [published]'s Threads entries in force: an entry
         * for a key XTA does not know, or one that does not fit its slot's kind,
         * changes nothing. A withdrawn entry goes back to the shipped value.
         */
        fun published(published: Map<String, String>): ThreadsRepairs = ThreadsRepairs(
            guestDocId = GUEST_DOC_ID.valueIn(published),
            guestQueryName = GUEST_QUERY_NAME.valueIn(published),
            appId = APP_ID.valueIn(published),
            asbdId = ASBD_ID.valueIn(published),
            appUserAgent = APP_USER_AGENT.valueIn(published),
            webUserAgent = WEB_USER_AGENT.valueIn(published),
            inlineRepliesDocId = INLINE_REPLIES_DOC_ID.valueIn(published),
            inlineRepliesVariable = INLINE_REPLIES_VARIABLE.valueIn(published),
            aboutProfileDocId = ABOUT_PROFILE_DOC_ID.valueIn(published),
            aboutProfileVariable = ABOUT_PROFILE_VARIABLE.valueIn(published),
            trendingTopicsDocId = TRENDING_TOPICS_DOC_ID.valueIn(published),
            inlineRepliesExpansion = INLINE_REPLIES_EXPANSION.valueIn(published),
            searchUsersDocId = SEARCH_USERS_DOC_ID.valueIn(published),
            searchUsersVariable = SEARCH_USERS_VARIABLE.valueIn(published),
            recommendedUsersDocId = RECOMMENDED_USERS_DOC_ID.valueIn(published),
            repliesRefetchDocId = REPLIES_REFETCH_DOC_ID.valueIn(published),
            searchRefetchDocId = SEARCH_REFETCH_DOC_ID.valueIn(published),
            profileRefetchDocId = PROFILE_REFETCH_DOC_ID.valueIn(published),
            repostsRefetchDocId = REPOSTS_REFETCH_DOC_ID.valueIn(published),
            feedRefetchDocId = FEED_REFETCH_DOC_ID.valueIn(published),
        )
    }
}
