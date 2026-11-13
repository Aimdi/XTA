package com.aimdi.xtanative.ui.pluginstore

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aimdi.xtanative.data.plugins.PluginMembers
import com.aimdi.xtanative.data.plugins.PluginSaves
import com.aimdi.xtanative.data.plugins.PluginSheet
import com.aimdi.xtanative.data.plugins.keepable
import com.aimdi.xtanative.store.settings.SubscriptionPrefs
import com.aimdi.xtanative.ui.common.LocalGraph
import com.aimdi.xtanative.ui.plugins.PluginPostArchive
import com.aimdi.xtanative.ui.plugins.PluginSharedPost
import com.aimdi.xtanative.ui.plugins.PostMenuHandlers
import com.aimdi.xtanative.ui.plugins.PostMenuRows
import com.aimdi.xtanative.ui.plugins.currentLanguage
import com.aimdi.xtanative.ui.plugins.pathIcon
import com.aimdi.xtanative.ui.post.CashtagLinks
import com.aimdi.xtanative.ui.post.MenuRow
import com.aimdi.xtanative.ui.saved.Keeping
import com.aimdi.xtanative.ui.saved.SaveSheet
import com.aimdi.xtanative.ui.saved.SavedNoteEditor
import com.aimdi.xtanative.ui.subscriptions.GroupBoardModel
import com.aimdi.xtanative.ui.subscriptions.GroupMembershipSheet
import com.aimdi.xtanative.ui.subscriptions.GroupPicker

/**
 * The sheets the plugins open over the app's screens (PluginHost's archive
 * and group calls): a post's save sheet, the note on it, the group picker for
 * an account, a post's menu, and an X post's cashtag's sheet, one at a time.
 * The window's root composes it once, beside its screens.
 */
@Composable
fun PluginSheetHost() {
    val plugins = LocalPlugins.current
    val sheet by plugins.sheet.collectAsStateWithLifecycle()
    val close = plugins::dismissSheet
    when (val shown = sheet) {
        is PluginSheet.Folders -> key(shown) { SaveSheet(remember { shown.post.keepable() }, close) }
        is PluginSheet.Note -> key(shown) { NoteSheet(shown.post, close) }
        is PluginSheet.Groups -> key(shown) { GroupsSheet(shown, close) }
        is PluginSheet.Menu -> key(shown) { PostMenuSheet(shown, close) }
        is PluginSheet.Cashtag -> key(shown) { CashtagSheet(shown.symbol, close) }
        null -> Unit
    }
}

/** The note on [post] in Saved (XTA's editPluginPostNote): written, it saves the post first where a plain tap files. */
@Composable
private fun NoteSheet(post: PluginPostArchive, onDismiss: () -> Unit) = SavedNoteEditor(remember(post) { post.keepable() }, onDismiss)

/** The group picker for a plugin's account (XTA's editPluginAccountGroups): the choice applies as it closes. */
@Composable
private fun GroupsSheet(sheet: PluginSheet.Groups, onDismiss: () -> Unit) {
    val graph = LocalGraph.current
    val plugins = LocalPlugins.current
    val loadedSession by graph.sessions.state.collectAsStateWithLifecycle()
    val loadedSettings by graph.settings.state.collectAsStateWithLifecycle()
    val session = loadedSession ?: return
    val settings = loadedSettings ?: return
    val initial = remember(sheet) { session.groups.filter { sheet.memberId in it.memberIds }.mapTo(LinkedHashSet()) { it.id } }
    GroupMembershipSheet(
        groups = GroupBoardModel.ordered(session.groups, settings[SubscriptionPrefs.groupsOrder], settings[SubscriptionPrefs.groupsAscending]),
        selected = initial,
        recent = GroupPicker.recent(settings[SubscriptionPrefs.groupsRecentPicks]),
        create = plugins.groups::create,
        onDone = { chosen ->
            onDismiss()
            if (chosen != null) plugins.putInGroups(sheet, initial, chosen)
        },
    )
}

/**
 * A plugin post's menu (XTA's showPluginPostActions), in XTA's order: saving
 * it on this device or taking it out, the note on it, its author's groups,
 * sharing its link, its folder, and the browser ([PostMenuRows]); then what
 * the running plugins add ([PluginSheetEntries]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PostMenuSheet(sheet: PluginSheet.Menu, onDismiss: () -> Unit) {
    val plugins = LocalPlugins.current
    val graph = LocalGraph.current
    val menu = sheet.menu
    val saved by plugins.savedIds.collectAsStateWithLifecycle()
    val handlers = remember(menu) {
        val item = menu.post.keepable()
        PostMenuHandlers(
            toggleSaved = { Keeping.toggleSave(graph, item) },
            editNote = { plugins.show(PluginSheet.Note(menu.post)) },
            addToGroup = { author -> plugins.show(PluginSheet.Groups(PluginMembers.memberId(sheet.pluginId, author), menu.follow)) },
            chooseFolder = { plugins.show(PluginSheet.Folders(menu.post)) },
        )
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 16.dp)) {
            PostMenuRows(menu, menu.post.id in saved, handlers, onDismiss)
            PluginSheetEntries(remember(menu) { PluginSaves.shared(menu) }, onDismiss)
        }
    }
}

/**
 * A cashtag's sheet, as XTA's long press on one opens it (rich_text.dart's
 * _showCashtagSheet): the cashtag, leading where a tap on it does (a
 * plugin's screen for it, else X's search), then what the running plugins
 * add for it, Stocks' Add to watchlist; each closes the sheet, then acts.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CashtagSheet(symbol: String, onDismiss: () -> Unit) {
    val plugins = LocalPlugins.current
    val running by plugins.running.collectAsStateWithLifecycle()
    val language = currentLanguage()
    val entries = remember(symbol, running, language) { plugins.cashtagEntries(symbol, language, running) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 16.dp)) {
            MenuRow(ShowChart, CashtagLinks.title(symbol)) {
                onDismiss()
                plugins.openCashtag(symbol)
            }
            entries.forEach { entry ->
                MenuRow(entry.icon, entry.label) {
                    onDismiss()
                    entry.run()
                }
            }
        }
    }
}

/** Material's show_chart (Apache 2.0), the cashtag's mark in its sheet as in XTA's. */
private val ShowChart: ImageVector by lazy { pathIcon("ShowChart", "M3.5,18.49l6,-6.01l4,4L22,6.92l-1.41,-1.41l-7.09,7.97l-4,-4L2,16.99L3.5,18.49z") }

/**
 * The running plugins' entries for [post] in a post sheet, below the app's
 * own, as XTA's share sheet ends with them: each closes the sheet, then runs
 * beyond it.
 */
@Composable
fun PluginSheetEntries(post: PluginSharedPost, onDismiss: () -> Unit) {
    val plugins = LocalPlugins.current
    val running by plugins.running.collectAsStateWithLifecycle()
    val language = currentLanguage()
    val entries = remember(post, running, language) { plugins.sheetEntries(post, language, running) }
    entries.forEach { entry ->
        MenuRow(entry.icon, entry.label) {
            onDismiss()
            entry.run()
        }
    }
}
