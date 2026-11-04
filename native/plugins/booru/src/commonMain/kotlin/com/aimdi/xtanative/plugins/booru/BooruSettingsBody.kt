package com.aimdi.xtanative.plugins.booru

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.booru.BooruEngine
import com.aimdi.xtanative.networks.booru.BooruHosts
import com.aimdi.xtanative.networks.booru.BooruPreset
import com.aimdi.xtanative.networks.booru.BooruPresets
import com.aimdi.xtanative.networks.booru.BooruQuery
import com.aimdi.xtanative.networks.booru.BooruRating
import com.aimdi.xtanative.networks.booru.BooruRatingRules
import com.aimdi.xtanative.store.settings.BooruPrefs
import com.aimdi.xtanative.store.settings.Settings
import com.aimdi.xtanative.ui.plugins.HomeTimelineSwitch
import com.aimdi.xtanative.ui.plugins.PluginFrame
import com.aimdi.xtanative.ui.plugins.pluginString

/** What the settings screen is asking the reader. */
private sealed interface SettingsAsk {
    data object Engine : SettingsAsk

    data object Rating : SettingsAsk

    data object Columns : SettingsAsk

    data object AddSite : SettingsAsk

    /** Adult posts, before [cap] lets them in; [site] names a mostly explicit site just chosen. */
    data class Adult(val site: String?, val cap: BooruRating) : SettingsAsk
}

/**
 * Booru's settings (booru_settings.dart), in XTA's order: the site, built in
 * or added, its engine and host; the login and key it alone is sent; the
 * content (each site keeps its own rating cap and blacklist) and whether the
 * followed tags join Home's timelines; the grid and viewer; the prompt's
 * kinds; the download name; the muted tags; a test.
 */
@Composable
internal fun BooruSettingsBody(app: BooruApp, frame: PluginFrame) = Bound(app) { settings ->
    var ask by remember { mutableStateOf<SettingsAsk?>(null) }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = frame.padding) {
            siteSection(app, settings) { ask = it }
            credentialsSection(app, settings)
            contentSection(app, settings) { ask = it }
            displaySection(app, settings) { ask = it }
            item(key = "prompt") { PromptSection(app, settings) }
            item(key = "download") { DownloadSection(app, settings) }
            muteSection(app, settings)
            item(key = "test") { TestConnection(app) }
        }
        BooruMessages(app, Modifier.align(Alignment.BottomCenter).padding(frame.padding))
    }
    ask?.let { SettingsDialog(app, settings, it) { next -> ask = next } }
}

@Composable
private fun SettingsDialog(app: BooruApp, settings: Settings, ask: SettingsAsk, onNext: (SettingsAsk?) -> Unit) {
    when (ask) {
        SettingsAsk.Engine -> ChoiceDialog(pluginString(Strings.engine), BooruEngine.entries.map { it to engineLabel(it) }, BooruConfig.engine(settings), onDismiss = { onNext(null) }) { engine ->
            onNext(null)
            app.update { it.with(BooruPrefs.engine, BooruConfig.stored(engine)).with(BooruPrefs.preset, BooruSites.CUSTOM) }
        }
        SettingsAsk.Rating -> ChoiceDialog(pluginString(Strings.max_rating), BooruRating.entries.map { it to ratingLabel(it) }, BooruConfig.maxRating(settings), onDismiss = { onNext(null) }) { cap ->
            if (BooruRatingRules.needsAdultConsent(BooruConfig.maxRating(settings), cap)) {
                onNext(SettingsAsk.Adult(null, cap))
            } else {
                onNext(null)
                app.update { BooruSites.withCap(it, cap) }
            }
        }
        SettingsAsk.Columns -> ChoiceDialog(pluginString(Strings.grid_columns), BooruDisplay.COLUMN_CHOICES.map { it to columnsLabel(it) }, settings[BooruPrefs.gridColumns], onDismiss = { onNext(null) }) { columns ->
            onNext(null)
            app.update { it.with(BooruPrefs.gridColumns, columns) }
        }
        SettingsAsk.AddSite -> AddSiteDialog(BooruConfig.host(settings), onDismiss = { onNext(null) }) { site ->
            onNext(chooseAdded(app, site))
        }
        is SettingsAsk.Adult -> AdultConsentDialog(ask.site) { adult ->
            onNext(null)
            if (adult) app.update { BooruSites.withCap(it, ask.cap) }
        }
    }
}

@Composable
internal fun columnsLabel(columns: Int): String = if (columns > 0) "$columns" else pluginString(Strings.grid_columns_auto)

/** [preset] in use, with what it kept brought back; a mostly explicit site seen for the first time asks for adult posts. */
private fun choose(app: BooruApp, preset: BooruPreset): SettingsAsk? {
    val cap = BooruSites.choose(app.current(), preset).second
    app.update { BooruSites.choose(it, preset).first }
    return (cap as? SiteCap.AskAdult)?.let { SettingsAsk.Adult(it.site, BooruRating.EXPLICIT) }
}

