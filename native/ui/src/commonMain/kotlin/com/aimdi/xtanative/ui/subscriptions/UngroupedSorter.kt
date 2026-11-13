package com.aimdi.xtanative.ui.subscriptions

/** One choice in the sorter: an account and the groups it went into, none when it was skipped. */
data class SorterChoice(val accountId: String, val groupIds: List<String>) {
    val placed: Boolean get() = groupIds.isNotEmpty()
}

/**
 * Where the one-by-one sorter stands (group_ungrouped_sorter.dart): the
 * accounts to go through and one choice for each account behind it.
 */
data class SorterState(
    val accounts: List<String>,
    val choices: List<SorterChoice> = emptyList(),
    /** A choice is being saved, and no other is taken meanwhile. */
    val saving: Boolean = false,
    /** The last move took a choice back: the card comes from the other side. */
    val backward: Boolean = false,
) {
    val position: Int get() = choices.size

    val finished: Boolean get() = position >= accounts.size

    val current: String? get() = accounts.getOrNull(position)

    val placedCount: Int get() = choices.count { it.placed }

    val skipped: List<String> get() = choices.filterNot { it.placed }.map { it.accountId }

    /** [current] put in [groupIds], or skipped when there are none. */
    fun decide(groupIds: List<String>): SorterState =
        current?.let { SorterState(accounts, choices + SorterChoice(it, groupIds)) } ?: this

    /** The last choice taken back, its account current again. */
    fun undo(): SorterState = if (choices.isEmpty()) this else SorterState(accounts, choices.dropLast(1), backward = true)

    /** A new round through the accounts skipped so far. */
    fun skippedRound(): SorterState = SorterState(skipped)

    fun withSaving(saving: Boolean): SorterState = copy(saving = saving)
}
