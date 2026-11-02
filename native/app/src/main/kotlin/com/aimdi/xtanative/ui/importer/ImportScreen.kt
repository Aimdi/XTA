package com.aimdi.xtanative.ui.importer

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aimdi.xtanative.R
import com.aimdi.xtanative.core.backup.XtaBackup
import com.aimdi.xtanative.ui.common.LoadingRow
import com.aimdi.xtanative.ui.common.LocalGraph

/** The first screen: bring XTA's X sessions and follows over from a file XTA exported. */
@Composable
fun ImportScreen(onImported: () -> Unit) {
    val graph = LocalGraph.current
    val vm: ImportViewModel = viewModel { ImportViewModel(graph) }
    val state by vm.ui.collectAsStateWithLifecycle()
    val resolver = LocalContext.current.contentResolver
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.read(resolver, uri)
    }
    val choose = { pick.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*")) }

    LaunchedEffect(state) {
        if (state == ImportUi.Done) {
            vm.reset()
            onImported()
        }
    }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.import_intro), style = MaterialTheme.typography.bodyLarge)
        Text(stringResource(R.string.import_steps), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        when (val now = state) {
            ImportUi.Idle, ImportUi.Done -> {
                Button(onClick = choose, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.import_choose)) }
                OutlinedButton(onClick = vm::readWithoutAccount, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.import_without_account))
                }
                Text(
                    stringResource(R.string.import_without_account_explained),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ImportUi.Reading -> LoadingRow()
            ImportUi.NotABackup -> {
                Text(stringResource(R.string.import_not_a_backup), color = MaterialTheme.colorScheme.error)
                Button(onClick = choose, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.import_choose_another)) }
            }
            is ImportUi.Ready -> Found(now.backup, onUse = { vm.use(now.backup) }, onAnother = choose)
        }
        Text(stringResource(R.string.import_privacy), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Found(backup: XtaBackup, onUse: () -> Unit, onAnother: () -> Unit) {
    val inFeed = backup.following.count { it.inFeed }
    Text(
        pluralStringResource(R.plurals.import_found_accounts, backup.accounts.size, backup.accounts.size) + "\n" +
            pluralStringResource(R.plurals.import_found_following, backup.following.size, backup.following.size, inFeed),
        style = MaterialTheme.typography.bodyLarge,
    )
    if (backup.accounts.isEmpty()) {
        Text(stringResource(R.string.import_no_accounts), color = MaterialTheme.colorScheme.error)
    }
    Button(onClick = onUse, enabled = backup.accounts.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.import_use))
    }
    OutlinedButton(onClick = onAnother, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.import_choose_another)) }
}
