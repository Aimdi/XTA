package com.aimdi.xtanative.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.aimdi.xtanative.data.Problem
import com.aimdi.xtanative.data.isRateLimit
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.close
import com.aimdi.xtanative.ui.generated.resources.problem_auto_retry_in
import com.aimdi.xtanative.ui.generated.resources.problem_auto_retry_seconds
import com.aimdi.xtanative.ui.generated.resources.problem_connection_failed
import com.aimdi.xtanative.ui.generated.resources.problem_endpoint_refused_text
import com.aimdi.xtanative.ui.generated.resources.problem_endpoint_refused_title
import com.aimdi.xtanative.ui.generated.resources.problem_more_info
import com.aimdi.xtanative.ui.generated.resources.problem_oops
import com.aimdi.xtanative.ui.generated.resources.problem_rate_limit_hint
import com.aimdi.xtanative.ui.generated.resources.problem_rate_limited_title
import com.aimdi.xtanative.ui.generated.resources.problem_request_unavailable_text
import com.aimdi.xtanative.ui.generated.resources.problem_request_unavailable_title
import com.aimdi.xtanative.ui.generated.resources.problem_retry_in
import com.aimdi.xtanative.ui.generated.resources.problem_service_unavailable_text
import com.aimdi.xtanative.ui.generated.resources.problem_service_unavailable_title
import com.aimdi.xtanative.ui.generated.resources.problem_sign_in_needed
import com.aimdi.xtanative.ui.generated.resources.problem_signing_text
import com.aimdi.xtanative.ui.generated.resources.problem_signing_title
import com.aimdi.xtanative.ui.generated.resources.problem_timed_out_title
import com.aimdi.xtanative.ui.generated.resources.retry
import com.aimdi.xtanative.ui.generated.resources.settings
import com.aimdi.xtanative.ui.generated.resources.settings_diagnostics
import com.aimdi.xtanative.ui.generated.resources.signin_add_account
import com.aimdi.xtanative.ui.search.DiscoverIcons
import com.aimdi.xtanative.ui.subscriptions.GroupIcons
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Who a read failed for, as its failure's sheet names it and what it offers:
 * X ([isX]: Add account when its sign-in expired, and Diagnostics), or a
 * plugin named [name] with its settings, which [openSettings] opens where it
 * has them.
 */
class ReadSource(val name: String, val isX: Boolean, val openSettings: (() -> Unit)? = null) {
    companion object {
        val X = ReadSource("X", isX = true)
    }
}

/**
 * Retries [problem] on its own while the reader can see it (XTA's
 * ReadRecovery): on a growing backoff for a transient failure, once after a
 * known rate-limit reset, never for one that needs the reader. Nothing is
 * scheduled while the screen is in the background, the connection is down or
 * a read is under way ([loading]), and a retry that changed nothing waits for
 * a real change. The budget starts over once a read goes well, the connection
 * returns or the app comes back. Answers the seconds left before the next
 * retry, 0 while none is scheduled.
 */
@Composable
fun rememberReadRecovery(problem: Problem?, loading: Boolean, retry: () -> Unit): Int {
    val budget = remember { ReadRetryBudget() }
    var seconds by remember { mutableIntStateOf(0) }
    val latestRetry by rememberUpdatedState(retry)
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val visible = lifecycle.isAtLeast(Lifecycle.State.STARTED)
    val online by rememberOnline()
    var wasAway by remember { mutableStateOf(false) }
    LaunchedEffect(visible, online) {
        val away = !visible || !online
        if (wasAway && !away) budget.renew()
        wasAway = away
    }
    LaunchedEffect(problem, loading, visible, online) {
        seconds = 0
        if (loading) return@LaunchedEffect
        if (problem == null) return@LaunchedEffect budget.renew()
        if (!visible || !online) return@LaunchedEffect
        val mode = ReadRetries.of(problem)
        val wait = budget.waitFor(mode, RetryCountdown.resetOf(problem), System.currentTimeMillis()) ?: return@LaunchedEffect
        seconds = ((wait + 999) / 1000).toInt()
        while (seconds > 0) {
            delay(1_000)
            seconds--
        }
        budget.spend(mode)
        latestRetry()
    }
    return seconds
}

