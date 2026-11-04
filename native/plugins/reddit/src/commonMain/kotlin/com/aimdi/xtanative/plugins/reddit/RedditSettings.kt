package com.aimdi.xtanative.plugins.reddit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.reddit.RedditAuth
import com.aimdi.xtanative.store.settings.RedditNsfwMode
import com.aimdi.xtanative.store.settings.RedditPrefs
import com.aimdi.xtanative.store.settings.RedditSource
import com.aimdi.xtanative.ui.common.LoadingRow
import com.aimdi.xtanative.ui.plugins.HomeTimelineSwitch
import com.aimdi.xtanative.ui.plugins.PluginFrame
import com.aimdi.xtanative.ui.plugins.PluginScaffold
import com.aimdi.xtanative.ui.plugins.PluginText
import com.aimdi.xtanative.ui.plugins.pluginString
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.launch

/**
 * Everything Reddit is doing, and every switch that changes it (XTA's
 * `RedditSettingsScreen`): the sign-in and the client id, the route Reddit
 * is read through, the order listings read in, how NSFW posts are shown, and
 * spoilers.
 */
@Composable
internal fun RedditSettingsBody(app: RedditApp, frame: PluginFrame) {
    val credentials by app.credentials.state.collectAsState()
    val choices by app.choices.collectAsState()
    var sorting by remember { mutableStateOf(false) }
    var editingClientId by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { app.credentials.load() }
    val signedIn = credentials[RedditPrefs.refreshToken].isNotBlank()
    LazyColumn(contentPadding = frame.padding, modifier = Modifier.fillMaxSize()) {
        item(key = "account") { SectionHeader(pluginString(Strings.accounts)) }
        item(key = "sign-in") { AccountRow(app, signedIn, onNeedsClientId = { editingClientId = true }) }
        item(key = "client-id") {
            val clientId = credentials[RedditPrefs.clientId].trim()
            SettingRow(RedditGlyphs.Key, pluginString(Strings.client_id), clientId.ifEmpty { pluginString(Strings.client_id_help) }) { editingClientId = true }
        }
        item(key = "source") { SectionHeader(pluginString(Strings.source)) }
        item(key = "auto") { SourceRow(app, RedditSource.AUTO, RedditGlyphs.AutoMode, Strings.source_auto, Strings.source_auto_description, !choices.preferPublic) }
        item(key = "public") { SourceRow(app, RedditSource.PUBLIC, RedditGlyphs.VisibilityOff, Strings.source_public, Strings.source_public_description, choices.preferPublic) }
        item(key = "feed") { SectionHeader(pluginString(Strings.feed)) }
        item(key = "sort") { SettingRow(sortIcon(choices.sort), pluginString(Strings.sort), sortTitle(choices)) { sorting = true } }
        item(key = "home") { HomeTimelineSwitch(app.host.settings, pluginString(Strings.plugin_name)) }
        item(key = "nsfw") { SectionHeader(pluginString(Strings.nsfw_display)) }
        RedditNsfwMode.entries.forEach { mode -> item(key = "nsfw-" + mode.stored) { NsfwRow(app, mode, choices.nsfw == mode) } }
        item(key = "spoiler") { SectionHeader(pluginString(Strings.spoiler)) }
        item(key = "spoilers") { SpoilerRow(app, choices.showSpoilers) }
    }
    if (sorting) SortSheet(app) { sorting = false }
    if (editingClientId) ClientIdDialog(app, credentials[RedditPrefs.clientId]) { editingClientId = false }
}

/** Signed in or not, and, since the sign-in asks Reddit for `identity`, as whom: a device signed in to two accounts in its life cannot tell from "signed in". */
@Composable
private fun AccountRow(app: RedditApp, signedIn: Boolean, onNeedsClientId: () -> Unit) {
    val credentials by app.credentials.state.collectAsState()
    val choices by app.choices.collectAsState()
    val name by produceState<String?>(null, signedIn, credentials[RedditPrefs.clientId], choices.preferPublic) { value = app.accountName() }
    val scope = rememberCoroutineScope()
    ListItem(
        leadingContent = { Icon(if (signedIn) Icons.Outlined.AccountCircle else RedditGlyphs.Login, contentDescription = null) },
        headlineContent = { Text(pluginString(if (signedIn) Strings.signed_in else Strings.sign_in)) },
        supportingContent = name?.let { { Text("u/$it") } },
        trailingContent = if (signedIn) {
            { TextButton(onClick = { scope.launch { app.signOut() } }) { Text(pluginString(Strings.sign_out)) } }
        } else {
            null
        },
        modifier = if (signedIn) Modifier else Modifier.clickable { if (credentials[RedditPrefs.clientId].isBlank()) onNeedsClientId() else app.host.open(RedditRoutes.SIGN_IN) },
    )
}

/** One of the two routes, with what it costs said where the choice is made. */
@Composable
private fun SourceRow(app: RedditApp, source: RedditSource, icon: ImageVector, title: PluginText, description: PluginText, selected: Boolean) {
    SettingRow(icon, pluginString(title), pluginString(description), selected) { app.change { it.with(RedditPrefs.source, source) } }
}

