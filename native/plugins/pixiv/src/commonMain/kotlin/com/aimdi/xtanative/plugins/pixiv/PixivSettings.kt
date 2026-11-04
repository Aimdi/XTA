package com.aimdi.xtanative.plugins.pixiv

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.store.settings.PixivPrefs
import com.aimdi.xtanative.store.settings.PixivSection
import com.aimdi.xtanative.ui.plugins.PluginFrame
import com.aimdi.xtanative.ui.plugins.PluginScaffold
import com.aimdi.xtanative.ui.plugins.currentLanguage
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.post.PostIcons
import com.aimdi.xtanative.ui.subscriptions.GroupIcons
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * The plugin's settings, in XTA's sections and order: the account, what is
 * shown, browsing, viewing, bookmarking (kept on this device), saving, and
 * muting. Its own keys, as XTA backs them up.
 */
@Composable
internal fun PixivSettingsBody(runtime: PixivRuntime, frame: PluginFrame) {
    PixivRoot(runtime) { SettingsList(frame.padding) }
}

/** The settings as a screen, as More opens them. */
@Composable
internal fun PixivSettingsScreen() {
    val runtime = LocalPixiv.current
    PluginScaffold(title = pluginString(Strings.title), onBack = runtime.host::back) { padding ->
        Box(Modifier.padding(padding)) { SettingsList(PaddingValues()) }
    }
}

@Composable
private fun SettingsList(padding: PaddingValues) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
        item(key = "intro") { Text(pluginString(Strings.settings_intro), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp)) }
        val sections: List<@Composable () -> Unit> = listOf(
            { AccountSettings() },
            { ContentSettings() },
            { BrowsingSettings() },
            { ViewingSettings() },
            { BookmarkSettings() },
            { DownloadSettings() },
            { MuteSettingsRow() },
        )
        sections.forEachIndexed { index, section ->
            item(key = "section-$index") {
                if (index > 0) HorizontalDivider()
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) { section() }
            }
        }
    }
}

/** The accounts on this device, Add account (or Sign in), Sign out, and the refresh token for readers who paste one. */
@Composable
private fun AccountSettings() {
    val runtime = LocalPixiv.current
    val sheets = LocalPixivSheets.current
    val settings = pixivSettings()
    val signedIn = PixivAccounts.signedIn(settings)
    val signOut = rememberSignOut()
    PixivAccountList()
    ActionRow(pluginString(if (signedIn) Strings.add_account else Strings.sign_in), { runtime.open(PixivRoute.SignIn) }, icon = if (signedIn) GroupIcons.PersonAddOutlined else Icons.Outlined.Person)
    if (signedIn) ActionRow(pluginString(Strings.sign_out), { signOut(sheets.ask) }, icon = Icons.AutoMirrored.Filled.ExitToApp)
    TokenField()
}

/** The token in use, hidden until revealed; it is put in use once the reader leaves the field or tests it. */
@Composable
private fun TokenField() {
    val runtime = LocalPixiv.current
    val scope = rememberCoroutineScope()
    val language = currentLanguage()
    val stored = pixivSettings()[PixivPrefs.refreshToken]
    var text by remember(stored) { mutableStateOf(stored) }
    var revealed by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(pluginString(Strings.advanced_token), style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text(pluginString(Strings.refresh_token_hint)) },
            singleLine = true,
            visualTransformation = if (revealed) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).onFocusChanged { state ->
                if (state.isFocused) {
                    focused = true
                } else if (focused) {
                    focused = false
                    PixivSession.useToken(runtime, text.trim())
                }
            },
        )
        Row {
            TextButton(onClick = { revealed = !revealed }) { Text(pluginString(Strings.reveal)) }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(enabled = !testing && text.isNotBlank(), onClick = {
                PixivSession.useToken(runtime, text.trim())
                testing = true
                scope.launch {
                    val user = PixivSession.verified(runtime)
                    testing = false
                    runtime.tell(user?.let { Strings.signed_in.get(language, it.displayName) } ?: Strings.error_unauthorized.get(language))
                }
            }) { Text(pluginString(Strings.test)) }
        }
    }
}

