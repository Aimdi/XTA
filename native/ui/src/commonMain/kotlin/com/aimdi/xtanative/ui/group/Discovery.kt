package com.aimdi.xtanative.ui.group

import com.aimdi.xtanative.core.backup.FollowedAccount
import com.aimdi.xtanative.store.follows.Judgement
import com.aimdi.xtanative.store.follows.KnownFollow

/** A member of the group vouching for an account by following it (group_discovery.dart's DiscoverySupporter). */
data class Supporter(val memberId: String, val handle: String, val name: String)

/** An account the group's members follow that the reader does not (group_discovery.dart's DiscoveryAccount). */
data class Candidate(
    val id: String,
    val handle: String,
    val name: String,
    val avatarUrl: String? = null,
    val bio: String? = null,
    val supporters: List<Supporter> = emptyList(),
) {
    val key: String get() = Discovery.identity(id)

    /** What the row shows under the name: the bio. */
    val snippet: String get() = bio.orEmpty()

    val memberCount: Int get() = supporters.mapTo(HashSet()) { it.memberId }.size
}

/**
 * A group's Discover (group_discovery.dart, group_discovery_follows.dart,
 * group_discovery_x.dart): the accounts its X members follow that the reader
 * does not, merged, ranked by how many members follow each, then by the
 * reader's "more / less like this", then by words shared with the group's
 * name. Each open reads a rotating few members' Following lists.
 */
object Discovery {
    /** Lists read on an open, and on "Scan more". */
    const val PER_LOAD = 4
    const val PER_SCAN_MORE = 8

    /** One page of whom a member follows. */
    const val FOLLOWS_PAGE = 100

    /** The words a judgement keeps of what was judged. */
    private const val JUDGED_TERMS = 20

    private val word = Regex("[\\p{L}\\p{N}]+")

    fun fetches(more: Boolean): Int = if (more) PER_SCAN_MORE else PER_LOAD

    /**
     * How long one member's list may take so that [fetches] of them, two at a
     * time, still finish within a minute (XTA's discoveryMemberBudget).
     */
    fun memberBudgetMillis(fetches: Int, concurrency: Int = 2): Long {
        val waves = ((fetches + concurrency - 1) / concurrency).coerceIn(1, fetches.coerceAtLeast(1))
        return (48 / waves).coerceIn(5, 25) * 1000L
    }

    /** An account as Discover tells it apart: by its id or its handle, without an @, whatever the case. */
    fun identity(id: String): String = "x:" + id.trim().removePrefix("@").lowercase()

    /** Who the reader follows here, by id and by handle. */
    fun followedIdentities(following: List<FollowedAccount>): Set<String> =
        following.flatMapTo(HashSet()) { listOf(identity(it.id), identity(it.screenName)) }

    fun follows(followed: Set<String>, candidate: Candidate): Boolean = candidate.key in followed || identity(candidate.handle) in followed

    /** XTA's unicodeNameTokens: the words of [text], in lower case, two letters or more. */
    fun tokens(text: String): Set<String> = word.findAll(text.lowercase()).map { it.value }.filterTo(LinkedHashSet()) { it.length >= 2 }

    /** Every account a member follows, outside the group, with that member vouching (xFollowDiscoveryAccounts). */
    fun candidates(byMember: Map<String, List<KnownFollow>>, members: Map<String, FollowedAccount>): List<Candidate> =
        byMember.flatMap { (memberId, follows) ->
            val member = members[memberId] ?: return@flatMap emptyList()
            follows.filter { it.id !in members }.map { follow ->
                Candidate(follow.id, follow.handle, follow.name, follow.avatarUrl, follow.bio, listOf(Supporter(member.id, member.screenName, member.name)))
            }
        }

    /** Every account once, with the members of all its mentions vouching, each member once. */
    fun merged(candidates: List<Candidate>): List<Candidate> =
        candidates.groupBy { it.key }.values.map { same ->
            val first = same.first()
            first.copy(
                avatarUrl = same.firstNotNullOfOrNull { it.avatarUrl },
                bio = same.firstNotNullOfOrNull { it.bio },
                supporters = same.flatMap { it.supporters }.distinctBy { it.memberId },
            )
        }

    /**
     * [candidates] merged, without those the reader already follows, best
     * first: the most members vouching, then the reader's feedback, then the
     * words shared with [groupName]; ties keep their order.
     */
    fun ranked(candidates: List<Candidate>, followed: Set<String>, groupName: String, feedback: Map<String, Judgement>): List<Candidate> {
        val usable = merged(candidates.filter { it.id.isNotEmpty() && it.handle.isNotEmpty() && !follows(followed, it) })
        val group = tokens(groupName)
        val scores = usable.associate { it.key to (feedbackScore(it, feedback) to tokens("${it.name} ${it.handle} ${it.snippet}").intersect(group).size) }
        return usable.sortedWith(
            compareByDescending<Candidate> { it.memberCount }
                .thenByDescending { scores.getValue(it.key).first }
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

    /** What the list shows of [ranked]: not hidden, and not followed unless the reader just added it here ([followed] spares those). */
    fun visible(ranked: List<Candidate>, feedback: Map<String, Judgement>, followed: Set<String>): List<Candidate> =
        ranked.filter { feedback[it.key]?.action != Judgement.HIDE && !follows(followed, it) }

    /** What the reader followed, less what they just added from Discover: a row added says so in place until the next load. */
    fun followedExcept(followed: Set<String>, added: Set<String>, candidates: List<Candidate>): Set<String> {
        val kept = candidates.filter { it.key in added }.flatMapTo(HashSet()) { listOf(it.key, identity(it.handle)) }
        return followed - kept
    }

    /** The judgement of [candidate] as [action]: the words of its bio, which later ones are weighed against. */
    fun judged(candidate: Candidate, action: Int): Judgement = Judgement(action, tokens(candidate.snippet).take(JUDGED_TERMS))

    /** How many of the group's [members] have had their lists read: the header's "12 of 40 members". */
    fun covered(byMember: Map<String, List<KnownFollow>>, members: Collection<String>): Int = members.count { it in byMember }
}