@Composable
private fun NsfwRow(app: RedditApp, mode: RedditNsfwMode, selected: Boolean) {
    val (icon, title, description) = when (mode) {
        RedditNsfwMode.HIDE -> Triple(RedditGlyphs.VisibilityOff, Strings.nsfw_hide, Strings.nsfw_hide_description)
        RedditNsfwMode.TAP -> Triple(RedditGlyphs.TouchApp, Strings.nsfw_tap, Strings.nsfw_tap_description)
        RedditNsfwMode.SHOW -> Triple(RedditGlyphs.Visibility, Strings.nsfw_show, Strings.nsfw_show_description)
    }
    SettingRow(icon, pluginString(title), pluginString(description), selected) { app.change { it.with(RedditPrefs.nsfwMode, mode) } }
}

@Composable
private fun SpoilerRow(app: RedditApp, on: Boolean) {
    val flip = { app.store { it.with(RedditPrefs.showSpoilers, !on) } }
    ListItem(
        leadingContent = { Icon(RedditGlyphs.WarningAmber, contentDescription = null) },
        headlineContent = { Text(pluginString(Strings.show_spoilers)) },
        supportingContent = { Text(pluginString(Strings.show_spoilers_description)) },
        trailingContent = { Switch(checked = on, onCheckedChange = { flip() }) },
        modifier = Modifier.clickable { flip() },
    )
}

/** A settings row: its icon, what it is, what it is set to or does, and a check when it is the one chosen. */
@Composable
private fun SettingRow(icon: ImageVector, title: String, subtitle: String, selected: Boolean = false, onClick: (() -> Unit)? = null) {
    val accent = MaterialTheme.colorScheme.primary
    ListItem(
        leadingContent = { Icon(icon, contentDescription = null) },
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = if (selected) { { Icon(Icons.Filled.Check, contentDescription = null) } } else null,
        colors = if (selected) ListItemDefaults.colors(headlineColor = accent, leadingIconColor = accent, trailingIconColor = accent) else ListItemDefaults.colors(),
        modifier = if (onClick != null && !selected) Modifier.clickable(onClick = onClick) else Modifier,
    )
}

/** The client id, with what it is for and the redirect the registered app must carry, which cannot be guessed. */
@Composable
private fun ClientIdDialog(app: RedditApp, initial: String, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(pluginString(Strings.client_id)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val small = MaterialTheme.typography.bodySmall
                Text(pluginString(Strings.client_id_help), style = small)
                Text(pluginString(Strings.redirect_uri_help, RedditAuth.REDIRECT_URI), style = small)
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    placeholder = { Text(pluginString(Strings.client_id)) },
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                app.setClientId(text.trim())
                onDismiss()
            }) { Text(pluginString(Strings.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(pluginString(Strings.cancel)) } },
    )
}

/**
 * Reddit's sign-in page, watched for the way back (XTA's
 * `RedditLoginWebview`): the code on the redirect, when its state is the
 * one sent, is traded for the sign-in the plugin keeps. Any way back ends
 * the page, a declined or foreign one included.
 */
@Composable
internal fun SignInScreen(app: RedditApp) {
    val credentials by app.credentials.state.collectAsState()
    val clientId = credentials[RedditPrefs.clientId].trim()
    val state = remember { RedditAuth.oauthState() }
    val url = remember(clientId) { RedditAuth.authorizeUrl(clientId, state).toString() }
    val returned = remember { AtomicBoolean(false) }
    var working by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    PluginScaffold(title = pluginString(Strings.sign_in), onBack = app.host::back) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val failed = failure
            when {
                failed != null -> SignInFailed(failed)
                working -> LoadingRow()
                else -> RedditSignInPage(url, Modifier.fillMaxSize()) { address ->
                    if (!RedditAuth.isRedirect(address)) return@RedditSignInPage false
                    if (!returned.compareAndSet(false, true)) return@RedditSignInPage true
                    val code = RedditAuth.codeFrom(address, state)
                    if (code == null) {
                        app.host.back()
                    } else {
                        working = true
                        scope.launch {
                            attempt { app.signIn(clientId, code) }
                                .onSuccess { app.host.back() }
                                .onFailure { failure = it.message.orEmpty() }
                            working = false
                        }
                    }
                    true
                }
            }
        }
    }
}

@Composable
private fun SignInFailed(detail: String) {
    Column(Modifier.fillMaxWidth().padding(32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(pluginString(Strings.sign_in_failed), style = MaterialTheme.typography.bodyLarge)
        if (detail.isNotBlank()) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Reddit's sign-in page at [url] (Android's web view), handing [onAddress]
 * each address it goes to and staying off the ones it answers true for: the
 * redirect never resolves, so it is caught on its way. The desktop has no
 * web view: it says so, as XTA's desktop does.
 */
@Composable
internal expect fun RedditSignInPage(url: String, modifier: Modifier, onAddress: (String) -> Boolean)
