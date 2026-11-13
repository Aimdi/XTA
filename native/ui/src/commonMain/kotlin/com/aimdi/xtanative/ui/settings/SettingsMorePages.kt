package com.aimdi.xtanative.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aimdi.xtanative.core.session.Session
import com.aimdi.xtanative.core.x.XAccount
import com.aimdi.xtanative.core.x.XAccountHealth
import com.aimdi.xtanative.services.ai.AiProvider
import com.aimdi.xtanative.store.settings.AccountPrefs
import com.aimdi.xtanative.store.settings.AiPrefs
import com.aimdi.xtanative.store.settings.HomePrefs
import com.aimdi.xtanative.store.settings.NetworkPrefs
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.*
import com.aimdi.xtanative.ui.home.AccountMark
import com.aimdi.xtanative.ui.signin.forgetWebSessions
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

// ---- Home (_home.dart) ----

@Composable
internal fun HomeSettings(title: String, vm: SettingsViewModel, onBack: () -> Unit) {
    val session by vm.session.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val minimum = stringResource(Res.string.settings_home_minimum)
    val first = remember { HomePages.load(vm.settings.value, vm.session.value.groups.map { it.id }) }
    var pages by remember { mutableStateOf(first.pages) }
    LaunchedEffect(Unit) {
        // Loading the pages seeds a network's tab once and repairs an empty bar, as XTA's loadPages writes back.
        vm.update { it.with(HomePrefs.pages, first.saved).with(HomePrefs.seededNetworkTabs, first.seeded) }
    }
    fun save(next: List<HomePage>) {
        pages = next
        vm.set(HomePrefs.pages, HomePages.selectedIds(next))
    }
    SettingsPage(
        title,
        onBack,
        snackbar,
        actions = {
            IconButton(onClick = {
                vm.set(HomePrefs.pages, HomePages.DEFAULTS)
                pages = HomePages.load(vm.settings.value.with(HomePrefs.pages, HomePages.DEFAULTS), session.groups.map { it.id }).pages
            }) { Icon(Icons.Outlined.Refresh, stringResource(Res.string.settings_reset_home_pages)) }
        },
    ) { padding ->
        SettingsList(padding) {
            pages.forEachIndexed { index, page ->
                Row(
                    Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = page.selected, onCheckedChange = { on ->
                        val next = HomePages.toggled(pages, page.id, on)
                        if (next == null) scope.launch { snackbar.showSnackbar(minimum) } else save(next)
                    })
                    Text(
                        homePageLabel(page.id) { group -> session.groups.firstOrNull { it.id == group }?.name },
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { save(HomePages.moved(pages, index, index - 1)) }, enabled = index > 0) {
                        Icon(Icons.Outlined.KeyboardArrowUp, stringResource(Res.string.settings_move_up))
                    }
                    IconButton(onClick = { save(HomePages.moved(pages, index, index + 1)) }, enabled = index < pages.lastIndex) {
                        Icon(Icons.Outlined.KeyboardArrowDown, stringResource(Res.string.settings_move_down))
                    }
                }
            }
        }
    }
}

// ---- Accounts (_account.dart) ----

