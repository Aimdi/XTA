package com.aimdi.xtanative.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.outlined.AddCircle
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import com.aimdi.xtanative.services.util.formatByteSize
import com.aimdi.xtanative.store.backup.BackupCategory
import com.aimdi.xtanative.store.backup.BackupDocument
import com.aimdi.xtanative.store.backup.ExportChoice
import com.aimdi.xtanative.store.backup.WebDavVersion
import com.aimdi.xtanative.store.backup.XtaTime
import com.aimdi.xtanative.store.settings.DataPrefs
import com.aimdi.xtanative.ui.common.LoadingRow
import com.aimdi.xtanative.ui.common.LocalGraph
import com.aimdi.xtanative.ui.common.currentLocale
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.*
import com.aimdi.xtanative.ui.importer.rememberBackupChooser
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** A moment as the reader's locale writes a short date and time. */
internal fun shortDateTime(instant: Instant, locale: Locale): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withLocale(locale).withZone(ZoneId.systemDefault()).format(instant)

/**
 * Shows [text] once, then [onShown] clears what it came from. The message is
 * cleared only after it was shown: clearing first would take the effect
 * showing it out of the composition, and the message with it.
 */
@Composable
internal fun SayOnce(snackbar: SnackbarHostState, text: String?, onShown: () -> Unit) {
    LaunchedEffect(text) {
        if (text != null) {
            snackbar.showSnackbar(text)
            onShown()
        }
    }
}

/** Why [read] cannot be restored, as XTA's import says it; null when it can. */
@Composable
internal fun backupProblem(read: BackupRead): String? = when (read) {
    BackupRead.NotABackup, BackupRead.Empty -> stringResource(Res.string.settings_unable_to_import)
    BackupRead.Unsupported -> stringResource(Res.string.settings_import_unsupported)
    is BackupRead.Ready -> null
}

/**
 * What a backup would restore and where it came from, before anything is
 * written (import_preview.dart); reading positions only when ticked.
 */
@Composable
internal fun ImportPreviewDialog(document: BackupDocument, onDismiss: () -> Unit, onImport: (includeReadPositions: Boolean) -> Unit) {
    var readPositions by remember { mutableStateOf(false) }
    val counts = document.counts()
    val locale = currentLocale()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.settings_import_backup)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                val version = document.appVersion
                val at = XtaTime.parse(document.exportedAt)
                if (version != null && at != null) {
                    Text(
                        stringResource(Res.string.settings_import_from, version, shortDateTime(at, locale)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }
                counts.forEach { (category, count) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(categoryLabel(category), modifier = Modifier.weight(1f))
                        Text(count.toString())
                    }
                }
                if (BackupCategory.READ_POSITIONS in counts) {
                    Row(
                        Modifier.fillMaxWidth().selectable(readPositions, role = Role.Checkbox) { readPositions = !readPositions }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(Res.string.settings_import_read_positions), style = MaterialTheme.typography.bodyLarge)
                            Text(stringResource(Res.string.settings_import_read_positions_description), style = MaterialTheme.typography.bodySmall)
                        }
                        Checkbox(checked = readPositions, onCheckedChange = null)
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onImport(readPositions) }) { Text(stringResource(Res.string.settings_import)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.settings_cancel)) } },
    )
}

/**
 * A backup the reader chose, previewed and, once they agree, restored into
 * every store; [onDone] hears whether it was. The restore runs to its end
 * even when the screen goes away.
 */
@Composable
internal fun RestoreDialog(document: BackupDocument?, vm: SettingsViewModel, snackbar: SnackbarHostState?, onClose: () -> Unit, onDone: (Boolean) -> Unit = {}) {
    if (document == null) return
    val scope = rememberCoroutineScope()
    val imported = stringResource(Res.string.settings_imported)
    ImportPreviewDialog(document, onDismiss = { onClose(); onDone(false) }) { includeReadPositions ->
        onClose()
        scope.launch {
            withContext(NonCancellable) { DeviceBackup(vm.graph).restore(document, includeReadPositions) }
            snackbar?.showSnackbar(imported)
            onDone(true)
        }
    }
}

// ---- Data (_data.dart, image_cache_setting.dart) ----

