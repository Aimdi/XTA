import 'package:flutter/material.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_settings_content.dart';

/// Bookmarking: how the heart files a bookmark, what else a bookmark or a
/// save sets off, and whether actions answer with a vibration.
class PixivBookmarkSettings extends StatelessWidget {
  const PixivBookmarkSettings({super.key});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(l10n.plugin_pixiv_bookmark_settings, style: Theme.of(context).textTheme.titleSmall),
        PixivPrefSwitch(
          key: const ValueKey('pixiv-default-private-bookmark'),
          pref: optionPluginPixivDefaultPrivateBookmark,
          title: l10n.plugin_pixiv_bookmark_default_private,
          subtitle: l10n.plugin_pixiv_bookmark_default_private_description,
        ),
        PixivPrefSwitch(
          key: const ValueKey('pixiv-auto-tag-bookmarks'),
          pref: optionPluginPixivAutoTagBookmarks,
          title: l10n.plugin_pixiv_bookmark_auto_tag,
          subtitle: l10n.plugin_pixiv_bookmark_auto_tag_description,
        ),
        PixivPrefSwitch(
          key: const ValueKey('pixiv-follow-after-bookmark'),
          pref: optionPluginPixivFollowAfterBookmark,
          title: l10n.plugin_pixiv_bookmark_follow_after,
          subtitle: l10n.plugin_pixiv_bookmark_follow_after_description,
        ),
        PixivPrefSwitch(
          key: const ValueKey('pixiv-download-after-bookmark'),
          pref: optionPluginPixivDownloadAfterBookmark,
          title: l10n.plugin_pixiv_bookmark_save_after,
          subtitle: l10n.plugin_pixiv_bookmark_save_after_description,
        ),
        PixivPrefSwitch(
          key: const ValueKey('pixiv-bookmark-after-download'),
          pref: optionPluginPixivBookmarkAfterDownload,
          title: l10n.plugin_pixiv_bookmark_after_save,
          subtitle: l10n.plugin_pixiv_bookmark_after_save_description,
        ),
        PixivPrefSwitch(
          key: const ValueKey('pixiv-haptics'),
          pref: optionPluginPixivHaptics,
          title: l10n.plugin_pixiv_haptics,
          subtitle: l10n.plugin_pixiv_haptics_description,
        ),
      ],
    );
  }
}
