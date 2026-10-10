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
Future<void> showPixivPostActions(BuildContext context, PixivIllust illust, {bool workActions = true}) {
  return showPluginLinkPostActions(
    context,
    source: 'pixiv',
    url: illust.url,
    author: illust.userName,
    text: [illust.title, illust.caption].where((value) => value.trim().isNotEmpty).join('\n\n'),
    images: illust.viewerUrls,
    extras: workActions ? pixivWorkActions(context, illust) : const [],
  );
}

/// What a tile's long-press offers before the shared entries.
List<PluginPostExtraAction> pixivWorkActions(BuildContext context, PixivIllust illust) {
  final l10n = L10n.of(context);
  final bookmarks = context.read<PixivBookmarkStore?>();
  return [
    PluginPostExtraAction(
      id: 'pixiv-download',
      icon: Icons.download_for_offline_outlined,
      label: illust.viewerUrls.length > 1 ? l10n.plugin_pixiv_download_all : l10n.download,
      run: (context) => downloadAllPixivPages(context, illust),
    ),
    if (bookmarks != null) _bookmarkAction(l10n, illust, bookmarked: bookmarks.isBookmarked(illust)),
    PluginPostExtraAction(
      id: 'pixiv-copy-link',
      icon: Icons.link,
      label: l10n.plugin_pixiv_copy_link,
      run: (context) => copyPixivLink(context, illust),
    ),
    if (context.read<PixivMuteStore?>() != null) ..._muteActions(l10n, illust),
  ];
}

PluginPostExtraAction _bookmarkAction(L10n l10n, PixivIllust illust, {required bool bookmarked}) =>
    PluginPostExtraAction(
      id: 'pixiv-bookmark',
      icon: bookmarked ? Icons.favorite : Icons.favorite_border,
      label: bookmarked ? l10n.plugin_pixiv_unbookmark : l10n.plugin_pixiv_bookmark,
      run: (context) => togglePixivBookmark(context, illust),
    );

/// Muting the author or the work itself; tags stay in the work's own mute sheet.
Iterable<PluginPostExtraAction> _muteActions(L10n l10n, PixivIllust illust) => [
  for (final (index, choice) in pixivMuteChoices(l10n, illust).take(2).indexed)
    PluginPostExtraAction(
      id: index == 0 ? 'pixiv-mute-author' : 'pixiv-mute-work',
      icon: choice.icon,
      label: choice.label,
      run: (context) => confirmPixivMute(context, choice),
    ),
];