@Composable
internal fun DataSettings(title: String, vm: SettingsViewModel, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var offered by remember { mutableStateOf<BackupDocument?>(null) }
    var problem by remember { mutableStateOf<BackupRead?>(null) }
    val choose = rememberBackupChooser { read ->
        scope.launch {
            val text = withContext(Dispatchers.IO) { runCatching { read() }.getOrNull() }
            when (val found = BackupRead.of(text)) {
                is BackupRead.Ready -> offered = found.document
                else -> problem = found
            }
        }
    }
    SayOnce(snackbar, problem?.let { backupProblem(it) }) { problem = null }
    SettingsPage(title, onBack, snackbar) { padding ->
        SettingsList(padding) {
            NavigationRow(stringResource(Res.string.settings_downloads), icon = Icons.Outlined.KeyboardArrowDown) { onOpen(SettingsRoutes.DOWNLOADS) }
            NavigationRow(stringResource(Res.string.settings_offline), icon = Icons.Outlined.Done) { onOpen(SettingsRoutes.OFFLINE) }
            ImageCacheRow()
            NavigationRow(stringResource(Res.string.settings_import), stringResource(Res.string.settings_import_description), Icons.Outlined.AddCircle, chevron = false, onClick = choose)
            NavigationRow(stringResource(Res.string.settings_export), stringResource(Res.string.settings_export_description), Icons.AutoMirrored.Outlined.ExitToApp) {
                onOpen(SettingsRoutes.BACKUP)
            }
            NavigationRow(stringResource(Res.string.settings_sync), stringResource(Res.string.settings_sync_description), Icons.Outlined.Refresh) {
                onOpen(SettingsRoutes.section(SettingsSection.SYNC.id))
            }
        }
    }
    RestoreDialog(offered, vm, snackbar, onClose = { offered = null })
}

/** How much the picture cache holds, and a way to empty it. */
@Composable
private fun ImageCacheRow() {
    val context = LocalPlatformContext.current
    val loader = remember(context) { SingletonImageLoader.get(context) }
    val scope = rememberCoroutineScope()
    var revision by remember { mutableIntStateOf(0) }
    var confirm by remember { mutableStateOf(false) }
    val size by produceState<Long?>(null, revision) {
        value = withContext(Dispatchers.IO) { runCatching { loader.diskCache?.size }.getOrNull() }
    }
    NavigationRow(
        stringResource(Res.string.settings_image_cache),
        size?.let { stringResource(Res.string.settings_image_cache_size, formatByteSize(it), ImageCacheAge.MAX_AGE_DAYS) },
        Icons.Outlined.Delete,
        chevron = false,
    ) { confirm = true }
    if (confirm) {
        ConfirmDialog(
            stringResource(Res.string.settings_image_cache_clear_question),
            stringResource(Res.string.settings_image_cache_clear_description),
            stringResource(Res.string.settings_image_cache_clear),
            stringResource(Res.string.settings_cancel),
            onDismiss = { confirm = false },
        ) {
            confirm = false
            loader.memoryCache?.clear()
            scope.launch {
                withContext(Dispatchers.IO) { runCatching { loader.diskCache?.clear() } }
                revision++
            }
        }
    }
}

// ---- Export (settings_export_screen.dart), at `backup` ----

