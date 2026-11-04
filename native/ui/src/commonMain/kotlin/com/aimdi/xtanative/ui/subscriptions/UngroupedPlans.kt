package com.aimdi.xtanative.ui.subscriptions

import com.aimdi.xtanative.core.session.Session
import com.aimdi.xtanative.services.ai.GroupingAccount
import com.aimdi.xtanative.services.ai.GroupingGroup
import com.aimdi.xtanative.services.ai.GroupingPlan

/**
 * Sorting followed accounts that are in no group (group_ungrouped.dart): each
 * goes to the group whose name and members share the most words with it, the
 * rest that share a long word with each other become suggested groups, and
 * whoever is left stays ungrouped. The reader's AI, when there is one, can
 * write the plan instead; this one fills in what it leaves out.
 */
object UngroupedPlans {
    private val asciiWords = Regex("[^a-z0-9]+")

    /**
     * The followed accounts in no group, X's and the plugins' ([plugins],
     * Subscriptions' rows of theirs), as XTA sorts every network's; saved
     * searches are not sorted.
     */
    fun ungrouped(session: Session, plugins: List<SubscriptionRow> = emptyList()): List<GroupingAccount> {
        val grouped = session.groups.flatMapTo(HashSet()) { it.memberIds }
        return accounts(session, plugins).filter { it.id !in grouped }
    }

    /** Every group with its members, named as they are followed (by id when they are not). */
    fun groups(session: Session, plugins: List<SubscriptionRow> = emptyList()): List<GroupingGroup> {
        val byId = accounts(session, plugins).associateBy { it.id }
        return session.groups.map { group -> GroupingGroup(group.id, group.name, group.memberIds.map { id -> byId[id] ?: GroupingAccount(id, id, id) }) }
    }

    private fun accounts(session: Session, plugins: List<SubscriptionRow>): List<GroupingAccount> =
        session.following.map { GroupingAccount(it.id, it.screenName, it.name) } + plugins.filterNot { it.search }.map { GroupingAccount(it.id, it.screenName, it.name) }

    /** The words of three letters or more in [text], lower-cased, of the Latin alphabet and digits. */
    fun nameTokens(text: String): Set<String> = text.lowercase().split(asciiWords).filterTo(LinkedHashSet()) { it.length >= 3 }

    fun accountTokens(account: GroupingAccount): Set<String> = nameTokens(account.handle) + nameTokens(account.name)

    /** How many words [account] shares with [group]'s name and members. */
    fun fitScore(account: GroupingAccount, group: GroupingGroup): Int {
        val tokens = nameTokens(group.name) + group.members.flatMap(::accountTokens)
        return accountTokens(account).count { it in tokens }
    }

    /** Each account in the group it fits best, the leftovers clustered into suggested groups. */
    fun planByNames(ungrouped: List<GroupingAccount>, groups: List<GroupingGroup>): GroupingPlan {
        val assign = ArrayList<GroupingPlan.Assignment>()
        val leftover = ArrayList<GroupingAccount>()
        for (account in ungrouped) {
            val best = bestGroup(account, groups)
            if (best == null) leftover += account else assign += GroupingPlan.Assignment(account.id, best)
        }
        val suggested = suggestClusters(leftover)
        val clustered = suggested.flatMapTo(HashSet()) { it.accountIds }
        return GroupingPlan(assign, suggested, leftover.map { it.id }.filter { it !in clustered })
    }

    private fun bestGroup(account: GroupingAccount, groups: List<GroupingGroup>): String? {
        var best: GroupingGroup? = null
        var bestScore = 0
        for (group in groups) {
            val score = fitScore(account, group)
            if (score > bestScore) {
                best = group
                bestScore = score
            }
        }
        return best?.id?.takeIf { bestScore >= 1 }
    }

    /** Leftover accounts that share a word of four letters or more, gathered into groups of two or more. */
    fun suggestClusters(leftover: List<GroupingAccount>): List<GroupingPlan.Suggestion> {
        if (leftover.size < 2) return emptyList()
        val parent = leftover.associateTo(HashMap()) { it.id to it.id }
        fun root(start: String): String {
            var id = start
            while (parent[id] != id) {
                parent[id] = parent.getValue(parent.getValue(id))
                id = parent.getValue(id)
            }
            return id
        }
        for (i in leftover.indices) {
            for (j in i + 1 until leftover.size) {
                val shared = accountTokens(leftover[i]).intersect(accountTokens(leftover[j]))
                if (shared.all { it.length < 4 }) continue
                parent[root(leftover[j].id)] = root(leftover[i].id)
            }
        }
        val buckets = LinkedHashMap<String, MutableList<GroupingAccount>>()
        for (account in leftover) buckets.getOrPut(root(account.id)) { ArrayList() } += account
        return buckets.values.filter { it.size >= 2 }.map { bucket -> GroupingPlan.Suggestion(clusterName(bucket), bucket.map { it.id }) }
    }

    /** The long word most of [accounts] share, capitalised; the first one's handle when there is none. */
    private fun clusterName(accounts: List<GroupingAccount>): String {
        val counts = LinkedHashMap<String, Int>()
        for (account in accounts) {
            for (token in accountTokens(account).filter { it.length >= 4 }) counts[token] = (counts[token] ?: 0) + 1
        }
        val best = counts.entries.reduceOrNull { a, b -> if (a.value >= b.value) a else b }?.key ?: return accounts.first().handle
        return best.replaceFirstChar { it.uppercaseChar() }
    }

    /** How many accounts [plan] places. */
    fun moveCount(plan: GroupingPlan): Int = plan.assign.size + plan.suggest.sumOf { it.accountIds.size }

    /**
     * [plan] applied as XTA applies it: a new group for each suggestion of two
     * accounts or more (ids from [newId], made at [now]), then each account
     * added to the group it was assigned. Nobody leaves a group they are in.
     * Returns the session and how many accounts were placed.
     */
    fun applied(session: Session, plan: GroupingPlan, newId: () -> String, now: Long): Pair<Session, Int> {
        var placed = 0
        var next = session
        for (suggestion in plan.suggest.filter { it.accountIds.size >= 2 }) {
            val id = newId()
            next = next.withNewGroup(id, suggestion.name, suggestion.accountIds).updatingGroup(id) { it.copy(createdAt = now) }
            placed += suggestion.accountIds.size
        }
        val known = next.groups.mapTo(HashSet()) { it.id }
        for (assignment in plan.assign.filter { it.groupId in known }) {
            next = next.addingTo(assignment.accountId, listOf(assignment.groupId))
            placed++
        }
        return next to placed
    }
}