/** Whether there is a connection, as the app hears of it; true where nothing tells (a preview). */
@Composable
private fun rememberOnline(): State<Boolean> {
    val graph = runCatching { LocalGraph.current }.getOrNull()
    val flow = remember(graph) { graph?.online ?: MutableStateFlow(true) }
    return produceState(true, flow) { flow.collect { value = it } }
}

/** [problem] in a few words, as a notice or its sheet heads it (XTA's readFailureMessage). */
@Composable
fun readFailureText(problem: Problem): String = stringResource(titleOf(ReadFailureKind.of(problem)))

private fun titleOf(kind: ReadFailureKind): StringResource = when (kind) {
    ReadFailureKind.CONNECTION -> Res.string.problem_connection_failed
    ReadFailureKind.TIMED_OUT -> Res.string.problem_timed_out_title
    ReadFailureKind.SESSION -> Res.string.problem_sign_in_needed
    ReadFailureKind.RATE_LIMITED -> Res.string.problem_rate_limited_title
    ReadFailureKind.ENDPOINT_REFUSED -> Res.string.problem_endpoint_refused_title
    ReadFailureKind.TRANSACTION_UNAVAILABLE -> Res.string.problem_signing_title
    ReadFailureKind.UNAVAILABLE -> Res.string.problem_request_unavailable_title
    ReadFailureKind.SERVICE_UNAVAILABLE -> Res.string.problem_service_unavailable_title
    ReadFailureKind.UNKNOWN -> Res.string.problem_oops
}

private fun hintOf(kind: ReadFailureKind): StringResource? = when (kind) {
    ReadFailureKind.RATE_LIMITED -> Res.string.problem_rate_limit_hint
    ReadFailureKind.ENDPOINT_REFUSED -> Res.string.problem_endpoint_refused_text
    ReadFailureKind.TRANSACTION_UNAVAILABLE -> Res.string.problem_signing_text
    ReadFailureKind.UNAVAILABLE -> Res.string.problem_request_unavailable_text
    ReadFailureKind.SERVICE_UNAVAILABLE -> Res.string.problem_service_unavailable_text
    else -> null
}

/**
 * A failed read in one row (XTA's ReaderFailureNotice): Retry, carrying the
 * countdown while it will retry on its own ([recover]), an ⓘ that opens
 * [ReadFailureSheet] with [contextMessage], and Close where [onDismiss] is given.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReadFailureNotice(
    problem: Problem,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    source: ReadSource = ReadSource.X,
    contextMessage: String? = null,
    recover: Boolean = false,
    loading: Boolean = false,
    onDismiss: (() -> Unit)? = null,
) {
    val seconds = if (recover) rememberReadRecovery(ReadRetries.recoverable(problem), loading, onRetry) else 0
    var explaining by remember { mutableStateOf(false) }
    FlowRow(modifier.padding(horizontal = 8.dp), verticalArrangement = Arrangement.Center, itemVerticalAlignment = Alignment.CenterVertically) {
        ReadRetryButton(problem, onRetry, seconds)
        IconButton(onClick = { explaining = true }) {
            Icon(GroupIcons.InfoOutline, stringResource(Res.string.problem_more_info), modifier = Modifier.size(18.dp))
        }
        if (onDismiss != null) {
            IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, stringResource(Res.string.close), modifier = Modifier.size(18.dp)) }
        }
    }
    if (explaining) ReadFailureSheet(source, problem, onRetry, contextMessage) { explaining = false }
}

/**
 * Why a read failed, in a sheet (XTA's showReaderFailureDetails): "‹source› ·
 * ‹reason›", [contextMessage], the hint for its kind, and what helps: Retry,
 * Add account where X's sign-in expired, Diagnostics for X or the plugin's
 * settings.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReadFailureSheet(source: ReadSource, problem: Problem?, onRetry: () -> Unit, contextMessage: String? = null, onDismiss: () -> Unit) {
    val actions = LocalProblemActions.current
    val kind = problem?.let(ReadFailureKind::of)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${source.name} · ${problem?.let { readFailureText(it) } ?: stringResource(Res.string.problem_oops)}",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, stringResource(Res.string.close)) }
            }
            contextMessage?.let { Text(it) }
            kind?.let(::hintOf)?.let { Text(stringResource(it)) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                val retry = {
                    onDismiss()
                    onRetry()
                }
                if (problem != null) ReadRetryButton(problem, retry, 0) else PlainRetry(retry)
                if (kind == ReadFailureKind.SESSION && source.isX && actions != null) {
                    SheetAction(DiscoverIcons.PersonAdd, stringResource(Res.string.signin_add_account)) {
                        onDismiss()
                        actions.addAccount()
                    }
                }
                val settings = if (source.isX) actions?.diagnostics else source.openSettings
                if (settings != null) {
                    SheetAction(GroupIcons.InfoOutline, stringResource(if (source.isX) Res.string.settings_diagnostics else Res.string.settings)) {
                        onDismiss()
                        settings()
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}

@Composable
private fun PlainRetry(onRetry: () -> Unit) {
    TextButton(onClick = onRetry) {
        Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(Res.string.retry))
    }
}

/**
 * Retry for [problem] (XTA's _ReadRetry): while it waits [seconds] to retry
 * on its own, the countdown, which a rate limit cannot cut short; a rate
 * limit with a known end counts down to it; else Retry.
 */