/** XTA's export: the categories to put in the file, then the file, saved where the reader says. */
@Composable
fun BackupScreen(onBack: () -> Unit) {
    val graph = LocalGraph.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var choice by remember { mutableStateOf(ExportChoice()) }
    var working by remember { mutableStateOf(false) }
    var exported by remember { mutableStateOf<String?>(null) }
    val save = rememberBackupSaver { name ->
        working = false
        exported = name
    }
    SayOnce(snackbar, exported?.let { stringResource(Res.string.settings_exported_to, it) }) { exported = null }
    fun change(next: ExportChoice) {
        // Members only mean something with the subscriptions and groups they join.
        choice = if (!next.subscriptions || !next.groups) next.copy(groupMembers = false) else next
    }
    SettingsPage(
        stringResource(Res.string.settings_export),
        onBack,
        snackbar,
        floating = {
            if (!choice.isEmpty && !working) {
                FloatingActionButton(onClick = {
                    working = true
                    scope.launch {
                        val text = withContext(Dispatchers.IO) { DeviceBackup(graph).export(choice) }
                        save(BackupExport.fileName(LocalDate.now()), text)
                    }
                }) { Icon(Icons.Outlined.Share, stringResource(Res.string.settings_export)) }
            }
        },
    ) { padding ->
        SettingsList(padding) {
            CheckRow(stringResource(Res.string.settings_export_settings), checked = choice.settings) { change(choice.copy(settings = it)) }
            CheckRow(stringResource(Res.string.settings_export_subscriptions), checked = choice.subscriptions) { change(choice.copy(subscriptions = it)) }
            CheckRow(stringResource(Res.string.settings_export_groups), checked = choice.groups) { change(choice.copy(groups = it)) }
            CheckRow(
                stringResource(Res.string.settings_export_group_members),
                checked = choice.groupMembers,
                enabled = choice.subscriptions && choice.groups,
            ) { change(choice.copy(groupMembers = it)) }
            CheckRow(stringResource(Res.string.settings_export_posts), stringResource(Res.string.settings_export_annotations), choice.savedPosts) {
                change(choice.copy(savedPosts = it))
            }
            CheckRow(stringResource(Res.string.settings_export_folders), checked = choice.savedFolders) { change(choice.copy(savedFolders = it)) }
            CheckRow(stringResource(Res.string.settings_export_likes), checked = choice.likedPosts) { change(choice.copy(likedPosts = it)) }
            CheckRow(stringResource(Res.string.settings_export_filters), checked = choice.filters) { change(choice.copy(filters = it)) }
            CheckRow(stringResource(Res.string.settings_export_read_positions), checked = choice.readPositions) { change(choice.copy(readPositions = it)) }
            CheckRow(stringResource(Res.string.settings_export_accounts), stringResource(Res.string.settings_export_accounts_details), choice.accounts) {
                change(choice.copy(accounts = it))
            }
            if (working) LoadingRow()
        }
    }
}

// ---- Sync (sync_screen.dart) ----

