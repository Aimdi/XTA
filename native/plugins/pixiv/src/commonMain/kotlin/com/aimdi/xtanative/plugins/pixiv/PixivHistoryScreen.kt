package com.aimdi.xtanative.plugins.pixiv

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.pixiv.PixivIllust
import com.aimdi.xtanative.networks.pixiv.PixivNovel
import com.aimdi.xtanative.store.settings.PixivPrefs
import com.aimdi.xtanative.ui.plugins.PluginScaffold
import com.aimdi.xtanative.ui.plugins.currentLanguage
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.rememberKept
import com.aimdi.xtanative.ui.saved.SavedIcons

/**
 * What the reader opened on this device, the latest first: works or novels,
 * narrowed by what is typed. A long press forgets one; the bar clears them
 * all; the switch pauses the history.
 */
@Composable
internal fun PixivHistoryScreen(initial: PixivKind) {
    val runtime = LocalPixiv.current
    val sheets = LocalPixivSheets.current
    val language = currentLanguage()
    var kind by rememberSaveable { mutableStateOf(initial) }
    val history = if (kind == PixivKind.NOVEL) runtime.novelHistory else runtime.history
    LaunchedEffect(history) { history.load() }
    PluginScaffold(
        title = pluginString(Strings.history),
        onBack = runtime.host::back,
        actions = {
            IconButton(onClick = {
                val question = if (kind == PixivKind.NOVEL) Strings.history_novels_clear_question else Strings.history_clear_question
                sheets.ask.ask(question.get(language), Strings.history_clear.get(language)) { runtime.launch { history.clear() } }
            }) { Icon(SavedIcons.DeleteSweep, pluginString(Strings.history_clear)) }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            HistoryHeader(kind) { kind = it }
            Box(Modifier.weight(1f)) { HistoryBody(kind, history) }
        }
    }
}

@Composable
private fun HistoryHeader(kind: PixivKind, onKind: (PixivKind) -> Unit) {
    val runtime = LocalPixiv.current
    PixivSwitch(listOf(PixivKind.WORK, PixivKind.NOVEL), kind, { pluginString(if (it == PixivKind.NOVEL) Strings.profile_novels else Strings.profile_illusts) }, onKind)
    SwitchRow(pluginString(Strings.history_pause), pluginString(Strings.history_pause_description), pixivSettings()[PixivPrefs.historyPaused]) { runtime.settings.set(PixivPrefs.historyPaused, it) }
}

@Composable
private fun HistoryBody(kind: PixivKind, history: PixivHistory) {
    val runtime = LocalPixiv.current
    val sheets = LocalPixivSheets.current
    val language = currentLanguage()
    var query by rememberSaveable(kind) { mutableStateOf("") }
    val visits by history.state.collectAsState()
    val all = visits.orEmpty()
    val shown = all.filter { query.isBlank() || it.kept.matches(query) }
    val forget = { id: Long, title: String ->
        sheets.ask.ask(Strings.history_remove_question.get(language, title), Strings.delete.get(language)) { runtime.launch { history.remove(id) } }
    }
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(pluginString(if (kind == PixivKind.NOVEL) Strings.history_novels_filter_hint else Strings.history_filter_hint)) },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        )
        Box(Modifier.weight(1f)) {
            val empty = emptyText(kind, none = all.isEmpty())
            if (kind == PixivKind.NOVEL) NovelVisits(shown, empty, forget) else WorkVisits(shown, empty, forget)
        }
    }
}

@Composable
private fun emptyText(kind: PixivKind, none: Boolean): String = pluginString(
    when {
        kind == PixivKind.NOVEL && none -> Strings.history_novels_empty
        kind == PixivKind.NOVEL -> Strings.history_novels_no_match
        none -> Strings.history_empty
        else -> Strings.history_no_match
    },
)

@Composable
private fun WorkVisits(visits: List<PixivVisit>, empty: String, forget: (Long, String) -> Unit) {
    val runtime = LocalPixiv.current
    val works = rememberKept("pixiv-history-works") { scope -> PixivLocalList<PixivVisit, PixivIllust>(scope, { it.id }) { it.kept.toIllust() } }
    LaunchedEffect(visits) { works.show(visits) }
    PixivWorksGrid(works.list, empty, hideMuted = false, tile = { shown, index ->
        WorkTile(shown[index], onOpen = { runtime.openWork(shown, index) }, onLongPress = { forget(shown[index].id, shown[index].title) })
    })
}

@Composable
private fun NovelVisits(visits: List<PixivVisit>, empty: String, forget: (Long, String) -> Unit) {
    val novels = rememberKept("pixiv-history-novels") { scope -> PixivLocalList<PixivVisit, PixivNovel>(scope, { it.id }) { it.kept.toNovel() } }
    LaunchedEffect(visits) { novels.show(visits) }
    PixivRows(novels.list, { it.id }, empty) { novel -> NovelCard(novel, onLongPress = { forget(novel.id, novel.title) }) }
}