/** An added site in use: a built-in one on its host takes its place, else it joins the added ones. */
private fun chooseAdded(app: BooruApp, site: BooruPreset): SettingsAsk? {
    BooruSites.builtIn(site.host)?.let { return choose(app, it) }
    val cap = BooruSites.choose(BooruSites.withCustom(app.current(), site), site).second
    app.update { BooruSites.choose(BooruSites.withCustom(it, site), site).first }
    return (cap as? SiteCap.AskAdult)?.let { SettingsAsk.Adult(it.site, BooruRating.EXPLICIT) }
}

@OptIn(ExperimentalLayoutApi::class)
private fun LazyListScope.siteSection(app: BooruApp, settings: Settings, onAsk: (SettingsAsk) -> Unit) {
    item(key = "site-title") { SectionTitle(pluginString(Strings.section_host)) }
    item(key = "sites") {
        val selected = settings[BooruPrefs.preset]
        FlowRow(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BooruPresets.ALL.forEach { preset ->
                FilterChip(selected = selected == preset.id, onClick = { choose(app, preset)?.let(onAsk) }, label = { Text(preset.name) })
            }
            BooruSites.custom(settings).forEach { site -> CustomSiteChip(app, site, selected == site.id, onAsk) }
            AssistChip(onClick = { onAsk(SettingsAsk.AddSite) }, label = { Text(pluginString(Strings.add_site)) }, leadingIcon = { Icon(Icons.Filled.Add, null, Modifier.size(18.dp)) })
        }
    }
    item(key = "engine") { ValueRow(pluginString(Strings.engine), engineLabel(BooruConfig.engine(settings))) { onAsk(SettingsAsk.Engine) } }
    item(key = "host") { HostField(app, settings) }
}

@Composable
private fun CustomSiteChip(app: BooruApp, site: BooruPreset, selected: Boolean, onAsk: (SettingsAsk) -> Unit) {
    InputChip(
        selected = selected,
        onClick = { choose(app, site)?.let(onAsk) },
        label = { Text(site.name) },
        trailingIcon = {
            IconButton(onClick = { app.update { BooruSites.withoutCustom(it, site) } }, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Filled.Close, pluginString(Strings.remove_site), Modifier.size(18.dp))
            }
        },
    )
}