@Composable
internal fun SyncSettings(title: String, vm: SettingsViewModel, onBack: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val sync = remember { WebDavSync(OkHttpWebDav(), vm.graph.webDavBaselines) }
    var url by rememberSaveable { mutableStateOf(vm.settings.value[DataPrefs.webDavUrl]) }
    var username by rememberSaveable { mutableStateOf(vm.settings.value[DataPrefs.webDavUsername]) }
    var password by remember { mutableStateOf(vm.settings.value[DataPrefs.webDavPassword]) }
    var busy by remember { mutableStateOf(false) }
    var offered by remember { mutableStateOf<Pair<BackupDocument, WebDavResult?>?>(null) }
    var versions by remember { mutableStateOf<List<WebDavVersion>?>(null) }
    var message by remember { mutableStateOf<WebDavResult?>(null) }
    var problem by remember { mutableStateOf<BackupRead?>(null) }
    val config = WebDavConfig(url, username, password)
    val locale = currentLocale()

    fun act(action: suspend () -> WebDavResult?) {
        if (busy) return
        busy = true
        scope.launch {
            vm.update { it.with(DataPrefs.webDavUrl, url.trim()).with(DataPrefs.webDavUsername, username).with(DataPrefs.webDavPassword, password) }
            val result = try {
                action()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                WebDavResult(WebDavOutcome.NETWORK_ERROR)
            }
            busy = false
            if (result != null) message = result
        }
    }

    fun offer(result: WebDavResult, acknowledge: Boolean) {
        when (val read = BackupRead.of(result.body)) {
            is BackupRead.Ready -> offered = read.document to result.takeIf { acknowledge }
            else -> problem = read
        }
    }

    SayOnce(snackbar, message?.let { webDavMessage(it) }) { message = null }
    SayOnce(snackbar, problem?.let { backupProblem(it) }) { problem = null }

    SettingsPage(title, onBack, snackbar) { padding ->
        SettingsList(padding) {
            Column(Modifier.padding(16.dp), verticalArrangement = FormSpacing) {
                Text(stringResource(Res.string.settings_sync_description), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(Res.string.settings_sync_url)) },
                    placeholder = { Text(stringResource(Res.string.settings_sync_url_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text(stringResource(Res.string.settings_sync_username)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(Res.string.settings_sync_password)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            SwitchRow(
                stringResource(Res.string.settings_sync_include_accounts),
                stringResource(Res.string.settings_sync_include_accounts_description),
                settings[DataPrefs.webDavIncludeAccounts],
            ) { vm.set(DataPrefs.webDavIncludeAccounts, it) }
            val last = XtaTime.parse(settings[DataPrefs.webDavLastSyncAt])
            Text(
                if (last == null) stringResource(Res.string.settings_sync_never) else stringResource(Res.string.settings_sync_last, shortDateTime(last, locale)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (busy) {
                LoadingRow()
            } else {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = {
                        act {
                            val body = DeviceBackup(vm.graph).export(ExportChoice.everything(includeAccounts = settings[DataPrefs.webDavIncludeAccounts]))
                            sync.upload(config, body).also { result ->
                                if (result.isSuccess) vm.set(DataPrefs.webDavLastSyncAt, XtaTime.iso(Instant.now()))
                            }
                        }
                    }, modifier = Modifier.weight(1f)) { Text(stringResource(Res.string.settings_sync_upload)) }
                    OutlinedButton(onClick = {
                        act {
                            val result = sync.download(config)
                            if (result.isSuccess) {
                                offer(result, acknowledge = true)
                                null
                            } else {
                                result
                            }
                        }
                    }, modifier = Modifier.weight(1f)) { Text(stringResource(Res.string.settings_sync_download)) }
                }
            }
            TextButton(
                onClick = {
                    act {
                        val current = sync.download(config)
                        if (current.isSuccess) {
                            versions = WebDavSync.versionsIn(current.body)
                            null
                        } else {
                            current
                        }
                    }
                },
                enabled = !busy,
                modifier = Modifier.padding(horizontal = 8.dp),
            ) { Text(stringResource(Res.string.settings_sync_history)) }
        }
    }

    versions?.let { list ->
        AlertDialog(
            onDismissRequest = { versions = null },
            title = { Text(stringResource(Res.string.settings_sync_history)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (list.isEmpty()) Text(stringResource(Res.string.settings_no_results), modifier = Modifier.padding(16.dp))
                    list.forEach { version ->
                        val at = XtaTime.parse(version.at)
                        TextButton(onClick = {
                            versions = null
                            act {
                                val result = sync.downloadVersion(config, version)
                                if (result.isSuccess) {
                                    offer(result, acknowledge = false)
                                    null
                                } else {
                                    result
                                }
                            }
                        }, modifier = Modifier.fillMaxWidth()) { Text(at?.let { shortDateTime(it, locale) } ?: version.at) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { versions = null }) { Text(stringResource(Res.string.settings_cancel)) } },
        )
    }

    offered?.let { (document, downloaded) ->
        RestoreDialog(document, vm, snackbar, onClose = { offered = null }) { restored ->
            // Only a confirmed restore becomes the baseline the next upload is checked against.
            if (restored && downloaded != null) scope.launch { sync.acknowledge(config, downloaded) }
        }
    }
}

@Composable
private fun webDavMessage(result: WebDavResult): String {
    val text = stringResource(
        when (result.outcome) {
            WebDavOutcome.SUCCESS -> Res.string.settings_sync_uploaded
            WebDavOutcome.NOT_CONFIGURED -> Res.string.settings_sync_not_configured
            WebDavOutcome.INSECURE_URL -> Res.string.settings_sync_insecure
            WebDavOutcome.UNAUTHORIZED -> Res.string.settings_sync_unauthorized
            WebDavOutcome.NOT_FOUND -> Res.string.settings_sync_not_found
            WebDavOutcome.SERVER_ERROR -> Res.string.settings_sync_server_error
            WebDavOutcome.NETWORK_ERROR -> Res.string.settings_sync_network_error
            WebDavOutcome.CONFLICT -> Res.string.settings_sync_conflict
            WebDavOutcome.UNSAFE_SERVER -> Res.string.settings_sync_unsafe
        },
    )
    val detail = result.detail?.takeIf { !result.isSuccess && it.isNotEmpty() }
    return if (detail == null) text else "$text ($detail)"
}

// ---- Diagnostics (diagnostics_screen.dart) ----

@Composable
internal fun DiagnosticsSettings(title: String, vm: SettingsViewModel, onBack: () -> Unit) {
    val graph = vm.graph
    val session by vm.session.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val locale = currentLocale()
    var revision by remember { mutableIntStateOf(0) }
    val crashes by produceState(emptyList<CrashReport>(), revision) { value = withContext(Dispatchers.IO) { graph.crashReports.list() } }
    val report = remember(session.accounts, settings, crashes, revision) {
        DiagnosticsReport.of(
            graph.version,
            session.accounts,
            graph.client.endpoints,
            graph.client.rateLimits,
            settings,
            crashes.size,
            Instant.now(),
            health = graph.accountHealth,
            operations = graph.client.reads.snapshot(),
        )
    }
    var readsShown by rememberSaveable { mutableStateOf(false) }
    val copied = stringResource(Res.string.settings_diagnostics_copied)
    SettingsPage(
        title,
        onBack,
        snackbar,
        actions = {
            IconButton(onClick = { revision++ }) { Icon(Icons.Outlined.Refresh, null) }
            IconButton(onClick = {
                clipboard.setText(AnnotatedString((listOf(report.toPlainText()) + crashes.map { it.text }).joinToString("\n\n")))
                scope.launch { snackbar.showSnackbar(copied) }
            }) { Icon(Icons.Outlined.Share, stringResource(Res.string.settings_diagnostics_copy)) }
        },
    ) { padding ->
        SettingsList(padding) {
            RecentReads(report.operations, readsShown) { readsShown = !readsShown }
            SettingsHeader(stringResource(Res.string.settings_account))
            if (report.accounts.isEmpty()) NavigationRow(stringResource(Res.string.settings_diagnostics_no_accounts), chevron = false, icon = Icons.Outlined.Info) {}
            report.accounts.forEach { account -> AccountHealthRow(account, locale) }
            SettingsHeader(stringResource(Res.string.settings_diagnostics_endpoints))
            NavigationRow(
                report.registryFetchedAt?.let { stringResource(Res.string.settings_diagnostics_checked, shortDateTime(it, locale)) }
                    ?: stringResource(Res.string.settings_diagnostics_never_checked),
                if (report.registryEnabled) null else stringResource(Res.string.settings_disabled),
                Icons.Outlined.Refresh,
                chevron = false,
            ) {}
            report.endpoints.forEach { endpoint ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(endpoint.name, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            if (endpoint.overridden) "${endpoint.queryId} · ${stringResource(Res.string.settings_diagnostics_updated)}" else endpoint.queryId,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(endpoint.host, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            SettingsHeader(stringResource(Res.string.settings_crash_title))
            if (crashes.isEmpty()) NavigationRow(stringResource(Res.string.settings_crash_none), chevron = false, icon = Icons.Outlined.CheckCircle) {}
            crashes.forEach { crash ->
                NavigationRow(
                    shortDateTime(crash.at, locale),
                    crash.text.lineSequence().dropWhile { it != "```" }.drop(1).firstOrNull(),
                    Icons.Outlined.Warning,
                    chevron = false,
                ) {
                    clipboard.setText(AnnotatedString(crash.text))
                    scope.launch { snackbar.showSnackbar(copied) }
                }
            }
        }
    }
}

/** XTA's "Recent read operations" fold: the reads asked of X lately, one line each, to select and copy. */
@Composable
private fun RecentReads(lines: List<String>, shown: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onToggle).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(Res.string.settings_diagnostics_recent_reads), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Icon(if (shown) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (shown) {
        SelectionContainer {
            Text(lines.joinToString("\n"), style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth().padding(16.dp))
        }
    }
}

/** An account as XTA's diagnostics show it: working, or its sign-in resting until when and the endpoints X limits it on. */
@Composable
private fun AccountHealthRow(account: AccountDiagnostics, locale: Locale) {
    val problems = listOfNotNull(
        account.signInBrokenUntil?.let { stringResource(Res.string.settings_diagnostics_sign_in_broken, shortDateTime(it, locale)) },
        account.rateLimited.size.takeIf { it > 0 }?.let { pluralStringResource(Res.plurals.settings_diagnostics_rate_limited, it, it) },
    )
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (account.isHealthy) Icons.Outlined.CheckCircle else Icons.Outlined.Warning,
            null,
            tint = if (account.isHealthy) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(account.screenName?.let { "@$it" } ?: stringResource(Res.string.settings_account), style = MaterialTheme.typography.bodyLarge)
            Text(
                if (problems.isEmpty()) stringResource(Res.string.settings_diagnostics_ok) else problems.joinToString("\n"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
