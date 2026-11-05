package com.aimdi.xtanative.ui.subscriptions

import com.aimdi.xtanative.store.backup.NetworkAccount
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** The accounts the running plugins follow, as the Subscriptions tab lists them beside X's; the app's plugins provide them. */
interface PluginFollows {
    /** Their rows ([SubscriptionList.pluginRow]), every running plugin's, as they change. */
    val rows: Flow<List<SubscriptionRow>>

    /** Their rows as each running plugin says them now, for a reader that asks once (Sort ungrouped). */
    suspend fun current(): List<SubscriptionRow>

    /**
     * [row]'s plugin unfollows it, which takes it out of its groups; answers
     * its undo (XTA's subscription_unfollow.dart), which puts back what the
     * plugin kept and the groups, unless that changed since; null when nothing
     * was taken.
     */
    suspend fun unfollow(row: SubscriptionRow): (suspend () -> Boolean)?

    /** [row]'s page, in its plugin. */
    fun open(row: SubscriptionRow)

    /** Every running plugin's accounts, as each app names them: what the backup writes them as. */
    suspend fun accounts(): List<NetworkAccount>

    /**
     * What [text], typed into a group's add box, names on the running
     * plugins' networks it could belong to ([GroupAddSources]), a row each in
     * that order; a network whose plugin is not running offers none, as XTA
     * offers only the networks switched on.
     */
    fun followables(text: String): List<FollowableRow> = emptyList()

    /** No plugins: X's accounts and searches alone. */
    object None : PluginFollows {
        override val rows: Flow<List<SubscriptionRow>> = flowOf(emptyList())

        override suspend fun current(): List<SubscriptionRow> = emptyList()

        override suspend fun unfollow(row: SubscriptionRow): (suspend () -> Boolean)? = null

        override fun open(row: SubscriptionRow) {}

        override suspend fun accounts(): List<NetworkAccount> = emptyList()
    }
}

/**
 * A row of a group's add box for another network (XTA's GroupAddCandidate):
 * [label] on plugin [plugin]'s network; [follow] follows it there and answers
 * it as a group holds it ("<plugin id>:<account>"), throwing when the network
 * has none.
 */
class FollowableRow(val plugin: String, val label: String, val follow: suspend () -> String) {
    /** What tells it from the other rows, and marks it added. */
    val key: String get() = "$plugin:$label"
}
