package com.aimdi.xtanative.plugins.pixiv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.pixiv.PixivIllust
import com.aimdi.xtanative.networks.pixiv.PixivImageHostChoice
import com.aimdi.xtanative.networks.pixiv.PixivImages
import com.aimdi.xtanative.store.settings.PixivDetailLayout
import com.aimdi.xtanative.store.settings.PixivImageQuality
import com.aimdi.xtanative.store.settings.PixivPrefs
import com.aimdi.xtanative.ui.plugins.PluginScaffold
import com.aimdi.xtanative.ui.plugins.PluginText
import com.aimdi.xtanative.ui.plugins.currentLanguage
import com.aimdi.xtanative.ui.plugins.pluginPlural
import com.aimdi.xtanative.ui.plugins.pluginString

private fun qualityLabel(quality: PixivImageQuality): PluginText = when (quality) {
    PixivImageQuality.MEDIUM -> Strings.quality_medium
    PixivImageQuality.LARGE -> Strings.quality_large
    PixivImageQuality.ORIGINAL -> Strings.quality_original
}

/** The image server, the sizes read, columns per orientation, the work page's layout, swiping between works and the AI badge, as pixiv_settings_viewing.dart orders them. */
@Composable
internal fun ViewingSettings() {
    val runtime = LocalPixiv.current
    val settings = pixivSettings()
    var host by remember { mutableStateOf(false) }
    SectionTitle(pluginString(Strings.viewing_section))
    ActionRow(pluginString(Strings.image_host), { host = true }, subtitle = settings[PixivPrefs.imageHost])
    QualityRow(Strings.quality_feed, PixivQualitySlot.FEED)
    QualityRow(Strings.quality_detail, PixivQualitySlot.DETAIL)
    QualityRow(Strings.quality_reader, PixivQualitySlot.READER)
    val columns = listOf(0, 2, 3, 4).map { it to if (it == 0) pluginString(Strings.columns_auto) else "$it" }
    ChoiceRow(pluginString(Strings.columns_portrait), settings[PixivPrefs.gridColumnsPortrait], columns) { runtime.settings.set(PixivPrefs.gridColumnsPortrait, it) }
    ChoiceRow(pluginString(Strings.columns_landscape), settings[PixivPrefs.gridColumnsLandscape], columns) { runtime.settings.set(PixivPrefs.gridColumnsLandscape, it) }
    ChoiceRow(
        pluginString(Strings.detail_layout),
        settings[PixivPrefs.detailLayout],
        listOf(
            PixivDetailLayout.AUTO to pluginString(Strings.detail_layout_auto),
            PixivDetailLayout.VERTICAL to pluginString(Strings.detail_layout_vertical),
            PixivDetailLayout.SPLIT to pluginString(Strings.detail_layout_split),
        ),
    ) { runtime.settings.set(PixivPrefs.detailLayout, it) }
    SwitchRow(pluginString(Strings.swipe_works), pluginString(Strings.swipe_works_description), settings[PixivPrefs.swipeBetweenWorks]) { runtime.settings.set(PixivPrefs.swipeBetweenWorks, it) }
    SwitchRow(pluginString(Strings.ai_badge), pluginString(Strings.ai_badge_description), settings[PixivPrefs.aiBadge]) { runtime.settings.set(PixivPrefs.aiBadge, it) }
    if (host) ImageHostDialog { host = false }
}

@Composable
private fun QualityRow(title: PluginText, slot: PixivQualitySlot) {
    val runtime = LocalPixiv.current
    ChoiceRow(pluginString(title), pixivSettings()[slot.pref], slot.choices.map { it to pluginString(qualityLabel(it)) }) { runtime.settings.set(slot.pref, it) }
}

