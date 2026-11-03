package com.aimdi.xtanative.core.session

import com.aimdi.xtanative.core.backup.FollowGroup
import com.aimdi.xtanative.core.backup.FollowedAccount
import com.aimdi.xtanative.core.backup.XtaBackup
import com.aimdi.xtanative.core.feed.FeedKeys
import com.aimdi.xtanative.core.feed.FeedMember
import com.aimdi.xtanative.core.feed.FeedSettings
import com.aimdi.xtanative.core.feed.FeedShaping
import com.aimdi.xtanative.core.feed.GroupFeedPlan
import com.aimdi.xtanative.core.x.XAccount
import com.aimdi.xtanative.core.x.model.XUser

/** How many groups keep their own reply choice and their own repost choice. */
data class OwnChoices(val replies: Int, val reposts: Int)

/**
 * What XTA Native keeps between launches: the X sessions, who and what is
 * followed, the groups and how each feed reads, and the feed's switches.
 * Nothing in it is ever sent to X.
 */
data class Session(
    val accounts: List<XAccount> = emptyList(),
    val following: List<FollowedAccount> = emptyList(),
    val groups: List<FollowGroup> = emptyList(),
    val includeReplies: Boolean = true,
    val includeReposts: Boolean = true,
    /** Videos and GIFs play muted in the feed as they come into view. */
    val autoplay: Boolean = true,
    /** Searches followed here; a group reads the ones put in it. */
    val searches: List<SavedSearch> = emptyList(),
    /** Each feed's own settings, by feed key: [FeedKeys.FOLLOWING] for the Following feed, a group's id for its feed. */
    val feeds: Map<String, FeedSettings> = emptyMap(),
    /** The group each nested group sits in, by the nested one's id. */
    val nesting: Map<String, String> = emptyMap(),
    /** The most posts one load keeps of a followed account, by its id. */
    val postCaps: Map<String, Int> = emptyMap(),
    /** Groups switched off the home feeds: their members' posts stay out of Following and For you. */
    val offFollowing: Set<String> = emptySet(),
    /** Groups pinned as the deck's columns, in order. */
    val deck: List<String> = emptyList(),
) {
    /** The members of groups switched off the home feeds, nested groups included. */
    val excludedFromFollowing: Set<String> get() = memberIdsOf(offFollowing.flatMapTo(HashSet()) { GroupTree.withDescendants(it, nesting) })

    /** The accounts the Following feed reads, as XTA's home feed picks them: shown in it, and in no group switched off it. */
    val feedMembers: List<FeedMember> get() {
        val excluded = excludedFromFollowing
        return following.filter { it.inFeed && it.id !in excluded }.map(::memberOf)
    }

    /** The members [groupId]'s feed reads, or the Following feed's for null. */
    fun membersOf(groupId: String?): List<FeedMember> = if (groupId == null) feedMembers else membersOf(groupId, emptySet())

    /**
     * The members [groupId]'s feed reads with [alsoRead] read alongside: its
     * own and those of every group nested in them, accounts (even ones kept
     * out of the Following feed), then saved searches.
     */
    fun membersOf(groupId: String, alsoRead: Set<String>): List<FeedMember> {
        val ids = memberIdsOf(CombinedGroups(alsoRead).covers(groupId, nesting))
        return following.filter { it.id in ids }.map(::memberOf) + searches.filter { it.query in ids }.map { it.member }
    }

    private fun memberIdsOf(groupIds: Set<String>): Set<String> = groups.filter { it.id in groupIds }.flatMapTo(HashSet()) { it.memberIds }

    private fun memberOf(account: FollowedAccount): FeedMember = account.member.copy(maxPosts = postCaps[account.id])

    /** The groups with someone to read, in XTA's order. */
    val feedGroups: List<FollowGroup> get() = FollowGroup.ordered(groups.filter { membersOf(it.id, emptySet()).isNotEmpty() })

    /** [key]'s own settings: [FeedKeys.FOLLOWING] or a group's id. */
    fun settingsOf(key: String): FeedSettings = feeds[key] ?: FeedSettings()

    fun withSettings(key: String, settings: FeedSettings): Session =
        copy(feeds = if (settings.isDefault) feeds - key else feeds + (key to settings))

    /**
     * How many groups keep their own reply and repost choices: the session's
     * switches do not reach them, which is worth saying where they are set.
     * The Following feed is no group and is not counted.
     */
    val ownChoices: OwnChoices get() {
        val own = groups.mapNotNull { feeds[it.id] }
        return OwnChoices(replies = own.count { it.includeReplies != null }, reposts = own.count { it.includeReposts != null })
    }

    /**
     * Every feed, the Following feed's too, following the session's reply
     * switch again (its repost switch for false). Only the reader asks for
     * this: a switch that overwrote every feed's own choice would be no default.
     */
    fun withoutOwnChoices(replies: Boolean): Session = copy(
        feeds = feeds.mapValues { (_, settings) -> if (replies) settings.copy(includeReplies = null) else settings.copy(includeReposts = null) }
            .filterValues { !it.isDefault },
    )

    /**
     * What [groupId]'s feed reads (the Following feed's for null), with the
     * groups [alsoRead] read alongside a group's: its members, its own reply
     * and repost choices over the session's, its order, rules and the members'
     * post caps. The reader's global filters (language, zen mode, hidden
     * profiles) are added to its shaping by whoever reads it.
     */
    fun feedPlan(groupId: String?, alsoRead: Set<String> = emptySet()): GroupFeedPlan {
        val key = groupId ?: FeedKeys.FOLLOWING
        val settings = settingsOf(key)
        val members = if (groupId == null) feedMembers else membersOf(groupId, alsoRead)
        return GroupFeedPlan(
            key = key,
            members = members,
            includeReplies = settings.includeReplies ?: includeReplies,
            includeReposts = settings.includeReposts ?: includeReposts,
            shaping = FeedShaping(
                sort = settings.sort,
                rules = settings.rules,
                postCaps = members.mapNotNull { member -> member.maxPosts?.takeIf { it > 0 }?.let { member.id to it } }.toMap(),
            ),
            catchUp = settings.catchUp,
        )
    }

    /** The sessions, follows and groups of [backup]; the switches, searches and feed settings stay as they were. */
    fun importing(backup: XtaBackup): Session = copy(accounts = backup.accounts, following = backup.following, groups = backup.groups)

    /** [userId] followed here, or null. */
    fun followed(userId: String): FollowedAccount? = following.firstOrNull { it.id == userId }

    /**
     * [user] followed here from [at] (epoch milliseconds), in the Following
     * feed. Nothing is sent to X. An account already followed keeps its place
     * and switches, with its name and picture brought up to date.
     */
    fun follow(user: XUser, at: Long): Session {
        val known = followed(user.id)
        val account = FollowedAccount(user.id, user.screenName, user.name, user.avatarUrl, user.verified, known?.inFeed ?: true, known?.followedAt ?: at)
        return copy(following = if (known == null) following + account else following.map { if (it.id == user.id) account else it })
    }

    /** [userId] no longer followed here, and out of every group. */
    fun unfollow(userId: String): Session =
        copy(following = following.filterNot { it.id == userId }, groups = groups.map { it.without(userId) }, postCaps = postCaps - userId)

    /** Whether the followed [userId] is read in the Following feed, or only in its groups. */
    fun withInFeed(userId: String, inFeed: Boolean): Session =
        copy(following = following.map { if (it.id == userId) it.copy(inFeed = inFeed) else it })

    /** The most of [userId]'s posts one load keeps; null or 0 keeps them all. */
    fun withPostCap(userId: String, max: Int?): Session =
        copy(postCaps = if (max == null || max <= 0) postCaps - userId else postCaps + (userId to max))

    /** The search [query] followed, or null. */
    fun followedSearch(query: String): SavedSearch? = searches.firstOrNull { it.query == query.trim() }

    /** [query] followed here from [at]; a search already followed keeps its place. */
    fun followSearch(query: String, at: Long): Session {
        val trimmed = query.trim()
        if (trimmed.isEmpty() || followedSearch(trimmed) != null) return this
        return copy(searches = searches + SavedSearch(trimmed, at))
    }

    /** [query] no longer followed, and out of every group. */
    fun unfollowSearch(query: String): Session {
        val trimmed = query.trim()
        return copy(searches = searches.filterNot { it.query == trimmed }, groups = groups.map { it.without(trimmed) })
    }

    /** What X shows now of the followed accounts among [users]: their handle, name and picture. */
    fun withProfilesFrom(users: Iterable<XUser>): Session {
        val refreshed = ProfileRefresh.refreshed(following, users)
        return if (refreshed.isEmpty()) this else copy(following = following.map { refreshed[it.id] ?: it })
    }

    /** [memberId] put in [groupId], or taken out of it. */
    fun withMembership(groupId: String, memberId: String, member: Boolean): Session =
        if (member) addingTo(memberId, listOf(groupId)) else removingFrom(memberId, listOf(groupId))

    /** [memberId] in exactly [groupIds]. */
    fun withMemberships(memberId: String, groupIds: Collection<String>): Session =
        copy(groups = groups.map { if (it.id in groupIds) it.with(memberId) else it.without(memberId) })

    /** [memberId] in [groupIds] too, and in every group it was in. */
    fun addingTo(memberId: String, groupIds: Collection<String>): Session =
        copy(groups = groups.map { if (it.id in groupIds) it.with(memberId) else it })

    /** [memberId] out of [groupIds] and no other group. */
    fun removingFrom(memberId: String, groupIds: Collection<String>): Session =
        copy(groups = groups.map { if (it.id in groupIds) it.without(memberId) else it })

    /** [groupId] holding exactly [memberIds]. */
    fun withGroupMembers(groupId: String, memberIds: Collection<String>): Session =
        updatingGroup(groupId) { it.copy(memberIds = memberIds.distinct()) }

    /** A new group named [name] (trimmed), holding [memberIds]; it follows the session's switches. */
    fun withNewGroup(id: String, name: String, memberIds: List<String>): Session =
        copy(groups = groups + FollowGroup(id, name.trim(), memberIds.distinct()))

    /** [groupId] as [change] makes it: its name, mark, pin or NSFW mark. */
    fun updatingGroup(groupId: String, change: (FollowGroup) -> FollowGroup): Session =
        copy(groups = groups.map { if (it.id == groupId) change(it) else it })

    /** [groupId] gone, with its settings; the groups nested in it come back to the top. */
    fun withoutGroup(groupId: String): Session = copy(
        groups = groups.filterNot { it.id == groupId },
        feeds = feeds - groupId,
        nesting = (nesting - groupId).filterValues { it != groupId },
        offFollowing = offFollowing - groupId,
        deck = deck - groupId,
    )

    /** Every member of [sourceId] moved into [targetId], once, and [sourceId] gone. */
    fun merging(sourceId: String, targetId: String): Session {
        val source = groups.firstOrNull { it.id == sourceId } ?: return this
        if (sourceId == targetId) return this
        return addingToEach(targetId, source.memberIds).withoutGroup(sourceId)
    }

    private fun addingToEach(groupId: String, memberIds: List<String>): Session =
        updatingGroup(groupId) { group -> group.copy(memberIds = (group.memberIds + memberIds).distinct()) }

    /**
     * [groupId] nested in [parentId], or back at the top for null. A nesting
     * that would put a group inside itself, directly or round a chain, is
     * refused (null): its feed would never finish resolving.
     */
    fun nestedIn(groupId: String, parentId: String?): Session? = when {
        parentId == null -> copy(nesting = nesting - groupId)
        GroupTree.wouldLoop(groupId, parentId, nesting) -> null
        else -> copy(nesting = nesting + (groupId to parentId))
    }

    /** Whether [groupId]'s members are kept out of the home feeds. */
    fun withOffFollowing(groupId: String, off: Boolean): Session =
        copy(offFollowing = if (off) offFollowing + groupId else offFollowing - groupId)

    /** [groupId] pinned as the deck's last column, or unpinned. */
    fun toggledDeck(groupId: String): Session = copy(deck = DeckGroups.toggled(deck, groupId))

    /** The deck's columns: the pinned groups that still exist, in pin order. */
    val deckGroups: List<FollowGroup> get() = deck.mapNotNull { id -> groups.firstOrNull { it.id == id } }

    /** [list] as a new group [groupId] named after it, its accounts not followed yet followed from [at]. */
    fun importingList(list: ImportedList, groupId: String, at: Long): Session =
        list.members.fold(this) { session, user -> session.follow(user, at) }.withNewGroup(groupId, list.name, list.members.map { it.id })

    /** [groupId]'s members as a pack to hand on: its accounts and saved searches, no sign-in. */
    fun packOf(groupId: String): GroupPack? {
        val group = groups.firstOrNull { it.id == groupId } ?: return null
        val ids = group.memberIds.toSet()
        return GroupPack(
            group.name,
            following.filter { it.id in ids }.map { PackMember(PackMember.USER, it.id, it.screenName) } +
                searches.filter { it.query in ids }.map { PackMember(PackMember.SEARCH, it.query) },
        )
    }

    /**
     * [pack] as a new group [groupId]: what it names that is not followed yet
     * is followed from [at], and the group holds every member it names.
     */
    fun importingPack(pack: GroupPack, groupId: String, at: Long): Session {
        val known = pack.members.filter { (it.type == PackMember.USER || it.type == PackMember.SEARCH) && it.id.isNotBlank() }
        val followed = known.fold(this) { session, member ->
            when {
                member.type == PackMember.SEARCH -> session.followSearch(member.id, at)
                session.followed(member.id) != null -> session
                else -> (member.screenName ?: member.id).let { name ->
                    session.copy(following = session.following + FollowedAccount(member.id, name, name, null, verified = false, inFeed = true, followedAt = at))
                }
            }
        }
        return followed.withNewGroup(groupId, pack.name, known.map { if (it.type == PackMember.SEARCH) it.id.trim() else it.id })
    }

    private fun FollowGroup.with(memberId: String) = if (memberId in memberIds) this else copy(memberIds = memberIds + memberId)

    private fun FollowGroup.without(memberId: String) = if (memberId in memberIds) copy(memberIds = memberIds - memberId) else this

    companion object {
        fun encode(session: Session): String = SessionCodec.encode(session)

        /** The session in [text]; null when there is none to read. */
        fun decode(text: String): Session? = SessionCodec.decode(text)
    }
}
