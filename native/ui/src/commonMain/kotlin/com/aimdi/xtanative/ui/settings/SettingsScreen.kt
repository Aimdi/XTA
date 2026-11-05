package com.aimdi.xtanative.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.AddCircle
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Create
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aimdi.xtanative.store.settings.AccessibilityPrefs
import com.aimdi.xtanative.store.settings.GeneralPrefs
import com.aimdi.xtanative.store.settings.MediaPrefs
import com.aimdi.xtanative.store.settings.PostsPrefs
import com.aimdi.xtanative.store.settings.ThemePrefs
import com.aimdi.xtanative.ui.common.LocalGraph
import com.aimdi.xtanative.ui.common.rememberCopyText
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.*
import com.aimdi.xtanative.ui.intro.IntroFlow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Where the settings home sends the reader, by route (Navigation.kt). */
object SettingsRoutes {
    const val PLUGINS = "plugins"
    const val BACKUP = "backup"
    const val IMPORT = "import"

    /** Adding an X account: X's sign-in (ui.signin). */
    const val SIGN_IN = "signin"
    const val DOWNLOADS = "downloads"
    const val OFFLINE = "offline"

    /** Settings › App info › Licences, and one library's licences there. */
    const val LICENCES = "licences"

    fun licence(module: String): String = "licences/library?module=" + java.net.URLEncoder.encode(module, "UTF-8")

    /** Read aloud's own screen (ui.speech), which Settings lists among its sections. */
    const val SPEECH = "speech/settings"

    fun section(id: String, focus: String? = null): String = "settings/$id" + (focus?.let { "?focus=$it" } ?: "")

    /** Where [section]'s row leads: its page here, or Read aloud's own screen. */
    fun of(section: SettingsSection): String = if (section == SettingsSection.SPEECH) SPEECH else section(section.id)
}

private const val REPOSITORY = "https://github.com/Aimdi/XTA"

/**
 * XTA's settings (settings.dart): a search over every control, then its
 * sections in groups, Advanced folded away, and the app's own information.
 */
@Composable
fun SettingsScreen(onBack: () -> Unit, onOpen: (String) -> Unit, onIntroAgain: () -> Unit) {
    val graph = LocalGraph.current
    val vm: SettingsViewModel = viewModel { SettingsViewModel(graph) }
    var query by rememberSaveable { mutableStateOf("") }
    val snackbar = remember { SnackbarHostState() }

    SettingsPage(stringResource(Res.string.settings_title), onBack, snackbar) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(Res.string.settings_search)) },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Close, null) }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
            )
            if (query.isBlank()) {
                SettingsHome(onOpen, onIntroAgain, snackbar, vm)
            } else {
                SearchResults(query, onOpen)
            }
        }
    }
}

