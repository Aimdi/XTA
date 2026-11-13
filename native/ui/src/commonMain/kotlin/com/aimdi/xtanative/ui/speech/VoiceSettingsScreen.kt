package com.aimdi.xtanative.ui.speech

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aimdi.xtanative.services.speech.OfflineVoice
import com.aimdi.xtanative.services.speech.Tts
import com.aimdi.xtanative.services.speech.VoiceEngines
import com.aimdi.xtanative.services.speech.VoiceFailure
import com.aimdi.xtanative.services.speech.VoiceInstall
import com.aimdi.xtanative.services.util.formatByteSize
import com.aimdi.xtanative.ui.common.LocalGraph
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.reader_back
import com.aimdi.xtanative.ui.generated.resources.reader_cancel
import com.aimdi.xtanative.ui.generated.resources.reader_delete
import com.aimdi.xtanative.ui.generated.resources.reader_download
import com.aimdi.xtanative.ui.generated.resources.reader_ok
import com.aimdi.xtanative.ui.generated.resources.reader_retry
import com.aimdi.xtanative.ui.generated.resources.reader_speech_default
import com.aimdi.xtanative.ui.generated.resources.reader_speech_engine
import com.aimdi.xtanative.ui.generated.resources.reader_speech_get_sherpa
import com.aimdi.xtanative.ui.generated.resources.reader_speech_no_engine
import com.aimdi.xtanative.ui.generated.resources.reader_speech_sherpa
import com.aimdi.xtanative.ui.generated.resources.reader_speech_sherpa_description
import com.aimdi.xtanative.ui.generated.resources.reader_speech_speed
import com.aimdi.xtanative.ui.generated.resources.reader_speech_title
import com.aimdi.xtanative.ui.generated.resources.reader_speech_voice
import com.aimdi.xtanative.ui.generated.resources.reader_speech_voices_only
import com.aimdi.xtanative.ui.generated.resources.reader_voice_delete_description
import com.aimdi.xtanative.ui.generated.resources.reader_voice_delete_question
import com.aimdi.xtanative.ui.generated.resources.reader_voice_engine
import com.aimdi.xtanative.ui.generated.resources.reader_voice_engine_delete_description
import com.aimdi.xtanative.ui.generated.resources.reader_voice_engine_delete_question
import com.aimdi.xtanative.ui.generated.resources.reader_voice_engine_failed_archive
import com.aimdi.xtanative.ui.generated.resources.reader_voice_engine_note
import com.aimdi.xtanative.ui.generated.resources.reader_voice_engine_unavailable
import com.aimdi.xtanative.ui.generated.resources.reader_voice_failed_archive
import com.aimdi.xtanative.ui.generated.resources.reader_voice_failed_checksum
import com.aimdi.xtanative.ui.generated.resources.reader_voice_failed_network
import com.aimdi.xtanative.ui.generated.resources.reader_voice_installing
import com.aimdi.xtanative.ui.generated.resources.reader_voice_licence
import com.aimdi.xtanative.ui.generated.resources.reader_voice_needs_engine
import com.aimdi.xtanative.ui.generated.resources.reader_voice_progress
import com.aimdi.xtanative.ui.generated.resources.reader_voice_ready
import com.aimdi.xtanative.ui.generated.resources.reader_voices
import com.aimdi.xtanative.ui.generated.resources.reader_voices_note
import com.aimdi.xtanative.ui.generated.resources.reader_voices_synthesis_missing
import com.aimdi.xtanative.ui.generated.resources.reader_voices_use
import com.aimdi.xtanative.ui.generated.resources.reader_voices_use_description
import com.aimdi.xtanative.ui.links.LocalLinks
import com.aimdi.xtanative.ui.media.ReaderIcons
import org.jetbrains.compose.resources.stringResource

