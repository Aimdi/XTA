package com.aimdi.xtanative.plugins.pixiv

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.store.settings.PixivPrefs
import com.aimdi.xtanative.ui.plugins.PluginText
import com.aimdi.xtanative.ui.plugins.currentLanguage
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.post.PostIcons
import com.aimdi.xtanative.ui.saved.SavedIcons
import com.aimdi.xtanative.ui.subscriptions.GroupIcons

/** One line of More: where it goes, and whether it needs an account. */
private class MoreEntry(val id: String, val icon: ImageVector, val label: PluginText, val needsAccount: Boolean = false, val open: PixivRuntime.(novels: Boolean, outside: PixivOutside) -> Unit)

private val moreEntries = listOf(
    MoreEntry("history", PostIcons.History, Strings.history) { novels, _ -> open(PixivRoute.History(if (novels) PixivKind.NOVEL else PixivKind.WORK)) },
    MoreEntry("favorite-tags", PixivIcons.Label, Strings.search_favorite_tags) { _, _ -> open(PixivRoute.FavoriteTags) },
    MoreEntry("pixivision", PostIcons.Article, Strings.pixivision_articles, needsAccount = true) { _, _ -> open(PixivRoute.PixivisionList) },
    MoreEntry("downloads", SavedIcons.Download, Strings.downloads_title) { _, _ -> host.openDownloads() },
    MoreEntry("preferences", PostIcons.Tune, Strings.more_preferences) { _, _ -> open(PixivRoute.Settings) },
    MoreEntry("mute", PixivIcons.VolumeOff, Strings.more_mute) { _, _ -> open(PixivRoute.Mute) },
    MoreEntry("manage", Icons.Filled.AccountCircle, Strings.manage_account, needsAccount = true) { _, outside -> outside.open(MANAGE_URL) },
)

private const val MANAGE_URL = "https://www.pixiv.net/settings/account"

/** More: the account (switch, add, sign out), then history, favorite tags, Pixivision, the app's downloads, preferences, mute and Pixiv's own account page. */
@Composable
internal fun MorePane(novels: Boolean, padding: PaddingValues, toTop: Int) {
    val runtime = LocalPixiv.current
    val sheets = LocalPixivSheets.current
    val settings = pixivSettings()
    val signedIn = PixivAccounts.signedIn(settings)
    val outside = rememberOutside()
    var switching by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(signedIn, settings[PixivPrefs.userId]) { name = if (signedIn) PixivSession.verified(runtime)?.displayName else null }
    val scroll = rememberLazyListState()
    ListToTop(scroll, toTop)
    val signOut = rememberSignOut()
    LazyColumn(state = scroll, contentPadding = padding) {
        item(key = "account") { AccountHeader(PixivAccounts.active(settings), name, signedIn, onSignIn = { runtime.open(PixivRoute.SignIn) }, onSwitch = { switching = true }) }
        item(key = "rule") { HorizontalDivider() }
        items(moreEntries.filter { signedIn || !it.needsAccount }, key = { it.id }) { entry ->
            ActionRow(pluginString(entry.label), { entry.open(runtime, novels, outside) }, icon = entry.icon)
        }
        item(key = "about") { ActionRow(pluginString(Strings.more_about), null, icon = Icons.Outlined.Info, subtitle = pluginString(Strings.plugin_description)) }
        if (signedIn) item(key = "sign-out") { ActionRow(pluginString(Strings.sign_out), { signOut(sheets.ask) }, icon = Icons.AutoMirrored.Filled.ExitToApp) }
    }
    if (switching) {
        AccountSwitcher(onClose = { switching = false }) {
            switching = false
            runtime.open(PixivRoute.SignIn)
        }
    }
}

/** Asks first, then signs the account in use out of this device, saying which one took over. */
@Composable
internal fun rememberSignOut(): (PixivAsk) -> Unit {
    val runtime = LocalPixiv.current
    val language = currentLanguage()
    return remember(runtime, language) {
        { ask ->
            val name = PixivAccounts.active(runtime.settings.now)?.displayName
            val question = if (name == null) Strings.sign_out_question.get(language) else Strings.sign_out_named.get(language, name)
            ask.ask(question, Strings.sign_out.get(language)) {
                PixivSession.signOut(runtime)?.let { next -> runtime.tell(Strings.signed_in.get(language, next.displayName)) }
            }
        }
    }
}

