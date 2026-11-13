package com.aimdi.xtanative.plugins.threads

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.threads.ThreadsSessionCheck
import com.aimdi.xtanative.networks.threads.ThreadsXy
import com.aimdi.xtanative.networks.threads.ThreadsXyException
import com.aimdi.xtanative.store.settings.Pref
import com.aimdi.xtanative.store.settings.ThreadsPrefs
import com.aimdi.xtanative.ui.plugins.PluginAvatar
import com.aimdi.xtanative.ui.plugins.AddToGroupButton
import com.aimdi.xtanative.ui.plugins.HomeTimelineSwitch
import com.aimdi.xtanative.ui.plugins.NoticeHost
import com.aimdi.xtanative.ui.plugins.PluginFrame
import com.aimdi.xtanative.ui.plugins.PluginText
import com.aimdi.xtanative.ui.plugins.SecretField
import com.aimdi.xtanative.ui.plugins.currentLanguage
import com.aimdi.xtanative.ui.plugins.pluginString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Where the reader points the plugin, and who they follow with it
 * (threads_settings.dart), in XTA's order: public reading, whether the
 * accounts followed here join Home's timelines, the switch that spends the
 * session on followed accounts, the accounts (each to put in groups or
 * remove), the optional session with its warning, an RSSHub instance and an
 * Xy server, each with its test. The session and the token are never backed
 * up or exported.
 */
@Composable
internal fun ThreadsSettingsBody(app: ThreadsApp, frame: PluginFrame) {
    val settings by app.prefs.collectAsState()
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(app) {
        app.ready()
        loaded = true
    }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = frame.padding, modifier = Modifier.fillMaxSize()) {
            item(key = "guest") { GuestCard() }
            item(key = "intro") { Paragraph(Strings.settings_intro) }
            item(key = "home") { HomeTimelineSwitch(app.host.settings, pluginString(Strings.in_home_feed), pluginString(Strings.in_home_feed_description)) }
            item(key = "session-apis") {
                Toggle(Strings.use_session, Strings.use_session_description, settings[ThreadsPrefs.useSessionApis]) { on -> app.update { it.with(ThreadsPrefs.useSessionApis, on) } }
            }
            if (app.direct.isSessionParked) item(key = "parked") { Parked() }
            item(key = "accounts") { Section(Strings.accounts) { Accounts(app) } }
            if (loaded) {
                item(key = "session") { Section(Strings.session_section) { Session(app) } }
                item(key = "rsshub") { Section(Strings.instance) { RssHub(app) } }
                item(key = "xy") { Section(null) { Xy(app) } }
            }
        }
        NoticeHost(app.notices)
    }
}

