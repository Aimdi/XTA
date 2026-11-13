package com.aimdi.xtanative.networks.substack

/** A publication followed on this device, and when (XTA's `created_at`, `yyyy-MM-dd HH:mm:ss`). Nothing is told to Substack. */
data class SubstackFollow(val publication: SubstackPublication, val createdAt: String? = null)

/** The follows as XTA's follows store keeps and lists them. */
object SubstackFollowing {
    /** By name, case aside, as XTA reads its table; then the pinned ones first, in pin order. */
    fun ordered(follows: List<SubstackPublication>, pins: List<String>): List<SubstackPublication> {
        val byName = follows.sortedBy { it.name.lowercase() }
        if (pins.isEmpty()) return byName
        val byId = byName.associateBy { it.id }
        val pinned = pins.mapNotNull { byId[it] }.distinctBy { it.id }
        val pinnedIds = pinned.mapTo(HashSet()) { it.id }
        return pinned + byName.filterNot { it.id in pinnedIds }
    }

    /**
     * [follows] with [publication] followed: placed as [SubstackFollows.place]
     * says, over the follow with that id (which keeps when it was followed),
     * else added, followed [now].
     */
    fun followed(follows: List<SubstackFollow>, publication: SubstackPublication, now: String): List<SubstackFollow> {
        val placed = SubstackFollows.place(follows.map { it.publication }, publication)
        val known = follows.firstOrNull { it.publication.id == placed.id }
        val follow = SubstackFollow(placed, known?.createdAt ?: now)
        return if (known == null) follows + follow else follows.map { if (it === known) follow else it }
    }

    fun unfollowed(follows: List<SubstackFollow>, id: String): List<SubstackFollow> = follows.filterNot { it.publication.id == id }

    /**
     * [follows] with a fresher copy of a followed publication ([fresh], from
     * its page): written over the follow it is, and moved to its own id when
     * placing it gives another, as XTA's publication page writes its header back.
     * The id it left, or null when it stayed.
     */
    fun refreshed(follows: List<SubstackFollow>, fresh: SubstackPublication, now: String): Pair<List<SubstackFollow>, String?> {
        val follow = SubstackFollows.followOf(follows.map { it.publication }, fresh) ?: return follows to null
        val placed = SubstackFollows.place(follows.map { it.publication }, fresh)
        val written = followed(follows, placed, now)
        return if (placed.id == follow.id) written to null else unfollowed(written, follow.id) to follow.id
    }
}
