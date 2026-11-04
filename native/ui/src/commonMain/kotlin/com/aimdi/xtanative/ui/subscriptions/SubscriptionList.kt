package com.aimdi.xtanative.ui.subscriptions

import com.aimdi.xtanative.core.session.Session
import com.aimdi.xtanative.store.settings.SubscriptionsOrder

/**
 * One followed thing on the Subscriptions tab: an X account, or a saved
 * search ([search]), whose id, name and handle are its query.
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
) {
    /** Its key in a list: an account and a search may share an id, as a search for a number can. */
    val key: String get() = (if (search) "search:" else "account:") + id
}

/** The Subscriptions tab's list, as XTA's subscriptions model orders and filters it (users_model.dart, _list.dart). */
object SubscriptionList {
    /** The caps XTA offers for one account; null is off. */
    val POST_CAPS: List<Int?> = listOf(null, 1, 2, 3, 5, 10)

    /** Every account followed here, then every saved search. */
    fun rows(session: Session): List<SubscriptionRow> =
        session.following.map {
            SubscriptionRow(it.id, it.name, it.screenName, it.avatarUrl, it.verified, it.inFeed, it.followedAt, postCap = session.postCaps[it.id])
        } + session.searches.map { SubscriptionRow(it.query, it.query, it.query, followedAt = it.followedAt, search = true) }

    /**
     * [rows] in the reader's own order when there is one (by id, or by handle
     * as older builds kept it, the rest after), else sorted by [field].
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
            left.firstOrNull { it.id == key || it.screenName == key }?.also { found -> left.removeAll { it.id == found.id } }
        }
        return placed + left
    }

    /** The reader's own order as XTA keeps it: the ids, comma-separated. */
    fun customOrder(rows: List<SubscriptionRow>): String = rows.joinToString(",") { it.id }

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
