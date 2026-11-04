package com.aimdi.xtanative.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aimdi.xtanative.data.Problem
import com.aimdi.xtanative.data.isRateLimit
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.problem_endpoint_refused_text
import com.aimdi.xtanative.ui.generated.resources.problem_endpoint_refused_title
import com.aimdi.xtanative.ui.generated.resources.problem_http_text
import com.aimdi.xtanative.ui.generated.resources.problem_no_account_text
import com.aimdi.xtanative.ui.generated.resources.problem_no_account_title
import com.aimdi.xtanative.ui.generated.resources.problem_no_working_account_text
import com.aimdi.xtanative.ui.generated.resources.problem_no_working_account_title
import com.aimdi.xtanative.ui.generated.resources.problem_offline
import com.aimdi.xtanative.ui.generated.resources.problem_offline_text
import com.aimdi.xtanative.ui.generated.resources.problem_offline_title
import com.aimdi.xtanative.ui.generated.resources.problem_oops
import com.aimdi.xtanative.ui.generated.resources.problem_other
import com.aimdi.xtanative.ui.generated.resources.problem_rate_limited
import com.aimdi.xtanative.ui.generated.resources.problem_rate_limited_text
import com.aimdi.xtanative.ui.generated.resources.problem_rate_limited_title
import com.aimdi.xtanative.ui.generated.resources.problem_refused
import com.aimdi.xtanative.ui.generated.resources.problem_request_unavailable_text
import com.aimdi.xtanative.ui.generated.resources.problem_request_unavailable_title
import com.aimdi.xtanative.ui.generated.resources.problem_retry_in
import com.aimdi.xtanative.ui.generated.resources.problem_service_unavailable_text
import com.aimdi.xtanative.ui.generated.resources.problem_service_unavailable_title
import com.aimdi.xtanative.ui.generated.resources.problem_signing
import com.aimdi.xtanative.ui.generated.resources.problem_signing_text
import com.aimdi.xtanative.ui.generated.resources.problem_signing_title
import com.aimdi.xtanative.ui.generated.resources.problem_timed_out_text
import com.aimdi.xtanative.ui.generated.resources.problem_timed_out_title
import com.aimdi.xtanative.ui.generated.resources.retry
import com.aimdi.xtanative.ui.generated.resources.settings_diagnostics
import com.aimdi.xtanative.ui.generated.resources.signin_add_account
import com.aimdi.xtanative.ui.search.DiscoverIcons
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Where a read failure's buttons lead: the sign-in that adds an X account,
 * and Diagnostics. The app's root provides them; without them, as in a
 * preview, the screens offer Retry alone.
 */
class ProblemActions(val addAccount: () -> Unit, val diagnostics: () -> Unit)

val LocalProblemActions = staticCompositionLocalOf<ProblemActions?> { null }

/** [problem] in one line, for a row under what is already shown. */
@Composable
fun problemText(problem: Problem): String = when (problem) {
    Problem.NoAccount -> stringResource(Res.string.problem_no_account_title)
    Problem.RateLimited, is Problem.RateLimitedUntil -> stringResource(Res.string.problem_rate_limited)
    Problem.Offline -> stringResource(Res.string.problem_offline)
    Problem.Signing -> stringResource(Res.string.problem_signing)
    is Problem.Refused -> stringResource(Res.string.problem_refused, problem.code)
    Problem.Other -> stringResource(Res.string.problem_other)
    Problem.NoWorkingAccount -> stringResource(Res.string.problem_no_working_account_title)
    Problem.EndpointRefused -> stringResource(Res.string.problem_endpoint_refused_title)
    Problem.TimedOut -> stringResource(Res.string.problem_timed_out_title)
}

/**
 * A read that brought nothing, as XTA's full-page errors say it: its mark,
 * what happened, what to do about it, and the buttons for that, Retry
 * counting down while X has said when a rate limit ends.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProblemView(problem: Problem, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val look = ProblemLook.of(problem)
    val actions = LocalProblemActions.current
    Column(modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        ProblemMark(look)
        Text(stringResource(titleOf(look)), fontSize = 18.sp, textAlign = TextAlign.Center)
        Text(
            detailsOf(look, problem),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp),
        )
        FlowRow(
            Modifier.padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (look.addAccount && actions != null) AddAccountButton(actions.addAccount)
            RetryButton(problem, onRetry, raised = look.raisedRetry)
            if (look.diagnostics && actions != null) {
                TextButton(onClick = actions.diagnostics) {
                    Icon(Icons.Outlined.Info, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.settings_diagnostics))
                }
            }
        }
    }
}

/** One row in a list: a failed page under what is already shown. */
@Composable
fun ProblemRow(problem: Problem, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(problemText(problem), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        RetryButton(problem, onRetry, raised = false)
    }
}

