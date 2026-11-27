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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.AddCircle
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.aimdi.xtanative.ui.common.RingedFocus
import com.aimdi.xtanative.ui.common.keyboardFocusRing
import com.aimdi.xtanative.ui.settings.isDesktop
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
import com.aimdi.xtanative.data.LocalGraph
import com.aimdi.xtanative.pluginhost.PluginStatus
import com.aimdi.xtanative.store.settings.NetworkPrefs
import com.aimdi.xtanative.store.settings.Settings
import com.aimdi.xtanative.store.settings.ThemePrefs
import com.aimdi.xtanative.ui.common.BackHandling
import com.aimdi.xtanative.ui.common.ConfirmCloseOnBack
import com.aimdi.xtanative.ui.common.LocalReducedMotion
import com.aimdi.xtanative.ui.common.TipText
import com.aimdi.xtanative.ui.common.turnTo
import com.aimdi.xtanative.ui.generated.resources.*
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.pluginstore.LocalPlugins
import com.aimdi.xtanative.ui.pluginstore.failureText
import com.aimdi.xtanative.ui.settings.AccentPicker
import com.aimdi.xtanative.ui.settings.BackgroundPicker
import com.aimdi.xtanative.ui.settings.SettingsRoutes
import com.aimdi.xtanative.ui.settings.networkName
import com.aimdi.xtanative.ui.settings.pluginIds
import com.aimdi.xtanative.ui.theme.edgeColor
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * XTA's first-launch cards (intro_screen.dart): welcome, what stays on the
 * device, the look, the X accounts and the sources on Home. A sideways swipe
 * or Next moves on, Skip leaves everything as it is, and Back steps to the
 * card before until the first, where it closes the app as Home does, asking
 * first while Confirm close is on. Finishing or skipping marks the cards seen.
 */
@Composable
fun IntroScreen(onFinished: () -> Unit, onImport: () -> Unit, onOpen: (String) -> Unit) {
    val graph = LocalGraph.current
    val stored by graph.settings.state.collectAsStateWithLifecycle()
    val session by graph.sessions.state.collectAsStateWithLifecycle()
    val settings = stored ?: Settings()
    val accounts = session?.accounts.orEmpty()
    val pager = rememberPagerState { IntroPage.entries.size }
    val scope = rememberCoroutineScope()
    val reduced = LocalReducedMotion.current
    val change: ((Settings) -> Settings) -> Unit = { transform -> graph.keep { graph.settings.update(transform) } }
    val finish = {
        graph.keep { graph.settings.update(IntroFlow::finished) }
        onFinished()
    }
    val show: (IntroPage) -> Unit = { page -> scope.launch { pager.turnTo(page.ordinal, reduced) } }
    val shown = IntroPage.entries[pager.currentPage.coerceIn(IntroPage.entries.indices)]

    BackHandling(enabled = shown.previous != null) { shown.previous?.let(show) }
    ConfirmCloseOnBack(enabled = shown.previous == null)

    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val primary = remember { FocusRequester() }
    // On the desktop the keys have a place from the start: Enter goes on, the arrows turn the cards.
    if (isDesktop) LaunchedEffect(Unit) { runCatching { primary.requestFocus() } }
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()
            .onKeyEvent { event -> event.type == KeyEventType.KeyDown && turnByKey(event, IntroPage.entries[pager.targetPage], rtl, show) },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.End) {
            if (shown != IntroPage.SOURCES) IntroTextButton(stringResource(Res.string.intro_skip), onClick = finish)
        }
        HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth()) { index ->
            val page = IntroPage.entries[index]
            IntroCard { CardOf(page, settings, accounts.map { it.screenName.orEmpty() }, change, onOpen) }
        }
        // Under the cards rather than on each, so the button the keys are on stays theirs as the cards turn.
        Footer(
            shown,
            noAccount = accounts.isEmpty(),
            primary = primary,
            onNext = { shown.next?.let(show) ?: finish() },
            onSignIn = { onOpen(SettingsRoutes.SIGN_IN) },
            onImport = onImport,
            onFinish = finish,
        )
    }
}

