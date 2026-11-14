package com.aimdi.xtanative.networks.threads

import com.aimdi.xtanative.core.json.J
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * A list whose next pages threads.com's client asks by a refetch query of
 * its own, not the one that preloaded its first page. CI's probe read each
 * one's doc id and variables out of the bundles ([ThreadsDocIds]); the id
 * is a repair slot ([ThreadsRepairs.refetchDocId]). A refetch query is a
 * fallback only: asked where the page's own query names no doc id, is
 * refused, or brings nothing, so a list that pages today pages as it did.
 * All three are queries; nothing here writes to Threads.
 */
enum class ThreadsRefetch(val query: String, val idVariable: String) {
    /** A conversation's replies: variables `after`, `filterType`, `first`, `postID`, `sortOrder`. */
    REPLIES("BarcelonaPostPageDirectRepliesRefetchQuery", "postID"),

    /** A search's results: variables `after`, `first`, `query`, `recent`, `requested_subtab`, `search_surface`, `tagID` and more. */
    SEARCH("BarcelonaSearchResultsRefetchableQuery", "query"),

    /** A profile's threads: variables `after`, `first`, `userID` and more. */
    PROFILE_THREADS("BarcelonaProfileThreadsTabRefetchableDirectQuery", "userID"),
    ;

    /** The variables a first page that named none is followed with: the one thing the query is about, [id]; null without it. */
    fun seed(id: String?): JsonObject? = id?.trim()?.ifEmpty { null }?.let { JsonObject(mapOf(idVariable to JsonPrimitive(it))) }

    companion object {
        /** The refetch asked under [query]'s name; null for any other query. */
        fun named(query: String): ThreadsRefetch? = entries.firstOrNull { it.query == query }
    }
}

/**
 * The variables Relay itself provides threads.com's queries
 * (`__relay_internal__pv__Barcelona…relayprovider`): flags a query declares
 * beside its own variables and may refuse to run without. A page names the
 * values of those its preloaded queries take; a query asked by a doc id
 * from a bundle is sent every name CI's probes saw declared, with the value
 * the pages read gave it, else what a guest's would be.
 */
object ThreadsRelayProviders {
    private const val PREFIX = "__relay_internal__pv__"
    private const val WINDOW = 160
    private const val MOST = 200
    private val value = Regex("""__relay_internal__pv__([A-Za-z0-9_]{1,120})"\s*:\s*(true|false)""")

    /** The flags a guest's client holds true whatever the page: it is logged out, and authors' names are wanted. */
    private val TRUE_FOR_A_GUEST = setOf("IsLoggedOut", "HasCommunitiesOrLoggedOut", "ShouldFetchPostAuthorFullName")

    /** Every provided variable CI's probes saw a query declare or a page name, without `Barcelona` and `relayprovider`. */
    private val SEEN: List<String> = (
        "DiscussionSummaries GenAIRepliesEnabled HasAMAConsumption HasActivityFeedTaskView HasCommunities " +
            "HasCommunitiesOrLoggedOut HasCommunityBobbleheads HasCommunityChampionCard HasCommunityEmojiUpdateCard " +
            "HasCommunityEntityCard HasCommunityFounderCard HasCommunityGreenDot HasCommunityIntroCard " +
            "HasCommunityNoteWriteEntrypoint HasCommunityPermalinkPivots HasCommunityTopContributors HasCommunityTrendingBadging " +
            "HasCustomLikesConsumption HasDearAlgoConsumption HasDearAlgoWebProduction HasEventBadge HasGameScoreShare " +
            "HasGhostPostEmojiActivation HasGhostPostProduction HasInsightsMetricShareCard HasInsightsPermalinkUFI " +
            "HasInsightsProfileM2 HasKeyboardShortcuts HasLiveChatHostlessUI HasMWRConsumption HasMembersOnlyReplyConsumption " +
            "HasMessaging HasMetaAiContentAttachments HasNewsletterLinkPreview HasNewspaperLinkStyle HasPermalinkIndentation " +
            "HasPermalinkReplyToReplyComposer HasPodcastTranscriptConsumption HasPodcastV2Consumption HasPodcastV2Production " +
            "HasPostAuthorNotifControls HasPrivateRepliesDeprecation HasProfileCardsCollection HasProfileCover " +
            "HasProfileInformTreatmentInfo HasProfileInlineGhostPosts HasProfileUpdatePost HasPublicViewCountCard " +
            "HasPublicViewCountShareCard HasSERPHeader HasSaveCount HasScorecardCommunity HasSportTeamAllegianceCard " +
            "HasStitchedAttachment HasTopLevelSaveButton HasUnseenReplies HasViewerReplied HasWebFavicons IsCrawler IsInternalUser " +
            "IsLoggedIn IsLoggedOut IsLoggedOutShareTestEnabled IsSearchDiscoveryEnabled MessagesHasLiveChatMessaging " +
            "MessagingHasMetaAIBot OptInNotifWebEnabled OptionalCookiesEnabled ShouldFetchPostAuthorFullName " +
            "ShouldFulfillLightboxQuery ShouldShowFediverseM075Features ShouldShowFediverseM1Features"
        ).split(' ').filter { it.isNotEmpty() }

