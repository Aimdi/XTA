package com.aimdi.xtanative.ui.intro

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.AddCircle
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aimdi.xtanative.store.settings.NetworkPrefs
import com.aimdi.xtanative.store.settings.Settings
import com.aimdi.xtanative.store.settings.ThemePrefs
import com.aimdi.xtanative.ui.common.BackHandling
import com.aimdi.xtanative.ui.common.LocalGraph
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.*
import com.aimdi.xtanative.ui.settings.AccentPicker
import com.aimdi.xtanative.ui.settings.BackgroundPicker
import com.aimdi.xtanative.ui.settings.SettingsRoutes
import com.aimdi.xtanative.ui.settings.networkName
import com.aimdi.xtanative.ui.settings.pluginIds
import org.jetbrains.compose.resources.stringResource

/**
 * XTA's first-launch cards (intro_screen.dart): welcome, what stays on the
 * device, the look, the X accounts (brought over from XTA's backup) and the
 * sources on Home. Next moves on, Skip leaves everything as it is, Back steps
 * to the card before. Finishing or skipping marks the cards seen.
 */
@Composable
fun IntroScreen(onFinished: () -> Unit, onImport: () -> Unit, onOpen: (String) -> Unit) {
    val graph = LocalGraph.current
    val stored by graph.settings.state.collectAsStateWithLifecycle()
    val session by graph.sessions.state.collectAsStateWithLifecycle()
    val settings = stored ?: Settings()
    var index by rememberSaveable { mutableStateOf(0) }
    val page = IntroPage.entries[index.coerceIn(IntroPage.entries.indices)]
    val change: ((Settings) -> Settings) -> Unit = { transform -> graph.keep { graph.settings.update(transform) } }
    val finish = {
        graph.keep { graph.settings.update(IntroFlow::finished) }
        onFinished()
    }
    val next = { page.next?.let { index = it.ordinal } ?: finish() }

    BackHandling(enabled = page.previous != null) { page.previous?.let { index = it.ordinal } }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.End) {
            if (page != IntroPage.SOURCES) TextButton(onClick = finish) { Text(stringResource(Res.string.intro_skip)) }
        }
        Column(
            Modifier.weight(1f).widthIn(max = 560.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (page) {
                IntroPage.WELCOME -> Card(stringResource(Res.string.intro_welcome_title), stringResource(Res.string.intro_welcome_body)) {
                    Mark(Icons.Outlined.Person)
                }
                IntroPage.DEVICE -> Card(
                    stringResource(Res.string.intro_device_title),
                    stringResource(Res.string.intro_device_body),
                    stringResource(Res.string.intro_device_footnote),
                ) { Mark(Icons.Outlined.Lock) }
                IntroPage.APPEARANCE -> Card(
                    stringResource(Res.string.intro_appearance_title),
                    stringResource(Res.string.intro_appearance_body),
                    label = stringResource(Res.string.intro_looks),
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        BackgroundPicker(settings[ThemePrefs.background]) { background -> change { it.with(ThemePrefs.background, background) } }
                        AccentPicker(settings[ThemePrefs.accent]) { accent -> change { it.with(ThemePrefs.accent, accent) } }
                    }
                }
                IntroPage.ACCOUNT -> {
                    val accounts = session?.accounts.orEmpty()
                    Card(
                        stringResource(Res.string.intro_account_title),
                        stringResource(Res.string.intro_account_body),
                        if (accounts.isEmpty()) stringResource(Res.string.intro_account_caption) else null,
                    ) {
                        if (accounts.isEmpty()) {
                            Mark(Icons.Outlined.AccountCircle)
                        } else {
                            FlowColumnOfAccounts(accounts.map { it.screenName.orEmpty() })
                        }
                    }
                }
                IntroPage.SOURCES -> Card(
                    stringResource(Res.string.intro_sources_title),
                    stringResource(Res.string.intro_sources_body),
                    label = stringResource(Res.string.intro_sources_label),
                ) {
                    SourcesGrid(settings, change, onOpen)
                }
            }
        }
        Footer(page, session?.accounts.isNullOrEmpty(), onNext = next, onImport = onImport, onFinish = finish)
    }
}