/** An unmodified ← or → turning to the card it points to from [from]: true when it did. */
private fun turnByKey(event: KeyEvent, from: IntroPage, rtl: Boolean, show: (IntroPage) -> Unit): Boolean {
    if (event.isCtrlPressed || event.isAltPressed || event.isShiftPressed || event.isMetaPressed) return false
    val towardsRight = when (event.key) {
        Key.DirectionRight -> true
        Key.DirectionLeft -> false
        else -> return false
    }
    IntroKeys.turned(from, towardsRight, rtl)?.let(show)
    return true
}

/** One card, as XTA's IntroPageFrame lays it out, scrolling when it must. */
@Composable
private fun IntroCard(card: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(
            Modifier.weight(1f).widthIn(max = 560.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) { card() }
    }
}

@Composable
private fun CardOf(page: IntroPage, settings: Settings, accounts: List<String>, change: ((Settings) -> Settings) -> Unit, onOpen: (String) -> Unit) {
    when (page) {
        IntroPage.WELCOME -> Card(stringResource(Res.string.intro_welcome_title), stringResource(Res.string.intro_welcome_body)) {
            OrbitIllustration()
        }
        IntroPage.DEVICE -> Card(
            stringResource(Res.string.intro_device_title),
            stringResource(Res.string.intro_device_body),
            stringResource(Res.string.intro_device_footnote),
        ) { PhoneIllustration() }
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
        IntroPage.ACCOUNT -> Card(
            stringResource(Res.string.intro_account_title),
            stringResource(Res.string.intro_account_body),
            if (accounts.isEmpty()) stringResource(Res.string.intro_account_caption) else null,
        ) {
            if (accounts.isEmpty()) {
                FlowIllustration(emptyList())
            } else {
                // The chips sit under the drawing, so the card grows to fit them.
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FlowIllustration(accounts, height = 112.dp)
                    FlowColumnOfAccounts(accounts)
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

/** A card's headline, body, what it shows (named [label] for a screen reader), and its footnote. */
@Composable
private fun Card(title: String, body: String, footnote: String? = null, label: String? = null, content: @Composable () -> Unit) {
    Text(title, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold))
    Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Box(
        Modifier.fillMaxWidth()
            .let { if (label != null) it.semantics { contentDescription = label } else it }
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
    if (footnote != null) Text(footnote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** "Signed in as @name", one chip per account the backup brought. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowColumnOfAccounts(names: List<String>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        names.forEach { name ->
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.AccountCircle, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.size(8.dp))
                TipText(stringResource(Res.string.intro_signed_in_as, name), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * Following and X, always there; then each open network, on when it is on
 * and pinned on Home; then the plugin store. A network turned on that is not
 * installed is installed from the catalogue, its tile showing the download as
 * the store's row does; one that fails comes off again, and says why under
 * the tiles. A chosen network that needs setting up gets a button there too.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SourcesGrid(settings: Settings, change: ((Settings) -> Settings) -> Unit, onOpen: (String) -> Unit) {
    val graph = LocalGraph.current
    val plugins = LocalPlugins.current
    val store by plugins.manager.state.collectAsStateWithLifecycle()
    fun toggle(network: NetworkPrefs) {
        val installs = IntroSources.installs(settings, network, installed = store[network.id]?.installed != null)
        change { IntroSources.toggled(it, network) }
        if (!installs) return
        graph.keep {
            if (plugins.manager.state.value[network.id] == null) plugins.manager.refresh()
            if (!plugins.install(network.id)) graph.settings.update { IntroSources.withdrawn(it, network) }
        }
    }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Tile(stringResource(Res.string.settings_tab_following), selected = true, fixed = true) {}
            Tile(stringResource(Res.string.settings_tab_for_you), selected = true, fixed = true) {}
            IntroSources.networks.forEach { network ->
                val status = store[network.id]?.status
                Tile(networkName(network.id), selected = IntroSources.selected(settings, network), status = status) { toggle(network) }
            }
            Tile(stringResource(Res.string.settings_plugin_store), selected = false, icon = Icons.Outlined.AddCircle) { onOpen(SettingsRoutes.PLUGINS) }
        }
        IntroSources.networks.forEach { network ->
            val status = store[network.id]?.status as? PluginStatus.Failed ?: return@forEach
            if (status.installed != null) return@forEach
            Text(
                "${networkName(network.id)}: ${failureText(status.reason)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
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

/**
 * One source: chosen ones are tinted and ringed with a check; the always-on
 * ones keep a hairline. While its plugin downloads or installs ([status]),
 * the tile shows how far it is and waits.
 */
@Composable
private fun Tile(
    title: String,
    selected: Boolean,
    fixed: Boolean = false,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    status: PluginStatus? = null,
    onClick: () -> Unit,
) {
    val busy = status is PluginStatus.Downloading || status == PluginStatus.Installing
    val shape = RoundedCornerShape(12.dp)
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier.size(width = 100.dp, height = 72.dp).clip(shape)
            .border(1.dp, if (selected && !fixed) colors.primary else edgeColor(), shape)
            .clickable(enabled = !fixed && !busy, onClick = onClick)
            .padding(6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (icon != null) Icon(icon, null, tint = colors.onSurfaceVariant, modifier = Modifier.size(24.dp))
            TipText(title, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        when (status) {
            is PluginStatus.Downloading -> LinearProgressIndicator(progress = { status.fraction }, modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth())
            PluginStatus.Installing -> LinearProgressIndicator(Modifier.align(Alignment.BottomCenter).fillMaxWidth())
            else -> Unit
        }
        if (selected) {
            Icon(
                Icons.Outlined.Check, null, tint = colors.onPrimary,
                modifier = Modifier.align(Alignment.TopEnd).size(16.dp).clip(CircleShape).background(colors.primary),
            )
        }
    }
}

/**
 * The page indicator and the card's buttons, pinned under the cards: on the
 * account card, XTA's Add account, Not now and Import backup. The first
 * button is always the same one, Next, Add account or Start reading, so the
 * keyboard's focus ([primary]) stays on it from card to card.
 */
@Composable
private fun Footer(
    page: IntroPage,
    noAccount: Boolean,
    primary: FocusRequester,
    onNext: () -> Unit,
    onSignIn: () -> Unit,
    onImport: () -> Unit,
    onFinish: () -> Unit,
) {
    val signIn = page == IntroPage.ACCOUNT && noAccount
    val (label, action) = when {
        page == IntroPage.SOURCES -> stringResource(Res.string.intro_start_reading) to onFinish
        signIn -> stringResource(Res.string.signin_add_account) to onSignIn
        else -> stringResource(Res.string.intro_next) to onNext
    }
    Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PageIndicator(page.ordinal, IntroPage.entries.size)
        RingedFocus {
            Button(onClick = action, modifier = Modifier.fillMaxWidth().focusRequester(primary).keyboardFocusRing(outset = RING_GAP)) { Text(label) }
        }
        if (signIn) {
            IntroTextButton(stringResource(Res.string.intro_not_now), Modifier.fillMaxWidth(), onNext)
            IntroTextButton(stringResource(Res.string.settings_import_backup), Modifier.fillMaxWidth(), onImport)
        }
    }
}

/** A text button whose keyboard focus is a ring round it, as the app's other buttons show it, not a pale fill. */
@Composable
private fun IntroTextButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    RingedFocus {
        TextButton(onClick = onClick, modifier = modifier.keyboardFocusRing(outset = RING_GAP)) { Text(text) }
    }
}

/** How far outside a button its keyboard ring is drawn. */
private val RING_GAP = 3.dp