/** Pixiv's own image server, the public mirror, or one the reader names (https only). */
@Composable
private fun ImageHostDialog(onClose: () -> Unit) {
    val runtime = LocalPixiv.current
    val stored = pixivSettings()[PixivPrefs.imageHost]
    var choice by remember { mutableStateOf(PixivImages.hostChoice(stored)) }
    var custom by remember { mutableStateOf(if (choice == PixivImageHostChoice.CUSTOM) stored else "") }
    val invalid = choice == PixivImageHostChoice.CUSTOM && PixivImages.parseHost(custom) == null
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(pluginString(Strings.image_host)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(pluginString(Strings.image_host_description), style = MaterialTheme.typography.bodySmall)
                Text(pluginString(Strings.image_host_privacy), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                HostOption(PixivImageHostChoice.PIXIV, Strings.image_host_pixiv, PixivImages.HOST, choice) { choice = it }
                HostOption(PixivImageHostChoice.MIRROR, Strings.image_host_mirror, PixivImages.MIRROR_HOST, choice) { choice = it }
                HostOption(PixivImageHostChoice.CUSTOM, Strings.image_host_custom, null, choice) { choice = it }
                if (choice == PixivImageHostChoice.CUSTOM) {
                    OutlinedTextField(
                        value = custom,
                        onValueChange = { custom = it },
                        label = { Text(pluginString(Strings.image_host_address)) },
                        placeholder = { Text(pluginString(Strings.image_host_example)) },
                        isError = invalid && custom.isNotBlank(),
                        supportingText = if (invalid && custom.isNotBlank()) ({ Text(pluginString(Strings.image_host_invalid)) }) else null,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !invalid, onClick = {
                val host = when (choice) {
                    PixivImageHostChoice.PIXIV -> PixivImages.HOST
                    PixivImageHostChoice.MIRROR -> PixivImages.MIRROR_HOST
                    PixivImageHostChoice.CUSTOM -> custom.trim()
                }
                runtime.settings.set(PixivPrefs.imageHost, host)
                onClose()
            }) { Text(pluginString(Strings.save)) }
        },
        dismissButton = {
            TextButton(onClick = {
                runtime.settings.set(PixivPrefs.imageHost, PixivImages.HOST)
                onClose()
            }) { Text(pluginString(Strings.image_host_reset)) }
            TextButton(onClick = onClose) { Text(pluginString(Strings.cancel)) }
        },
    )
}

@Composable
private fun HostOption(value: PixivImageHostChoice, label: PluginText, detail: String?, choice: PixivImageHostChoice, onPick: (PixivImageHostChoice) -> Unit) {
    ListItem(
        headlineContent = { Text(pluginString(label)) },
        supportingContent = detail?.let { { Text(it) } },
        leadingContent = { RadioButton(selected = value == choice, onClick = null) },
        modifier = Modifier.selectable(selected = value == choice) { onPick(value) },
    )
}

/** The file-name template, folders per creator and for R-18, and the record of pages saved here, as pixiv_settings_downloads.dart orders them. */
@Composable
internal fun DownloadSettings() {
    val runtime = LocalPixiv.current
    val sheets = LocalPixivSheets.current
    val settings = pixivSettings()
    val language = currentLanguage()
    var naming by remember { mutableStateOf(false) }
    SectionTitle(pluginString(Strings.downloads_section))
    ActionRow(pluginString(Strings.file_name), { naming = true }, subtitle = PixivSaveNaming.of(settings).template, trailing = { Icon(Icons.Filled.Edit, null) })
    SwitchRow(pluginString(Strings.folder_per_artist), pluginString(Strings.folder_per_artist_description), settings[PixivPrefs.folderPerArtist]) { runtime.settings.set(PixivPrefs.folderPerArtist, it) }
    SwitchRow(pluginString(Strings.folder_r18), pluginString(Strings.folder_r18_description), settings[PixivPrefs.folderR18]) { runtime.settings.set(PixivPrefs.folderR18, it) }
    val saved = PixivSavedPages.of(settings).size
    val forget = { sheets.ask.ask(Strings.download_index_forget_question.get(language), Strings.download_index_forget.get(language)) { runtime.settings.update(PixivSavedPages::cleared) } }
    ActionRow(pluginString(Strings.download_index), null, subtitle = pluginString(Strings.download_index_description) + "\n" + savedCount(saved), trailing = {
        TextButton(onClick = forget, enabled = saved > 0) { Text(pluginString(Strings.download_index_forget)) }
    })
    if (naming) FileNameDialog { naming = false }
}

@Composable
private fun savedCount(count: Int): String = if (count == 0) pluginString(Strings.download_index_count_none) else pluginPlural(Strings.download_index_count, count, count)

/** The file-name template with its tokens to insert, a preview of a sample work's first page, and `{part}` required. */
@Composable
private fun FileNameDialog(onClose: () -> Unit) {
    val runtime = LocalPixiv.current
    val language = currentLanguage()
    val stored = PixivSaveNaming.of(pixivSettings()).template
    var value by remember { mutableStateOf(TextFieldValue(stored, TextRange(stored.length))) }
    val hasPart = PixivSaveNaming.hasPart(value.text)
    val sample = remember(language) { PixivIllust(
            id = 12345678,
            title = Strings.file_name_token_title.get(language),
            caption = "",
            type = "illust",
            thumbnailUrl = "",
            pageCount = 1,
            userId = 1234,
            userName = Strings.file_name_token_user_name.get(language),
            userAccount = "",
        ) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(pluginString(Strings.file_name)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = true,
                    isError = !hasPart,
                    supportingText = { Text(if (hasPart) pluginString(Strings.file_name_help) else pluginString(Strings.file_name_part_required, PixivNameToken.PART.token)) },
                    trailingIcon = { IconButton(onClick = { value = TextFieldValue(PixivSaveNaming.DEFAULT_TEMPLATE, TextRange(PixivSaveNaming.DEFAULT_TEMPLATE.length)) }) { Icon(Icons.Filled.Refresh, pluginString(Strings.file_name_reset)) } },
                    modifier = Modifier.fillMaxWidth(),
                )
                TokenChips(PixivNameToken.entries.map { it.token to tokenLabel(it) }) { token -> value = inserted(value, token) }
                Text(pluginString(Strings.file_name_preview, PixivSaveNaming.fileName(value.text, sample, 0, ".png")), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = {
            TextButton(enabled = hasPart, onClick = {
                runtime.settings.set(PixivPrefs.fileNameTemplate, value.text)
                onClose()
            }) { Text(pluginString(Strings.save)) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text(pluginString(Strings.cancel)) } },
    )
}

@Composable
private fun tokenLabel(token: PixivNameToken): String = pluginString(
    when (token) {
        PixivNameToken.ILLUST_ID -> Strings.file_name_token_illust_id
        PixivNameToken.PART -> Strings.file_name_token_part
        PixivNameToken.TITLE -> Strings.file_name_token_title
        PixivNameToken.USER_ID -> Strings.file_name_token_user_id
        PixivNameToken.USER_NAME -> Strings.file_name_token_user_name
    },
)

/** [token] put where the cursor is, replacing what is selected. */
internal fun inserted(value: TextFieldValue, token: String): TextFieldValue {
    val start = value.selection.min.coerceIn(0, value.text.length)
    val end = value.selection.max.coerceIn(start, value.text.length)
    return TextFieldValue(value.text.replaceRange(start, end, token), TextRange(start + token.length))
}

@Composable
private fun TokenChips(tokens: List<Pair<String, String>>, onInsert: (String) -> Unit) {
    FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        tokens.forEach { (token, label) -> AssistChip(onClick = { onInsert(token) }, label = { Text(label) }) }
    }
}

/** The template a work's Copy info fills: its placeholders and two links to insert, a reset, and what it gives for a sample. */
@Composable
internal fun PixivCopyTemplateScreen() {
    val runtime = LocalPixiv.current
    val language = currentLanguage()
    val fallback = defaultCopyTemplate(language)
    val stored = PixivCopyInfo.template(pixivSettings(), fallback)
    var value by remember { mutableStateOf(TextFieldValue(stored, TextRange(stored.length))) }
    val keep = { next: TextFieldValue ->
        value = next
        runtime.settings.set(PixivPrefs.copyTemplate, next.text.takeIf { it != fallback }.orEmpty())
    }
    PluginScaffold(
        title = pluginString(Strings.copy_template),
        onBack = runtime.host::back,
        actions = { IconButton(onClick = { keep(TextFieldValue(fallback, TextRange(fallback.length))) }) { Icon(Icons.Filled.Refresh, pluginString(Strings.copy_template_reset)) } },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            OutlinedTextField(value = value, onValueChange = keep, minLines = 4, modifier = Modifier.fillMaxWidth())
            Text(pluginString(Strings.copy_template_help), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            Text(pluginString(Strings.copy_template_insert), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp))
            val links = listOf(PixivCopyInfo.ARTWORK_URL to pluginString(Strings.copy_template_artwork_url), PixivCopyInfo.USER_URL to pluginString(Strings.copy_template_user_url))
            TokenChips(PixivCopyInfo.PLACEHOLDERS.map { it to it } + links) { token -> keep(inserted(value, token)) }
        }
    }
}