/** A card's headline, body, what it shows (named [label] for a screen reader), and its footnote. */
@Composable
private fun Card(title: String, body: String, footnote: String? = null, label: String? = null, content: @Composable () -> Unit) {
    Text(title, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold))
    Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainer)
            .let { if (label != null) it.semantics { contentDescription = label } else it }
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
    if (footnote != null) Text(footnote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Mark(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Box(Modifier.padding(24.dp).size(96.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
    }
}

/** "Signed in as @name", one chip per account the backup brought. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowColumnOfAccounts(names: List<String>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        names.forEach { name ->
            Row(
                Modifier.clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)).padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.AccountCircle, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.size(8.dp))
                Text(stringResource(Res.string.intro_signed_in_as, name), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * Following and X, always there; then each open network, on when it is on
 * and pinned on Home; then the plugin store. A chosen network that needs
 * setting up gets a button under the tiles.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SourcesGrid(settings: Settings, change: ((Settings) -> Settings) -> Unit, onOpen: (String) -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Tile(stringResource(Res.string.settings_tab_following), selected = true, fixed = true) {}
            Tile(stringResource(Res.string.settings_tab_for_you), selected = true, fixed = true) {}
            IntroSources.networks.forEach { network ->
                Tile(networkName(network.id), selected = IntroSources.selected(settings, network)) { change { IntroSources.toggled(it, network) } }
            }
            Tile(stringResource(Res.string.settings_plugin_store), selected = false, icon = Icons.Outlined.AddCircle) { onOpen(SettingsRoutes.PLUGINS) }
        }
        IntroSources.toSetUp(settings, pluginIds()).forEach { network: NetworkPrefs ->
            TextButton(onClick = { onOpen(SettingsRoutes.section(network.id)) }) {
                Icon(Icons.Outlined.Settings, null, Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(stringResource(Res.string.intro_set_up, networkName(network.id)))
            }
        }
    }
}

/** One source: chosen ones are tinted and ringed with a check; the always-on ones keep a hairline. */
@Composable
private fun Tile(title: String, selected: Boolean, fixed: Boolean = false, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier.size(width = 100.dp, height = 72.dp).clip(shape)
            .background(if (selected) colors.primaryContainer else colors.surface)
            .border(if (selected && !fixed) 2.dp else 1.dp, if (selected && !fixed) colors.primary else colors.outlineVariant, shape)
            .clickable(enabled = !fixed, onClick = onClick)
            .padding(6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (icon != null) Icon(icon, null, tint = colors.onSurfaceVariant, modifier = Modifier.size(24.dp))
            Text(title, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (selected) {
            Icon(
                Icons.Outlined.Check, null, tint = colors.onPrimary,
                modifier = Modifier.align(Alignment.TopEnd).size(16.dp).clip(CircleShape).background(colors.primary),
            )
        }
    }
}

/** The page dots and the card's buttons, pinned under the card. */
@Composable
private fun Footer(page: IntroPage, noAccount: Boolean, onNext: () -> Unit, onImport: () -> Unit, onFinish: () -> Unit) {
    Column(Modifier.fillMaxWidth().widthIn(max = 560.dp).padding(horizontal = 24.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            IntroPage.entries.forEach { dot ->
                Box(
                    Modifier.padding(horizontal = 4.dp).size(if (dot == page) 10.dp else 8.dp).clip(CircleShape)
                        .background(if (dot == page) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                )
            }
        }
        when {
            page == IntroPage.SOURCES -> Button(onClick = onFinish, modifier = Modifier.fillMaxWidth()) { Text(stringResource(Res.string.intro_start_reading)) }
            page == IntroPage.ACCOUNT && noAccount -> {
                Button(onClick = onImport, modifier = Modifier.fillMaxWidth()) { Text(stringResource(Res.string.settings_import_backup)) }
                OutlinedButton(onClick = onNext, modifier = Modifier.fillMaxWidth()) { Text(stringResource(Res.string.intro_not_now)) }
            }
            else -> Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) { Text(stringResource(Res.string.intro_next)) }
        }
    }
}
