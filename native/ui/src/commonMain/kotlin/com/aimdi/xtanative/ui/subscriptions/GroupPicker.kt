package com.aimdi.xtanative.ui.subscriptions

import com.aimdi.xtanative.core.backup.FollowGroup
import com.aimdi.xtanative.core.session.GroupPicks

/** What the chips of the group picker are under. */
enum class PickSection { RECENT, ALL, MATCHES, NO_MATCH }

/** The picker's search, whether every group is out, and the groups made in it, newest first. */
data class PickView(val query: String = "", val showAll: Boolean = false, val createdIds: List<String> = emptyList()) {
    fun searching(text: String) = copy(query = text)

    fun showingAll() = copy(showAll = true)

    /** [id] was made from the search, which it empties. */
    fun created(id: String) = copy(query = "", createdIds = listOf(id) + createdIds)
}

/** The chips for one moment: the groups, under which label, whether the typed name can be a new group, and whether All has more. */
data class PickChips(val groups: List<FollowGroup>, val section: PickSection, val canCreate: Boolean, val hasMore: Boolean)

/** Where the account is, in the line under the chips. */
sealed interface PickStatus {
    data object None : PickStatus

    data class One(val name: String) : PickStatus

    data class Two(val first: String, val second: String) : PickStatus

    data class Many(val count: Int) : PickStatus
}

/**
 * The membership sheet's rules (group_membership_sheet.dart, group_pick.dart,
 * quick_pick.dart): the groups in the order it opened with, fixed so a chip
 * never moves under a finger; the first few until All; a search that offers
 * to make the group it does not find.
 */
object GroupPicker {
    /** How many chips show before All. */
    const val SHOWN = 6

    /** The order the sheet opens in: the account's groups, then the recently picked, then the rest. */
    fun openOrder(groups: List<FollowGroup>, selected: Collection<String>, recent: List<String>): List<String> =
        GroupPicks.order(groups.map { it.id }, selected, recent)

    /** The groups made in the sheet first, then [openOrder], then any that came since, each once while it exists. */
    fun ordered(groups: List<FollowGroup>, openOrder: List<String>, createdIds: List<String>): List<FollowGroup> {
        val byId = groups.associateBy { it.id }
        return (createdIds + openOrder + byId.keys).distinct().mapNotNull(byId::get)
    }

    fun chips(ordered: List<FollowGroup>, view: PickView, shown: Int = SHOWN): PickChips {
        val query = view.query.trim()
        if (query.isNotEmpty()) {
            val needle = query.lowercase()
            val matches = ordered.filter { it.name.lowercase().contains(needle) }
            val taken = ordered.any { it.name.trim().lowercase() == needle }
            return PickChips(matches, if (matches.isEmpty()) PickSection.NO_MATCH else PickSection.MATCHES, canCreate = !taken, hasMore = false)
        }
        val hasMore = !view.showAll && ordered.size > shown
        return PickChips(if (hasMore) ordered.take(shown) else ordered, if (view.showAll) PickSection.ALL else PickSection.RECENT, false, hasMore)
    }

    /** As many chips as the account has groups, at least [SHOWN]. */
    fun shownFor(selected: Collection<String>): Int = maxOf(SHOWN, selected.size)

    fun status(ordered: List<FollowGroup>, chosen: Set<String>): PickStatus {
        val names = ordered.filter { it.id in chosen }.map { it.name }
        return when (names.size) {
            0 -> PickStatus.None
            1 -> PickStatus.One(names[0])
            2 -> PickStatus.Two(names[0], names[1])
            else -> PickStatus.Many(names.size)
        }
    }

    /** [chosen] with [id] ticked, or unticked when it was. */
    fun toggled(chosen: Set<String>, id: String): Set<String> = if (id in chosen) chosen - id else chosen + id

    /** The answer the sheet gives as it closes: the groups chosen, or null when they are the ones it opened with. */
    fun answer(initial: Set<String>, chosen: Set<String>): Set<String>? = chosen.takeIf { it != initial }

    /** XTA keeps the recent picks comma-separated, newest first. */
    fun recent(stored: String): List<String> = stored.split(',').filter { it.isNotEmpty() }

    /** The recent picks after [chosen] closed the sheet opened on [initial]: what was added comes first. */
    fun remembered(stored: String, initial: Set<String>, chosen: Set<String>): String =
        GroupPicks.remembered(recent(stored), (chosen - initial).toList()).joinToString(",")
}
