package com.aimdi.xtanative.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aimdi.xtanative.services.links.BrowserPicker
import com.aimdi.xtanative.services.links.LinkDestination
import com.aimdi.xtanative.store.settings.AccessibilityPrefs
import com.aimdi.xtanative.store.settings.CrashReportPrefs
import com.aimdi.xtanative.store.settings.DownloadType
import com.aimdi.xtanative.store.settings.GeneralPrefs
import com.aimdi.xtanative.store.settings.LanguageFilterAction
import com.aimdi.xtanative.store.settings.MediaGridLayout
import com.aimdi.xtanative.store.settings.MediaPrefs
import com.aimdi.xtanative.store.settings.MediaQuality
import com.aimdi.xtanative.store.settings.PostsPrefs
import com.aimdi.xtanative.store.settings.Pref
import com.aimdi.xtanative.store.settings.ProfileTab
import com.aimdi.xtanative.store.settings.Settings
import com.aimdi.xtanative.store.settings.ThemeAccent
import com.aimdi.xtanative.store.settings.ThemeBackground
import com.aimdi.xtanative.store.settings.ThemePrefs
import com.aimdi.xtanative.store.downloads.DownloadDestination
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.*
import com.aimdi.xtanative.ui.theme.Contrast
import com.aimdi.xtanative.ui.theme.XLookTokens
import java.util.Locale
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** A switch bound to [pref]; [focus] is the control the search opened the page for. */
@Composable
private fun PrefSwitch(settings: Settings, pref: Pref<Boolean>, title: String, description: String?, vm: SettingsViewModel, focus: String?, enabled: Boolean = true) {
    SwitchRow(title, description, settings[pref], enabled, focus == pref.key) { vm.set(pref, it) }
}

// ---- General (_general.dart, _browser_picker.dart, _crash_reports.dart) ----

@Composable
internal fun GeneralSettings(title: String, vm: SettingsViewModel, focus: String?, onBack: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    SettingsPage(title, onBack) { padding ->
        SettingsList(padding) {
            SettingsHeader(stringResource(Res.string.settings_general))
            val languages = listOf(SettingsChoices.SYSTEM_LANGUAGE) + SettingsChoices.languages.map { it.code }
            val language = settings[GeneralPrefs.locale].takeIf { it in languages } ?: SettingsChoices.SYSTEM_LANGUAGE
            ChoiceRow(
                stringResource(Res.string.settings_language),
                stringResource(Res.string.settings_language_subtitle),
                languages,
                language,
                label = { code -> if (code == SettingsChoices.SYSTEM_LANGUAGE) stringResource(Res.string.settings_system) else SettingsChoices.languageName(code) ?: code },
                focused = focus == GeneralPrefs.locale.key,
            ) { vm.set(GeneralPrefs.locale, it) }
            PrefSwitch(settings, GeneralPrefs.checkForUpdates, stringResource(Res.string.settings_update_check), stringResource(Res.string.settings_update_check_description), vm, focus)
            PrefSwitch(settings, GeneralPrefs.confirmClose, stringResource(Res.string.settings_confirm_close), stringResource(Res.string.settings_confirm_close_description), vm, focus)
            // The desktop opens every link in its default browser.
            if (!isDesktop) BrowserPickerRow(settings, vm)
            PrefSwitch(settings, GeneralPrefs.cleanLinks, stringResource(Res.string.settings_clean_links), stringResource(Res.string.settings_clean_links_description), vm, focus)
            if (!isDesktop) {
                PrefSwitch(settings, GeneralPrefs.disableScreenshots, stringResource(Res.string.settings_disable_screenshots), stringResource(Res.string.settings_disable_screenshots_hint), vm, focus)
            }

            SettingsHeader(stringResource(Res.string.settings_default_tab))
            ChoiceRow(
                stringResource(Res.string.settings_default_tab),
                stringResource(Res.string.settings_default_tab_description),
                SettingsChoices.initialTabs,
                settings[GeneralPrefs.initialTab],
                label = { homePageLabel(it) { null } },
                focused = focus == GeneralPrefs.initialTab.key,
            ) { vm.set(GeneralPrefs.initialTab, it) }
            ChoiceRow(
                stringResource(Res.string.settings_default_feed_tab),
                stringResource(Res.string.settings_default_feed_tab_description),
                SettingsChoices.feedTabs(settings),
                settings[GeneralPrefs.defaultFeedTab].let { if (it == "foryou") "x" else it },
                label = { feedTabLabel(it) },
                focused = focus == GeneralPrefs.defaultFeedTab.key,
            ) { vm.set(GeneralPrefs.defaultFeedTab, it) }
            ChoiceRow(
                stringResource(Res.string.settings_default_profile_tab),
                stringResource(Res.string.settings_default_profile_tab_description),
                ProfileTab.entries,
                settings[GeneralPrefs.defaultProfileTab],
                label = { profileTabLabel(it) },
                focused = focus == GeneralPrefs.defaultProfileTab.key,
            ) { vm.set(GeneralPrefs.defaultProfileTab, it) }
            TextRow(
                stringResource(Res.string.settings_share_base_url),
                stringResource(Res.string.settings_share_base_url_description),
                settings[GeneralPrefs.shareBaseUrl],
                hint = "https://x.com",
                url = true,
            ) { vm.set(GeneralPrefs.shareBaseUrl, it.trim()) }

            SettingsHeader(stringResource(Res.string.settings_crash_title))
            CrashReportRows(settings, vm)
        }
    }
}

