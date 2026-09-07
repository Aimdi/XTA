import 'package:flutter/material.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/settings/settings_search_target.dart';
import 'package:xta/settings/_general.dart';
import 'package:xta/settings/_posts.dart';
import 'package:xta/settings/_media.dart';
import 'package:xta/settings/_theme.dart';
import 'package:xta/settings/_accessibility.dart';

enum SearchableSettingsSection { general, posts, media, theme, accessibility }

class SettingsControlResult {
  final SearchableSettingsSection section;
  final String target;
  final String title;
  final String description;
  const SettingsControlResult(this.section, this.target, this.title, this.description);

  String sectionTitle(L10n l10n) => switch (section) {
    SearchableSettingsSection.general => l10n.general,
    SearchableSettingsSection.posts => l10n.tweets,
    SearchableSettingsSection.media => l10n.media,
    SearchableSettingsSection.theme => l10n.theme,
    SearchableSettingsSection.accessibility => l10n.accessibility,
  };

  Widget destination() => SettingsSearchTarget(id: target, child: switch (section) {
    SearchableSettingsSection.general => const SettingsGeneralFragment(),
    SearchableSettingsSection.posts => const SettingsPostsFragment(),
    SearchableSettingsSection.media => const SettingsMediaFragment(),
    SearchableSettingsSection.theme => const SettingsThemeFragment(),
    SearchableSettingsSection.accessibility => const SettingsAccessibilityFragment(),
  });
}

List<SettingsControlResult> searchSettingsControls(L10n l10n, String input) {
  final words = input.trim().toLowerCase().split(RegExp(r'\s+')).where((word) => word.isNotEmpty).toList();
  if (words.isEmpty) return const [];
  final results = settingsControls(l10n).where((entry) {
    final text = '${entry.title} ${entry.description}'.toLowerCase();
    return words.every(text.contains);
  }).toList();
  results.sort((a, b) => a.title.toLowerCase().startsWith(words.first) == b.title.toLowerCase().startsWith(words.first)
    ? a.title.compareTo(b.title) : a.title.toLowerCase().startsWith(words.first) ? -1 : 1);
  return results;
}

