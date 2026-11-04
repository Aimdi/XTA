package com.aimdi.xtanative.ui.subscriptions

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.core.session.AccountLookup
import com.aimdi.xtanative.core.session.FollowCheck
import com.aimdi.xtanative.core.x.model.XUser
import com.aimdi.xtanative.ui.common.LocalGraph
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.cancel
import com.aimdi.xtanative.ui.generated.resources.close
import com.aimdi.xtanative.ui.generated.resources.groups_account_suspended
import com.aimdi.xtanative.ui.generated.resources.groups_broken_found
import com.aimdi.xtanative.ui.generated.resources.groups_checking
import com.aimdi.xtanative.ui.generated.resources.groups_delete
import com.aimdi.xtanative.ui.generated.resources.groups_find_broken
import com.aimdi.xtanative.ui.generated.resources.groups_no_broken
import com.aimdi.xtanative.ui.generated.resources.groups_renamed_updated
import com.aimdi.xtanative.ui.generated.resources.groups_some_unchecked
import com.aimdi.xtanative.ui.generated.resources.groups_user_not_found
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * XTA's search for broken subscriptions (_cleanup.dart): every followed
 * account is checked one by one; a renamed one is repaired at once, and the
 * ones X says are gone are listed for removal. A rate limit stops the scan;
 * whatever it did not reach is kept.
 */
@Composable
fun BrokenSubscriptionsDialog(onDismiss: () -> Unit) {
    val graph = LocalGraph.current
    val accounts = remember { graph.sessions.state.value?.following.orEmpty() }
    var state by remember { mutableStateOf(CleanupState(total = accounts.size)) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        for (account in accounts) {
            val check = CleanupScan.checkOf(
                FollowCheck.check(byName = { lookup { graph.client.profile(account.screenName).first } }, byId = { lookup { graph.client.profileById(account.id).first } }),
            )
            if (check is CleanupCheck.Renamed) graph.sessions.update { it.withProfilesFrom(listOf(check.user)) }
            state = CleanupScan.after(state, account, check) ?: break
            delay(CleanupScan.PAUSE_MILLIS)
        }
        state = CleanupScan.finished(state)
    }

    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(Res.string.groups_find_broken)) },
        text = { if (state.done) Results(state) else Progress(state) },
        confirmButton = {
            if (state.done && state.broken.isNotEmpty()) {
                TextButton(onClick = {
                    val ids = state.broken.map { it.first.id }.toSet()
                    scope.launch {
                        graph.sessions.update { session -> ids.fold(session) { next, id -> next.unfollow(id) } }
                        onDismiss()
                    }
                }) { Text(stringResource(Res.string.groups_delete) + " (${state.broken.size})") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(if (state.done && state.broken.isEmpty()) Res.string.close else Res.string.cancel)) }
        },
    )
}

/** What X answered of one account, as the check reads it; errors that name no account are thrown on, for the check to weigh. */
private suspend fun lookup(read: suspend () -> XUser): AccountLookup<XUser> = try {
    AccountLookup.Found(read())
} catch (error: CancellationException) {
    throw error
} catch (error: Throwable) {
    CleanupScan.lookupOf(error) ?: throw error
}

@Composable
private fun Progress(state: CleanupState) {
    Column {
        LinearProgressIndicator(progress = { state.fraction }, modifier = Modifier.fillMaxWidth())
        Text("${stringResource(Res.string.groups_checking)} (${state.checked} / ${state.total})", modifier = Modifier.padding(top = 16.dp))
    }
}

@Composable
private fun Results(state: CleanupState) {
    Column {
        if (state.broken.isEmpty()) {
            Text(stringResource(Res.string.groups_no_broken))
        } else {
            Text(stringResource(Res.string.groups_broken_found))
            LazyColumn(Modifier.heightIn(max = 280.dp).padding(top = 8.dp)) {
                items(state.broken, key = { it.first.id }) { (account, reason) ->
                    Column(Modifier.padding(vertical = 4.dp)) {
                        Text("@${account.screenName}", maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            stringResource(if (reason == BrokenReason.SUSPENDED) Res.string.groups_account_suspended else Res.string.groups_user_not_found),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        if (state.renamed.isNotEmpty()) {
            Hint(stringResource(Res.string.groups_renamed_updated) + "\n" + state.renamed.joinToString("\n") { "@${it.from} → @${it.to}" })
        }
        if (state.unreachable > 0) Hint(stringResource(Res.string.groups_some_unchecked))
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
}