@Composable
private fun ReadRetryButton(problem: Problem, onRetry: () -> Unit, seconds: Int) {
    when {
        seconds > 0 && problem.isRateLimit -> ReadRetryCountdown(seconds)
        seconds > 0 -> ReadRetryCountdown(seconds, onRetry)
        problem.isRateLimit && RetryCountdown.resetOf(problem) != null -> RateLimitRetry(problem, onRetry)
        else -> PlainRetry(onRetry)
    }
}

@Composable
private fun RateLimitRetry(problem: Problem, onRetry: () -> Unit) {
    val resetsAt = RetryCountdown.resetOf(problem) ?: return PlainRetry(onRetry)
    val left by produceState(RetryCountdown.secondsLeft(resetsAt, System.currentTimeMillis()), resetsAt) {
        while (value > 0) {
            delay(1_000 - System.currentTimeMillis() % 1_000)
            value = RetryCountdown.secondsLeft(resetsAt, System.currentTimeMillis())
        }
    }
    TextButton(onClick = onRetry, enabled = left == 0) {
        Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(if (left > 0) stringResource(Res.string.problem_retry_in, RetryCountdown.format(left)) else stringResource(Res.string.retry))
    }
}

/**
 * "Trying again in 5 s …" while a surface retries on its own (XTA's
 * ReadRetryCountdown); with [onRetry], a Retry button whose mark turns while
 * it waits, the sentence its description, so retrying now stays one tap.
 */
@Composable
fun ReadRetryCountdown(seconds: Int, onRetry: (() -> Unit)? = null) {
    val sentence = if (seconds < 60) {
        stringResource(Res.string.problem_auto_retry_seconds, seconds)
    } else {
        stringResource(Res.string.problem_auto_retry_in, RetryCountdown.format(seconds))
    }
    val hint = MaterialTheme.colorScheme.onSurfaceVariant
    if (onRetry != null) {
        TextButton(onClick = onRetry, modifier = Modifier.semantics { contentDescription = sentence }) {
            WaitMark(seconds, null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(Res.string.retry))
        }
        return
    }
    Row(Modifier.padding(horizontal = 8.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        WaitMark(seconds, hint)
        Spacer(Modifier.width(8.dp))
        Text(sentence, style = MaterialTheme.typography.bodySmall, color = hint)
    }
}

/** A long wait, or reduced motion, gets a still clock rather than a spinner. */
@Composable
private fun WaitMark(seconds: Int, color: Color?) {
    val tint = color ?: MaterialTheme.colorScheme.primary
    if (seconds > 60 || LocalReducedMotion.current) {
        Icon(GroupIcons.ScheduleOutlined, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
    } else {
        CircularProgressIndicator(Modifier.size(14.dp), color = tint, strokeWidth = 2.dp)
    }
}