    /** What a guest is sent for each name seen, before any page said otherwise. */
    val defaults: Map<String, Boolean> = SEEN.associate { "Barcelona${it}relayprovider" to (it in TRUE_FOR_A_GUEST) }

    /** The provided variables [html] names with a true or false value, without their prefix, the first value of each. */
    fun read(html: String): Map<String, Boolean> {
        val found = LinkedHashMap<String, Boolean>()
        var at = html.indexOf(PREFIX)
        while (at >= 0 && found.size < MOST) {
            value.find(html.subSequence(at, minOf(html.length, at + WINDOW)))?.takeIf { it.range.first == 0 }
                ?.let { found.putIfAbsent(it.groupValues[1], it.groupValues[2] == "true") }
            at = html.indexOf(PREFIX, at + PREFIX.length)
        }
        return found
    }

    /**
     * [variables] with every provided variable a query might declare:
     * [known]'s values over the [defaults], [variables]' own over both. With
     * [only], just the names it holds, for a query whose bundle says which
     * it declares.
     */
    fun with(variables: JsonObject, known: Map<String, Boolean> = emptyMap(), only: Set<String>? = null): JsonObject =
        JsonObject((defaults + known).filterKeys { only == null || it in only }.entries.associate { (name, on) -> PREFIX + name to JsonPrimitive(on) } + variables)

    /** Whether [name] is a provided variable's. */
    fun isProvided(name: String): Boolean = name.startsWith(PREFIX)
}

/**
 * The accounts a guest's people reads answer with
 * ([ThreadsBundleReads.SEARCH_USERS], [ThreadsBundleReads.RECOMMENDED_USERS]).
 * Their answers were never seen, so an account is read wherever under
 * `data` an object carries a username and an id of digits, each once, in
 * the answer's order; a post, were an answer to carry any, is not a result
 * and neither is its author.
 */
object ThreadsGuestPeople {
    /** How many accounts one read keeps. */
    const val MAX = 40
    private val POST_KEYS = setOf("caption", "code", "text_post_app_info", "thread_items")

    /** The accounts under the `data` of [roots]; empty when they hold none. */
    fun users(roots: List<JsonElement>): List<ThreadsProfile> = roots.asSequence()
        .mapNotNull { (it as? JsonObject)?.get("data") }
        .flatMap { users(it, 0) }
        .distinctBy { it.username.lowercase() }
        .take(MAX)
        .toList()

    private fun users(node: JsonElement, depth: Int): Sequence<ThreadsProfile> = when {
        depth > ThreadsJson.MAX_DEPTH -> emptySequence()
        node is JsonArray -> node.asSequence().flatMap { users(it, depth + 1) }
        node is JsonObject && POST_KEYS.any { it in node } -> emptySequence()
        node is JsonObject -> person(node)?.let { sequenceOf(it) } ?: node.values.asSequence().flatMap { users(it, depth + 1) }
        else -> emptySequence()
    }

    /** An object with a username and an id of digits, as a row shows it: without the counts a row never carries. */
    private fun person(node: JsonObject): ThreadsProfile? {
        if ("username" !in node || ("pk" !in node && "id" !in node)) return null
        return ThreadsParser.profile(J(node))
            ?.takeIf { it.pk.isNotEmpty() && it.pk.all(Char::isDigit) }
            ?.copy(followerCount = null, followingCount = null, mediaCount = null)
    }
}