@Composable
private fun GuestCard() {
    Card(Modifier.fillMaxWidth().padding(16.dp)) {
        ListItem(
            leadingContent = { Icon(ThreadsIcons.Public, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            headlineContent = { Text(pluginString(Strings.guest_card)) },
            supportingContent = { Text(pluginString(Strings.guest_card_body)) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
}

@Composable
private fun Paragraph(text: PluginText, color: Color = MaterialTheme.colorScheme.onSurface) {
    Text(pluginString(text), style = MaterialTheme.typography.bodyMedium, color = color, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
}

/** That Meta parked the session, with XTA's paused mark in the error colour. */
@Composable
private fun Parked() {
    ListItem(
        leadingContent = { Icon(ThreadsIcons.PauseCircle, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        headlineContent = { Text(pluginString(Strings.session_parked)) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
private fun Toggle(title: PluginText, detail: PluginText, on: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        leadingContent = { Icon(Icons.Filled.Warning, contentDescription = null) },
        headlineContent = { Text(pluginString(title)) },
        supportingContent = { Text(pluginString(detail)) },
        trailingContent = { Switch(checked = on, onCheckedChange = onChange) },
    )
}

/** A divided section: its title when it has one, then its rows. */
@Composable
private fun Section(title: PluginText?, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        HorizontalDivider(Modifier.padding(vertical = 16.dp))
        title?.let { Text(pluginString(it), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 16.dp)) }
        content()
    }
}

/** The accounts followed here, each to put in the app's groups or remove. */
@Composable
private fun Accounts(app: ThreadsApp) {
    val rows by app.accounts.state.collectAsState()
    val accounts = ThreadsAccounts.accounts(rows)
    if (accounts.isEmpty()) {
        Text(pluginString(Strings.no_accounts), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp))
        return
    }
    var unfollowing by remember { mutableStateOf<String?>(null) }
    accounts.forEach { account ->
        ListItem(
            leadingContent = { PluginAvatar(account.avatarUrl, account.name, account.handle, 36.dp) },
            headlineContent = { Text("@" + account.handle) },
            trailingContent = {
                Row {
                    AddToGroupButton(app.host, account.handle)
                    IconButton(onClick = { unfollowing = account.handle }) { Icon(Icons.Filled.Delete, contentDescription = pluginString(Strings.unfollow)) }
                }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
    unfollowing?.let { handle -> UnfollowDialog(handle, onConfirm = { app.unfollow(handle) }) { unfollowing = null } }
}

/**
 * The session: why it is risky, Threads' own sign-in page where there is a
 * web view, the cookies and the Bearer with what is wrong with them as they
 * are pasted, where to find the cookies, whom the session signs in as, a
 * test and a way to clear both.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Session(app: ThreadsApp) {
    val signIns by app.signIns.collectAsState()
    var cookies by remember(signIns) { mutableStateOf(app.current()[ThreadsPrefs.directCookies]) }
    var bearer by remember(signIns) { mutableStateOf(app.current()[ThreadsPrefs.directBearer]) }
    var testing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val language = currentLanguage()
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(pluginString(Strings.direct_intro), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
        Text(pluginString(Strings.session_unlocks), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (webSignIn) WebSignIn(app)
        SignedIn(app)
        val cookieProblem = ThreadsFailures.pasted(ThreadsSessionCheck.cookies(cookies), language)
        Secret(app, ThreadsPrefs.directCookies, cookies, Strings.direct_cookies, Strings.direct_cookies_hint, lines = 4, supporting = cookieProblem, keep = ThreadsSessionCheck::kept) { cookies = it }
        CookieSteps()
        val bearerProblem = Strings.bearer_invalid.get(language).takeIf { ThreadsSessionCheck.badBearer(bearer) }
        Secret(app, ThreadsPrefs.directBearer, bearer, Strings.direct_bearer, Strings.direct_bearer_hint, supporting = bearerProblem) { bearer = it }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(enabled = !testing, onClick = {
                testing = true
                scope.launch {
                    app.host.settings.update { it.with(ThreadsPrefs.directCookies, ThreadsSessionCheck.kept(cookies)).with(ThreadsPrefs.directBearer, bearer.trim()) }
                    app.notices.tell(ThreadsFailures.untestable(cookies, bearer, language) ?: app.checkSession())
                    testing = false
                }
            }) { Text(pluginString(Strings.direct_test)) }
            TextButton(onClick = {
                cookies = ""
                bearer = ""
                app.clearSession()
            }) { Text(pluginString(Strings.direct_clear)) }
        }
    }
}

/** Threads' own sign-in page, its session kept as a pasted one is. */
@Composable
private fun WebSignIn(app: ThreadsApp) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FilledTonalButton(onClick = { app.host.open(ThreadsRoutes.SIGN_IN) }) {
            Icon(ThreadsIcons.Mark, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(pluginString(Strings.sign_in_web), modifier = Modifier.padding(start = 8.dp))
        }
        Text(pluginString(Strings.sign_in_web_detail), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** "Signed in as @handle", once the session was tried with a read. */
@Composable
private fun SignedIn(app: ThreadsApp) {
    val handle by app.signedIn.collectAsState()
    val shown = handle ?: return
    ListItem(
        leadingContent = { Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        headlineContent = { Text(pluginString(Strings.signed_in_as, shown)) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

/** Where a desktop browser shows the cookies, folded under its title; open where there is no sign-in page. */
@Composable
private fun CookieSteps() {
    var open by rememberSaveable { mutableStateOf(!webSignIn) }
    Column {
        ListItem(
            leadingContent = { Icon(Icons.Filled.Info, contentDescription = null) },
            headlineContent = { Text(pluginString(Strings.cookies_how)) },
            trailingContent = { Icon(if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, contentDescription = null) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.clickable { open = !open },
        )
        if (open) Text(pluginString(Strings.cookies_steps), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp))
    }
}

/** A secret field written as it is typed, as [keep] keeps it, to the preference XTA keeps it in, which no backup carries. */
@Composable
private fun Secret(
    app: ThreadsApp,
    pref: Pref<String>,
    value: String,
    label: PluginText,
    hint: PluginText?,
    lines: Int = 1,
    supporting: String? = null,
    keep: (String) -> String = String::trim,
    onValue: (String) -> Unit,
) {
    SecretField(
        value = value,
        onValueChange = { typed ->
            onValue(typed)
            app.update { it.with(pref, keep(typed)) }
        },
        label = pluginString(label),
        reveal = pluginString(Strings.show),
        conceal = pluginString(Strings.hide),
        hint = hint?.let { pluginString(it) },
        supporting = supporting,
        lines = lines,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** The RSSHub instance posts may be read through, and its test against a real feed. */
@Composable
private fun RssHub(app: ThreadsApp) {
    val language = currentLanguage()
    var instance by remember { mutableStateOf(app.current()[ThreadsPrefs.instance]) }
    var testing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(pluginString(Strings.instance_intro), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
        Address(instance, Strings.instance, Strings.instance_hint) { typed ->
            instance = typed
            app.update { it.with(ThreadsPrefs.instance, typed.trim()) }
        }
        FilledTonalButton(enabled = !testing, onClick = {
            testing = true
            scope.launch {
                app.notices.tell(tested(language) { app.rssHub.verify(instance.trim()); Strings.test_ok.get(language) })
                testing = false
            }
        }) { Text(pluginString(Strings.test)) }
    }
}

/** The reader's Xy server for richer profiles: its address, its token, and a test of the address alone. */
@Composable
private fun Xy(app: ThreadsApp) {
    val language = currentLanguage()
    var base by remember { mutableStateOf(app.current()[ThreadsPrefs.apiBase].ifBlank { ThreadsXy.DEFAULT_BASE }) }
    var token by remember { mutableStateOf(app.current()[ThreadsPrefs.apiToken]) }
    var testing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(pluginString(Strings.api_intro), style = MaterialTheme.typography.bodyMedium)
        Address(base, Strings.api_base, null) { typed ->
            base = typed
            app.update { it.with(ThreadsPrefs.apiBase, typed.trim()) }
        }
        Secret(app, ThreadsPrefs.apiToken, token, Strings.api_token, null) { token = it }
        FilledTonalButton(enabled = !testing, onClick = {
            testing = true
            scope.launch {
                app.notices.tell(testedXy(language) { if (app.xy.health(base.trim())) Strings.api_test_ok.get(language) else Strings.error_unreachable.get(language) })
                testing = false
            }
        }) { Text(pluginString(Strings.test)) }
    }
}

@Composable
private fun Address(value: String, label: PluginText, hint: PluginText?, onValue: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        singleLine = true,
        label = { Text(pluginString(label)) },
        placeholder = hint?.let { { Text(pluginString(it)) } },
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Uri),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** What a test says: its own sentence when it passed, else the settings' words for why it failed. */
private suspend fun tested(language: String, test: suspend () -> String): String = try {
    test()
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    ThreadsFailures.settings(e).get(language)
}

/** An Xy test's sentence: what the server itself said when it said something. */
private suspend fun testedXy(language: String, test: suspend () -> String): String = try {
    test()
} catch (e: CancellationException) {
    throw e
} catch (e: ThreadsXyException) {
    e.serverMessage?.trim()?.ifEmpty { null } ?: ThreadsFailures.xy(e).get(language)
} catch (e: Exception) {
    Strings.error_unreachable.get(language)
}
