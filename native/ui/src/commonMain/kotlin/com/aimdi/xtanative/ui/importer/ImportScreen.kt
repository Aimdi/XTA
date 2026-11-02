package com.aimdi.xtanative.ui.importer

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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aimdi.xtanative.core.backup.XtaBackup
import com.aimdi.xtanative.ui.common.LoadingRow
import com.aimdi.xtanative.ui.common.LocalGraph
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.app_name
import com.aimdi.xtanative.ui.generated.resources.import_choose
import com.aimdi.xtanative.ui.generated.resources.import_choose_another
import com.aimdi.xtanative.ui.generated.resources.import_found_accounts
import com.aimdi.xtanative.ui.generated.resources.import_found_following
import com.aimdi.xtanative.ui.generated.resources.import_intro
import com.aimdi.xtanative.ui.generated.resources.import_no_accounts
import com.aimdi.xtanative.ui.generated.resources.import_not_a_backup
import com.aimdi.xtanative.ui.generated.resources.import_privacy
import com.aimdi.xtanative.ui.generated.resources.import_steps
import com.aimdi.xtanative.ui.generated.resources.import_use
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** The first screen: bring XTA's X sessions and follows over from a file XTA exported. */
@Composable
fun ImportScreen(onImported: () -> Unit) {
    val graph = LocalGraph.current
    val vm: ImportViewModel = viewModel { ImportViewModel(graph) }
    val state by vm.ui.collectAsStateWithLifecycle()
    val choose = rememberBackupChooser(onChosen = vm::read)

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
        Text(stringResource(Res.string.app_name), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(Res.string.import_intro), style = MaterialTheme.typography.bodyLarge)
        Text(stringResource(Res.string.import_steps), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        when (val now = state) {
            ImportUi.Idle, ImportUi.Done -> Button(onClick = choose, modifier = Modifier.fillMaxWidth()) { Text(stringResource(Res.string.import_choose)) }
            ImportUi.Reading -> LoadingRow()
            ImportUi.NotABackup -> {
                Text(stringResource(Res.string.import_not_a_backup), color = MaterialTheme.colorScheme.error)
                Button(onClick = choose, modifier = Modifier.fillMaxWidth()) { Text(stringResource(Res.string.import_choose_another)) }
            }
            is ImportUi.Ready -> Found(now.backup, onUse = { vm.use(now.backup) }, onAnother = choose)
        }
        Text(stringResource(Res.string.import_privacy), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Found(backup: XtaBackup, onUse: () -> Unit, onAnother: () -> Unit) {
    val inFeed = backup.following.count { it.inFeed }
    Text(
        pluralStringResource(Res.plurals.import_found_accounts, backup.accounts.size, backup.accounts.size) + "\n" +
            pluralStringResource(Res.plurals.import_found_following, backup.following.size, backup.following.size, inFeed),
        style = MaterialTheme.typography.bodyLarge,
    )
    if (backup.accounts.isEmpty()) {
        Text(stringResource(Res.string.import_no_accounts), color = MaterialTheme.colorScheme.error)
    }
    Button(onClick = onUse, enabled = backup.accounts.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(Res.string.import_use))
    }
    OutlinedButton(onClick = onAnother, modifier = Modifier.fillMaxWidth()) { Text(stringResource(Res.string.import_choose_another)) }
}
