package com.aimdi.xtanative.ui.subscriptions

import com.aimdi.xtanative.core.session.Session
import com.aimdi.xtanative.store.backup.NetworkAccount
import com.aimdi.xtanative.store.backup.NetworkMembers
import com.aimdi.xtanative.store.settings.SubscriptionsOrder
import com.aimdi.xtanative.ui.plugins.PluginSubscription

/**
 * One followed thing on the Subscriptions tab: an X account, a saved search
 * ([search]), whose id, name and handle are its query, or a plugin's account
 * ([plugin]), whose id is its group member's ("<plugin>:<account>").
 */
data class SubscriptionRow(
    val id: String,
    val name: String,
    val screenName: String,
    val avatarUrl: String? = null,
    val verified: Boolean = false,
    /** Read in the Following feed; a saved search is read only in its groups. */
    val inFeed: Boolean = true,
    /** When it was followed, epoch milliseconds. */
    val followedAt: Long = 0,
    val search: Boolean = false,
    /** XTA's "max posts per feed load"; null keeps every post. */
    val postCap: Int? = null,
    /** The plugin whose account it is; null for X's and a search. */
    val plugin: String? = null,
    /** What its plugin says under its name ("r/dartlang", a feed's site); null for X's "@handle" and a search's line. */
    val subtitle: String? = null,
    /** Its plugin's own screen for it. */
    val route: String? = null,
    /** What the reader's own order keeps it as, XTA's id for it: its id, but XTA's subscription id for a plugin's account. */
    val orderKey: String = id,
) {
    /** Its key in a list: an account and a search may share an id, as a search for a number can. */
    val key: String get() = (if (search) "search:" else "account:") + id

    /** An X account: only these are shown in Following or kept out of it and quieted, as XTA offers it. */
    val isX: Boolean get() = !search && plugin == null
}

/** The Subscriptions tab's list, as XTA's subscriptions model orders and filters it (users_model.dart, _list.dart). */
object SubscriptionList {
    /** The caps XTA offers for one account; null is off. */
    val POST_CAPS: List<Int?> = listOf(null, 1, 2, 3, 5, 10)

    /** Every account followed here, then every saved search, then [plugins]' rows: XTA's tables, then its plugins'. */
    fun rows(session: Session, plugins: List<SubscriptionRow> = emptyList()): List<SubscriptionRow> =
        session.following.map {
            SubscriptionRow(it.id, it.name, it.screenName, it.avatarUrl, it.verified, it.inFeed, it.followedAt, postCap = session.postCaps[it.id])
        } + session.searches.map { SubscriptionRow(it.query, it.query, it.query, followedAt = it.followedAt, search = true) } + plugins

    /** [subscription], one of plugin [pluginId]'s accounts, as its row. */
    fun pluginRow(pluginId: String, subscription: PluginSubscription): SubscriptionRow {
        val account = NetworkAccount(pluginId, subscription.backupId, subscription.id)
        return SubscriptionRow(
            id = account.memberId,
            name = subscription.name,
            screenName = subscription.handle,
            avatarUrl = subscription.avatarUrl,
            followedAt = subscription.followedAt,
            plugin = pluginId,
            subtitle = subscription.subtitle,
            route = subscription.route,
            orderKey = NetworkMembers.xtaId(account),
        )
    }

    /**
     * [rows] in the reader's own order when there is one (by XTA's id, or by
     * handle as older builds kept it, the rest after), else sorted by [field].
     */
    fun ordered(rows: List<SubscriptionRow>, field: SubscriptionsOrder, ascending: Boolean, custom: String): List<SubscriptionRow> =
        if (custom.isEmpty()) sorted(rows, field, ascending) else customOrdered(rows, custom)

    private fun sorted(rows: List<SubscriptionRow>, field: SubscriptionsOrder, ascending: Boolean): List<SubscriptionRow> {
        val order: Comparator<SubscriptionRow> = when (field) {
            SubscriptionsOrder.NAME -> compareBy { it.name.lowercase() }
            SubscriptionsOrder.SCREEN_NAME -> compareBy { it.screenName.lowercase() }
            SubscriptionsOrder.CREATED_AT -> compareBy { it.followedAt }
        }
        return rows.sortedWith(if (ascending) order else order.reversed())
    }

    private fun customOrdered(rows: List<SubscriptionRow>, custom: String): List<SubscriptionRow> {
        val left = rows.toMutableList()
        val placed = custom.split(',').mapNotNull { key ->
            left.firstOrNull { it.orderKey == key || it.id == key || it.screenName == key }?.also { found -> left.removeAll { it.key == found.key } }
        }
        return placed + left
    }

    /** The reader's own order as XTA keeps it: the ids, comma-separated. */
    fun customOrder(rows: List<SubscriptionRow>): String = rows.joinToString(",") { it.orderKey }

    /** The rows whose name or handle holds [query], whatever its case. */
    fun filtered(rows: List<SubscriptionRow>, query: String): List<SubscriptionRow> {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return rows
        return rows.filter { it.name.lowercase().contains(needle) || it.screenName.lowercase().contains(needle) }
    }

    /** [rows] with the one at [from] dropped at [to], as a drag leaves it. */
    fun <T> moved(rows: List<T>, from: Int, to: Int): List<T> {
        if (from !in rows.indices || from == to) return rows
        val next = rows.toMutableList()
        val row = next.removeAt(from)
        next.add(to.coerceIn(0, next.size), row)
        return next
    }

    /** A cap the reader may pick; any other stored value reads as off, as XTA's profile sheet reads it. */
    fun capChoice(cap: Int?): Int? = cap.takeIf { it in POST_CAPS }
}