/** Where links go: in the app, the system's choice, or one browser (one row, always enabled). */
@Composable
private fun BrowserPickerRow(settings: Settings, vm: SettingsViewModel) {
    val browsers = rememberInstalledBrowsers()
    val selected = LinkChoice.selected(settings, browsers)
    var open by remember { mutableStateOf(false) }
    val label: @Composable (LinkDestination) -> String = { destination ->
        when (destination) {
            LinkDestination.InApp -> stringResource(Res.string.settings_links_in_app)
            LinkDestination.SystemDefault -> stringResource(Res.string.settings_links_system)
            is LinkDestination.Browser -> BrowserPicker.label(destination.packageName, browsers)
        }
    }
    NavigationRow(stringResource(Res.string.settings_links_open_in), label(selected), chevron = false) { open = true }
    if (open) {
        ChoiceDialog(
            stringResource(Res.string.settings_links_open_in),
            BrowserPicker.options(browsers),
            selected,
            label,
            description = { if (it == LinkDestination.InApp) stringResource(Res.string.settings_links_in_app_description) else null },
            header = stringResource(Res.string.settings_links_open_in_description),
            onDismiss = { open = false },
        ) { destination ->
            open = false
            vm.update { LinkChoice.chosen(it, destination) }
        }
    }
}

/** The crash-report switch, and the reports kept. */
@Composable
private fun CrashReportRows(settings: Settings, vm: SettingsViewModel) {
    var cleared by remember { mutableIntStateOf(0) }
    val kept by produceState(0, cleared, settings[CrashReportPrefs.enabled]) { value = vm.graph.crashReports.list().size }
    SwitchRow(
        stringResource(Res.string.settings_crash_keep),
        stringResource(Res.string.settings_crash_keep_description),
        settings[CrashReportPrefs.enabled],
    ) { vm.set(CrashReportPrefs.enabled, it) }
    NavigationRow(
        stringResource(Res.string.settings_image_cache_clear),
        if (kept == 0) stringResource(Res.string.settings_crash_none) else pluralStringResource(Res.plurals.settings_crash_count, kept, kept),
        enabled = kept > 0,
        chevron = false,
    ) {
        vm.graph.crashReports.clear()
        cleared++
    }
}