@Composable
private fun SettingsHome(onOpen: (String) -> Unit, onIntroAgain: () -> Unit, snackbar: SnackbarHostState, vm: SettingsViewModel) {
    var advanced by rememberSaveable { mutableStateOf(false) }
    SettingsHeader(stringResource(Res.string.settings_general))
    SettingsSection.everyday.forEach { SectionRow(it, onOpen) }
    SettingsHeader(stringResource(Res.string.settings_account))
    SectionRow(SettingsSection.ACCOUNT, onOpen)
    NavigationRow(
        stringResource(Res.string.settings_plugin_store),
        stringResource(Res.string.settings_plugin_store_description),
        Icons.Outlined.AddCircle,
    ) { onOpen(SettingsRoutes.PLUGINS) }
    SettingsHeader(stringResource(Res.string.settings_data))
    SectionRow(SettingsSection.DATA, onOpen)
    Row(
        Modifier.fillMaxWidth().clickable { advanced = !advanced }.padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Build, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Text(stringResource(Res.string.settings_advanced), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Icon(if (advanced) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown, null)
    }
    if (advanced) SettingsSection.advanced.forEach { SectionRow(it, onOpen) }
    SettingsHeader(stringResource(Res.string.settings_app_info))
    AboutRows(onOpen, onIntroAgain, snackbar, vm)
}

@Composable
private fun SectionRow(section: SettingsSection, onOpen: (String) -> Unit) {
    NavigationRow(stringResource(sectionTitle(section)), stringResource(sectionHint(section)), sectionIcon(section)) {
        onOpen(SettingsRoutes.of(section))
    }
}

private fun sectionIcon(section: SettingsSection): ImageVector = when (section) {
    SettingsSection.GENERAL -> Icons.Outlined.Settings
    SettingsSection.POSTS -> Icons.AutoMirrored.Outlined.List
    SettingsSection.MEDIA -> Icons.Outlined.PlayArrow
    SettingsSection.HOME -> Icons.Outlined.Home
    SettingsSection.THEME -> Icons.Outlined.Star
    SettingsSection.ACCESSIBILITY -> Icons.Outlined.Face
    SettingsSection.SPEECH -> Icons.Outlined.Notifications
    SettingsSection.ACCOUNT -> Icons.Outlined.AccountCircle
    SettingsSection.DATA -> Icons.AutoMirrored.Outlined.ExitToApp
    SettingsSection.SYNC -> Icons.Outlined.Refresh
    SettingsSection.AI -> Icons.Outlined.Create
    SettingsSection.DIAGNOSTICS -> Icons.Outlined.CheckCircle
}

/** Settings › App info (_about.dart): the version, where XTA lives, its licences, and the first-launch cards again. */
@Composable
private fun AboutRows(onOpen: (String) -> Unit, onIntroAgain: () -> Unit, snackbar: SnackbarHostState, vm: SettingsViewModel) {
    val copyText = rememberCopyText()
    val links = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val version = vm.graph.version.ifEmpty { "dev" }
    val copied = stringResource(Res.string.settings_version_copied)
    NavigationRow(stringResource(Res.string.settings_version_label), version, Icons.Outlined.Info, chevron = false) {
        copyText(version)
        scope.launch { snackbar.showSnackbar(copied) }
    }
    NavigationRow(stringResource(Res.string.settings_contribute), stringResource(Res.string.settings_contribute_description), Icons.Outlined.Favorite, chevron = false) {
        runCatching { links.openUri(REPOSITORY) }
    }
    NavigationRow(stringResource(Res.string.settings_report_bug), stringResource(Res.string.settings_report_bug_description), Icons.Outlined.Warning, chevron = false) {
        runCatching { links.openUri("$REPOSITORY/issues") }
    }
    NavigationRow(stringResource(Res.string.settings_licenses), stringResource(Res.string.settings_licenses_description), Icons.AutoMirrored.Outlined.List) {
        onOpen(SettingsRoutes.LICENCES)
    }
    NavigationRow(stringResource(Res.string.settings_intro_again), stringResource(Res.string.settings_intro_again_description), Icons.Outlined.Refresh, chevron = false) {
        vm.graph.keep {
            vm.graph.settings.update(IntroFlow::again)
        }
        onIntroAgain()
    }
}

@Composable
private fun SearchResults(query: String, onOpen: (String) -> Unit) {
    val controls = SettingsSearch.controls(searchableControls(), query)
    controls.forEach { control ->
        val section = stringResource(sectionTitle(control.section))
        NavigationRow(
            control.title,
            if (control.description.isEmpty()) section else "$section · ${control.description}",
            Icons.Outlined.Settings,
        ) { onOpen(SettingsRoutes.section(control.section.id, control.key)) }
    }
    val entries = (SettingsSection.everyday + SettingsSection.ACCOUNT + SettingsSection.DATA + SettingsSection.advanced).map { section ->
        SettingsEntry(SettingsRoutes.of(section), stringResource(sectionTitle(section)), stringResource(sectionHint(section)))
    } + listOf(
        SettingsEntry(SettingsRoutes.PLUGINS, stringResource(Res.string.settings_plugin_store), stringResource(Res.string.settings_plugin_store_description)),
        SettingsEntry(SettingsRoutes.DOWNLOADS, stringResource(Res.string.settings_downloads), stringResource(Res.string.settings_media)),
        SettingsEntry(SettingsRoutes.OFFLINE, stringResource(Res.string.settings_offline), stringResource(Res.string.settings_saved)),
    )
    val found = SettingsSearch.entries(entries, query)
    found.forEach { entry -> NavigationRow(entry.title, entry.description, Icons.Outlined.Done) { onOpen(entry.target) } }
    if (controls.isEmpty() && found.isEmpty()) {
        Text(
            stringResource(Res.string.settings_no_results),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(24.dp),
        )
    }
}

/** One row of XTA's search index (settings_search_index.dart). */
private class Indexed(val section: SettingsSection, val key: String, val title: StringResource, val description: StringResource?)

private val INDEX = listOf(
    Indexed(SettingsSection.GENERAL, GeneralPrefs.checkForUpdates.key, Res.string.settings_update_check, Res.string.settings_update_check_description),
    Indexed(SettingsSection.GENERAL, GeneralPrefs.confirmClose.key, Res.string.settings_confirm_close, Res.string.settings_confirm_close_description),
    Indexed(SettingsSection.GENERAL, GeneralPrefs.cleanLinks.key, Res.string.settings_clean_links, Res.string.settings_clean_links_description),
    Indexed(SettingsSection.GENERAL, GeneralPrefs.disableScreenshots.key, Res.string.settings_disable_screenshots, Res.string.settings_disable_screenshots_hint),
    Indexed(SettingsSection.GENERAL, GeneralPrefs.initialTab.key, Res.string.settings_default_tab, Res.string.settings_default_tab_description),
    Indexed(SettingsSection.GENERAL, GeneralPrefs.defaultFeedTab.key, Res.string.settings_default_feed_tab, Res.string.settings_default_feed_tab_description),
    Indexed(SettingsSection.GENERAL, GeneralPrefs.defaultProfileTab.key, Res.string.settings_default_profile_tab, Res.string.settings_default_profile_tab_description),
    Indexed(SettingsSection.GENERAL, GeneralPrefs.locale.key, Res.string.settings_language, Res.string.settings_language_subtitle),
    Indexed(SettingsSection.POSTS, PostsPrefs.absoluteTimestamps.key, Res.string.settings_absolute_timestamp, Res.string.settings_absolute_timestamp_description),
    Indexed(SettingsSection.POSTS, PostsPrefs.hideSensitive.key, Res.string.settings_hide_sensitive, Res.string.settings_hide_sensitive_description),
    Indexed(SettingsSection.POSTS, PostsPrefs.alwaysShowSensitiveMedia.key, Res.string.settings_always_show_sensitive, null),
    Indexed(SettingsSection.POSTS, PostsPrefs.fullPostContents.key, Res.string.settings_full_contents, Res.string.settings_full_contents_description),
    Indexed(SettingsSection.POSTS, PostsPrefs.nonConfirmationBias.key, Res.string.settings_non_confirmation, Res.string.settings_non_confirmation_description),
    Indexed(SettingsSection.POSTS, PostsPrefs.hideUnrelatedPostWarnings.key, Res.string.settings_unrelated_warnings, Res.string.settings_unrelated_warnings_description),
    Indexed(SettingsSection.POSTS, PostsPrefs.subscribeBadge.key, Res.string.settings_subscribe_badge, Res.string.settings_subscribe_badge_description),
    Indexed(SettingsSection.POSTS, PostsPrefs.includeReplies.key, Res.string.settings_include_replies, Res.string.settings_feed_default_description),
    Indexed(SettingsSection.POSTS, PostsPrefs.includeReposts.key, Res.string.settings_include_reposts, Res.string.settings_feed_default_description),
    Indexed(SettingsSection.POSTS, PostsPrefs.threadedReplies.key, Res.string.settings_threaded_replies, Res.string.settings_threaded_replies_description),
    Indexed(SettingsSection.POSTS, PostsPrefs.collapseReposts.key, Res.string.settings_collapse_boosts, Res.string.settings_collapse_boosts_description),
    Indexed(SettingsSection.POSTS, PostsPrefs.zenMode.key, Res.string.settings_zen_mode, Res.string.settings_zen_mode_description),
    Indexed(SettingsSection.POSTS, PostsPrefs.calmMode.key, Res.string.settings_calm_mode, Res.string.settings_calm_mode_description),
    Indexed(SettingsSection.POSTS, PostsPrefs.zenPageCap.key, Res.string.settings_zen_page_cap, Res.string.settings_zen_page_cap_description),
    Indexed(SettingsSection.POSTS, PostsPrefs.rememberReadingPosition.key, Res.string.settings_reading_position, Res.string.settings_reading_position_description),
    Indexed(SettingsSection.MEDIA, MediaPrefs.loadManually.key, Res.string.settings_load_manually, Res.string.settings_load_manually_description),
    Indexed(SettingsSection.MEDIA, MediaPrefs.imageQuality.key, Res.string.settings_image_quality, Res.string.settings_save_bandwidth),
    Indexed(SettingsSection.MEDIA, MediaPrefs.gridColumns.key, Res.string.settings_grid_columns, Res.string.settings_grid_columns_description),
    Indexed(SettingsSection.MEDIA, MediaPrefs.gridLayout.key, Res.string.settings_media_layout, null),
    Indexed(SettingsSection.MEDIA, MediaPrefs.videoQuality.key, Res.string.settings_video_quality, Res.string.settings_video_quality_description),
    Indexed(SettingsSection.MEDIA, MediaPrefs.mute.key, Res.string.settings_mute_videos, Res.string.settings_mute_videos_description),
    Indexed(SettingsSection.MEDIA, MediaPrefs.loop.key, Res.string.settings_loop_videos, Res.string.settings_loop_videos_description),
    Indexed(SettingsSection.MEDIA, MediaPrefs.autoplay.key, Res.string.settings_autoplay_videos, Res.string.settings_autoplay_videos_description),
    Indexed(SettingsSection.MEDIA, MediaPrefs.prefetchSeconds.key, Res.string.settings_video_prefetch, Res.string.settings_video_prefetch_description),
    Indexed(SettingsSection.MEDIA, MediaPrefs.directHardwareDecoding.key, Res.string.settings_hardware_decoding, Res.string.settings_hardware_decoding_description),
    Indexed(SettingsSection.MEDIA, MediaPrefs.backgroundPlayback.key, Res.string.settings_background_play, Res.string.settings_background_play_description),
    Indexed(SettingsSection.MEDIA, MediaPrefs.backgroundPlaybackWithOtherApps.key, Res.string.settings_background_play_other_apps, Res.string.settings_background_play_other_apps_description),
    Indexed(SettingsSection.MEDIA, MediaPrefs.downloadType.key, Res.string.settings_download_handling, Res.string.settings_download_handling_description),
    Indexed(SettingsSection.MEDIA, MediaPrefs.downloadPath.key, Res.string.settings_download_path, null),
    Indexed(SettingsSection.MEDIA, MediaPrefs.downloadConcurrency.key, Res.string.settings_download_concurrency, Res.string.settings_download_concurrency_description),
    Indexed(SettingsSection.THEME, ThemePrefs.background.key, Res.string.settings_background, Res.string.settings_background_description),
    Indexed(SettingsSection.THEME, ThemePrefs.accent.key, Res.string.settings_accent, Res.string.settings_accent_description),
    Indexed(SettingsSection.THEME, ThemePrefs.trueBlack.key, Res.string.settings_true_black, Res.string.settings_true_black_description),
    Indexed(SettingsSection.THEME, ThemePrefs.trueBlackPostCards.key, Res.string.settings_true_black_cards, Res.string.settings_true_black_cards_description),
    Indexed(SettingsSection.THEME, ThemePrefs.showNavigationLabels.key, Res.string.settings_navigation_labels, null),
    Indexed(SettingsSection.THEME, ThemePrefs.hideNavigationOnScroll.key, Res.string.settings_hide_navigation, null),
    Indexed(SettingsSection.ACCESSIBILITY, AccessibilityPrefs.textScale.key, Res.string.settings_text_scale, Res.string.settings_text_scale_description),
    Indexed(SettingsSection.ACCESSIBILITY, AccessibilityPrefs.disableAnimations.key, Res.string.settings_disable_animations, Res.string.settings_disable_animations_description),
    Indexed(SettingsSection.ACCESSIBILITY, AccessibilityPrefs.haptics.key, Res.string.settings_haptics, Res.string.settings_haptics_description),
    Indexed(SettingsSection.ACCESSIBILITY, AccessibilityPrefs.tickerChart.key, Res.string.settings_ticker_chart, Res.string.settings_ticker_chart_description),
    Indexed(SettingsSection.ACCESSIBILITY, AccessibilityPrefs.doubleTapLike.key, Res.string.settings_double_tap_like, Res.string.settings_double_tap_like_description),
    Indexed(SettingsSection.ACCESSIBILITY, AccessibilityPrefs.swipeBackAnywhere.key, Res.string.settings_swipe_back_anywhere, Res.string.settings_swipe_back_anywhere_description),
    Indexed(SettingsSection.ACCESSIBILITY, AccessibilityPrefs.swipeBackTilt.key, Res.string.settings_swipe_back_tilt, Res.string.settings_swipe_back_tilt_description),
)

/** Every control the search can find, in this platform's settings: the desktop has no screenshot switch nor haptics. */
@Composable
private fun searchableControls(): List<SettingsControl> = INDEX
    .filter { !isDesktop || (it.key != GeneralPrefs.disableScreenshots.key && it.key != AccessibilityPrefs.haptics.key) }
    .map { SettingsControl(it.section, it.key, stringResource(it.title), it.description?.let { res -> stringResource(res) }.orEmpty()) }
