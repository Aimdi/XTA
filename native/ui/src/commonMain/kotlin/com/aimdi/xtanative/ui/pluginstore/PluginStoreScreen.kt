package com.aimdi.xtanative.ui.pluginstore

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aimdi.xtanative.data.plugins.AppPlugins
import com.aimdi.xtanative.data.plugins.PluginUse
import com.aimdi.xtanative.data.plugins.RunningPlugin
import com.aimdi.xtanative.pluginhost.PluginCategory
import com.aimdi.xtanative.pluginhost.PluginFailure
import com.aimdi.xtanative.pluginhost.PluginState
import com.aimdi.xtanative.pluginhost.PluginStatus
import com.aimdi.xtanative.ui.common.Hairline
import com.aimdi.xtanative.ui.plugins.MessageText
import com.aimdi.xtanative.ui.plugins.PluginScaffold
import com.aimdi.xtanative.ui.common.currentLocale
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.cancel
import com.aimdi.xtanative.ui.generated.resources.plugin_more_options
import com.aimdi.xtanative.ui.generated.resources.plugin_available
import com.aimdi.xtanative.ui.generated.resources.plugin_catalogue_cached
import com.aimdi.xtanative.ui.generated.resources.plugin_catalogue_unavailable
import com.aimdi.xtanative.ui.generated.resources.plugin_category_art
import com.aimdi.xtanative.ui.generated.resources.plugin_category_bookmarks
import com.aimdi.xtanative.ui.generated.resources.plugin_category_communities
import com.aimdi.xtanative.ui.generated.resources.plugin_category_markets
import com.aimdi.xtanative.ui.generated.resources.plugin_category_media
import com.aimdi.xtanative.ui.generated.resources.plugin_category_newsletters
import com.aimdi.xtanative.ui.generated.resources.plugin_category_other
import com.aimdi.xtanative.ui.generated.resources.plugin_category_sites
import com.aimdi.xtanative.ui.generated.resources.plugin_category_social
import com.aimdi.xtanative.ui.generated.resources.plugin_downloading
import com.aimdi.xtanative.ui.generated.resources.plugin_failed_damaged
import com.aimdi.xtanative.ui.generated.resources.plugin_failed_download
import com.aimdi.xtanative.ui.generated.resources.plugin_failed_download_broke
import com.aimdi.xtanative.ui.generated.resources.plugin_failed_file
import com.aimdi.xtanative.ui.generated.resources.plugin_failed_load
import com.aimdi.xtanative.ui.generated.resources.plugin_failed_manifest
import com.aimdi.xtanative.ui.generated.resources.plugin_failed_newer_plugin
import com.aimdi.xtanative.ui.generated.resources.plugin_failed_not_offered
import com.aimdi.xtanative.ui.generated.resources.plugin_failed_signature
import com.aimdi.xtanative.ui.generated.resources.plugin_failed_storage
import com.aimdi.xtanative.ui.generated.resources.plugin_install
import com.aimdi.xtanative.ui.generated.resources.plugin_installed
import com.aimdi.xtanative.ui.generated.resources.plugin_installing
import com.aimdi.xtanative.ui.generated.resources.plugin_needs_newer_app
import com.aimdi.xtanative.ui.generated.resources.plugin_not_started
import com.aimdi.xtanative.ui.generated.resources.plugin_open
import com.aimdi.xtanative.ui.generated.resources.plugin_pin
import com.aimdi.xtanative.ui.generated.resources.plugin_storage_empty
import com.aimdi.xtanative.ui.generated.resources.plugin_storage_used
import com.aimdi.xtanative.ui.generated.resources.plugin_store
import com.aimdi.xtanative.ui.generated.resources.plugin_store_empty
import com.aimdi.xtanative.ui.generated.resources.plugin_store_show_private
import com.aimdi.xtanative.ui.generated.resources.plugin_uninstall
import com.aimdi.xtanative.ui.generated.resources.plugin_uninstall_confirm
import com.aimdi.xtanative.ui.generated.resources.plugin_uninstall_confirm_detail
import com.aimdi.xtanative.ui.generated.resources.plugin_update
import com.aimdi.xtanative.ui.generated.resources.plugin_update_to
import com.aimdi.xtanative.ui.generated.resources.plugin_version
import com.aimdi.xtanative.ui.generated.resources.retry
import com.aimdi.xtanative.ui.generated.resources.settings
import com.aimdi.xtanative.ui.plugins.currentLanguage
import java.text.NumberFormat
import java.util.Locale
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The plugin store, in XTA's shape: the plugins installed first, then what
 * the catalogue offers by category. Install, update and uninstall run in the
 * store's own engine, so leaving the screen does not stop them; a failure is
 * said in plain words on its row.
 */