/** Settings › Accessibility › Read aloud as its own screen (TtsSettingsScreen). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceSettingsScreen(onBack: () -> Unit) {
    val vm = voiceSettingsViewModel()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.reader_speech_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.reader_back)) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        VoiceSettingsList(vm, PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp), Modifier.fillMaxSize())
    }
}

/**
 * The same settings as a sheet over a reader ("Voice settings" in its menu,
 * `openTtsSettings`): its title, the list, and OK.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceSettingsSheet(onDismiss: () -> Unit) {
    val vm = voiceSettingsViewModel()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxHeight(0.8f)) {
            Text(stringResource(Res.string.reader_speech_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            VoiceSettingsList(vm, PaddingValues(bottom = 8.dp), Modifier.weight(1f))
            Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.CenterEnd) {
                TextButton(onClick = onDismiss) { Text(stringResource(Res.string.reader_ok)) }
            }
        }
    }
}

@Composable
private fun voiceSettingsViewModel(): VoiceSettingsViewModel {
    val graph = LocalGraph.current
    val parts = rememberSpeechParts()
    return viewModel { VoiceSettingsViewModel(graph, parts) }
}

@Composable
private fun VoiceSettingsList(vm: VoiceSettingsViewModel, padding: PaddingValues, modifier: Modifier) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val parts = rememberSpeechParts()
    LazyColumn(modifier, contentPadding = padding) {
        offlineVoices(ui, vm, parts)
        if (parts.systemVoices == null) {
            item(key = "no-engine") {
                Note(stringResource(if (parts.synthesisAvailable) Res.string.reader_speech_voices_only else Res.string.reader_speech_no_engine))
            }
        } else {
            engines(ui, vm)
            voices(ui, vm)
            item(key = "speed") { SpeedRow(ui.choice.rate, vm::setRate) }
        }
    }
}

/** "Downloaded voices": the switch, the engine that runs them where it is downloaded, each voice, and what to know about them. */
private fun LazyListScope.offlineVoices(ui: VoiceSettingsUi, vm: VoiceSettingsViewModel, parts: SpeechParts) {
    val engine = parts.voices.engine
    item(key = "voices-header") { Header(stringResource(Res.string.reader_voices)) }
    item(key = "voices-switch") {
        SwitchRow(stringResource(Res.string.reader_voices_use), stringResource(Res.string.reader_voices_use_description), ui.offlineVoice, vm::setOfflineVoice)
    }
    if (!parts.synthesisAvailable) item(key = "voices-synthesis") { Note(stringResource(Res.string.reader_voices_synthesis_missing)) }
    if (!engine.bundled) {
        item(key = "voice-engine") {
            val archive = engine.engine?.archiveBytes ?: 0
            DownloadRow(
                title = stringResource(Res.string.reader_voice_engine),
                details = listOfNotNull(
                    "sherpa-onnx ${VoiceEngines.VERSION}",
                    engine.engine?.let { formatByteSize(it.archiveBytes) },
                    stringResource(Res.string.reader_voice_licence, VoiceEngines.LICENCES),
                ).joinToString(" · "),
                install = ui.engine,
                expectedBytes = archive,
                note = if (engine.available) null else stringResource(Res.string.reader_voice_engine_unavailable),
                archiveFailure = stringResource(Res.string.reader_voice_engine_failed_archive),
                deleteQuestion = stringResource(Res.string.reader_voice_engine_delete_question),
                deleteDescription = stringResource(Res.string.reader_voice_engine_delete_description),
                onDownload = if (engine.available) vm::downloadEngine else null,
                onCancel = vm::cancelEngine,
                onDelete = vm::deleteEngine,
            )
        }
    }
    items(parts.voices.catalog, key = { "voice-" + it.id }) { voice -> VoiceRow(voice, ui, vm, parts) }
    item(key = "voices-note") { Note(stringResource(Res.string.reader_voices_note)) }
    if (!engine.bundled) item(key = "engine-note") { Note(stringResource(Res.string.reader_voice_engine_note)) }
}

