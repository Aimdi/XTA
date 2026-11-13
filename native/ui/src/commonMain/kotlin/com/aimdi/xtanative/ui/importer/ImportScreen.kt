package com.aimdi.xtanative.ui.importer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aimdi.xtanative.store.backup.BackupCategory
import com.aimdi.xtanative.store.backup.BackupDocument
import com.aimdi.xtanative.store.backup.XtaTime
import com.aimdi.xtanative.ui.common.LoadingRow
import com.aimdi.xtanative.ui.common.LocalGraph
import com.aimdi.xtanative.ui.common.currentLocale
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.app_name
import com.aimdi.xtanative.ui.generated.resources.import_choose
import com.aimdi.xtanative.ui.generated.resources.import_choose_another
import com.aimdi.xtanative.ui.generated.resources.import_intro
import com.aimdi.xtanative.ui.generated.resources.import_no_accounts
import com.aimdi.xtanative.ui.generated.resources.import_privacy
import com.aimdi.xtanative.ui.generated.resources.import_steps
import com.aimdi.xtanative.ui.generated.resources.import_use
import com.aimdi.xtanative.ui.generated.resources.settings_import_from
import com.aimdi.xtanative.ui.generated.resources.settings_import_read_positions
import com.aimdi.xtanative.ui.generated.resources.settings_import_read_positions_description
import com.aimdi.xtanative.ui.generated.resources.signin_add_account
import com.aimdi.xtanative.ui.settings.backupProblem
import com.aimdi.xtanative.ui.settings.categoryLabel
import com.aimdi.xtanative.ui.settings.shortDateTime
import org.jetbrains.compose.resources.stringResource

/**
 * XTA's backup import, from the intro and from "You are not logged in": a file XTA
 * exported, previewed as XTA previews it, then restored whole (sessions,
 * follows, groups, saved posts, notes, settings and the rest); or X's
 * sign-in ([onSignIn]), after which the app moves on as after an import.
 */
@Composable
fun ImportScreen(onImported: () -> Unit, onSignIn: () -> Unit) {
    val graph = LocalGraph.current
    val vm: ImportViewModel = viewModel { ImportViewModel(graph) }
    val state by vm.ui.collectAsStateWithLifecycle()
    val choose = rememberBackupChooser(onChosen = vm::read)
    val session by graph.sessions.state.collectAsStateWithLifecycle()
    val accounts = session?.accounts?.size ?: 0
    // The fewest accounts seen here: after Remove everything the session empties once this screen is up.
    var fewest by rememberSaveable { mutableIntStateOf(accounts) }

    LaunchedEffect(state) {
        if (state == ImportUi.Done) {
            vm.reset()
            onImported()
        }
    }
    LaunchedEffect(accounts) {
        if (accounts < fewest) fewest = accounts else if (accounts > fewest) onImported()
    }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(Res.string.app_name), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(Res.string.import_intro), style = MaterialTheme.typography.bodyLarge)
        Text(stringResource(Res.string.import_steps), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        when (val now = state) {
            ImportUi.Idle, ImportUi.Done -> {
                Button(onClick = choose, modifier = Modifier.fillMaxWidth()) { Text(stringResource(Res.string.import_choose)) }
                OutlinedButton(onClick = onSignIn, modifier = Modifier.fillMaxWidth()) { Text(stringResource(Res.string.signin_add_account)) }
            }
            ImportUi.Reading, ImportUi.Restoring -> LoadingRow()
            is ImportUi.Refused -> {
                backupProblem(now.read)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(onClick = choose, modifier = Modifier.fillMaxWidth()) { Text(stringResource(Res.string.import_choose_another)) }
            }
            is ImportUi.Ready -> Found(now.document, onUse = { vm.use(now.document, it) }, onAnother = choose)
        }
        Text(stringResource(Res.string.import_privacy), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** What the file would restore, where it came from, and the choice XTA offers about reading positions. */
@Composable
private fun Found(document: BackupDocument, onUse: (includeReadPositions: Boolean) -> Unit, onAnother: () -> Unit) {
    var readPositions by remember { mutableStateOf(false) }
    val counts = document.counts()
    val version = document.appVersion
    val at = XtaTime.parse(document.exportedAt)
    if (version != null && at != null) {
        Text(
            stringResource(Res.string.settings_import_from, version, shortDateTime(at, currentLocale())),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Column {
        counts.forEach { (category, count) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(categoryLabel(category), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(count.toString(), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
    if (BackupCategory.ACCOUNTS !in counts) {
        Text(stringResource(Res.string.import_no_accounts), color = MaterialTheme.colorScheme.error)
    }
    if (BackupCategory.READ_POSITIONS in counts) {
        Row(
            Modifier.fillMaxWidth().selectable(readPositions, role = Role.Checkbox) { readPositions = !readPositions },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(Res.string.settings_import_read_positions), style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(Res.string.settings_import_read_positions_description), style = MaterialTheme.typography.bodySmall)
            }
            Checkbox(checked = readPositions, onCheckedChange = null)
        }
    }
    Button(onClick = { onUse(readPositions) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(Res.string.import_use)) }
    OutlinedButton(onClick = onAnother, modifier = Modifier.fillMaxWidth()) { Text(stringResource(Res.string.import_choose_another)) }
}
