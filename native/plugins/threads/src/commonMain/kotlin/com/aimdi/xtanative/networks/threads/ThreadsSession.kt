package com.aimdi.xtanative.networks.threads

import com.aimdi.xtanative.core.repairs.RepairKind
import com.aimdi.xtanative.core.repairs.RepairSlot
import kotlin.random.Random
import kotlinx.coroutines.delay

/**
 * A Threads session the reader pasted, if any: a browser's Cookie header for
 * threads.com, and an `IGT:2` Bearer for the app API. Optional: public pages
 * are read without one. [useSessionApis] is the reader's opt-in to spend the
 * cookies on followed accounts and people search, the one setting that can
 * cost them their Threads login.
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
 * when it was taken, and when a parked session may talk to Meta again. Times
 * are epoch milliseconds.
 */
data class ThreadsKept(
    val deviceId: String? = null,
    val userIds: Map<String, String> = emptyMap(),
    val guestLsd: String? = null,
    val guestLsdAt: Long? = null,
    val cooldownUntil: Long? = null,
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
 */
class ThreadsPacing(
    val sessionGapMillis: Long = 3_000,
    val guestGapMillis: Long = 300,
    val sessionJitterMillis: Int = 750,
    val guestJitterMillis: Int = 200,
    private val random: Random = Random.Default,
    internal val sleep: suspend (Long) -> Unit = { delay(it) },
) {
    /** The gap before the next departure. */
    internal fun gap(asGuest: Boolean): Long {
        val jitter = if (asGuest) guestJitterMillis else sessionJitterMillis
        return (if (asGuest) guestGapMillis else sessionGapMillis) + (if (jitter > 0) random.nextInt(jitter) else 0)
    }

    companion object {
        /** No gap at all: for tests. */
        val NONE = ThreadsPacing(0, 0, 0, 0)
    }
}

/**
 * Values Meta may change, each with the one XTA ships. XTA's published
 * repairs (`repairs.json`) can replace one with a value of the same kind, so a
 * rotated doc id is fixed without a release; nothing here names a host.
 *
 * The reads asked by a doc id that only threads.com's bundles name
 * ([ThreadsDocIds]) ship with none: [inlineRepliesDocId],
 * [aboutProfileDocId] and [trendingTopicsDocId] are empty, and each read
 * is off, asking Meta nothing, until its id is shipped or published. The
 * variable such a query takes its id under is a value too, since only a
 * bundle says its name.
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
) {
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

        /** Every Threads value `repairs.json` may replace. */
        val SLOTS = listOf(
            GUEST_DOC_ID, GUEST_QUERY_NAME, APP_ID, ASBD_ID, APP_USER_AGENT, WEB_USER_AGENT,
            INLINE_REPLIES_DOC_ID, INLINE_REPLIES_VARIABLE, ABOUT_PROFILE_DOC_ID, ABOUT_PROFILE_VARIABLE, TRENDING_TOPICS_DOC_ID,
        )

        /** The slots that ship empty: each a read waiting for its doc id, which `repairs.json` names only once it is known. */
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
        )
    }
}
