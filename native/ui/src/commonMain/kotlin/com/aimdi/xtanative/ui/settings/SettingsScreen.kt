package com.aimdi.xtanative.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aimdi.xtanative.core.session.Session
import com.aimdi.xtanative.ui.common.Hairline
import com.aimdi.xtanative.ui.common.LocalGraph
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.back
import com.aimdi.xtanative.ui.generated.resources.cancel
import com.aimdi.xtanative.ui.generated.resources.settings
import com.aimdi.xtanative.ui.generated.resources.settings_about
import com.aimdi.xtanative.ui.generated.resources.settings_accounts
import com.aimdi.xtanative.ui.generated.resources.settings_autoplay
import com.aimdi.xtanative.ui.generated.resources.settings_clear
import com.aimdi.xtanative.ui.generated.resources.settings_clear_confirm
import com.aimdi.xtanative.ui.generated.resources.settings_clear_explained
import com.aimdi.xtanative.ui.generated.resources.settings_feed
import com.aimdi.xtanative.ui.generated.resources.settings_following
import com.aimdi.xtanative.ui.generated.resources.settings_import_again
import com.aimdi.xtanative.ui.generated.resources.settings_prototype
import com.aimdi.xtanative.ui.generated.resources.settings_replies
import com.aimdi.xtanative.ui.generated.resources.settings_reposts
import com.aimdi.xtanative.ui.generated.resources.settings_version
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onImport: () -> Unit, onCleared: () -> Unit) {
    val graph = LocalGraph.current
    val session by graph.sessions.state.collectAsStateWithLifecycle()
    val current = session ?: Session()
    val scope = rememberCoroutineScope()
    var confirmClear by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.back)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            Section(stringResource(Res.string.settings_feed))
            SwitchRow(stringResource(Res.string.settings_replies), current.includeReplies) { on ->
                scope.launch { graph.sessions.update { it.copy(includeReplies = on) } }
            }
            SwitchRow(stringResource(Res.string.settings_reposts), current.includeReposts) { on ->
                scope.launch { graph.sessions.update { it.copy(includeReposts = on) } }
            }
            SwitchRow(stringResource(Res.string.settings_autoplay), current.autoplay) { on ->
                scope.launch { graph.sessions.update { it.copy(autoplay = on) } }
            }
            Hairline()
            Section(stringResource(Res.string.settings_accounts))
            current.accounts.forEach { account ->
                Line("@" + (account.screenName ?: account.id))
            }
            Line(stringResource(Res.string.settings_following, current.following.size, current.feedMembers.size))
            ActionRow(stringResource(Res.string.settings_import_again), onImport)
            ActionRow(stringResource(Res.string.settings_clear), { confirmClear = true }, danger = true)
            Hairline()
            Section(stringResource(Res.string.settings_about))
            Line(stringResource(Res.string.settings_version, graph.version))
            Line(stringResource(Res.string.settings_prototype))
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(Res.string.settings_clear)) },
            text = { Text(stringResource(Res.string.settings_clear_explained)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    scope.launch {
                        graph.sessions.update { Session() }
                        graph.clearFeedCaches()
                        graph.signers.clear()
                        onCleared()
                    }
                }) { Text(stringResource(Res.string.settings_clear_confirm), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(Res.string.cancel)) } },
        )
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun Line(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ActionRow(label: String, onClick: () -> Unit, danger: Boolean = false) {
    Text(
        label,
        style = MaterialTheme.typography.bodyLarge,
        color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
    )
}