// ---- Posts (_posts.dart) ----

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PostsSettings(title: String, vm: SettingsViewModel, focus: String?, onBack: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val session by vm.session.collectAsStateWithLifecycle()
    var confirmAll by remember { mutableStateOf(false) }
    SettingsPage(title, onBack) { padding ->
        SettingsList(padding) {
            SettingsHeader(stringResource(Res.string.settings_posts))
            PrefSwitch(settings, PostsPrefs.absoluteTimestamps, stringResource(Res.string.settings_absolute_timestamp), stringResource(Res.string.settings_absolute_timestamp_description), vm, focus)
            CheckRow(
                stringResource(Res.string.settings_hide_sensitive),
                stringResource(Res.string.settings_hide_sensitive_description),
                settings[PostsPrefs.hideSensitive],
                focused = focus == PostsPrefs.hideSensitive.key,
            ) { vm.set(PostsPrefs.hideSensitive, it) }
            PrefSwitch(settings, PostsPrefs.alwaysShowSensitiveMedia, stringResource(Res.string.settings_always_show_sensitive), null, vm, focus)
            PrefSwitch(settings, PostsPrefs.fullPostContents, stringResource(Res.string.settings_full_contents), stringResource(Res.string.settings_full_contents_description), vm, focus)
            PrefSwitch(settings, PostsPrefs.nonConfirmationBias, stringResource(Res.string.settings_non_confirmation), stringResource(Res.string.settings_non_confirmation_description), vm, focus)
            PrefSwitch(settings, PostsPrefs.hideUnrelatedPostWarnings, stringResource(Res.string.settings_unrelated_warnings), stringResource(Res.string.settings_unrelated_warnings_description), vm, focus)
            PrefSwitch(settings, PostsPrefs.subscribeBadge, stringResource(Res.string.settings_subscribe_badge), stringResource(Res.string.settings_subscribe_badge_description), vm, focus)

            SettingsHeader(stringResource(Res.string.settings_default_feed_tab))
            PrefSwitch(settings, PostsPrefs.includeReplies, stringResource(Res.string.settings_include_replies), stringResource(Res.string.settings_feed_default_description), vm, focus)
            PrefSwitch(settings, PostsPrefs.includeReposts, stringResource(Res.string.settings_include_reposts), stringResource(Res.string.settings_feed_default_description), vm, focus)
            val own = session.ownChoices.replies + session.ownChoices.reposts
            NavigationRow(
                stringResource(Res.string.settings_apply_all),
                stringResource(Res.string.settings_apply_all_description) + "\n" + stringResource(Res.string.settings_own_choice_count, own),
                enabled = own > 0,
                chevron = false,
                destructive = true,
            ) { confirmAll = true }

            SettingsHeader(stringResource(Res.string.settings_zen_mode))
            PrefSwitch(settings, PostsPrefs.threadedReplies, stringResource(Res.string.settings_threaded_replies), stringResource(Res.string.settings_threaded_replies_description), vm, focus)
            PrefSwitch(settings, PostsPrefs.collapseReposts, stringResource(Res.string.settings_collapse_boosts), stringResource(Res.string.settings_collapse_boosts_description), vm, focus)
            PrefSwitch(settings, PostsPrefs.zenMode, stringResource(Res.string.settings_zen_mode), stringResource(Res.string.settings_zen_mode_description), vm, focus)
            PrefSwitch(settings, PostsPrefs.calmMode, stringResource(Res.string.settings_calm_mode), stringResource(Res.string.settings_calm_mode_description), vm, focus)
            ChoiceRow(
                stringResource(Res.string.settings_zen_page_cap),
                stringResource(Res.string.settings_zen_page_cap_description),
                SettingsChoices.zenPageCaps,
                settings[PostsPrefs.zenPageCap],
                label = { it.toString() },
                focused = focus == PostsPrefs.zenPageCap.key,
            ) { vm.set(PostsPrefs.zenPageCap, it) }
            PrefSwitch(settings, PostsPrefs.rememberReadingPosition, stringResource(Res.string.settings_reading_position), stringResource(Res.string.settings_reading_position_description), vm, focus)

            SettingsHeader(stringResource(Res.string.settings_language_filter), stringResource(Res.string.settings_language_filter_description))
            LanguageFilterRows(settings, vm)
        }
    }
    if (confirmAll) {
        ConfirmDialog(
            stringResource(Res.string.settings_are_you_sure),
            stringResource(Res.string.settings_apply_all_description),
            stringResource(Res.string.settings_yes),
            stringResource(Res.string.settings_no),
            onDismiss = { confirmAll = false },
        ) {
            confirmAll = false
            vm.updateSession { it.withoutOwnChoices(replies = true).withoutOwnChoices(replies = false) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageFilterRows(settings: Settings, vm: SettingsViewModel) {
    var typed by rememberSaveable { mutableStateOf(settings[PostsPrefs.languages]) }
    var focused by remember { mutableStateOf(false) }
    val commit = { vm.update { LanguageFilterInput.committed(it, typed) } }
    OutlinedTextField(
        value = typed,
        onValueChange = { typed = it },
        label = { Text(stringResource(Res.string.settings_language_filter_languages)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { commit() }),
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp).onFocusChanged { state ->
            if (focused && !state.isFocused) commit()
            focused = state.isFocused
        },
    )
    val action = settings[PostsPrefs.languageAction]
    val active = LanguageFilterInput.isActive(typed)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        LanguageFilterAction.entries.forEachIndexed { index, choice ->
            SegmentedButton(
                selected = action == choice,
                onClick = { vm.update { LanguageFilterInput.withAction(LanguageFilterInput.committed(it, typed), choice) ?: it } },
                enabled = choice == LanguageFilterAction.OFF || active,
                shape = SegmentedButtonDefaults.itemShape(index, LanguageFilterAction.entries.size),
            ) { Text(languageActionLabel(choice)) }
        }
    }
    ValueRow(stringResource(Res.string.settings_language_filter_action), languageActionLabel(action))
}

// ---- Media (_media.dart) ----

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MediaSettings(title: String, vm: SettingsViewModel, focus: String?, onBack: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    SettingsPage(title, onBack) { padding ->
        SettingsList(padding) {
            SettingsHeader(stringResource(Res.string.settings_image_quality))
            PrefSwitch(settings, MediaPrefs.loadManually, stringResource(Res.string.settings_load_manually), stringResource(Res.string.settings_load_manually_description), vm, focus)
            ChoiceRow(
                stringResource(Res.string.settings_image_quality),
                stringResource(Res.string.settings_save_bandwidth),
                MediaQuality.entries,
                settings[MediaPrefs.imageQuality],
                label = { qualityLabel(it) },
                focused = focus == MediaPrefs.imageQuality.key,
            ) { vm.set(MediaPrefs.imageQuality, it) }
            ChoiceRow(
                stringResource(Res.string.settings_grid_columns),
                stringResource(Res.string.settings_grid_columns_description),
                SettingsChoices.gridColumns,
                settings[MediaPrefs.gridColumns],
                label = { it.toString() },
                focused = focus == MediaPrefs.gridColumns.key,
            ) { vm.set(MediaPrefs.gridColumns, it) }

            SettingsHeader(stringResource(Res.string.settings_media_layout))
            val layout = settings[MediaPrefs.gridLayout]
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().focusedSetting(focus == MediaPrefs.gridLayout.key).padding(horizontal = 16.dp, vertical = 8.dp)) {
                MediaGridLayout.entries.forEachIndexed { index, choice ->
                    SegmentedButton(
                        selected = layout == choice,
                        onClick = { vm.set(MediaPrefs.gridLayout, choice) },
                        shape = SegmentedButtonDefaults.itemShape(index, MediaGridLayout.entries.size),
                    ) { Text(layoutLabel(choice), maxLines = 1) }
                }
            }

            SettingsHeader(stringResource(Res.string.settings_video_quality))
            ChoiceRow(
                stringResource(Res.string.settings_video_quality),
                stringResource(Res.string.settings_video_quality_description),
                MediaQuality.entries,
                settings[MediaPrefs.videoQuality],
                label = { qualityLabel(it) },
                focused = focus == MediaPrefs.videoQuality.key,
            ) { vm.set(MediaPrefs.videoQuality, it) }
            PrefSwitch(settings, MediaPrefs.mute, stringResource(Res.string.settings_mute_videos), stringResource(Res.string.settings_mute_videos_description), vm, focus)
            PrefSwitch(settings, MediaPrefs.loop, stringResource(Res.string.settings_loop_videos), stringResource(Res.string.settings_loop_videos_description), vm, focus)
            PrefSwitch(settings, MediaPrefs.autoplay, stringResource(Res.string.settings_autoplay_videos), stringResource(Res.string.settings_autoplay_videos_description), vm, focus)
            ChoiceRow(
                stringResource(Res.string.settings_video_prefetch),
                stringResource(Res.string.settings_video_prefetch_description),
                SettingsChoices.prefetchSeconds,
                settings[MediaPrefs.prefetchSeconds],
                label = { if (it == 0) stringResource(Res.string.settings_video_prefetch_unlimited) else stringResource(Res.string.settings_video_prefetch_seconds, it) },
                focused = focus == MediaPrefs.prefetchSeconds.key,
            ) { vm.set(MediaPrefs.prefetchSeconds, it) }
            PrefSwitch(settings, MediaPrefs.directHardwareDecoding, stringResource(Res.string.settings_hardware_decoding), stringResource(Res.string.settings_hardware_decoding_description), vm, focus)
            PrefSwitch(settings, MediaPrefs.backgroundPlayback, stringResource(Res.string.settings_background_play), stringResource(Res.string.settings_background_play_description), vm, focus)
            PrefSwitch(settings, MediaPrefs.backgroundPlaybackWithOtherApps, stringResource(Res.string.settings_background_play_other_apps), stringResource(Res.string.settings_background_play_other_apps_description), vm, focus)

            SettingsHeader(stringResource(Res.string.settings_download_handling))
            DownloadRows(settings, vm, focus)
        }
    }
}

@Composable
private fun DownloadRows(settings: Settings, vm: SettingsViewModel, focus: String?) {
    val type = settings[MediaPrefs.downloadType]
    val treeUri = settings[MediaPrefs.downloadTreeUri]
    val path = settings[MediaPrefs.downloadPath]
    val background = DownloadDestination.of(type.stored, treeUri).background
    ChoiceRow(
        stringResource(Res.string.settings_download_handling),
        stringResource(if (background) Res.string.settings_download_auto_description else Res.string.settings_download_handling_description),
        listOf(DownloadType.AUTO, DownloadType.ASK, DownloadType.DIRECTORY),
        type,
        label = { downloadTypeLabel(it) },
        focused = focus == MediaPrefs.downloadType.key,
    ) { vm.set(MediaPrefs.downloadType, it) }
    if (type == DownloadType.DIRECTORY) {
        val pick = rememberFolderPicker { stored, name ->
            vm.update { it.with(MediaPrefs.downloadTreeUri, stored).with(MediaPrefs.downloadPath, name) }
        }
        NavigationRow(
            stringResource(Res.string.settings_download_path),
            when {
                treeUri.isEmpty() && path.isEmpty() -> stringResource(Res.string.settings_not_set)
                treeUri.isEmpty() -> path + " — " + stringResource(Res.string.settings_download_path_reselect)
                else -> path.ifEmpty { treeUri }
            },
            chevron = false,
            focused = focus == MediaPrefs.downloadPath.key,
            action = stringResource(Res.string.settings_choose),
            onClick = pick,
        )
    }
    ChoiceRow(
        stringResource(Res.string.settings_download_concurrency),
        stringResource(Res.string.settings_download_concurrency_description),
        SettingsChoices.downloadConcurrency,
        settings[MediaPrefs.downloadConcurrency],
        label = { it.toString() },
        focused = focus == MediaPrefs.downloadConcurrency.key,
    ) { vm.set(MediaPrefs.downloadConcurrency, it) }
}

// ---- Theme (_theme.dart) ----

@Composable
internal fun ThemeSettings(title: String, vm: SettingsViewModel, focus: String?, onBack: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    SettingsPage(title, onBack) { padding ->
        SettingsList(padding) {
            SettingsHeader(stringResource(Res.string.settings_background), stringResource(Res.string.settings_background_description))
            BackgroundPicker(settings[ThemePrefs.background], Modifier.focusedSetting(focus == ThemePrefs.background.key)) { vm.set(ThemePrefs.background, it) }
            SettingsHeader(stringResource(Res.string.settings_accent), stringResource(Res.string.settings_accent_description))
            AccentPicker(settings[ThemePrefs.accent], Modifier.focusedSetting(focus == ThemePrefs.accent.key)) { vm.set(ThemePrefs.accent, it) }
            SettingsDivider()
            SwitchRow(
                stringResource(Res.string.settings_true_black),
                stringResource(Res.string.settings_true_black_description),
                settings[ThemePrefs.trueBlack],
                focused = focus == ThemePrefs.trueBlack.key,
            ) { on -> vm.update { it.with(ThemePrefs.trueBlack, on).with(ThemePrefs.trueBlackPostCards, on) } }
            PrefSwitch(
                settings, ThemePrefs.trueBlackPostCards, stringResource(Res.string.settings_true_black_cards),
                stringResource(Res.string.settings_true_black_cards_description), vm, focus, enabled = settings[ThemePrefs.trueBlack],
            )
            PrefSwitch(settings, ThemePrefs.showNavigationLabels, stringResource(Res.string.settings_navigation_labels), null, vm, focus)
            PrefSwitch(settings, ThemePrefs.hideNavigationOnScroll, stringResource(Res.string.settings_hide_navigation), null, vm, focus)
        }
    }
}

/** System, Light, Dim and Lights out, each as the page it paints; System is half Light, half Lights out. */
@Composable
internal fun BackgroundPicker(selected: ThemeBackground, modifier: Modifier = Modifier, onSelect: (ThemeBackground) -> Unit) {
    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ThemeBackground.entries.forEach { background ->
            val chosen = background == selected
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { onSelect(background) }.padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.size(width = 56.dp, height = 72.dp).clip(RoundedCornerShape(10.dp))
                        .border(if (chosen) 2.dp else 1.dp, if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp)),
                ) {
                    val swatch = com.aimdi.xtanative.ui.theme.XLook.swatch(background)
                    if (swatch == null) {
                        Row(Modifier.fillMaxSize()) {
                            Box(Modifier.weight(1f).fillMaxSize().background(Color(XLookTokens.LIGHT.background)))
                            Box(Modifier.weight(1f).fillMaxSize().background(Color(XLookTokens.LIGHTS_OUT.background)))
                        }
                    } else {
                        Box(Modifier.fillMaxSize().background(Color(swatch)))
                    }
                    if (chosen) {
                        Icon(
                            Icons.Outlined.Check, null, tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).size(18.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                        )
                    }
                }
                Text(backgroundLabel(background), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

/** X's six accents, each a disc; the chosen one carries a check. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AccentPicker(selected: ThemeAccent, modifier: Modifier = Modifier, onSelect: (ThemeAccent) -> Unit) {
    FlowRow(modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        ThemeAccent.entries.forEach { accent ->
            val chosen = accent == selected
            Column(
                Modifier.width(64.dp).clip(RoundedCornerShape(12.dp)).clickable { onSelect(accent) }.padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.size(32.dp).clip(CircleShape).background(Color(accent.argb))
                        .border(if (chosen) 2.dp else 0.dp, if (chosen) MaterialTheme.colorScheme.onBackground else Color.Transparent, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (chosen) Icon(Icons.Outlined.Check, null, tint = Color(Contrast.foreground(accent.argb)), modifier = Modifier.size(18.dp))
                }
                Text(accentLabel(accent), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp), maxLines = 1)
            }
        }
    }
}

// ---- Accessibility (_accessibility.dart) ----

@Composable
internal fun AccessibilitySettings(title: String, vm: SettingsViewModel, focus: String?, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    SettingsPage(title, onBack) { padding ->
        SettingsList(padding) {
            SliderRow(
                stringResource(Res.string.settings_text_scale),
                stringResource(Res.string.settings_text_scale_description),
                settings[AccessibilityPrefs.textScale].toFloat(),
                SettingsChoices.TEXT_SCALE_MIN..SettingsChoices.TEXT_SCALE_MAX,
                SettingsChoices.TEXT_SCALE_STEPS,
                label = { String.format(Locale.ROOT, "%.2f", it) },
                focused = focus == AccessibilityPrefs.textScale.key,
            ) { vm.set(AccessibilityPrefs.textScale, (it * 100).roundToInt() / 100.0) }
            PrefSwitch(settings, AccessibilityPrefs.disableAnimations, stringResource(Res.string.settings_disable_animations), stringResource(Res.string.settings_disable_animations_description), vm, focus)
            if (!isDesktop) {
                PrefSwitch(settings, AccessibilityPrefs.haptics, stringResource(Res.string.settings_haptics), stringResource(Res.string.settings_haptics_description), vm, focus)
            }
            PrefSwitch(settings, AccessibilityPrefs.tickerChart, stringResource(Res.string.settings_ticker_chart), stringResource(Res.string.settings_ticker_chart_description), vm, focus)
            PrefSwitch(settings, AccessibilityPrefs.doubleTapLike, stringResource(Res.string.settings_double_tap_like), stringResource(Res.string.settings_double_tap_like_description), vm, focus)
            PrefSwitch(settings, AccessibilityPrefs.swipeBackAnywhere, stringResource(Res.string.settings_swipe_back_anywhere), stringResource(Res.string.settings_swipe_back_anywhere_description), vm, focus)
            PrefSwitch(settings, AccessibilityPrefs.swipeBackTilt, stringResource(Res.string.settings_swipe_back_tilt), stringResource(Res.string.settings_swipe_back_tilt_description), vm, focus)
            NavigationRow(
                stringResource(Res.string.settings_speech),
                stringResource(Res.string.settings_speech_description),
                Icons.Outlined.Notifications,
            ) { onOpen(SettingsRoutes.section(SettingsSection.SPEECH.id)) }
        }
    }
}
