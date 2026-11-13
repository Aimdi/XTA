package com.aimdi.xtanative.ui.settings

import androidx.compose.runtime.Composable
import com.aimdi.xtanative.store.backup.BackupCategory
import com.aimdi.xtanative.store.settings.DownloadType
import com.aimdi.xtanative.store.settings.LanguageFilterAction
import com.aimdi.xtanative.store.settings.MediaGridLayout
import com.aimdi.xtanative.store.settings.MediaQuality
import com.aimdi.xtanative.store.settings.ProfileTab
import com.aimdi.xtanative.store.settings.ThemeAccent
import com.aimdi.xtanative.store.settings.ThemeBackground
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

// What each choice is called, in XTA's words.

@Composable
internal fun qualityLabel(quality: MediaQuality): String = stringResource(
    when (quality) {
        MediaQuality.THUMB -> Res.string.settings_quality_low
        MediaQuality.SMALL -> Res.string.settings_quality_medium
        MediaQuality.MEDIUM -> Res.string.settings_quality_high
        MediaQuality.LARGE -> Res.string.settings_quality_maximum
    },
)

@Composable
internal fun layoutLabel(layout: MediaGridLayout): String = stringResource(
    when (layout) {
        MediaGridLayout.MASONRY -> Res.string.settings_layout_masonry
        MediaGridLayout.FEED -> Res.string.settings_layout_feed
        MediaGridLayout.TWO_COLUMNS -> Res.string.settings_layout_two_columns
    },
)

@Composable
internal fun downloadTypeLabel(type: DownloadType): String = stringResource(
    when (type) {
        DownloadType.AUTO -> Res.string.settings_download_auto
        DownloadType.ASK -> Res.string.settings_download_ask
        DownloadType.DIRECTORY -> Res.string.settings_download_directory
    },
)

@Composable
internal fun languageActionLabel(action: LanguageFilterAction): String = stringResource(
    when (action) {
        LanguageFilterAction.OFF -> Res.string.settings_language_filter_off
        LanguageFilterAction.HIDE -> Res.string.settings_language_filter_hide
        LanguageFilterAction.FOLD -> Res.string.settings_language_filter_fold
    },
)

@Composable
internal fun profileTabLabel(tab: ProfileTab): String = stringResource(
    when (tab) {
        ProfileTab.POSTS -> Res.string.settings_posts
        ProfileTab.POSTS_AND_REPLIES -> Res.string.settings_profile_replies
        ProfileTab.MEDIA -> Res.string.settings_media
        ProfileTab.SAVED -> Res.string.settings_saved
    },
)

@Composable
internal fun backgroundLabel(background: ThemeBackground): String = stringResource(
    when (background) {
        ThemeBackground.SYSTEM -> Res.string.settings_system
        ThemeBackground.LIGHT -> Res.string.settings_light
        ThemeBackground.DIM -> Res.string.settings_dim
        ThemeBackground.LIGHTS_OUT -> Res.string.settings_lights_out
    },
)

@Composable
internal fun accentLabel(accent: ThemeAccent): String = stringResource(
    when (accent) {
        ThemeAccent.BLUE -> Res.string.settings_colour_blue
        ThemeAccent.YELLOW -> Res.string.settings_colour_yellow
        ThemeAccent.PINK -> Res.string.settings_colour_pink
        ThemeAccent.PURPLE -> Res.string.settings_colour_purple
        ThemeAccent.ORANGE -> Res.string.settings_colour_orange
        ThemeAccent.GREEN -> Res.string.settings_colour_green
    },
)

/** A network by its id, as XTA's plugins call themselves; the id itself for one this build does not know. */
@Composable
internal fun networkName(id: String): String = networkTitle(id)?.let { stringResource(it) } ?: id

internal fun networkTitle(id: String): StringResource? = when (id) {
    "threads" -> Res.string.settings_net_threads
    "bluesky" -> Res.string.settings_net_bluesky
    "mastodon" -> Res.string.settings_net_mastodon
    "tiktok" -> Res.string.settings_net_tiktok
    "instagram" -> Res.string.settings_net_instagram
    "reddit" -> Res.string.settings_net_reddit
    "hackernews" -> Res.string.settings_net_hackernews
    "substack" -> Res.string.settings_net_substack
    "rss" -> Res.string.settings_net_rss
    "websources" -> Res.string.settings_net_websources
    "pixiv" -> Res.string.settings_net_pixiv
    "booru" -> Res.string.settings_net_booru
    "ehviewer" -> Res.string.settings_net_ehviewer
    "stocks" -> Res.string.settings_net_stocks
    "karakeep" -> Res.string.settings_net_karakeep
    "deepmarks" -> Res.string.settings_net_deepmarks
    "immich" -> Res.string.settings_net_immich
    else -> null
}

/** A feed of Home's strip: Following, For you (XTA calls it X), or a network. */
@Composable
internal fun feedTabLabel(id: String): String = when (id) {
    "following" -> stringResource(Res.string.settings_tab_following)
    "x", "foryou" -> stringResource(Res.string.settings_tab_for_you)
    else -> networkName(id)
}

/** One of Home's pages: Home's own four, a network's tab, or a group by its name. */
@Composable
internal fun homePageLabel(id: String, groupName: (String) -> String?): String = when (id) {
    "feed" -> stringResource(Res.string.settings_home)
    "subscriptions" -> stringResource(Res.string.settings_page_subscriptions)
    "trending" -> stringResource(Res.string.settings_page_discover)
    "saved" -> stringResource(Res.string.settings_saved)
    else -> HomePages.groupOf(id)?.let { groupName(it) ?: it } ?: networkName(id)
}