@Composable
fun PluginStoreScreen(onBack: () -> Unit, onOpen: (String) -> Unit, onSettings: (String) -> Unit) {
    val plugins = LocalPlugins.current
    val store by plugins.manager.state.collectAsStateWithLifecycle()
    val running by plugins.running.collectAsStateWithLifecycle()
    val pins by plugins.stripPins.collectAsStateWithLifecycle(emptyList())
    val problems by plugins.startProblems.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var confirming by remember { mutableStateOf<PluginState?>(null) }
    val locale = currentLocale()
    LaunchedEffect(Unit) { plugins.manager.refresh() }

    PluginScaffold(
        title = stringResource(Res.string.plugin_store),
        onBack = onBack,
        actions = {
            IconButton(onClick = { scope.launch { plugins.manager.refresh() } }, enabled = !store.catalogue.refreshing) {
                Icon(Icons.Filled.Refresh, stringResource(Res.string.retry))
            }
            ShowPrivateMenu(store.showPrivate) { show -> scope.launch { plugins.showPrivate(show) } }
        },
    ) { padding ->
        val actions = StoreActions(
            open = onOpen,
            settings = onSettings,
            install = { id -> scope.launch { plugins.install(id) } },
            update = { id -> scope.launch { plugins.manager.update(id) } },
            cancel = plugins.manager::cancel,
            uninstall = { state -> confirming = state },
            pin = { id, pinned -> scope.launch { plugins.pinOnStrip(id, pinned) } },
        )
        LazyColumn(
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (store.catalogue.refreshing) item(key = "refreshing") { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (store.catalogue.unreachable) item(key = "unreachable") { Unreachable(store.catalogue.fetchedAt != null) }
            val installed = store.installed
            if (installed.isNotEmpty()) {
                item(key = "installed") { Header(stringResource(Res.string.plugin_installed)) }
                items(installed, key = { "installed-" + it.id }) { state ->
                    InstalledRow(state, running.firstOrNull { it.plugin.id == state.id }, state.id in pins, problems[state.id], plugins, locale, actions)
                }
            }
            available(store.onOffer, locale, actions)
            if (installed.isEmpty() && store.onOffer.isEmpty() && !store.catalogue.refreshing) {
                item(key = "empty") { MessageText(stringResource(Res.string.plugin_store_empty)) }
            }
        }
    }

    confirming?.let { state ->
        UninstallDialog(
            name = state.name(locale),
            onConfirm = {
                confirming = null
                scope.launch { plugins.uninstall(state.id) }
            },
            onDismiss = { confirming = null },
        )
    }
}

/** What the store's rows ask for. */
private class StoreActions(
    val open: (String) -> Unit,
    val settings: (String) -> Unit,
    val install: (String) -> Unit,
    val update: (String) -> Unit,
    val cancel: (String) -> Unit,
    val uninstall: (PluginState) -> Unit,
    /** On Home's strip or off it. */
    val pin: (String, Boolean) -> Unit,
)

/** What is on offer and not installed, under Available, by category in the store's order. */
private fun LazyListScope.available(offer: Map<PluginCategory, List<PluginState>>, locale: Locale, actions: StoreActions) {
    if (offer.isEmpty()) return
    item(key = "available") { Header(stringResource(Res.string.plugin_available)) }
    PluginCategory.entries.forEach { category ->
        val plugins = offer[category].orEmpty()
        if (plugins.isEmpty()) return@forEach
        item(key = "category-" + category.key) { CategoryHeader(stringResource(categoryName(category))) }
        items(plugins, key = { "available-" + it.id }) { state -> AvailableRow(state, locale, actions) }
    }
}

@Composable
private fun InstalledRow(
    state: PluginState,
    running: RunningPlugin?,
    pinned: Boolean,
    startProblem: String?,
    plugins: AppPlugins,
    locale: Locale,
    actions: StoreActions,
) {
    val plugin = running?.plugin
    val name = plugin?.title(currentLanguage()) ?: state.name(locale)
    val opens = plugin != null && (plugin.clientTab != null || plugin.homeTab != null)
    val use by produceState<PluginUse?>(null, state.installed?.version, running) { value = plugins.use(state.id) }
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = opens || plugin?.settings != null) { if (opens) actions.open(state.id) else actions.settings(state.id) },
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            PluginMark(running, name, 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                StatusLine(installedStatus(state.status, startProblem, use, locale), failed = state.status is PluginStatus.Failed || startProblem != null)
                use?.let { Footprint(it, locale) }
            }
            if (state.status is PluginStatus.UpdateAvailable) {
                FilledTonalButton(onClick = { actions.update(state.id) }) { Text(stringResource(Res.string.plugin_update)) }
            } else if (opens) {
                TextButton(onClick = { actions.open(state.id) }) { Text(stringResource(Res.string.plugin_open)) }
            }
            if (plugin?.settings != null) {
                IconButton(onClick = { actions.settings(state.id) }) {
                    Icon(Icons.Filled.Settings, stringResource(Res.string.settings), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            InstalledMenu(
                hasTab = plugin?.homeTab != null,
                pinned = pinned,
                onPin = { pin -> actions.pin(state.id, pin) },
                onUninstall = { actions.uninstall(state) },
            )
        }
        Progress(state.status)
    }
    Hairline()
}

@Composable
private fun AvailableRow(state: PluginState, locale: Locale, actions: StoreActions) {
    val name = state.name(locale)
    val status = state.status
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            PluginMark(null, name, 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                state.description(locale).takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                offerStatus(status, locale)?.let { StatusLine(it, failed = status is PluginStatus.Failed) }
            }
            Spacer(Modifier.width(8.dp))
            when (status) {
                is PluginStatus.Downloading -> TextButton(onClick = { actions.cancel(state.id) }) { Text(stringResource(Res.string.cancel)) }
                PluginStatus.Installing, is PluginStatus.NeedsNewerApp -> Unit
                else -> FilledTonalButton(onClick = { actions.install(state.id) }) { Text(stringResource(Res.string.plugin_install)) }
            }
        }
        Progress(status)
    }
}

