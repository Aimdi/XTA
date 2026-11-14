package com.aimdi.xtanative.plugins.threads

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.threads.ThreadsAccountGuard
import com.aimdi.xtanative.networks.threads.ThreadsAccountState
import com.aimdi.xtanative.networks.threads.ThreadsAccountStatus
import com.aimdi.xtanative.ui.plugins.currentLanguage
import com.aimdi.xtanative.ui.plugins.pluginString
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.delay

/** What the screens and Settings say of the reader's account, decided apart from drawing it. */
internal object ThreadsAccountView {
    /** How the account stands at [now]: a rest whose end has passed is no rest. */
    fun state(status: ThreadsAccountStatus, now: Instant = Instant.now()): ThreadsAccountState = when {
        status.state == ThreadsAccountState.RESTING && status.until?.isAfter(now) != true -> ThreadsAccountState.READY
        else -> status.state
    }

    /**
     * The line a screen that reads with the account shows above what it read:
     * that it does, else that it is paused until a time in [language] where
     * the reader is, else that Threads signed the session out.
     */
    fun line(status: ThreadsAccountStatus, language: String, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): String =
        when (state(status, now)) {
            ThreadsAccountState.READY -> Strings.account_in_use.get(language)
            ThreadsAccountState.RESTING -> Strings.account_resting.get(language, ThreadsFailures.time(checkNotNull(status.until), language, zone))
            ThreadsAccountState.SIGNED_OUT -> Strings.account_signed_out.get(language)
        }

    /** Settings' line for the account: active, resting until a time, or signed out. */
    fun settingsLine(status: ThreadsAccountStatus, language: String, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): String =
        when (state(status, now)) {
            ThreadsAccountState.READY -> Strings.account_active.get(language)
            ThreadsAccountState.RESTING -> Strings.account_resting_until.get(language, ThreadsFailures.time(checkNotNull(status.until), language, zone))
            ThreadsAccountState.SIGNED_OUT -> Strings.account_signed_out.get(language)
        }

    /** Today's reads against the day's budget, as Settings shows them. */
    fun usage(status: ThreadsAccountStatus, language: String): String = Strings.account_usage.get(language, status.usedToday, status.dailyBudget)
}

/** The account's state as it changes, brought up to date when a rest it shows ends. */
@Composable
internal fun rememberAccountStatus(guard: ThreadsAccountGuard): ThreadsAccountStatus {
    val status by guard.status.collectAsState()
    LaunchedEffect(status.until) {
        val until = status.until ?: return@LaunchedEffect
        delay((until.toEpochMilli() - System.currentTimeMillis()).coerceAtLeast(0) + 1_000)
        guard.refresh()
    }
    return status
}

/**
 * The small line over a screen that reads with the reader's account: that it
 * does, or why it is paused and until when, so the account is never used
 * unseen.
 */
@Composable
internal fun AccountInUse(app: ThreadsApp) {
    val status = rememberAccountStatus(app.direct.guard)
    val language = currentLanguage()
    val state = ThreadsAccountView.state(status)
    val text = remember(status, language, state) { ThreadsAccountView.line(status, language) }
    val quiet = state == ThreadsAccountState.READY
    val color = if (quiet) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Icon(iconOf(state), contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = color, modifier = Modifier.padding(start = 8.dp))
    }
}

/** Settings' account protection: how the account stands, today's reads against the budget, and how it is spent. */
@Composable
internal fun AccountProtection(app: ThreadsApp) {
    val status = rememberAccountStatus(app.direct.guard)
    val language = currentLanguage()
    val state = ThreadsAccountView.state(status)
    val color = if (state == ThreadsAccountState.READY) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    ListItem(
        leadingContent = { Icon(iconOf(state), contentDescription = null, tint = color) },
        headlineContent = { Text(ThreadsAccountView.settingsLine(status, language)) },
        supportingContent = {
            Column {
                Text(ThreadsAccountView.usage(status, language))
                Text(pluginString(Strings.account_guard_detail), modifier = Modifier.padding(top = 4.dp))
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

private fun iconOf(state: ThreadsAccountState): ImageVector = when (state) {
    ThreadsAccountState.READY -> Icons.Filled.CheckCircle
    ThreadsAccountState.RESTING -> ThreadsIcons.PauseCircle
    ThreadsAccountState.SIGNED_OUT -> Icons.Filled.Warning
}