List<SettingsControlResult> settingsControls(L10n l10n) => [
  SettingsControlResult(SearchableSettingsSection.general, optionShouldCheckForUpdates, l10n.should_check_for_updates_label, l10n.should_check_for_updates_description),
  SettingsControlResult(SearchableSettingsSection.general, optionConfirmClose, l10n.option_confirm_close_label, l10n.option_confirm_close_description),
  SettingsControlResult(SearchableSettingsSection.general, optionCleanLinks, l10n.option_clean_links_label, l10n.option_clean_links_description),
  SettingsControlResult(SearchableSettingsSection.general, optionDisableScreenshots, l10n.disable_screenshots, l10n.disable_screenshots_hint),
  SettingsControlResult(SearchableSettingsSection.general, optionHomeInitialTab, l10n.default_tab, l10n.which_tab_is_shown_when_the_app_opens),
  SettingsControlResult(SearchableSettingsSection.general, optionHomeDefaultFeedTab, l10n.default_feed_tab, l10n.default_feed_tab_description),
  SettingsControlResult(SearchableSettingsSection.general, optionDefaultProfileTab, l10n.default_profile_tab, l10n.default_profile_tab_description),
  SettingsControlResult(SearchableSettingsSection.general, optionLocale, l10n.language, l10n.language_subtitle),
  SettingsControlResult(SearchableSettingsSection.posts, optionUseAbsoluteTimestamp, l10n.use_absolute_timestamp, l10n.use_absolute_timestamp_description),
  SettingsControlResult(SearchableSettingsSection.posts, optionTweetsHideSensitive, l10n.hide_sensitive_tweets, l10n.whether_to_hide_tweets_marked_as_sensitive),
  SettingsControlResult(SearchableSettingsSection.posts, optionAlwaysShowSensitiveMedia, l10n.sensitive_media_always_show, ''),
  SettingsControlResult(SearchableSettingsSection.posts, alwaysShowFullTweetContents, l10n.always_show_full_tweet_contents, l10n.always_show_full_tweet_contents_description),
  SettingsControlResult(SearchableSettingsSection.posts, optionNonConfirmationBiasMode, l10n.activate_non_confirmation_bias_mode_label, l10n.activate_non_confirmation_bias_mode_description),
  SettingsControlResult(SearchableSettingsSection.posts, optionDisableWarningsForUnrelatedPostsInFeed, l10n.disable_warnings_for_unrelated_posts_in_feed, l10n.disable_warnings_for_unrelated_posts_in_feed_description),
  SettingsControlResult(SearchableSettingsSection.posts, optionTweetsShowSubscribeBadge, l10n.show_subscribe_button_on_avatars, l10n.show_subscribe_button_on_avatars_description),
  SettingsControlResult(SearchableSettingsSection.posts, optionGlobalIncludeReplies, l10n.include_replies, l10n.feed_default_filter_description),
  SettingsControlResult(SearchableSettingsSection.posts, optionGlobalIncludeRetweets, l10n.include_retweets, l10n.feed_default_filter_description),
  SettingsControlResult(SearchableSettingsSection.posts, optionThreadedReplies, l10n.threaded_replies, l10n.threaded_replies_description),
  SettingsControlResult(SearchableSettingsSection.posts, optionFeedCollapseBoosts, l10n.collapse_boosts, l10n.collapse_boosts_description),
  SettingsControlResult(SearchableSettingsSection.posts, optionZenMode, l10n.zen_mode, l10n.zen_mode_description),
  SettingsControlResult(SearchableSettingsSection.posts, optionCalmMode, l10n.calm_mode, l10n.calm_mode_description),
  SettingsControlResult(SearchableSettingsSection.posts, optionZenModePageCap, l10n.zen_mode_page_cap, l10n.zen_mode_page_cap_description),
  SettingsControlResult(SearchableSettingsSection.posts, optionFeedReadingPosition, l10n.remember_reading_position, l10n.remember_reading_position_description),
  SettingsControlResult(SearchableSettingsSection.media, optionMediaDisableAutoload, l10n.load_media_manually, l10n.load_media_manually_description),
  SettingsControlResult(SearchableSettingsSection.media, optionImageQuality, l10n.image_quality, l10n.save_bandwidth_using_smaller_images),
  SettingsControlResult(SearchableSettingsSection.media, optionMediaGridColumns, l10n.media_grid_columns, l10n.media_grid_columns_description),
  SettingsControlResult(SearchableSettingsSection.media, optionMediaGridLayout, l10n.media_layout, ''),
  SettingsControlResult(SearchableSettingsSection.media, optionMediaVideoQuality, l10n.video_quality, l10n.video_quality_description),
  SettingsControlResult(SearchableSettingsSection.media, optionMediaDefaultMute, l10n.mute_videos, l10n.mute_video_description),
  SettingsControlResult(SearchableSettingsSection.media, optionMediaDefaultLoop, l10n.loop_videos, l10n.loop_videos_description),
  SettingsControlResult(SearchableSettingsSection.media, optionMediaDefaultAutoPlay, l10n.autoplay_videos, l10n.autoplay_videos_description),
  SettingsControlResult(SearchableSettingsSection.media, optionMediaVideoPrefetchSeconds, l10n.video_prefetch, l10n.video_prefetch_description),
  SettingsControlResult(SearchableSettingsSection.media, optionMediaDirectHardwareDecoding, l10n.direct_hardware_decoding, l10n.direct_hardware_decoding_description),
  SettingsControlResult(SearchableSettingsSection.media, optionMediaBackgroundPlayback, l10n.allow_background_play, l10n.allow_background_play_description),
  SettingsControlResult(SearchableSettingsSection.media, optionMediaAllowBackgroundPlayOtherApps, l10n.allow_background_play_other_apps, l10n.allow_background_play_other_apps_description),
  SettingsControlResult(SearchableSettingsSection.media, optionDownloadType, l10n.download_handling, l10n.download_handling_description),
  SettingsControlResult(SearchableSettingsSection.media, optionDownloadPath, l10n.download_path, ''),
  SettingsControlResult(SearchableSettingsSection.theme, optionXLookBackground, l10n.theme_background, l10n.theme_background_description),
  SettingsControlResult(SearchableSettingsSection.theme, optionXLookAccent, l10n.theme_accent, l10n.theme_accent_description),
  SettingsControlResult(SearchableSettingsSection.theme, optionThemeTrueBlack, l10n.true_black, l10n.use_true_black_for_the_dark_mode_theme),
  SettingsControlResult(SearchableSettingsSection.theme, optionThemeTrueBlackTweetCards, l10n.true_black_tweet_cards, l10n.use_true_black_for_tweet_cards),
  SettingsControlResult(SearchableSettingsSection.theme, optionShowNavigationLabels, l10n.show_navigation_labels, ''),
  SettingsControlResult(SearchableSettingsSection.accessibility, optionTextScaleFactor, l10n.text_scale_factor, l10n.text_scale_factor_description),
  SettingsControlResult(SearchableSettingsSection.accessibility, optionDisableAnimations, l10n.disable_animations, l10n.disable_animations_description),
  SettingsControlResult(SearchableSettingsSection.accessibility, optionTickerChart, l10n.ticker_chart, l10n.ticker_chart_description),
  SettingsControlResult(SearchableSettingsSection.accessibility, optionGestureDoubleTapLike, l10n.gesture_double_tap_like, l10n.gesture_double_tap_like_description),
];
