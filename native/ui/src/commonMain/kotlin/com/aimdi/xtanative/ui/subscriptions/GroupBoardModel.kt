package com.aimdi.xtanative.ui.subscriptions

import com.aimdi.xtanative.core.backup.FollowGroup
import com.aimdi.xtanative.core.session.GroupTree
import com.aimdi.xtanative.core.session.Session
import com.aimdi.xtanative.store.settings.GroupsOrder

/** One group on the board or in the list: how deep it is nested, how many members it has and whose faces it shows. */
data class GroupTile(
    val group: FollowGroup,
    val depth: Int = 0,
    val memberCount: Int = 0,
    val faces: List<MemberFace> = emptyList(),
    /** Newer posts were read than the reader's last read mark. */
    val unread: Boolean = false,
) {
    val id: String get() = group.id
}

/** The board's two parts: NSFW groups sit in their own section, under Censored. */
data class GroupSections(val safe: List<GroupTile>, val nsfw: List<GroupTile>) {
    val isEmpty: Boolean get() = safe.isEmpty() && nsfw.isEmpty()
    val all: List<GroupTile> get() = safe + nsfw
}

/**
 * The Groups tab as XTA builds it (_groups.dart, group_model.dart): the
 * groups in the reader's order, NSFW ones last and pinned ones first within
 * each part, each nested group under its parent unless a search is under way.
 */
object GroupBoardModel {
    /**
     * [groups] as XTA's groups query orders them: NSFW last, pinned first, then
     * by [field] ([GroupsOrder.POSITION] is the reader's own order). Ties keep
     * the order they came in.
     */
    fun ordered(groups: List<FollowGroup>, field: GroupsOrder, ascending: Boolean): List<FollowGroup> {
        val byField: Comparator<FollowGroup> = when (field) {
            GroupsOrder.NAME -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            GroupsOrder.CREATED_AT -> compareBy { it.createdAt }
            // SQLite puts a group without a place first, in either direction.
            GroupsOrder.POSITION -> compareBy { it.position ?: Int.MIN_VALUE }
        }
        val within = if (ascending) byField else byField.reversed()
        return groups.sortedWith(compareBy<FollowGroup> { it.nsfw }.thenByDescending { it.pinned }.then(within))
    }

    /**
     * The tiles for [session]'s groups: ordered, filtered by [query] (on the
     * name, whatever its case) and, without a query, nested under their parents.
     */
    fun tiles(session: Session, field: GroupsOrder, ascending: Boolean, query: String, unread: Set<String> = emptySet()): List<GroupTile> {
        val needle = query.trim().lowercase()
        val ordered = ordered(session.groups, field, ascending).filter { needle.isEmpty() || it.name.lowercase().contains(needle) }
        val byId = ordered.associateBy { it.id }
        val shown = if (needle.isEmpty()) GroupTree.treeOrder(ordered.map { it.id }, session.nesting).mapNotNull(byId::get) else ordered
        val faces = facesByGroup(session)
        return shown.map { group ->
            GroupTile(group, GroupTree.depthOf(group.id, session.nesting), group.memberIds.size, faces[group.id].orEmpty(), group.id in unread)
        }
    }

    /** The first [GroupLook.FACES] followed accounts of each group, by handle, whatever its case, as XTA's tiles show them. */
    fun facesByGroup(session: Session): Map<String, List<MemberFace>> {
        val byId = session.following.associateBy { it.id }
        return session.groups.associate { group ->
            group.id to group.memberIds.mapNotNull(byId::get)
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.screenName })
                .take(GroupLook.FACES)
                .map { MemberFace(it.id, it.name.ifBlank { it.screenName }, it.avatarUrl) }
        }
    }

    fun sections(tiles: List<GroupTile>): GroupSections {
        val (nsfw, safe) = tiles.partition { it.group.nsfw }
        return GroupSections(safe, nsfw)
    }

    /** Whether the list may be dragged: only in the reader's own order, and not while searching. */
    fun canReorder(field: GroupsOrder, query: String): Boolean = field == GroupsOrder.POSITION && query.isBlank()

    /**
     * The groups' ids after a drag in the list from [from] to [to], in its rows:
     * the safe groups, the Censored header when there are NSFW ones, then those.
     * A drag stays within its own part; the header itself does not move.
     */
    fun reordered(sections: GroupSections, from: Int, to: Int): List<String>? {
        val header = if (sections.nsfw.isEmpty()) -1 else sections.safe.size
        if (from == header) return null
        val rows: List<String?> = sections.safe.map { it.id } + (if (header >= 0) listOf(null) else emptyList()) + sections.nsfw.map { it.id }
        if (from !in rows.indices) return null
        val target = when {
            header < 0 -> to
            from < header -> to.coerceAtMost(header - 1)
            else -> to.coerceAtLeast(header + 1)
        }
        return SubscriptionList.moved(rows, from, target.coerceIn(0, rows.lastIndex)).filterNotNull()
    }

    /** [session] with each group's place its index in [ids], as XTA saves the reader's own order. */
    fun withPositions(session: Session, ids: List<String>): Session {
        val places = ids.withIndex().associate { (index, id) -> id to index }
        return session.copy(groups = session.groups.map { group -> places[group.id]?.let { group.copy(position = it) } ?: group })
    }
}
