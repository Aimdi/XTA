package com.aimdi.xtanative.ui.group

import com.aimdi.xtanative.core.x.model.XThread
import com.aimdi.xtanative.data.plugins.PluginMembers
import com.aimdi.xtanative.store.follows.FollowsOrder
import com.aimdi.xtanative.ui.plugins.PluginDiscovered
import com.aimdi.xtanative.ui.subscriptions.SubscriptionRow
import java.util.concurrent.ConcurrentHashMap

/** What a rotating read holds: each key's rows (stale ones too), the keys answered lately, and a failure that cut it short. */
data class RotatingRead<T>(val rows: Map<String, List<T>>, val fresh: Set<String>, val error: Throwable? = null) {
    val read: Int get() = fresh.size

    /** Nothing at all could be read: a failure to report, not a quiet group. */
    val failed: Boolean get() = error != null && rows.values.all { it.isEmpty() }
}

/**
 * One answer per member, kept [ttlMillis] in memory, as XTA's
 * AccountPostCache keeps an account's page for Discover's sources
 * (discovery_rotation.dart's readRotating): a read asks only the members not
 * answered lately, the never asked first, then the longest unasked, up to its
 * cap, two at a time; one that fails keeps what it said before.
 */
class RotatingReads<T>(private val ttlMillis: Long = TTL_MILLIS, private val clock: () -> Long = System::currentTimeMillis) {
    private class Answer<T>(val at: Long, val rows: List<T>)

    private val answers = ConcurrentHashMap<String, Answer<T>>()
    private val asked = ConcurrentHashMap<String, Long>()

    /**
     * The rows of [keys], after [fetch] was asked for up to [maxFetches] of
     * those not answered within the TTL, each within [budgetMillis];
     * [onPartial] hears the rows so far each time one answers.
     */
    suspend fun read(
        keys: List<String>,
        maxFetches: Int,
        budgetMillis: Long,
        fetch: suspend (String) -> List<T>,
        onPartial: (RotatingRead<T>) -> Unit = {},
    ): RotatingRead<T> {
        val ordered = FollowsOrder.prioritized(keys.distinct()) { asked[it] }
        val pending = ordered.filterNot(::fresh).take(maxFetches.coerceAtLeast(0))
        pending.forEach { asked[it] = clock() }
        val error = FollowsOrder.eachWithin(pending, CONCURRENCY, budgetMillis, fetch) { key, rows ->
            answers[key] = Answer(clock(), rows)
            onPartial(held(ordered, null))
        }
        return held(ordered, error)
    }

    private fun fresh(key: String): Boolean = answers[key]?.let { clock() - it.at <= ttlMillis } == true

    private fun held(keys: List<String>, error: Throwable?): RotatingRead<T> =
        RotatingRead(keys.mapNotNull { key -> answers[key]?.let { key to it.rows } }.toMap(), keys.filterTo(LinkedHashSet(), ::fresh), error)

    companion object {
        /** XTA's kAccountPostsCacheTtl. */
        const val TTL_MILLIS = 10L * 60 * 1000

        const val CONCURRENCY = 2
    }
}

/** What Discover read lately, for as long as the app runs (XTA's caches of its sources): the X members' posts, and each plugin's answers. */
object DiscoveryCaches {
    val posts = RotatingReads<XThread>()

    private val plugins = ConcurrentHashMap<String, RotatingReads<PluginDiscovered>>()

    fun of(pluginId: String): RotatingReads<PluginDiscovered> = plugins.getOrPut(pluginId) { RotatingReads() }
}

/** What one source has answered so far: its candidates, how many of its members it has read, and a failure that spoilt part of the read. */
data class DiscoveryBatch(val candidates: List<Candidate>, val read: Int = 0, val error: Throwable? = null)

/** A running plugin's part of Discover as the app sees it: how many members one open asks about, and what its answers depend on. */
data class DiscoveryPlan(val perScan: Int, val scope: String)

/** The running plugins' parts of a group's Discover ([com.aimdi.xtanative.ui.plugins.XtaPlugin.discovery]), as the app runs them. */
interface DiscoveryPlugins {
    /** The running plugins with a part, by id. */
    fun plans(): Map<String, DiscoveryPlan>

    /** What plugin [pluginId] finds around its [account]; throws when it could not say. */
    suspend fun around(pluginId: String, account: String): List<PluginDiscovered>

    /** No plugins: X alone. */
    object None : DiscoveryPlugins {
        override fun plans(): Map<String, DiscoveryPlan> = emptyMap()

        override suspend fun around(pluginId: String, account: String): List<PluginDiscovered> = emptyList()
    }
}

/** A group member of a plugin as Discover names it in "Reposted by @…". */
data class PluginMember(val account: String, val handle: String, val name: String)

/** A plugin's candidates (group_discovery_sources.dart): each found account with the member it was found around vouching. */
object PluginFinds {
    /** The cache key of [account]'s answer: what the plugin's answers depend on, then the account. */
    fun keyOf(plan: DiscoveryPlan, account: String): String = plan.scope + "|" + account

    /** [rows], kept by [keyOf], back by the account of [accounts] each answers for. */
    fun byAccount(rows: Map<String, List<PluginDiscovered>>, plan: DiscoveryPlan, accounts: List<String>): Map<String, List<PluginDiscovered>> =
        accounts.mapNotNull { account -> rows[keyOf(plan, account)]?.let { account to it } }.toMap()

    /** How the group's [accounts] of plugin [pluginId] are named, from Subscriptions' [rows] of the plugins. */
    fun members(pluginId: String, accounts: List<String>, rows: List<SubscriptionRow>): Map<String, PluginMember> {
        val named = rows.filter { it.plugin == pluginId }.associateBy { it.id }
        return accounts.associateWith { account ->
            named[PluginMembers.memberId(pluginId, account)]?.let { PluginMember(account, it.screenName, it.name) } ?: PluginMember(account, account, account)
        }
    }

    /** Whom the reader follows through the plugins (Subscriptions' [rows] of theirs), by account and by handle. */
    fun followed(rows: List<SubscriptionRow>): Set<String> = rows.flatMapTo(HashSet()) { row ->
        val plugin = row.plugin ?: return@flatMapTo emptyList()
        listOf(Discovery.identity(plugin, row.id.removePrefix("$plugin:")), Discovery.identity(plugin, row.screenName))
    }

    /** The group's accounts of each plugin ([members], by plugin): no candidates either. */
    fun memberIdentities(members: Map<String, List<String>>): Set<String> =
        members.flatMapTo(HashSet()) { (plugin, accounts) -> accounts.map { Discovery.identity(plugin, it) } }

    /** What [rows] (by account) found, each with the member [members] names for its account vouching. */
    fun candidates(source: String, rows: Map<String, List<PluginDiscovered>>, members: Map<String, PluginMember>): List<Candidate> =
        rows.flatMap { (account, found) ->
            val member = members[account] ?: PluginMember(account, account, account)
            found.filter { it.id.isNotEmpty() && it.handle.isNotEmpty() }.map { candidate(source, it, member) }
        }

    private fun candidate(source: String, found: PluginDiscovered, member: PluginMember) = Candidate(
        id = found.id,
        handle = found.handle,
        name = found.name,
        avatarUrl = found.avatarUrl,
        bio = found.bio,
        supporters = listOf(Supporter(member.account, member.handle, member.name, found.signal, (found.pointedAt ?: found.postedAt)?.toEpochMilli())),
        source = source,
        text = found.text,
        postUrl = found.postUrl.orEmpty(),
        date = found.postedAt?.toEpochMilli(),
        post = found.post?.let(ShownPost::Network),
    )
}