/** Show R-18, what the account itself shows of AI works (changed on Pixiv), and Hide AI. */
@Composable
private fun ContentSettings() {
    val runtime = LocalPixiv.current
    val settings = pixivSettings()
    SwitchRow(pluginString(Strings.show_r18), null, settings[PixivPrefs.showR18]) { runtime.settings.set(PixivPrefs.showR18, it) }
    if (PixivAccounts.signedIn(settings)) AccountAiRow(settings[PixivPrefs.userId])
    SwitchRow(pluginString(Strings.hide_ai), pluginString(Strings.hide_ai_description), settings[PixivPrefs.hideAi]) { runtime.settings.set(PixivPrefs.hideAi, it) }
}

private enum class AiShown { LOADING, FAILED, SHOWN, HIDDEN }

@Composable
private fun AccountAiRow(userId: Long) {
    val runtime = LocalPixiv.current
    val outside = rememberOutside()
    var shown by remember(userId) { mutableStateOf(AiShown.LOADING) }
    LaunchedEffect(userId) {
        shown = try {
            if (runtime.social.showsAiWorks()) AiShown.SHOWN else AiShown.HIDDEN
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AiShown.FAILED
        }
    }
    val state = when (shown) {
        AiShown.LOADING -> Strings.account_ai_loading
        AiShown.FAILED -> Strings.account_ai_failed
        AiShown.SHOWN -> Strings.account_ai_shown
        AiShown.HIDDEN -> Strings.account_ai_hidden
    }
    ActionRow(pluginString(Strings.account_ai), null, subtitle = pluginString(state), trailing = {
        TextButton(onClick = { outside.open(VIEWING_SETTINGS_URL) }) { Text(pluginString(Strings.change_on_pixiv)) }
    })
}

private const val VIEWING_SETTINGS_URL = "https://www.pixiv.net/settings/viewing"

/** Where the plugin opens, the history and its pause, and the copy template. */
@Composable
private fun BrowsingSettings() {
    val runtime = LocalPixiv.current
    val settings = pixivSettings()
    ChoiceRow(
        pluginString(Strings.start_section),
        settings[PixivPrefs.startSection],
        listOf(
            PixivSection.HOME to pluginString(Strings.home),
            PixivSection.RANKING to pluginString(Strings.tab_ranking),
            PixivSection.FAVORITES to pluginString(Strings.tab_favorites),
            PixivSection.SEARCH to pluginString(Strings.search),
        ),
    ) { runtime.settings.set(PixivPrefs.startSection, it) }
    ActionRow(pluginString(Strings.history), { runtime.open(PixivRoute.History(PixivKind.WORK)) }, icon = PostIcons.History)
    SwitchRow(pluginString(Strings.history_pause), pluginString(Strings.history_pause_description), settings[PixivPrefs.historyPaused]) { runtime.settings.set(PixivPrefs.historyPaused, it) }
    ActionRow(pluginString(Strings.copy_template), { runtime.open(PixivRoute.CopyTemplate) }, icon = PixivIcons.ContentCopy)
}

/** Bookmarks kept on this device: the work's tags, following, saving and the other way round; and haptics. */
@Composable
private fun BookmarkSettings() {
    val runtime = LocalPixiv.current
    val settings = pixivSettings()
    SectionTitle(pluginString(Strings.bookmark_settings))
    listOf(
        Triple(PixivPrefs.autoTagBookmarks, Strings.bookmark_auto_tag, Strings.bookmark_auto_tag_description),
        Triple(PixivPrefs.followAfterBookmark, Strings.bookmark_follow_after, Strings.bookmark_follow_after_description),
        Triple(PixivPrefs.downloadAfterBookmark, Strings.bookmark_save_after, Strings.bookmark_save_after_description),
        Triple(PixivPrefs.bookmarkAfterDownload, Strings.bookmark_after_save, Strings.bookmark_after_save_description),
        Triple(PixivPrefs.haptics, Strings.haptics, Strings.haptics_description),
    ).forEach { (pref, title, description) ->
        SwitchRow(pluginString(title), pluginString(description), settings[pref]) { runtime.settings.set(pref, it) }
    }
}

@Composable
private fun MuteSettingsRow() {
    val runtime = LocalPixiv.current
    ActionRow(pluginString(Strings.mute_settings), { runtime.open(PixivRoute.Mute) }, icon = PixivIcons.VolumeOff)
}
