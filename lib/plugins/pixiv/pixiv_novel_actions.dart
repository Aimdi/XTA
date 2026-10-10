import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_mute_sheet.dart';
import 'package:xta/plugins/pixiv/pixiv_mute_store.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_bookmark_button.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_models.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_store.dart';
import 'package:xta/plugins/pixiv/pixiv_post_actions.dart';
import 'package:xta/plugins/plugin_post_actions.dart';

/// Muting one novel by its id.
PixivMuteChoice pixivNovelMuteChoice(L10n l10n, PixivNovel novel) =>
    (icon: Icons.block, label: l10n.plugin_pixiv_mute_novel, mute: (store) => store.muteNovel(novel.id));

/// A novel card's long press: Pixiv's own entries above the shared post sheet's.
Future<void> showPixivNovelActions(BuildContext context, PixivNovel novel) => showPixivPageSheet(
  context,
  url: novel.url,
  author: novel.user.name,
  title: novel.title,
  caption: novel.caption,
  images: [?novel.coverUrl],
  extras: pixivNovelActions(context, novel),
);

/// Bookmark, copy link, and muting the author or the novel.
List<PluginPostExtraAction> pixivNovelActions(BuildContext context, PixivNovel novel) {
  final l10n = L10n.of(context);
  final bookmarks = context.read<PixivNovelBookmarkStore?>();
  return [
    if (bookmarks != null)
      pixivBookmarkEntry(
        l10n,
        id: 'pixiv-novel-bookmark',
        bookmarked: bookmarks.isBookmarked(novel),
        toggle: (context) => togglePixivNovelBookmark(context, novel),
      ),
    pixivCopyLinkEntry(l10n, novel.url),
    if (context.read<PixivMuteStore?>() != null) ...[
      pixivMuteEntry('pixiv-mute-author', pixivAuthorMuteChoice(l10n, novel.user.id, novel.user.name)),
      pixivMuteEntry('pixiv-mute-novel', pixivNovelMuteChoice(l10n, novel)),
    ],
  ];
}