@Composable
private fun ProblemMark(look: ProblemLook) {
    val emoji = look.emoji
    if (emoji == null) {
        Icon(Icons.Outlined.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 16.dp).size(36.dp))
    } else {
        Text(emoji, fontSize = 36.sp, modifier = Modifier.padding(bottom = 16.dp))
    }
}

@Composable
private fun AddAccountButton(onClick: () -> Unit) {
    ElevatedButton(onClick = onClick) {
        Icon(DiscoverIcons.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(Res.string.signin_add_account))
    }
}

/**
 * Retry, or "Retry in m:ss", not to be pressed, while X has said when the
 * rate limit it set ends, as XTA's RateLimitRetryButton counts down.
 */
@Composable
private fun RetryButton(problem: Problem, onRetry: () -> Unit, raised: Boolean) {
    val resetsAt = RetryCountdown.resetOf(problem)
    val seconds by produceState(resetsAt?.let { RetryCountdown.secondsLeft(it, System.currentTimeMillis()) } ?: 0, resetsAt) {
        if (resetsAt == null) return@produceState
        while (true) {
            value = RetryCountdown.secondsLeft(resetsAt, System.currentTimeMillis())
            if (value == 0) break
            delay(1_000 - System.currentTimeMillis() % 1_000)
        }
    }
    val label = if (seconds > 0) stringResource(Res.string.problem_retry_in, RetryCountdown.format(seconds)) else stringResource(Res.string.retry)
    when {
        raised -> ElevatedButton(onClick = onRetry) { Text(label) }
        problem.isRateLimit -> TextButton(onClick = onRetry, enabled = seconds == 0) {
            Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(label)
        }
        else -> TextButton(onClick = onRetry) { Text(label) }
    }
}

private fun titleOf(look: ProblemLook): StringResource = when (look) {
    ProblemLook.NO_ACCOUNT -> Res.string.problem_no_account_title
    ProblemLook.RATE_LIMITED -> Res.string.problem_rate_limited_title
    ProblemLook.NO_WORKING_ACCOUNT -> Res.string.problem_no_working_account_title
    ProblemLook.ENDPOINT_REFUSED -> Res.string.problem_endpoint_refused_title
    ProblemLook.SIGNING -> Res.string.problem_signing_title
    ProblemLook.OFFLINE -> Res.string.problem_offline_title
    ProblemLook.TIMED_OUT -> Res.string.problem_timed_out_title
    ProblemLook.SERVICE_UNAVAILABLE -> Res.string.problem_service_unavailable_title
    ProblemLook.REQUEST_UNAVAILABLE -> Res.string.problem_request_unavailable_title
    ProblemLook.HTTP, ProblemLook.OTHER -> Res.string.problem_oops
}

@Composable
private fun detailsOf(look: ProblemLook, problem: Problem): String = when (look) {
    ProblemLook.NO_ACCOUNT -> stringResource(Res.string.problem_no_account_text)
    ProblemLook.RATE_LIMITED -> stringResource(Res.string.problem_rate_limited_text)
    ProblemLook.NO_WORKING_ACCOUNT -> stringResource(Res.string.problem_no_working_account_text)
    ProblemLook.ENDPOINT_REFUSED -> stringResource(Res.string.problem_endpoint_refused_text)
    ProblemLook.SIGNING -> stringResource(Res.string.problem_signing_text)
    ProblemLook.OFFLINE -> stringResource(Res.string.problem_offline_text)
    ProblemLook.TIMED_OUT -> stringResource(Res.string.problem_timed_out_text)
    ProblemLook.SERVICE_UNAVAILABLE -> stringResource(Res.string.problem_service_unavailable_text)
    ProblemLook.REQUEST_UNAVAILABLE -> stringResource(Res.string.problem_request_unavailable_text)
    ProblemLook.HTTP -> stringResource(Res.string.problem_http_text, (problem as? Problem.Refused)?.code ?: 0)
    ProblemLook.OTHER -> stringResource(Res.string.problem_other)
}