@Composable
internal fun AccountSettings(title: String, vm: SettingsViewModel, onBack: () -> Unit, onOpen: (String) -> Unit, onStartOver: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val session by vm.session.collectAsStateWithLifecycle()
    var deleting by remember { mutableStateOf<String?>(null) }
    var clearing by remember { mutableStateOf(false) }
    var avatar by remember { mutableStateOf(false) }
    val hidden = settings[AccountPrefs.disabledAccountIds]
    val ids = session.accounts.map { it.id }
    SettingsPage(
        title,
        onBack,
        actions = {
            IconButton(onClick = { onOpen(SettingsRoutes.SIGN_IN) }) { Icon(Icons.Outlined.Add, stringResource(Res.string.signin_add_account)) }
        },
    ) { padding ->
        SettingsList(padding) {
            AvatarRow(session.accounts.firstOrNull(), settings[AccountPrefs.avatarRevision]) { avatar = true }
            SettingsDivider()
            if (session.accounts.isEmpty()) {
                Text(stringResource(Res.string.settings_accounts_empty), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(24.dp))
            } else {
                Text(
                    stringResource(Res.string.settings_accounts_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                session.accounts.forEach { account ->
                    val on = ForYouAccounts.isOn(account.id, hidden)
                    AccountRow(account, on, locked = on && !ForYouAccounts.canTurnOff(account.id, ids, hidden), onDelete = { deleting = account.id }) { next ->
                        vm.update { current ->
                            ForYouAccounts.toggled(current[AccountPrefs.disabledAccountIds], account.id, next, ids)
                                ?.let { current.with(AccountPrefs.disabledAccountIds, it) } ?: current
                        }
                    }
                }
            }
            SettingsDivider()
            NavigationRow(stringResource(Res.string.settings_clear), stringResource(Res.string.settings_clear_explained), chevron = false, destructive = true) {
                clearing = true
            }
        }
    }
    if (avatar) AvatarSheet(onDismiss = { avatar = false })
    deleting?.let { id ->
        val name = session.accounts.firstOrNull { it.id == id }?.screenName ?: stringResource(Res.string.settings_unknown_username)
        ConfirmDialog(
            stringResource(Res.string.settings_are_you_sure),
            stringResource(Res.string.settings_account_delete_confirm, name),
            stringResource(Res.string.settings_delete),
            stringResource(Res.string.settings_cancel),
            destructive = true,
            onDismiss = { deleting = null },
        ) {
            deleting = null
            vm.updateSession { it.copy(accounts = it.accounts.filterNot { account -> account.id == id }) }
            vm.update { it.with(AccountPrefs.disabledAccountIds, it[AccountPrefs.disabledAccountIds] - id) }
            vm.graph.keep { vm.graph.accountHealth.write(id, XAccountHealth()) }
            // As XTA does: the web view's session goes too, so the next sign-in shows X's form.
            forgetWebSessions()
        }
    }
    if (clearing) {
        ConfirmDialog(
            stringResource(Res.string.settings_clear),
            stringResource(Res.string.settings_clear_explained),
            stringResource(Res.string.settings_clear_confirm),
            stringResource(Res.string.settings_cancel),
            destructive = true,
            onDismiss = { clearing = false },
        ) {
            clearing = false
            vm.graph.keep {
                vm.graph.sessions.update { Session() }
                vm.graph.clearFeedCaches()
                vm.graph.signers.clear()
            }
            forgetWebSessions()
            onStartOver()
        }
    }
}

/** Your avatar (XTA's _ChromeAvatarSettingsTile): the mark as it shows in the upper left, and what a tap does. */
@Composable
private fun AvatarRow(account: XAccount?, revision: Int, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 72.dp).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AccountMark(account, 40.dp)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(Res.string.settings_avatar_title), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(if (revision > 0) Res.string.settings_avatar_change else Res.string.settings_avatar_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * One account (XTA's HomeAccountToggleTile): on or off for the feeds, a tap
 * turning it, and why it cannot be turned off when it is the last one on;
 * then the way to remove it.
 */
@Composable
private fun AccountRow(account: XAccount, on: Boolean, locked: Boolean, onDelete: () -> Unit, onToggle: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .selectable(selected = on, enabled = !locked, role = Role.Checkbox) { onToggle(!on) }
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Icon(if (on) Icons.Filled.Person else Icons.Outlined.Person, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(account.screenName ?: stringResource(Res.string.settings_unknown_username), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (locked) {
                Text(stringResource(Res.string.home_filter_keep_one), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Checkbox(checked = on, onCheckedChange = null, enabled = !locked)
        IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, stringResource(Res.string.settings_delete)) }
    }
}

// ---- AI (_ai.dart) ----

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AiSettings(title: String, vm: SettingsViewModel, onBack: () -> Unit) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val saved = stringResource(Res.string.settings_ai_saved)
    var form by remember {
        val settings = vm.settings.value
        mutableStateOf(AiForm(settings[AiPrefs.baseUrl], settings[AiPrefs.apiKey], settings[AiPrefs.model]))
    }
    SettingsPage(title, onBack, snackbar) { padding ->
        SettingsList(padding) {
            Column(Modifier.padding(16.dp), verticalArrangement = FormSpacing) {
                Text(stringResource(Res.string.settings_ai_description), style = MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = { form = form.withPreset(AiProvider.GROK) },
                        label = { Text(stringResource(Res.string.settings_ai_grok)) },
                        leadingIcon = { Icon(Icons.Outlined.Star, null, Modifier.size(18.dp)) },
                    )
                    AssistChip(onClick = { form = form.withPreset(AiProvider.OPENROUTER) }, label = { Text(stringResource(Res.string.settings_ai_openrouter)) })
                    AssistChip(onClick = { form = form.withPreset(AiProvider.OPENAI) }, label = { Text(stringResource(Res.string.settings_ai_openai)) })
                }
                OutlinedTextField(
                    value = form.baseUrl,
                    onValueChange = { form = form.copy(baseUrl = it) },
                    label = { Text(stringResource(Res.string.settings_ai_base_url)) },
                    placeholder = { Text(AiProvider.GROK.baseUrl) },
                    supportingText = { Text(stringResource(Res.string.settings_ai_base_url_description)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = form.apiKey,
                    onValueChange = { form = form.copy(apiKey = it) },
                    label = { Text(stringResource(Res.string.settings_ai_api_key)) },
                    supportingText = when {
                        form.isOpenRouter -> ({ Text(stringResource(Res.string.settings_ai_openrouter_key_hint)) })
                        form.isGrok -> ({ Text(stringResource(Res.string.settings_ai_grok_key_hint)) })
                        else -> null
                    },
                    singleLine = true,
                    visualTransformation = if (form.keyHidden) PasswordVisualTransformation() else VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                    trailingIcon = {
                        TextButton(onClick = { form = form.copy(keyHidden = !form.keyHidden) }) {
                            Text(stringResource(if (form.keyHidden) Res.string.settings_show else Res.string.settings_hide))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = form.model,
                    onValueChange = { form = form.copy(model = it) },
                    label = { Text(stringResource(Res.string.settings_ai_model)) },
                    placeholder = { Text(form.modelPlaceholder) },
                    supportingText = if (form.isOpenRouter) ({ Text(stringResource(Res.string.settings_ai_openrouter_model_hint)) }) else null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(onClick = {
                    val config = form.saved()
                    vm.update { it.with(AiPrefs.baseUrl, config.baseUrl).with(AiPrefs.apiKey, config.apiKey).with(AiPrefs.model, config.model) }
                    scope.launch { snackbar.showSnackbar(saved) }
                }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(Res.string.settings_save)) }
            }
        }
    }
}

// ---- A network's section, from its preferences, until its plugin brings its own ----

@Composable
internal fun NetworkSettings(network: NetworkPrefs, vm: SettingsViewModel, onBack: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    SettingsPage(networkName(network.id), onBack) { padding ->
        SettingsList(padding) {
            network.showTab?.let { pref ->
                SwitchRow(stringResource(Res.string.settings_show_as_tab), checked = settings[pref]) { vm.set(pref, it) }
            }
            network.inHomeFeed?.let { pref ->
                SwitchRow(stringResource(Res.string.settings_in_home_timeline), checked = settings[pref]) { vm.set(pref, it) }
            }
            NetworkSections.fields(network).forEach { field ->
                when (field) {
                    is NetworkField.Flag -> SwitchRow(fieldLabel(field.label), checked = settings[field.pref]) { vm.set(field.pref, it) }
                    is NetworkField.Text -> {
                        val value = settings[field.pref]
                        TextRow(
                            fieldLabel(field.label),
                            when {
                                value.isEmpty() -> stringResource(Res.string.settings_not_set)
                                field.secret -> "••••••••"
                                else -> value
                            },
                            value,
                            hint = fieldHint(field.label),
                            secret = field.secret,
                            url = !field.secret,
                        ) { vm.set(field.pref, it.trim()) }
                    }
                }
            }
        }
    }
}

@Composable
private fun fieldLabel(label: NetworkFieldLabel): String = stringResource(
    when (label) {
        NetworkFieldLabel.THREADS_INSTANCE -> Res.string.settings_threads_instance
        NetworkFieldLabel.BLUESKY_INSTANCE -> Res.string.settings_bluesky_instance
        NetworkFieldLabel.MASTODON_INSTANCE -> Res.string.settings_mastodon_instance
        NetworkFieldLabel.BOORU_HOST -> Res.string.settings_booru_host
        NetworkFieldLabel.KARAKEEP_SERVER -> Res.string.settings_karakeep_server
        NetworkFieldLabel.DEEPMARKS_SERVER -> Res.string.settings_deepmarks_server
        NetworkFieldLabel.DEEPMARKS_SIGNING_KEY -> Res.string.settings_deepmarks_signing_key
        NetworkFieldLabel.IMMICH_SERVER -> Res.string.settings_immich_server
        NetworkFieldLabel.IMMICH_ALBUM_PER_FOLDER -> Res.string.settings_immich_album
        NetworkFieldLabel.IMMICH_INCLUDE_VIDEOS -> Res.string.settings_immich_videos
        NetworkFieldLabel.API_KEY -> Res.string.settings_api_key
    },
)

@Composable
private fun fieldHint(label: NetworkFieldLabel): String? = when (label) {
    NetworkFieldLabel.THREADS_INSTANCE -> stringResource(Res.string.settings_threads_instance_hint)
    NetworkFieldLabel.BLUESKY_INSTANCE -> stringResource(Res.string.settings_bluesky_instance_hint)
    NetworkFieldLabel.MASTODON_INSTANCE -> stringResource(Res.string.settings_mastodon_instance_hint)
    NetworkFieldLabel.BOORU_HOST -> stringResource(Res.string.settings_booru_host_hint)
    NetworkFieldLabel.KARAKEEP_SERVER -> stringResource(Res.string.settings_karakeep_server_hint)
    NetworkFieldLabel.IMMICH_SERVER -> stringResource(Res.string.settings_immich_server_hint)
    else -> null
}