/** The line under an installed plugin's name: its version and the size of its code, or what is under way, or what went wrong. */
@Composable
private fun installedStatus(status: PluginStatus, startProblem: String?, use: PluginUse?, locale: Locale): String = when (status) {
    is PluginStatus.Downloading -> stringResource(Res.string.plugin_downloading, percent(status.fraction, locale))
    PluginStatus.Installing -> stringResource(Res.string.plugin_installing)
    is PluginStatus.UpdateAvailable -> stringResource(Res.string.plugin_update_to, status.installed, status.available)
    is PluginStatus.NeedsNewerApp -> stringResource(Res.string.plugin_needs_newer_app)
    is PluginStatus.Failed -> failureText(status.reason)
    is PluginStatus.Installed -> when {
        startProblem != null -> stringResource(Res.string.plugin_not_started)
        else -> stringResource(Res.string.plugin_version, status.version, size(use?.codeBytes ?: 0, locale))
    }
    PluginStatus.Available -> ""
}

/** The line under a plugin on offer: what is under way, what it needs, or what went wrong; null when it is simply on offer. */
@Composable
private fun offerStatus(status: PluginStatus, locale: Locale): String? = when (status) {
    is PluginStatus.Downloading -> stringResource(Res.string.plugin_downloading, percent(status.fraction, locale))
    PluginStatus.Installing -> stringResource(Res.string.plugin_installing)
    is PluginStatus.NeedsNewerApp -> stringResource(Res.string.plugin_needs_newer_app)
    is PluginStatus.Failed -> failureText(status.reason)
    else -> null
}

/** Why an install, update or uninstall did not happen, in plain words. */
@Composable
private fun failureText(reason: PluginFailure): String = when (reason) {
    PluginFailure.NotOffered, PluginFailure.NotInstalled -> stringResource(Res.string.plugin_failed_not_offered)
    is PluginFailure.NeedsNewerApp -> stringResource(Res.string.plugin_needs_newer_app)
    is PluginFailure.NeedsNewerPlugin -> stringResource(Res.string.plugin_failed_newer_plugin)
    is PluginFailure.DownloadFailed -> reason.code?.let { stringResource(Res.string.plugin_failed_download, it) }
        ?: stringResource(Res.string.plugin_failed_download_broke)
    is PluginFailure.WrongSize, PluginFailure.WrongChecksum -> stringResource(Res.string.plugin_failed_file)
    is PluginFailure.BadSignature -> stringResource(Res.string.plugin_failed_signature)
    is PluginFailure.BadManifest -> stringResource(Res.string.plugin_failed_manifest)
    PluginFailure.Damaged -> stringResource(Res.string.plugin_failed_damaged)
    is PluginFailure.LoadFailed -> stringResource(Res.string.plugin_failed_load)
    is PluginFailure.StorageFailed -> stringResource(Res.string.plugin_failed_storage)
}