@Composable
private fun VoiceRow(voice: OfflineVoice, ui: VoiceSettingsUi, vm: VoiceSettingsViewModel, parts: SpeechParts) {
    val install = ui.installs[voice.id] ?: VoiceInstall.Absent
    val engine = parts.voices.engine
    DownloadRow(
        title = voice.name,
        details = listOf(VoiceRows.localeName(voice.locale), formatByteSize(voice.archiveBytes), stringResource(Res.string.reader_voice_licence, voice.licence))
            .joinToString(" · "),
        install = install,
        expectedBytes = voice.archiveBytes,
        note = if (install is VoiceInstall.Ready && !engine.usable) stringResource(Res.string.reader_voice_needs_engine) else null,
        archiveFailure = stringResource(Res.string.reader_voice_failed_archive),
        deleteQuestion = stringResource(Res.string.reader_voice_delete_question, voice.name),
        deleteDescription = stringResource(Res.string.reader_voice_delete_description),
        onDownload = if (engine.available) ({ vm.download(voice) }) else null,
        onCancel = { vm.cancel(voice) },
        onDelete = { vm.delete(voice) },
    )
}

/** The engines, in XTA's order: the system's default, Sherpa with a way to get it, then the rest. */
private fun LazyListScope.engines(ui: VoiceSettingsUi, vm: VoiceSettingsViewModel) {
    val options = ui.options
    item(key = "engine-header") { Header(stringResource(Res.string.reader_speech_engine)) }
    if (options == null) {
        item(key = "engine-loading") {
            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) }
        }
        return
    }
    val chosen = VoiceChoices.chosenEngine(ui.choice)
    VoiceChoices.engineRows(options.engines).forEach { row ->
        when (row) {
            EngineRow.SystemDefault -> item(key = "engine-default") {
                ChoiceRow(stringResource(Res.string.reader_speech_default), null, chosen == null) { vm.setEngine(null) }
            }
            is EngineRow.Sherpa -> {
                item(key = "engine-sherpa") {
                    ChoiceRow(stringResource(Res.string.reader_speech_sherpa), stringResource(Res.string.reader_speech_sherpa_description), chosen == Tts.SHERPA_ENGINE) {
                        vm.pickSherpa()
                    }
                }
                if (!row.installed) item(key = "engine-sherpa-get") { GetSherpa() }
            }
            is EngineRow.Engine -> item(key = "engine-" + row.engine.packageName) {
                val label = row.engine.label.ifEmpty { row.engine.packageName }
                ChoiceRow(label, row.engine.packageName.takeIf { it != label }, chosen == row.engine.packageName) { vm.setEngine(row.engine.packageName) }
            }
        }
    }
}

@Composable
private fun GetSherpa() {
    val links = LocalLinks.current
    Box(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp)) {
        TextButton(onClick = { links?.openOutside(Tts.SHERPA_INSTALL_URL) }) {
            Icon(ReaderIcons.Download, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(Res.string.reader_speech_get_sherpa), modifier = Modifier.padding(start = 8.dp))
        }
    }
}

/** The bound engine's voices, after its own default. */
private fun LazyListScope.voices(ui: VoiceSettingsUi, vm: VoiceSettingsViewModel) {
    val voices = VoiceChoices.sortedVoices(ui.options?.voices.orEmpty())
    if (voices.isEmpty()) return
    item(key = "voice-header") { Header(stringResource(Res.string.reader_speech_voice)) }
    item(key = "voice-default") { ChoiceRow(stringResource(Res.string.reader_speech_default), null, ui.choice.voiceName == null) { vm.setVoice(null) } }
    items(voices, key = { "voice:" + it.name + "|" + it.locale }) { voice ->
        ChoiceRow(voice.name, voice.locale, ui.choice.voiceName == voice.name) { vm.setVoice(voice) }
    }
}

