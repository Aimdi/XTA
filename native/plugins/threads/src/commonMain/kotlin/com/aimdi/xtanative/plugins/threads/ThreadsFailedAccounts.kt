package com.aimdi.xtanative.plugins.threads

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.threads.ThreadsAccountFailure
import com.aimdi.xtanative.ui.plugins.currentLanguage
import com.aimdi.xtanative.ui.plugins.pluginPlural
import com.aimdi.xtanative.ui.plugins.pluginString
import java.time.Instant

/**
 * "2 accounts could not be read" over Home's feed while any followed account
 * failed its last read, opening the list of them: each with why, Retry, its
 * profile, and Unfollow, which only changes this device.
 */
@Composable
internal fun FailedAccountsRow(app: ThreadsApp) {
    val failures by app.failures.collectAsState()
    val rows by app.accounts.state.collectAsState()
    val shown = remember(failures, rows) { failures.filter { ThreadsAccounts.follows(rows, it.handle) } }
    var open by rememberSaveable { mutableStateOf(false) }
    if (shown.isEmpty()) return
    ListItem(
        leadingContent = { Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp)) },
        headlineContent = { Text(pluginPlural(Strings.accounts_failed, shown.size, shown.size), style = MaterialTheme.typography.labelLarge) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable { open = true },
    )
    if (open) FailedAccountsSheet(app, shown) { open = false }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FailedAccountsSheet(app: ThreadsApp, failures: List<ThreadsAccountFailure>, onDismiss: () -> Unit) {
    var unfollowing by remember { mutableStateOf<String?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(Modifier.navigationBarsPadding().padding(horizontal = 8.dp)) {
            item(key = "title") {
                Text(pluginPlural(Strings.accounts_failed, failures.size, failures.size), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(16.dp))
            }
            items(failures, key = { it.handle }) { failure ->
                FailedAccount(app, failure, onOpen = { onDismiss(); app.openProfile(failure.handle) }, onUnfollow = { unfollowing = failure.handle })
            }
        }
    }
    unfollowing?.let { handle -> UnfollowDialog(handle, onConfirm = { app.unfollow(handle) }) { unfollowing = null } }
}

@Composable
private fun FailedAccount(app: ThreadsApp, failure: ThreadsAccountFailure, onOpen: () -> Unit, onUnfollow: () -> Unit) {
    val retrying by app.retrying.collectAsState()
    val language = currentLanguage()
    ListItem(
        headlineContent = { Text("@" + failure.handle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(ThreadsFailures.trouble(failure, language)) },
        trailingContent = {
            Row {
                if (failure.handle in retrying) {
                    CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp), strokeWidth = 2.dp)
                } else {
                    IconButton(onClick = { app.retryAccount(failure.handle) }, enabled = failure.retryAt?.isAfter(Instant.now()) != true) { Icon(Icons.Filled.Refresh, contentDescription = pluginString(Strings.retry)) }
                }
                IconButton(onClick = onOpen) { Icon(Icons.Filled.Person, contentDescription = pluginString(Strings.open_handle, failure.handle)) }
                IconButton(onClick = onUnfollow) { Icon(ThreadsIcons.PersonRemove, contentDescription = pluginString(Strings.unfollow_on_device)) }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}
