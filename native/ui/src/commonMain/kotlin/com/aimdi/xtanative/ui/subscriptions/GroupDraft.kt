package com.aimdi.xtanative.ui.subscriptions

import com.aimdi.xtanative.core.backup.FollowGroup
import com.aimdi.xtanative.core.session.GroupTree
import com.aimdi.xtanative.core.session.Session

/**
 * A group as the edit sheet holds it until OK (_groups_edit.dart): its name,
 * colour, mark and members. [id] is null for a group not made yet.
 */
data class GroupDraft(
    val id: String? = null,
    val name: String = "",
    val color: Long? = null,
    val emoji: String? = null,
    val icon: String? = null,
    val markStyle: Int = GroupMarkStyle.AUTO,
    val members: Set<String> = emptySet(),
) {
    val canSave: Boolean get() = name.isNotBlank()

    /** The mark this draft shows. */
    val markKind: GroupMarkKind get() = GroupLook.markKind(markStyle, emoji, icon)

    /** The colour of the mark: the chosen one, else the one made from the name. */
    val seedColor: Long get() = color ?: GroupLook.fallbackColor(name)

    /** The segment the mark style shows as selected: a generated mark reads as automatic. */
    val shownStyle: Int get() = if (markStyle == GroupMarkStyle.EMOJI || markStyle == GroupMarkStyle.SYMBOL) markStyle else GroupMarkStyle.AUTO

    fun toggled(member: String, on: Boolean): GroupDraft = copy(members = if (on) members + member else members - member)

    /** Every candidate in, or everyone out when some already are. */
    fun toggledAll(candidates: List<String>): GroupDraft = copy(members = if (members.isEmpty()) candidates.toSet() else emptySet())

    /** The emoji typed: its first grapheme, or none for an empty answer; the mark becomes the emoji. */
    fun withEmoji(typed: String): GroupDraft = copy(emoji = GroupLook.firstGrapheme(typed).ifEmpty { null }, markStyle = GroupMarkStyle.EMOJI)

    fun withIcon(key: String): GroupDraft = copy(icon = GroupLook.iconJson(key), markStyle = GroupMarkStyle.SYMBOL)

    /** Back to the automatic mark, which drops the emoji. */
    fun automatic(): GroupDraft = copy(markStyle = GroupMarkStyle.AUTO, emoji = null)

    companion object {
        fun of(group: FollowGroup): GroupDraft =
            GroupDraft(group.id, group.name, group.color, group.emoji, group.icon, GroupMarkStyle.coerce(group.markStyle), group.memberIds.toSet())
    }
}

/** What the edit sheet does to the session (group_model.dart's GroupsModel). */
object GroupEdits {
    /** [id]'s draft, or a blank one for a new group (or a group that is gone). */
    fun draftOf(session: Session, id: String?): GroupDraft =
        id?.let { wanted -> session.groups.firstOrNull { it.id == wanted } }?.let(GroupDraft::of) ?: GroupDraft()

    /**
     * The member list as the sheet opens: the group's members first, then
     * everything else followed, each part in [rows]' order. Fixed while the
     * sheet is open, so a ticked row does not jump.
     */
    fun candidates(rows: List<SubscriptionRow>, members: Set<String>): List<SubscriptionRow> {
        val (inside, outside) = rows.partition { it.id in members }
        return inside + outside
    }

    /** The candidates whose name or handle holds [query]. */
    fun visible(candidates: List<SubscriptionRow>, query: String): List<SubscriptionRow> {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return candidates
        return candidates.filter { "${it.name} ${it.screenName}".lowercase().contains(needle) }
    }

    /**
     * [draft] saved: a new group under [newId], made at [now], or the group
     * brought up to date, its members exactly the draft's, in [order].
     */
    fun saved(session: Session, draft: GroupDraft, newId: String, now: Long, order: List<String> = draft.members.toList()): Session {
        val members = order.filter { it in draft.members } + draft.members.filter { it !in order }
        val id = draft.id?.takeIf { existing -> session.groups.any { it.id == existing } }
        val base = if (id == null) session.withNewGroup(newId, draft.name, members) else session.withGroupMembers(id, members)
        return base.updatingGroup(id ?: newId) { group ->
            group.copy(
                name = draft.name.trim(),
                color = draft.color,
                emoji = draft.emoji,
                icon = draft.icon,
                markStyle = draft.markStyle,
                createdAt = if (id == null) now else group.createdAt,
            )
        }
    }

    /** An empty group named [name] (trimmed), made at [now], as the pickers create one. */
    fun created(session: Session, id: String, name: String, now: Long): Session =
        session.withNewGroup(id, name, emptyList()).updatingGroup(id) { it.copy(createdAt = now) }

    /** The groups [groupId] may be merged into: every other one. */
    fun mergeTargets(session: Session, groupId: String): List<FollowGroup> = session.groups.filter { it.id != groupId }

    /** The groups [groupId] may sit inside: none that would make it contain itself. */
    fun nestTargets(session: Session, groupId: String): List<FollowGroup> =
        session.groups.filter { it.id != groupId && !GroupTree.wouldLoop(groupId, it.id, session.nesting) }
}
