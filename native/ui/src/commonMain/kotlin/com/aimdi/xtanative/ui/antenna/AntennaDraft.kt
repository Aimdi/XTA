package com.aimdi.xtanative.ui.antenna

import com.aimdi.xtanative.core.session.Session
import com.aimdi.xtanative.core.x.model.XThread
import com.aimdi.xtanative.store.antenna.Antenna
import com.aimdi.xtanative.store.antenna.AntennaQuery
import com.aimdi.xtanative.store.antenna.AntennaScope

/**
 * The antenna editor as it is being filled in: the terms as typed, split at
 * commas and line breaks only when saved. [id] is the antenna changed, null
 * for a new one.
 */
data class AntennaDraft(
    val id: String? = null,
    val name: String = "",
    val include: String = "",
    val exclude: String = "",
    val scope: AntennaScope = AntennaScope.SEARCH,
) {
    val isNew: Boolean get() = id == null
    val includeTerms: List<String> get() = Antenna.terms(include)
    val excludeTerms: List<String> get() = Antenna.terms(exclude)

    /** XTA's OK: a name, and at least one term to listen for. */
    val canSave: Boolean get() = Antenna.canSave(name, includeTerms)

    companion object {
        /** The editor for [antenna], or an empty one for a new antenna. */
        fun of(antenna: Antenna?): AntennaDraft = antenna?.let {
            AntennaDraft(it.id, it.name, Antenna.typed(it.includeTerms), Antenna.typed(it.excludeTerms), it.scope)
        } ?: AntennaDraft()
    }
}

/** An antenna's feed: one search of its terms, newest first, and for a following antenna only followed accounts' posts. */
object AntennaFeed {
    /** The accounts followed here, when the feed opened: a following antenna keeps their posts. */
    fun followedIds(session: Session?): Set<String> = session?.following?.mapTo(HashSet()) { it.id }.orEmpty()

    /** Who wrote a row, as the antenna reads it: the first post's author, the reposter for a repost. */
    fun authorOf(thread: XThread): String? = thread.posts.firstOrNull()?.author?.id

    /** What [antenna] keeps of a page of [threads]. */
    fun kept(antenna: Antenna, threads: List<XThread>, followedIds: Set<String>): List<XThread> =
        AntennaQuery.kept(antenna, threads, followedIds) { authorOf(it) }
}
