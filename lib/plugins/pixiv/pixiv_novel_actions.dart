import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_mute_sheet.dart';
import 'package:xta/plugins/pixiv/pixiv_mute_store.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_bookmark_button.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_models.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_store.dart';
import 'package:xta/plugins/pixiv/pixiv_page_actions.dart';
import 'package:xta/plugins/plugin_link_post.dart';
import 'package:xta/plugins/plugin_post_actions.dart';

/// Muting one novel by its id.
PixivMuteChoice pixivNovelMuteChoice(L10n l10n, PixivNovel novel) =>
    (icon: Icons.block, label: l10n.plugin_pixiv_mute_novel, mute: (store) => store.muteNovel(novel.id));

/// A novel card's long press: Pixiv's own entries above the shared post sheet's.
Future<void> showPixivNovelActions(BuildContext context, PixivNovel novel) => showPluginLinkPostActions(
  context,
  source: 'pixiv',
  url: novel.url,
  author: novel.user.name,
  text: [novel.title, novel.caption].where((value) => value.trim().isNotEmpty).join('\n\n'),
  images: [?novel.coverUrl],
  extras: pixivNovelActions(context, novel),
);

/// Bookmark, copy link, and muting the author or the novel.
List<PluginPostExtraAction> pixivNovelActions(BuildContext context, PixivNovel novel) {
  final l10n = L10n.of(context);
  final bookmarks = context.read<PixivNovelBookmarkStore?>();
  final mutes = context.read<PixivMuteStore?>() == null
      ? const <(String, PixivMuteChoice)>[]
      : [
          ('pixiv-mute-author', pixivAuthorMuteChoice(l10n, novel.user.id, novel.user.name)),
          ('pixiv-mute-novel', pixivNovelMuteChoice(l10n, novel)),
        ];
  return [
    if (bookmarks != null) _bookmarkAction(l10n, novel, bookmarked: bookmarks.isBookmarked(novel)),
    PluginPostExtraAction(
      id: 'pixiv-copy-link',
      icon: Icons.link,
      label: l10n.plugin_pixiv_copy_link,
      run: (context) => copyPixivUrl(context, novel.url),
    ),
    for (final (id, choice) in mutes)
      PluginPostExtraAction(
        id: id,
        icon: choice.icon,
        label: choice.label,
        run: (context) => confirmPixivMute(context, choice),
      ),
  ];
}

PluginPostExtraAction _bookmarkAction(L10n l10n, PixivNovel novel, {required bool bookmarked}) => PluginPostExtraAction(
  id: 'pixiv-novel-bookmark',
  icon: bookmarked ? Icons.favorite : Icons.favorite_border,
  label: bookmarked ? l10n.plugin_pixiv_unbookmark : l10n.plugin_pixiv_bookmark,
  run: (context) => togglePixivNovelBookmark(context, novel),
);