/** The speed, from 0.2 to 1.0 in eight steps, written once the slider is let go. */
@Composable
private fun SpeedRow(rate: Double, onRate: (Double) -> Unit) {
    var dragged by remember(rate) { mutableFloatStateOf(rate.toFloat()) }
    Column {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(Res.string.reader_speech_speed), style = MaterialTheme.typography.labelLarge)
            Text(VoiceChoices.rateText(dragged.toDouble()), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(
            value = dragged,
            onValueChange = { dragged = it },
            onValueChangeFinished = { onRate(dragged.toDouble()) },
            valueRange = VoiceChoices.MIN_RATE.toFloat()..VoiceChoices.MAX_RATE.toFloat(),
            steps = VoiceChoices.RATE_STEPS,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

/** A voice or the voice engine: its details, where it stands, and what can be done with it now. */
@Composable
private fun DownloadRow(
    title: String,
    details: String,
    install: VoiceInstall,
    expectedBytes: Long,
    note: String?,
    archiveFailure: String,
    deleteQuestion: String,
    deleteDescription: String,
    onDownload: (() -> Unit)?,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    var confirm by remember { mutableStateOf(false) }
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(details, style = MaterialTheme.typography.bodySmall, color = secondary)
            statusText(VoiceRows.status(install, expectedBytes), archiveFailure)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = secondary) }
            note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = secondary) }
            if (VoiceRows.downloading(install)) {
                val fraction = VoiceRows.progress(install)
                val bar = Modifier.fillMaxWidth().padding(top = 6.dp, end = 12.dp)
                if (fraction != null) LinearProgressIndicator(progress = { fraction }, modifier = bar) else LinearProgressIndicator(modifier = bar)
            }
        }
        when (VoiceRows.action(install)) {
            VoiceAction.DOWNLOAD -> IconButton(onClick = { onDownload?.invoke() }, enabled = onDownload != null) {
                Icon(ReaderIcons.Download, stringResource(Res.string.reader_download))
            }
            VoiceAction.RETRY -> IconButton(onClick = { onDownload?.invoke() }, enabled = onDownload != null) {
                Icon(Icons.Filled.Refresh, stringResource(Res.string.reader_retry))
            }
            VoiceAction.CANCEL -> IconButton(onClick = onCancel) { Icon(Icons.Filled.Close, stringResource(Res.string.reader_cancel)) }
            VoiceAction.WAIT -> Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) }
            VoiceAction.DELETE -> IconButton(onClick = { confirm = true }) { Icon(Icons.Filled.Delete, stringResource(Res.string.reader_delete)) }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(deleteQuestion) },
            text = { Text(deleteDescription) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    onDelete()
                }) { Text(stringResource(Res.string.reader_delete)) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(Res.string.reader_cancel)) } },
        )
    }
}

@Composable
private fun statusText(status: VoiceStatus?, archiveFailure: String): String? = when (status) {
    null -> null
    is VoiceStatus.Progress -> stringResource(Res.string.reader_voice_progress, formatByteSize(status.received), formatByteSize(status.total))
    VoiceStatus.Installing -> stringResource(Res.string.reader_voice_installing)
    is VoiceStatus.Ready -> stringResource(Res.string.reader_voice_ready, formatByteSize(status.bytes))
    is VoiceStatus.Failed -> when (status.reason) {
        VoiceFailure.NETWORK -> stringResource(Res.string.reader_voice_failed_network)
        VoiceFailure.CHECKSUM -> stringResource(Res.string.reader_voice_failed_checksum)
        VoiceFailure.ARCHIVE -> archiveFailure
    }
}

@Composable
private fun Header(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp))
}

@Composable
private fun Note(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
    )
}

@Composable
private fun SwitchRow(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ChoiceRow(title: String, subtitle: String?, selected: Boolean, onPick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, role = Role.RadioButton, onClick = onPick).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.padding(start = 8.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