/** The host typed: kept as XTA keeps one, the preset set aside, and the engine guessed from its name. */
@Composable
private fun HostField(app: BooruApp, settings: Settings) {
    val stored = settings[BooruPrefs.host]
    var text by remember { mutableStateOf(stored) }
    // A site chosen above puts its host here.
    LaunchedEffect(settings[BooruPrefs.preset]) { if (settings[BooruPrefs.preset] != BooruSites.CUSTOM) text = stored }
    OutlinedTextField(
        value = text,
        onValueChange = { value ->
            text = value
            app.update { current ->
                val host = BooruHosts.normalise(value)
                val typed = current.with(BooruPrefs.host, host).with(BooruPrefs.preset, BooruSites.CUSTOM)
                BooruHosts.guessEngine(host)?.let { typed.with(BooruPrefs.engine, BooruConfig.stored(it)) } ?: typed
            }
        },
        label = { Text(pluginString(Strings.host)) },
        placeholder = { Text(pluginString(Strings.host_hint)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

/** The login and key, shown only on the host they belong to; a pair saved for another site is said to be there. */
private fun LazyListScope.credentialsSection(app: BooruApp, settings: Settings) {
    item(key = "credentials-title") { SectionTitle(pluginString(Strings.section_credentials)) }
    BooruConfig.credentialsElsewhere(settings)?.let { owner ->
        item(key = "credentials-elsewhere") { Note(pluginString(Strings.credentials_other_site, BooruHosts.displayName(owner))) }
    }
    item(key = "credentials") { CredentialFields(app, settings) }
}

@Composable
private fun CredentialFields(app: BooruApp, settings: Settings) {
    val host = BooruConfig.host(settings)
    val engine = BooruConfig.engine(settings)
    var login by remember(host) { mutableStateOf(BooruConfig.credential(settings, BooruPrefs.login)) }
    var key by remember(host) { mutableStateOf(BooruConfig.credential(settings, BooruPrefs.apiKey)) }
    OutlinedTextField(
        value = login,
        onValueChange = { value -> login = value; app.update { BooruConfig.withCredential(it, BooruPrefs.login, value) } },
        label = { Text(pluginString(Strings.login)) },
        supportingText = { Text(loginHelp(engine)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    )
    OutlinedTextField(
        value = key,
        onValueChange = { value -> key = value; app.update { BooruConfig.withCredential(it, BooruPrefs.apiKey, value) } },
        label = { Text(pluginString(if (engine == BooruEngine.MOEBOORU) Strings.password_hash else Strings.api_key)) },
        supportingText = { Text(keyHelp(engine)) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@Composable
private fun loginHelp(engine: BooruEngine): String = pluginString(
    when (engine) {
        BooruEngine.GELBOORU_V2 -> Strings.login_help_gelbooru
        BooruEngine.MOEBOORU -> Strings.login_help_moebooru
        else -> Strings.login_help
    },
)

@Composable
private fun keyHelp(engine: BooruEngine): String = pluginString(
    when (engine) {
        BooruEngine.MOEBOORU -> Strings.password_hash_help
        BooruEngine.GELBOORU_V2 -> Strings.api_key_help_gelbooru
        else -> Strings.api_key_help
    },
)

private fun LazyListScope.contentSection(app: BooruApp, settings: Settings, onAsk: (SettingsAsk) -> Unit) {
    item(key = "content-title") { SectionTitle(pluginString(Strings.section_content)) }
    item(key = "per-site") { Note(pluginString(Strings.per_site_note)) }
    item(key = "rating") { ValueRow(pluginString(Strings.max_rating), ratingLabel(BooruConfig.maxRating(settings))) { onAsk(SettingsAsk.Rating) } }
    item(key = "home") { HomeTimelineSwitch(app.host.settings, pluginString(Strings.plugin_name)) }
}

/** Grid and viewer choices (booru_display_settings.dart). */
private fun LazyListScope.displaySection(app: BooruApp, settings: Settings, onAsk: (SettingsAsk) -> Unit) {
    item(key = "display-title") { SectionTitle(pluginString(Strings.section_display)) }
    item(key = "columns") { ValueRow(pluginString(Strings.grid_columns), columnsLabel(settings[BooruPrefs.gridColumns])) { onAsk(SettingsAsk.Columns) } }
    listOf(
        Triple(BooruPrefs.smallThumbnails, Strings.small_thumbnails, Strings.small_thumbnails_description),
        Triple(BooruPrefs.tileDetails, Strings.tile_details, Strings.tile_details_description),
        Triple(BooruPrefs.blurExplicit, Strings.blur_explicit, Strings.blur_explicit_description),
        Triple(BooruPrefs.originalInViewer, Strings.original_in_viewer, Strings.original_in_viewer_description),
    ).forEach { (pref, title, description) ->
        item(key = pref.key) { SwitchRow(pluginString(title), pluginString(description), settings[pref]) { on -> app.update { it.with(pref, on) } } }
    }
}

/** How downloaded files are named, with the tokens a name may use. */
@Composable
private fun DownloadSection(app: BooruApp, settings: Settings) {
    var template by remember { mutableStateOf(settings[BooruPrefs.fileNameTemplate]) }
    SectionTitle(pluginString(Strings.download))
    OutlinedTextField(
        value = template,
        onValueChange = { value -> template = value; app.update { it.with(BooruPrefs.fileNameTemplate, value.trim()) } },
        label = { Text(pluginString(Strings.file_name_template)) },
        supportingText = { Text(pluginString(Strings.file_name_template_help, BooruDownloads.TOKENS.joinToString(" "))) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

/** The blacklist of the site in use: an entry of several tags hides only the posts that have them all. */
@OptIn(ExperimentalLayoutApi::class)
private fun LazyListScope.muteSection(app: BooruApp, settings: Settings) {
    item(key = "mute-title") { SectionTitle(pluginString(Strings.mute_section_on, BooruHosts.displayName(BooruConfig.host(settings)))) }
    item(key = "mute-field") { MuteField(app) }
    val muted = BooruConfig.muted(settings).sorted()
    item(key = "muted") {
        if (muted.isEmpty()) {
            Note(pluginString(Strings.mute_empty))
        } else {
            FlowRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                muted.forEach { entry ->
                    InputChip(
                        selected = false,
                        onClick = {},
                        label = { Text(queryText(entry)) },
                        trailingIcon = {
                            IconButton(onClick = { app.update { BooruConfig.withoutMuted(it, entry) } }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Filled.Close, pluginString(Strings.delete), Modifier.size(18.dp))
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun MuteField(app: BooruApp) {
    var entry by remember { mutableStateOf("") }
    val mute = {
        if (BooruQuery.normalise(entry) != null) {
            val muted = entry
            app.update { BooruConfig.withMuted(it, muted) }
            entry = ""
        }
    }
    OutlinedTextField(
        value = entry,
        onValueChange = { entry = it },
        label = { Text(pluginString(Strings.mute_tag)) },
        supportingText = { Text(pluginString(Strings.mute_help)) },
        trailingIcon = { IconButton(onClick = mute) { Icon(Icons.Filled.Add, pluginString(Strings.mute_tag)) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { mute() }),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp))
}
