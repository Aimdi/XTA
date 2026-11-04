package com.aimdi.xtanative.ui.subscriptions

import com.aimdi.xtanative.store.backup.NetworkAccount
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** The accounts the running plugins follow, as the Subscriptions tab lists them beside X's; the app's plugins provide them. */
interface PluginFollows {
    /** Their rows ([SubscriptionList.pluginRow]), every running plugin's, as they change. */
    val rows: Flow<List<SubscriptionRow>>

    /** [row]'s plugin unfollows it, which takes it out of its groups. */
    fun unfollow(row: SubscriptionRow)

    /** [row]'s page, in its plugin. */
    fun open(row: SubscriptionRow)

    /** Every running plugin's accounts, as each app names them: what the backup writes them as. */
    suspend fun accounts(): List<NetworkAccount>

    /** No plugins: X's accounts and searches alone. */
    object None : PluginFollows {
        override val rows: Flow<List<SubscriptionRow>> = flowOf(emptyList())

        override fun unfollow(row: SubscriptionRow) {}

        override fun open(row: SubscriptionRow) {}

        override suspend fun accounts(): List<NetworkAccount> = emptyList()
    }
}
