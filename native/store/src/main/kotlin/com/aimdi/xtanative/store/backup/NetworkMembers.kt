package com.aimdi.xtanative.store.backup

import com.aimdi.xtanative.core.json.J

/**
 * One account a plugin follows, as each app names it: [subscriptionId] among
 * XTA's subscriptions (its row's id in XTA's backup), [account] among the
 * plugin's group members, which XTA Native's groups hold as [memberId]. The
 * two are one but where a plugin reads an account by another name than XTA
 * keeps, as Bluesky reads by the DID an account XTA keeps by its handle.
 */
data class NetworkAccount(val plugin: String, val subscriptionId: String, val account: String = subscriptionId) {
    val memberId: String get() = NetworkMembers.memberId(plugin, account)
}

/**
 * The networks' accounts in XTA's groups (group_model.dart joins a member to
 * every table that keeps its id): Threads, Bluesky, Mastodon, Reddit,
 * Substack and RSS name theirs bare, by the subscription's own id (a handle,
 * an acct, a subreddit, a publication, a feed); TikTok, Instagram, Hacker
 * News, Pixiv, the web sources and Booru as "<plugin>:<id>", as XTA Native's
 * groups name every plugin's account. Booru's tags joined bare before XTA's
 * migration 62, so a backup from before brings them bare, and XTA's restore
 * prefixes them (prefixBooruMemberships). A restore here makes XTA's bare
 * members the plugins'; an export writes them back bare.
 */
object NetworkMembers {
    /** The networks whose accounts XTA's groups name bare, by plugin, with the key of their rows in XTA's backup. */
    private val BARE: Map<String, String> = mapOf(
        "threads" to BackupKeys.THREADS_SUBSCRIPTIONS,
        "bluesky" to BackupKeys.BLUESKY_SUBSCRIPTIONS,
        "mastodon" to BackupKeys.MASTODON_SUBSCRIPTIONS,
        "reddit" to BackupKeys.REDDIT_SUBSCRIPTIONS,
        "substack" to BackupKeys.SUBSTACK_SUBSCRIPTIONS,
        "rss" to BackupKeys.RSS_SUBSCRIPTIONS,
    )

    /** Those whose bare members a restore takes over: the bare networks, and Booru's tags from before migration 62. */
    private val TAKEN_OVER: Map<String, String> = BARE + ("booru" to BackupKeys.BOORU_SUBSCRIPTIONS)

    /** What a group holds plugin [plugin]'s [account] as. */
    fun memberId(plugin: String, account: String): String = "$plugin:$account"

    /** The accounts of [document]'s rows whose bare members a restore takes over, each named alike in both apps. */
    fun accounts(document: BackupDocument): List<NetworkAccount> {
        val rows = document.networkRows()
        return TAKEN_OVER.flatMap { (plugin, key) ->
            J.parse(rows[key].orEmpty()).list.mapNotNull { row -> row["id"].string?.takeIf { it.isNotEmpty() } }.map { NetworkAccount(plugin, it) }
        }
    }

    /** [rows], each named as the running plugins name it where one of [running] is the same subscription. */
    fun named(rows: List<NetworkAccount>, running: List<NetworkAccount>): List<NetworkAccount> {
        val names = running.associateBy { it.plugin to it.subscriptionId }
        return rows.map { names[it.plugin to it.subscriptionId] ?: it }
    }

    /**
     * [memberIds] (a group's members as XTA's backup brings them) with each
     * bare one that is the id of one of [owners] made that plugin's member,
     * of every plugin that keeps the id, as XTA's groups read it. One
     * [claimed] here too (an X account followed, a saved search) stays
     * beside them, as XTA keeps a bare row another source still follows.
     * The first of [owners] a plugin keeps an id as counts.
     */
    fun adopted(memberIds: List<String>, owners: List<NetworkAccount>, claimed: Set<String>): List<String> {
        val byId = owners.filter { it.plugin in TAKEN_OVER }.distinctBy { it.plugin to it.subscriptionId }.groupBy({ it.subscriptionId }, { it.memberId })
        return memberIds.flatMap { id ->
            val own = byId[id] ?: return@flatMap listOf(id)
            if (id in claimed) listOf(id) + own else own
        }.distinct()
    }

    /** What XTA's subscriptions call [account]: its subscription's id for a bare network, else its member id, as XTA's list keeps its order. */
    fun xtaId(account: NetworkAccount): String = if (account.plugin in BARE) account.subscriptionId else account.memberId

    /**
     * [memberId] as XTA's groups hold it: a bare network's account by its
     * subscription's id ([accounts] names the ones a plugin reads by another
     * name), any other member as it is. A saved search ([searches]) holding
     * a colon stays the search it is.
     */
    fun xtaMemberId(memberId: String, accounts: List<NetworkAccount>, searches: Set<String> = emptySet()): String {
        val plugin = memberId.substringBefore(':', "")
        val account = memberId.substringAfter(':', "")
        if (memberId in searches || plugin !in BARE || account.isEmpty()) return memberId
        return accounts.firstOrNull { it.plugin == plugin && it.account == account }?.subscriptionId ?: account
    }
}