@Composable
private fun StatusLine(text: String, failed: Boolean) {
    if (text.isEmpty()) return
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
    )
}

/** What the plugin keeps, as XTA's store says it: "12 items · 1.2 MB", or nothing yet. */
@Composable
private fun Footprint(use: PluginUse, locale: Locale) {
    val text = if (use.items == 0 && use.dataBytes == 0L) {
        stringResource(Res.string.plugin_storage_empty)
    } else {
        stringResource(Res.string.plugin_storage_used, use.items, size(use.dataBytes, locale))
    }
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
}

/** The download's progress under its row. */
@Composable
private fun Progress(status: PluginStatus) {
    when (status) {
        is PluginStatus.Downloading -> LinearProgressIndicator(progress = { status.fraction }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        PluginStatus.Installing -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        else -> Unit
    }
}

/**
 * A plugin's mark: its own icon in its colour once it runs; before that, the
 * catalogue gives no picture, so its initial.
 */
@Composable
private fun PluginMark(running: RunningPlugin?, name: String, size: Dp) {
    val plugin = running?.plugin
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(plugin?.brandColor?.copy(alpha = 0.16f) ?: MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (plugin != null) {
            Icon(plugin.icon, contentDescription = null, tint = plugin.brandColor, modifier = Modifier.size(size * 0.6f))
        } else {
            Text(name.take(1).uppercase(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun InstalledMenu(hasTab: Boolean, pinned: Boolean, onPin: (Boolean) -> Unit, onUninstall: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.MoreVert, stringResource(Res.string.plugin_more_options), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (hasTab) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.plugin_pin)) },
                    onClick = {
                        open = false
                        onPin(!pinned)
                    },
                    trailingIcon = { Checkbox(checked = pinned, onCheckedChange = null) },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.plugin_uninstall), color = MaterialTheme.colorScheme.error) },
                onClick = {
                    open = false
                    onUninstall()
                },
            )
        }
    }
}

@Composable
private fun ShowPrivateMenu(showPrivate: Boolean, onChange: (Boolean) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Filled.MoreVert, stringResource(Res.string.plugin_more_options)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.plugin_store_show_private)) },
                onClick = {
                    open = false
                    onChange(!showPrivate)
                },
                trailingIcon = { Checkbox(checked = showPrivate, onCheckedChange = null) },
            )
        }
    }
}

/** XTA's confirm: what goes with the plugin, and that it can come back. */
@Composable
private fun UninstallDialog(name: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.plugin_uninstall_confirm, name)) },
        text = { Text(stringResource(Res.string.plugin_uninstall_confirm_detail)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(Res.string.plugin_uninstall), color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
    )
}

/** The catalogue could not be read; [cached] when the list shown is the last that arrived. */
@Composable
private fun Unreachable(cached: Boolean) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(Res.string.plugin_catalogue_unavailable), style = MaterialTheme.typography.bodyMedium)
            if (cached) {
                Text(
                    stringResource(Res.string.plugin_catalogue_cached),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Header(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

@Composable
private fun CategoryHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 2.dp),
    )
}

private fun categoryName(category: PluginCategory): StringResource = when (category) {
    PluginCategory.SOCIAL -> Res.string.plugin_category_social
    PluginCategory.COMMUNITIES -> Res.string.plugin_category_communities
    PluginCategory.NEWSLETTERS -> Res.string.plugin_category_newsletters
    PluginCategory.ART -> Res.string.plugin_category_art
    PluginCategory.MARKETS -> Res.string.plugin_category_markets
    PluginCategory.BOOKMARKS -> Res.string.plugin_category_bookmarks
    PluginCategory.MEDIA -> Res.string.plugin_category_media
    PluginCategory.SITES -> Res.string.plugin_category_sites
    PluginCategory.OTHER -> Res.string.plugin_category_other
}

/** 45 %, as [locale] writes a share. */
private fun percent(fraction: Float, locale: Locale): String = NumberFormat.getPercentInstance(locale).format(fraction.toDouble())

/** 412 KB, 1.2 MB, as [locale] writes the number. */
private fun size(bytes: Long, locale: Locale): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> String.format(locale, "%.0f KB", bytes / 1024.0)
    else -> String.format(locale, "%.1f MB", bytes / (1024.0 * 1024.0))
}
