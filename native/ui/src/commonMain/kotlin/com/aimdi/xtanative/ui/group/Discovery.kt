package com.aimdi.xtanative.ui.group

import com.aimdi.xtanative.core.backup.FollowedAccount
import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.store.follows.Judgement
import com.aimdi.xtanative.ui.plugins.DiscoverySignal
import com.aimdi.xtanative.ui.plugins.NetworkPost

/** A member of the group vouching for an account, and how (group_discovery.dart's DiscoverySupporter); [at] is when, in epoch milliseconds. */
data class Supporter(
    val memberId: String,
    val handle: String,
    val name: String,
    val signal: DiscoverySignal = DiscoverySignal.FOLLOWED,
    val at: Long? = null,
) {
    /** A member counts once per kind of signal, however many posts carried it. */
    val identity: String get() = "$memberId:${signal.name}"
}

/** The post that shows why an account is suggested, drawn whole when its row is opened. */
sealed interface ShownPost {
    data class X(val post: XPost) : ShownPost

    data class Network(val post: NetworkPost) : ShownPost
}

/**
 * An account the group's members point at that the reader does not follow
 * (group_discovery.dart's DiscoveryAccount). [source] is [Discovery.X] or the
 * plugin whose account it is; an X account met only by name (a reply, a
 * mention) has no [id] until another mention of it names one. [text] and
 * [postUrl] are the post that shows it, [date] when that was written (epoch
 * milliseconds).
 */
data class Candidate(
    val id: String,
    val handle: String,
    val name: String,
    val avatarUrl: String? = null,
    val bio: String? = null,
    val supporters: List<Supporter> = emptyList(),
    val source: String = Discovery.X,
    val text: String = "",
    val postUrl: String = "",
    val date: Long? = null,
    val post: ShownPost? = null,
) {
    val key: String get() = Discovery.identity(source, id.ifEmpty { handle })

    /** What the row shows under the name: the post's text, else the bio. */
    val snippet: String get() = text.ifEmpty { bio.orEmpty() }

    val memberCount: Int get() = supporters.mapTo(HashSet()) { it.memberId }.size

    val signalScore: Int get() = supporters.sumOf { Discovery.weight(it.signal) }

    /** The newest of the support and the post. */
    val newestSupport: Long? get() = (supporters.mapNotNull { it.at } + listOfNotNull(date)).maxOrNull()

    /** Shows a post (else only who points at it). */
    val hasPost: Boolean get() = postUrl.isNotEmpty()
}

/**
 * A group's Discover (group_discovery.dart): every account the members
 * point at on each network, merged, the members of all its mentions
 * vouching, and ranked by how many members vouch, then by how strongly, then
 * by the reader's "more / less like this", then by the newest support, a
 * match with the group's name breaking the last ties.
 */
object Discovery {
    /** X's own candidates' source; every other source is a plugin's id. */
    const val X = "x"

    /** Following lists read on an open, and on "Scan more". */
    const val PER_LOAD = 4
    const val PER_SCAN_MORE = 8

    /** Members whose latest posts are read on an open: twice as many on "Scan more". */
    const val AUTHORS_PER_LOAD = 6

    /** One page of whom a member follows; one page of a member's posts. */
    const val FOLLOWS_PAGE = 100
    const val POSTS_PAGE = 20

    /** How long one source may take before the others stop waiting for it. */
    const val SOURCE_MILLIS = 60_000L

    /** The words a judgement keeps of what was judged. */
    private const val JUDGED_TERMS = 20

    private val word = Regex("[\\p{L}\\p{N}]+")

    fun fetches(more: Boolean): Int = if (more) PER_SCAN_MORE else PER_LOAD

    /** [perLoad] members, or twice that on "Scan more" (discovery_rotation.dart). */
    fun rotating(perLoad: Int, more: Boolean): Int = if (more) perLoad * 2 else perLoad

    /**
     * How long one member's read may take so that [fetches] of them, two at a
     * time, still finish within a minute (XTA's discoveryMemberBudget).
     */
    fun memberBudgetMillis(fetches: Int, concurrency: Int = 2): Long {
        val waves = ((fetches + concurrency - 1) / concurrency).coerceIn(1, fetches.coerceAtLeast(1))
        return (48 / waves).coerceIn(5, 25) * 1000L
    }

    /** A repost, a quote or a network's own pick is a recommendation; a follow or a reply a relationship; a mention often just a conversation. */
    fun weight(signal: DiscoverySignal): Int = when (signal) {
        DiscoverySignal.REPOSTED, DiscoverySignal.QUOTED, DiscoverySignal.SUGGESTED, DiscoverySignal.RELATED -> 3
        DiscoverySignal.FOLLOWED, DiscoverySignal.REPLIED -> 2
        DiscoverySignal.MENTIONED -> 1
    }

    /** An account as Discover tells it apart: its source and its id or handle, without an @, whatever the case. */
    fun identity(source: String, id: String): String = source + ":" + id.trim().removePrefix("@").lowercase()

    /** An X account as Discover tells it apart. */
    fun identity(id: String): String = identity(X, id)

    /** Who the reader follows on X, by id and by handle. */
    fun followedIdentities(following: List<FollowedAccount>): Set<String> =
        following.flatMapTo(HashSet()) { listOf(identity(it.id), identity(it.screenName)) }

    fun follows(followed: Set<String>, candidate: Candidate): Boolean =
        candidate.key in followed || identity(candidate.source, candidate.handle) in followed

    /** XTA's unicodeNameTokens: the words of [text], in lower case, two letters or more. */
    fun tokens(text: String): Set<String> = word.findAll(text.lowercase()).map { it.value }.filterTo(LinkedHashSet()) { it.length >= 2 }

