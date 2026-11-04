package com.aimdi.xtanative.data.plugins

import com.aimdi.xtanative.core.session.CombinedGroups
import com.aimdi.xtanative.core.session.Session

/**
 * The accounts of other networks the reader put in groups: a member written
 * "<plugin id>:<account>", as the plugins' "Add to group" writes it, is read
 * by that plugin, never searched on X. A saved search can hold a colon too
 * ("from:someone"): a member that is a search followed here stays a search.
 */
object PluginMembers {
    /** What a group holds plugin [pluginId]'s [account] as. */
    fun memberId(pluginId: String, account: String): String = "$pluginId:$account"

    /**
     * The members of [groupId]'s feed (its nested groups' and those of the
     * groups [alsoRead] alongside included) that belong to one of [plugins],
     * by plugin, each account once, in the groups' order.
     */
    fun of(session: Session, groupId: String, alsoRead: Set<String>, plugins: Set<String>): Map<String, List<String>> {
        val covered = CombinedGroups(alsoRead).covers(groupId, session.nesting)
        val ids = session.groups.filter { it.id in covered }.flatMap { it.memberIds }.distinct()
        return accounts(ids, plugins, searches = session.searches.mapTo(HashSet()) { it.query })
    }

    /** The plugin accounts among [memberIds], by plugin: those of [plugins] that are no search in [searches]. */
    fun accounts(memberIds: List<String>, plugins: Set<String>, searches: Set<String> = emptySet()): Map<String, List<String>> =
        memberIds.filter { it !in searches }
            .mapNotNull { id -> split(id)?.takeIf { it.first in plugins } }
            .groupBy({ it.first }, { it.second })

    private fun split(memberId: String): Pair<String, String>? {
        val colon = memberId.indexOf(':')
        if (colon <= 0 || colon == memberId.lastIndex) return null
        return memberId.substring(0, colon) to memberId.substring(colon + 1)
    }
}
