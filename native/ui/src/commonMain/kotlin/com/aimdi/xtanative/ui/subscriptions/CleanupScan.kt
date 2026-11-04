package com.aimdi.xtanative.ui.subscriptions

import com.aimdi.xtanative.core.backup.FollowedAccount
import com.aimdi.xtanative.core.session.AccountLookup
import com.aimdi.xtanative.core.session.FollowHealth
import com.aimdi.xtanative.core.session.HealthCheck
import com.aimdi.xtanative.core.x.XAnswerException
import com.aimdi.xtanative.core.x.XUserUnavailableException
import com.aimdi.xtanative.core.x.model.XUser

/** Why X says a followed account is gone. */
enum class BrokenReason { MISSING, SUSPENDED }

/** What checking one followed account came to. */
sealed interface CleanupCheck {
    data object Ok : CleanupCheck

    /** It was renamed: [user] is how X shows it now. */
    data class Renamed(val user: XUser) : CleanupCheck

    data class Broken(val reason: BrokenReason) : CleanupCheck

    /** The check failed for a passing reason: the account is kept. */
    data object Unreachable : CleanupCheck

    /** X rate-limited the check: the scan stops, and what it did not reach is kept. */
    data object RateLimited : CleanupCheck
}

/** A renamed account, from its old handle to its new one. */
data class Rename(val from: String, val to: String)

/** How far the scan has come: what it checked, what X says is gone, what it fixed, and what it could not check. */
data class CleanupState(
    val total: Int = 0,
    val checked: Int = 0,
    val broken: List<Pair<FollowedAccount, BrokenReason>> = emptyList(),
    val renamed: List<Rename> = emptyList(),
    val unreachable: Int = 0,
    val done: Boolean = false,
) {
    /** The share checked, for the progress bar. */
    val fraction: Float get() = if (total == 0) 0f else checked.toFloat() / total
}

/**
 * XTA's search for broken subscriptions (_cleanup.dart, cleanup_dialog.dart,
 * subscription_health.dart): each followed account is looked up by handle,
 * and by id when the handle is gone, so a rename is repaired rather than
 * removed. Only what X confirmed gone is offered for removal.
 */
object CleanupScan {
    /** How long the scan waits between two accounts, so it does not hammer X. */
    const val PAUSE_MILLIS = 200L

    /** What X's answer about a lookup means: an error naming the account gone, or the account itself. */
    fun lookupOf(error: Throwable): AccountLookup<XUser>? = when {
        error is XUserUnavailableException && error.reason.equals("Suspended", ignoreCase = true) -> AccountLookup.Suspended
        error is XUserUnavailableException -> AccountLookup.Missing
        error is XAnswerException && error.errorCode in MISSING_CODES -> AccountLookup.Missing
        error is XAnswerException && error.errorCode == SUSPENDED_CODE -> AccountLookup.Suspended
        else -> null
    }

    fun checkOf(health: HealthCheck<XUser>): CleanupCheck = when (health.health) {
        FollowHealth.EXISTS -> CleanupCheck.Ok
        FollowHealth.RENAMED -> health.profile?.let { CleanupCheck.Renamed(it) } ?: CleanupCheck.Unreachable
        FollowHealth.MISSING -> CleanupCheck.Broken(BrokenReason.MISSING)
        FollowHealth.SUSPENDED -> CleanupCheck.Broken(BrokenReason.SUSPENDED)
        FollowHealth.UNREACHABLE -> CleanupCheck.Unreachable
        FollowHealth.RATE_LIMITED -> CleanupCheck.RateLimited
    }

    /** The scan after [account] was checked; null when it has to stop there. */
    fun after(state: CleanupState, account: FollowedAccount, check: CleanupCheck): CleanupState? = when (check) {
        CleanupCheck.RateLimited -> null
        CleanupCheck.Ok -> state.copy(checked = state.checked + 1)
        is CleanupCheck.Renamed -> state.copy(checked = state.checked + 1, renamed = state.renamed + Rename(account.screenName, check.user.screenName))
        is CleanupCheck.Broken -> state.copy(checked = state.checked + 1, broken = state.broken + (account to check.reason))
        CleanupCheck.Unreachable -> state.copy(checked = state.checked + 1, unreachable = state.unreachable + 1)
    }

    /** The scan at its end: whatever it did not reach counts as not checked, and is kept. */
    fun finished(state: CleanupState): CleanupState = state.copy(unreachable = state.unreachable + (state.total - state.checked), done = true)

    private val MISSING_CODES = setOf(34, 50)
    private const val SUSPENDED_CODE = 63
}