/** What a backup preview calls each section (import_preview.dart). */
@Composable
internal fun categoryLabel(category: BackupCategory): String = when (category) {
    BackupCategory.ARCHIVE_ANNOTATIONS -> stringResource(Res.string.settings_export_annotations)
    BackupCategory.SETTINGS -> stringResource(Res.string.settings_title)
    BackupCategory.SUBSCRIPTIONS -> stringResource(Res.string.settings_page_subscriptions)
    BackupCategory.SUBSTACK -> stringResource(Res.string.settings_net_substack)
    BackupCategory.RSS -> stringResource(Res.string.settings_net_rss)
    BackupCategory.SUBREDDITS -> stringResource(Res.string.settings_cat_subreddits)
    BackupCategory.STOCKS -> stringResource(Res.string.settings_net_stocks)
    BackupCategory.THREADS -> stringResource(Res.string.settings_net_threads)
    BackupCategory.BLUESKY -> stringResource(Res.string.settings_net_bluesky)
    BackupCategory.MASTODON -> stringResource(Res.string.settings_net_mastodon)
    BackupCategory.BOORU_TAGS -> stringResource(Res.string.settings_cat_booru_tags)
    BackupCategory.EH_FAVORITES -> stringResource(Res.string.settings_net_ehviewer) + " · " + stringResource(Res.string.settings_cat_eh_favorites)
    BackupCategory.EH_HISTORY -> stringResource(Res.string.settings_net_ehviewer) + " · " + stringResource(Res.string.settings_cat_eh_history)
    BackupCategory.TIKTOK_SUBSCRIPTIONS -> stringResource(Res.string.settings_net_tiktok) + " · " + stringResource(Res.string.settings_cat_tiktok_accounts)
    BackupCategory.INSTAGRAM_SUBSCRIPTIONS -> stringResource(Res.string.settings_net_instagram) + " · " + stringResource(Res.string.settings_cat_instagram_accounts)
    BackupCategory.GROUPS -> stringResource(Res.string.settings_cat_groups)
    BackupCategory.GROUP_MEMBERS -> stringResource(Res.string.settings_cat_group_members)
    BackupCategory.SAVED_POSTS -> stringResource(Res.string.settings_saved)
    BackupCategory.FOLDERS -> stringResource(Res.string.settings_cat_folders)
    BackupCategory.LIKED_POSTS -> stringResource(Res.string.settings_cat_likes)
    BackupCategory.FILTERS -> stringResource(Res.string.settings_cat_filters)
    BackupCategory.READ_POSITIONS -> stringResource(Res.string.settings_cat_read_positions)
    BackupCategory.UPVOTES -> stringResource(Res.string.settings_cat_upvotes)
    BackupCategory.THREADS_LIKES -> stringResource(Res.string.settings_net_threads) + " · " + stringResource(Res.string.settings_cat_threads_likes)
    BackupCategory.BLUESKY_LIKES -> stringResource(Res.string.settings_net_bluesky) + " · " + stringResource(Res.string.settings_cat_bluesky_likes)
    BackupCategory.ACCOUNTS -> stringResource(Res.string.settings_account)
    BackupCategory.PROFILE_NOTES -> stringResource(Res.string.settings_cat_profile_notes)
    BackupCategory.ANTENNAS -> stringResource(Res.string.settings_cat_antennas)
    BackupCategory.LOCAL_POSTS -> stringResource(Res.string.settings_cat_local_notes)
    BackupCategory.PLUGINS -> stringResource(Res.string.settings_cat_plugins)
}

/** A section's title and what it holds, as Settings lists it. */
internal fun sectionTitle(section: SettingsSection): StringResource = when (section) {
    SettingsSection.GENERAL -> Res.string.settings_general
    SettingsSection.POSTS -> Res.string.settings_posts
    SettingsSection.MEDIA -> Res.string.settings_media
    SettingsSection.HOME -> Res.string.settings_home
    SettingsSection.THEME -> Res.string.settings_theme
    SettingsSection.ACCESSIBILITY -> Res.string.settings_accessibility
    SettingsSection.SPEECH -> Res.string.settings_speech
    SettingsSection.ACCOUNT -> Res.string.settings_account
    SettingsSection.DATA -> Res.string.settings_data
    SettingsSection.SYNC -> Res.string.settings_sync
    SettingsSection.AI -> Res.string.settings_ai
    SettingsSection.DIAGNOSTICS -> Res.string.settings_diagnostics
}

internal fun sectionHint(section: SettingsSection): StringResource = when (section) {
    SettingsSection.GENERAL -> Res.string.settings_general_hint
    SettingsSection.POSTS -> Res.string.settings_posts_hint
    SettingsSection.MEDIA -> Res.string.settings_media_hint
    SettingsSection.HOME -> Res.string.settings_home_hint
    SettingsSection.THEME -> Res.string.settings_theme_hint
    SettingsSection.ACCESSIBILITY -> Res.string.settings_accessibility_hint
    SettingsSection.SPEECH -> Res.string.settings_speech_description
    SettingsSection.ACCOUNT -> Res.string.settings_account_hint
    SettingsSection.DATA -> Res.string.settings_data_hint
    SettingsSection.SYNC -> Res.string.settings_sync_description
    SettingsSection.AI -> Res.string.settings_ai_description
    SettingsSection.DIAGNOSTICS -> Res.string.settings_diagnostics_description
}