/** Who is signed in, with their @account and Premium, or the way to sign in. */
@Composable
private fun AccountHeader(account: PixivAccount?, name: String?, signedIn: Boolean, onSignIn: () -> Unit, onSwitch: () -> Unit) {
    if (!signedIn) {
        ListItem(
            headlineContent = { Text(pluginString(Strings.sign_in)) },
            supportingContent = { Text(pluginString(Strings.not_configured)) },
            leadingContent = { Icon(Icons.Outlined.Person, null) },
            modifier = Modifier.clickable(onClick = onSignIn),
        )
        return
    }
    val shown = account?.displayName ?: name ?: pluginString(Strings.more_account)
    ListItem(
        headlineContent = { Text(shown, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = account?.takeIf { it.account.isNotEmpty() || it.isPremium }?.let { known -> { AccountDetails(known) } },
        leadingContent = { PixivAvatar(account?.avatarUrl, shown, size = 48.dp) },
        trailingContent = { IconButton(onClick = onSwitch) { Icon(GroupIcons.PeopleOutlined, pluginString(Strings.switch_account)) } },
        modifier = Modifier.clickable(onClick = onSwitch).padding(vertical = 4.dp),
    )
}

@Composable
private fun AccountDetails(account: PixivAccount) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (account.account.isNotEmpty()) Text("@" + account.account)
        if (account.isPremium) PremiumBadge()
    }
}

@Composable
internal fun PremiumBadge() {
    val scheme = MaterialTheme.colorScheme
    Text(
        pluginString(Strings.premium),
        style = MaterialTheme.typography.labelSmall,
        color = scheme.onTertiaryContainer,
        modifier = Modifier.background(scheme.tertiaryContainer, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** The accounts on this device to switch between, and Add account. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountSwitcher(onClose: () -> Unit, onAdd: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onClose) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
            Text(pluginString(Strings.switch_account), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 8.dp))
            PixivAccountList(onChanged = onClose)
            ListItem(
                headlineContent = { Text(pluginString(Strings.add_account)) },
                leadingContent = { Icon(GroupIcons.PersonAddOutlined, null) },
                modifier = Modifier.clickable(onClick = onAdd),
            )
        }
    }
}

/** The accounts stored on this device, the one in use checked: a tap switches to another, which can also be removed. */
@Composable
internal fun PixivAccountList(onChanged: () -> Unit = {}) {
    val runtime = LocalPixiv.current
    val sheets = LocalPixivSheets.current
    val settings = pixivSettings()
    val language = currentLanguage()
    PixivAccounts.of(settings).forEach { account ->
        val active = account.userId == settings[PixivPrefs.userId]
        ListItem(
            headlineContent = { Text(account.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            supportingContent = account.account.takeIf { it.isNotEmpty() }?.let { { Text("@$it", maxLines = 1, overflow = TextOverflow.Ellipsis) } },
            leadingContent = { PixivAvatar(account.avatarUrl, account.displayName) },
            trailingContent = {
                if (active) {
                    Icon(Icons.Filled.CheckCircle, pluginString(Strings.account_active), tint = MaterialTheme.colorScheme.primary)
                } else {
                    IconButton(onClick = {
                        sheets.ask.ask(Strings.account_remove_question.get(language, account.displayName), Strings.account_remove.get(language)) {
                            PixivSession.remove(runtime, account)
                            onChanged()
                        }
                    }) { Icon(Icons.Outlined.Delete, pluginString(Strings.account_remove)) }
                }
            },
            colors = ListItemDefaults.colors(headlineColor = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface),
            modifier = Modifier.clickable(enabled = !active) {
                PixivSession.switchTo(runtime, account)
                runtime.tell(Strings.signed_in.get(language, account.displayName))
                onChanged()
            },
        )
    }
}
