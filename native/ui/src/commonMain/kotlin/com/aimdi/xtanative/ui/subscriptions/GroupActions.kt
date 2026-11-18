package com.aimdi.xtanative.ui.subscriptions

import androidx.compose.runtime.staticCompositionLocalOf
import com.aimdi.xtanative.core.session.GroupPack
import com.aimdi.xtanative.core.session.PackMember
import com.aimdi.xtanative.core.session.Session
import com.aimdi.xtanative.core.x.model.XUser
import com.aimdi.xtanative.data.SessionStore
import com.aimdi.xtanative.store.settings.SettingsStore
import com.aimdi.xtanative.store.settings.SubscriptionPrefs
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** What the one-by-one sorter writes, so a save can be held or failed in a test. */
interface UngroupedWrites {
    suspend fun addTo(memberId: String, groupIds: List<String>)

    suspend fun removeFrom(memberId: String, groupIds: List<String>)

    suspend fun create(name: String): String
}

/**
 * What the group screens change, all of it in the session on this device:
 * nothing here is sent to X. [scope] is the screen's, and each change is
 * written as soon as it is made.
 */
class GroupActions(private val sessions: SessionStore, private val settings: SettingsStore, private val scope: CoroutineScope) : UngroupedWrites {
    private fun now() = System.currentTimeMillis()

    private fun change(transform: (Session) -> Session) {
        scope.launch { sessions.update(transform) }
    }

    /** The edit sheet's draft saved, its members in the order the list shows them. */
    fun save(draft: GroupDraft, order: List<String>) = change { GroupEdits.saved(it, draft, newId(), now(), order) }

    fun delete(groupId: String) = change { it.withoutGroup(groupId) }

    fun merge(sourceId: String, targetId: String) = change { it.merging(sourceId, targetId) }

    /** [groupId] inside [parentId], or back on its own for null; false when that would put it inside itself. */
    suspend fun nest(groupId: String, parentId: String?): Boolean {
        var applied = true
        sessions.update { session -> session.nestedIn(groupId, parentId) ?: session.also { applied = false } }
        return applied
    }

    fun setPinned(groupId: String, pinned: Boolean) = change { session -> session.updatingGroup(groupId) { it.copy(pinned = pinned) } }

    fun setNsfw(groupId: String, nsfw: Boolean) = change { session -> session.updatingGroup(groupId) { it.copy(nsfw = nsfw) } }

    fun toggleDeck(groupId: String) = change { it.toggledDeck(groupId) }

    /** Whether [groupId]'s members are read in Following and For you. */
    fun setInFollowing(groupId: String, shown: Boolean) = change { it.withOffFollowing(groupId, !shown) }

    /** A new empty group named [name]; its id. */
    override suspend fun create(name: String): String {
        val id = newId()
        sessions.update { GroupEdits.created(it, id, name, now()) }
        return id
    }

    /**
     * [memberId] in exactly [groupIds], the groups added remembered for the
     * picker; "Changes saved" offers to put its groups back as they were.
     */
    fun setMemberships(memberId: String, initial: Set<String>, groupIds: Set<String>) {
        scope.launch {
            var before: Set<String>? = null
            sessions.update { session ->
                before = MembershipUndo.groupsOf(session, memberId)
                session.withMemberships(memberId, groupIds)
            }
            val previous = before ?: return@launch
            if (previous != groupIds) {
                Unfollowing.offer {
                    sessions.update { MembershipUndo.restored(it, memberId, previous) }
                    true
                }
            }
        }
        rememberPicks(initial, groupIds)
    }

    /** [memberId] in [groupIds] too, as the one-by-one sorter adds them. */
    override suspend fun addTo(memberId: String, groupIds: List<String>) = sessions.update { it.addingTo(memberId, groupIds) }

    override suspend fun removeFrom(memberId: String, groupIds: List<String>) = sessions.update { it.removingFrom(memberId, groupIds) }

    /** [user] followed here, as adding someone new to a group follows them first. */
    suspend fun follow(user: XUser) = sessions.update { it.follow(user, now()) }

    fun rememberPicks(initial: Set<String>, chosen: Set<String>) {
        if ((chosen - initial).isEmpty()) return
        scope.launch {
            settings.update { settings ->
                settings.with(SubscriptionPrefs.groupsRecentPicks, GroupPicker.remembered(settings[SubscriptionPrefs.groupsRecentPicks], initial, chosen))
            }
        }
    }

    /** [pack] as a new group; how many members it holds. */
    suspend fun importPack(pack: GroupPack): Int {
        val id = newId()
        sessions.update { it.importingPack(pack, id, now()).updatingGroup(id) { group -> group.copy(createdAt = now()) } }
        return sessions.state.value?.groups?.firstOrNull { it.id == id }?.memberIds?.size ?: 0
    }

    companion object {
        fun newId(): String = UUID.randomUUID().toString()

        /** The pack of what [draft] holds, saved or not, as XTA exports it from the edit sheet: its accounts and saved searches. */
        fun packOf(session: Session, draft: GroupDraft): GroupPack = GroupPack(
            draft.name,
            session.following.filter { it.id in draft.members }.map { PackMember(PackMember.USER, it.id, it.screenName) } +
                session.searches.filter { it.query in draft.members }.map { PackMember(PackMember.SEARCH, it.query) },
        )

        /** The file a pack is saved as. */
        fun packFileName(at: Long): String = "xta-pack-$at.json"
    }
}

/** The app's own [GroupActions], whose changes are written to their end whatever screen asked (the follow menu's group sheet). */
val LocalGroupActions = staticCompositionLocalOf<GroupActions> { error("No group actions provided") }
