package com.aimdi.xtanative.ui.home

import com.aimdi.xtanative.core.backup.FollowGroup
import com.aimdi.xtanative.core.session.Session
import com.aimdi.xtanative.core.x.XAccount
import com.aimdi.xtanative.store.settings.AccountPrefs
import com.aimdi.xtanative.store.settings.HomePrefs
import com.aimdi.xtanative.store.settings.Settings

/**
 * Home feed accounts being edited (XTA's home filter sheet): the X accounts
 * For you leaves out, and the groups kept off the home feeds. Nothing is kept
 * until it is applied, so the draft may turn every account off on the way to
 * picking a few; [keepsAnAccount] stops that being applied.
 */
data class HomeFilterDraft(val accountsOff: Set<String> = emptySet(), val groupsOff: Set<String> = emptySet()) {
    fun off(groups: Boolean): Set<String> = if (groups) groupsOff else accountsOff

    /** [id] of its section on or off. */
    fun toggled(groups: Boolean, id: String, on: Boolean): HomeFilterDraft {
        val next = if (on) off(groups) - id else off(groups) + id
        return if (groups) copy(groupsOff = next) else copy(accountsOff = next)
    }

    /** Exactly [off] turned off in its section, everything else there on. */
    fun selecting(groups: Boolean, off: Set<String>): HomeFilterDraft = if (groups) copy(groupsOff = off) else copy(accountsOff = off)

    /** Only [id] left on among [ids]: a long press. */
    fun only(groups: Boolean, id: String, ids: Collection<String>): HomeFilterDraft = selecting(groups, ids.toSet() - id)

    /** How many of [ids] are on. */
    fun active(groups: Boolean, ids: Collection<String>): Int = ids.count { it !in off(groups) }

    /** At least one account stays on, or there is none. */
    fun keepsAnAccount(accountIds: Collection<String>): Boolean = accountIds.isEmpty() || accountIds.any { it !in accountsOff }

    companion object {
        /** XTA's "Reset filters": everything on. */
        val NONE_OFF = HomeFilterDraft()
    }
}

/** Where the home feed filter is kept, and how much of it is on. */
object HomeFilter {
    /** The filter as kept: the accounts For you leaves out (settings) and the groups off the home feeds (the session). */
    fun draftOf(session: Session, settings: Settings): HomeFilterDraft =
        HomeFilterDraft(settings[AccountPrefs.disabledAccountIds].toSet(), session.offFollowing)

    /** How many accounts and groups that exist are turned off: the dot on the button. */
    fun turnedOff(draft: HomeFilterDraft, accounts: List<XAccount>, groups: List<FollowGroup>): Int =
        accounts.count { it.id in draft.accountsOff } + groups.count { it.id in draft.groupsOff }

    /** [settings] holding [draft]: For you's accounts, and XTA's own copy of the groups kept off. */
    fun applied(settings: Settings, draft: HomeFilterDraft): Settings = settings
        .with(AccountPrefs.disabledAccountIds, draft.accountsOff.toList())
        .with(HomePrefs.followingHiddenGroupIds, draft.groupsOff.toList())

    /** [session] with exactly the groups of [draft] kept off the home feeds. */
    fun applied(session: Session, draft: HomeFilterDraft): Session =
        session.groups.fold(session) { next, group -> next.withOffFollowing(group.id, group.id in draft.groupsOff) }
}