    /** An X account met only by name takes the id another candidate of the same handle has. */
    fun resolved(candidates: List<Candidate>): List<Candidate> {
        val ids = candidates.filter { it.id.isNotEmpty() }.associate { identity(it.source, it.handle) to it.id }
        return candidates.map { if (it.id.isNotEmpty()) it else ids[identity(it.source, it.handle)]?.let { id -> it.copy(id = id) } ?: it }
    }

    /** Every account once: the union of its supporters, newest first, and the newest post among its mentions (mergeDiscoveryAccounts). */
    fun merged(candidates: List<Candidate>): List<Candidate> =
        resolved(candidates).groupBy { it.key }.values.map { same -> same.reduce(::mergedPair) }

    private fun mergedPair(a: Candidate, b: Candidate): Candidate {
        val (newest, other) = if (hasNewerPost(b, a)) b to a else a to b
        val supporters = (a.supporters + b.supporters).distinctBy { it.identity }.sortedByDescending { it.at ?: 0L }
        return newest.copy(
            supporters = supporters,
            avatarUrl = newest.avatarUrl ?: other.avatarUrl,
            bio = newest.bio ?: other.bio,
            name = newest.name.takeUnless { it.isEmpty() || it == newest.handle } ?: other.name.ifEmpty { newest.name },
        )
    }

    private fun hasNewerPost(a: Candidate, b: Candidate): Boolean = when {
        a.post == null && a.date == null -> false
        b.post == null && b.date == null -> true
        else -> (a.date ?: 0L) > (b.date ?: 0L)
    }

    /** [candidates] merged, without those the reader already follows, best first (rankDiscoveryAccounts); ties keep their order. */
    fun ranked(candidates: List<Candidate>, followed: Set<String>, groupName: String, feedback: Map<String, Judgement>): List<Candidate> {
        val usable = merged(candidates.filter { it.handle.isNotEmpty() && (it.id.isNotEmpty() || it.source == X) }).filterNot { follows(followed, it) }
        val group = tokens(groupName)
        val scores = usable.associate { it.key to (feedbackScore(it, feedback) to tokens("${it.name} ${it.handle} ${it.snippet}").intersect(group).size) }
        return usable.sortedWith(
            compareByDescending<Candidate> { it.memberCount }
                .thenByDescending { it.signalScore }
                .thenByDescending { scores.getValue(it.key).first }
                .thenByDescending { it.newestSupport ?: 0L }
                .thenByDescending { scores.getValue(it.key).second },
        )
    }

    /**
     * How the reader's judgements bear on [candidate]: one of it weighs most,
     * then the words it shares with the accounts judged (discoveryFeedbackScore).
     */
    fun feedbackScore(candidate: Candidate, feedback: Map<String, Judgement>): Int {
        val terms = tokens("${candidate.name} ${candidate.snippet}")
        return feedback.entries.filter { it.value.action != Judgement.HIDE }.sumOf { (key, judgement) ->
            val weight = if (key == candidate.key) 8 else terms.intersect(judgement.terms.toSet()).size.coerceIn(0, 4)
            judgement.action * weight
        }
    }

    /** [ordered] with the reader's feedback applied on top, ties keeping their order (applyDiscoveryFeedbackOrder). */
    fun feedbackOrdered(ordered: List<Candidate>, feedback: Map<String, Judgement>): List<Candidate> =
        ordered.withIndex().sortedWith(compareByDescending<IndexedValue<Candidate>> { feedbackScore(it.value, feedback) }.thenBy { it.index }).map { it.value }

    /** What the list shows of [ranked]: not hidden, and not followed unless the reader just added it here ([followed] spares those). */
    fun visible(ranked: List<Candidate>, feedback: Map<String, Judgement>, followed: Set<String>): List<Candidate> =
        ranked.filter { feedback[it.key]?.action != Judgement.HIDE && !follows(followed, it) }

    /** What the reader followed, less what they just added from Discover: a row added says so in place until the next load. */
    fun followedExcept(followed: Set<String>, added: Set<String>, candidates: List<Candidate>): Set<String> {
        val kept = candidates.filter { it.key in added }.flatMapTo(HashSet()) { listOf(it.key, identity(it.source, it.handle)) }
        return followed - kept
    }

    /** The judgement of [candidate] as [action]: the words of what shows it, which later ones are weighed against. */
    fun judged(candidate: Candidate, action: Int): Judgement = Judgement(action, tokens(candidate.snippet).take(JUDGED_TERMS))
}

/** Why a row is here, from the signal most of its supporters share: "Reposted by @a, @b and 3 more", "Followed by 4 members". */
data class DiscoveryReason(val signal: DiscoverySignal, val names: List<String>, val count: Int) {
    /** How many members it does not name. */
    val more: Int get() = (count - names.size).coerceAtLeast(0)

    companion object {
        /** XTA's discoveryReason; null without supporters. */
        fun of(candidate: Candidate): DiscoveryReason? {
            val byKind = LinkedHashMap<DiscoverySignal, MutableList<Supporter>>()
            candidate.supporters.distinctBy { it.identity }.forEach { byKind.getOrPut(it.signal) { ArrayList() } += it }
            if (byKind.isEmpty()) return null
            val (signal, members) = byKind.entries.reduce { best, next -> if (next.value.size > best.value.size) next else best }
            val names = if (signal == DiscoverySignal.FOLLOWED) emptyList() else members.take(2).map { if (it.handle.isEmpty()) it.name else "@" + it.handle }
            return DiscoveryReason(signal, names, members.size)
        }
    }
}
