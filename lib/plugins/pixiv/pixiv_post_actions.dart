import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_bookmark_actions.dart';
import 'package:xta/plugins/pixiv/pixiv_bookmark_store.dart';
import 'package:xta/plugins/pixiv/pixiv_download.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_mute_sheet.dart';
import 'package:xta/plugins/pixiv/pixiv_mute_store.dart';
import 'package:xta/plugins/pixiv/pixiv_page_actions.dart';
import 'package:xta/plugins/plugin_link_post.dart';
import 'package:xta/plugins/plugin_post_actions.dart';

/// The shared post sheet for [illust]. [workActions] puts Pixiv's own entries
/// (save, bookmark, copy link, mute) on top, for places that offer them nowhere else.
Future<void> showPixivPostActions(BuildContext context, PixivIllust illust, {bool workActions = true}) =>
    showPixivPageSheet(
      context,
      url: illust.url,
      author: illust.userName,
      title: illust.title,
      caption: illust.caption,
      images: illust.viewerUrls,
      extras: workActions ? pixivWorkActions(context, illust) : const [],
    );

/// The shared post sheet for any Pixiv page, a work's or a novel's, with
/// Pixiv's own [extras] on top.
Future<void> showPixivPageSheet(
  BuildContext context, {
  required String url,
  required String author,
  required String title,
  required String caption,
  required List<String> images,
  required List<PluginPostExtraAction> extras,
}) => showPluginLinkPostActions(
  context,
  source: 'pixiv',
  url: url,
  author: author,
  text: [title, caption].where((value) => value.trim().isNotEmpty).join('\n\n'),
  images: images,
  extras: extras,
);

/// What a tile's long-press offers before the shared entries.
List<PluginPostExtraAction> pixivWorkActions(BuildContext context, PixivIllust illust) {
  final l10n = L10n.of(context);
  final bookmarks = context.read<PixivBookmarkStore?>();
  return [
    PluginPostExtraAction(
      id: 'pixiv-download',
      icon: Icons.download_for_offline_outlined,
      label: illust.viewerUrls.length > 1 ? l10n.plugin_pixiv_download_all : l10n.download,
      run: (context) => savePixivThenBookmark(context, illust, downloadAllPixivPages(context, illust)),
    ),
    if (bookmarks != null)
      pixivBookmarkEntry(
        l10n,
        id: 'pixiv-bookmark',
        bookmarked: bookmarks.isBookmarked(illust),
        toggle: (context) => togglePixivBookmark(context, illust),
      ),
    pixivCopyLinkEntry(l10n, illust.url),
    if (context.read<PixivMuteStore?>() != null)
      // Muting the author or the work itself; tags stay in the work's own mute sheet.
      for (final (index, choice) in pixivMuteChoices(l10n, illust).take(2).indexed)
        pixivMuteEntry(index == 0 ? 'pixiv-mute-author' : 'pixiv-mute-work', choice),
  ];
}

/// The sheet's Bookmark or Remove bookmark entry, for a work or a novel.
PluginPostExtraAction pixivBookmarkEntry(
  L10n l10n, {
  required String id,
  required bool bookmarked,
  required Future<void> Function(BuildContext context) toggle,
}) => PluginPostExtraAction(
  id: id,
  icon: bookmarked ? Icons.favorite : Icons.favorite_border,
  label: bookmarked ? l10n.plugin_pixiv_unbookmark : l10n.plugin_pixiv_bookmark,
  run: toggle,
);

PluginPostExtraAction pixivCopyLinkEntry(L10n l10n, String url) => PluginPostExtraAction(
  id: 'pixiv-copy-link',
  icon: Icons.link,
  label: l10n.plugin_pixiv_copy_link,
  run: (context) => copyPixivUrl(context, url),
);

/// A sheet entry that asks before muting [choice].
PluginPostExtraAction pixivMuteEntry(String id, PixivMuteChoice choice) => PluginPostExtraAction(
  id: id,
  icon: choice.icon,
  label: choice.label,
  run: (context) => confirmPixivMute(context, choice),
);
