import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_bookmark_actions.dart';
import 'package:xta/plugins/pixiv/pixiv_bookmark_button.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_models.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_store.dart';
import 'package:xta/plugins/plugin_counts.dart';

Future<void> _runNovelBookmarkWrite(
  BuildContext context,
  Future<bool?> Function(PixivNovelBookmarkActions actions) write,
) => runPixivBookmarkWrite(
  context,
  () => write(PixivNovelBookmarkActions.of(context)),
  (feedback, _) => feedback.landed(),
);

/// What a novel's heart does on a tap: bookmark with the default visibility, or remove.
Future<void> togglePixivNovelBookmark(BuildContext context, PixivNovel novel) =>
    _runNovelBookmarkWrite(context, (actions) => actions.toggle(novel));

/// What a long press on the heart does.
Future<void> bookmarkPixivNovelPrivately(BuildContext context, PixivNovel novel) =>
    _runNovelBookmarkWrite(context, (actions) => actions.bookmarkPrivately(novel));

/// A novel's heart and its bookmark count. A tap bookmarks with the reader's
/// default visibility or removes the bookmark; a long press bookmarks privately.
class PixivNovelBookmarkButton extends StatelessWidget {
  final PixivNovel novel;

  const PixivNovelBookmarkButton({super.key, required this.novel});

  @override
  Widget build(BuildContext context) {
    final store = context.read<PixivNovelBookmarkStore>();
    return ScopedBuilder<PixivNovelBookmarkStore, Map<int, bool>>(
      store: store,
      distinct: (_) => (store.isBookmarked(novel), store.isBusy(novel.id)),
      onState: (context, _) => _button(context, store),
    );
  }

  Widget _button(BuildContext context, PixivNovelBookmarkStore store) {
    final theme = Theme.of(context);
    final bookmarked = store.isBookmarked(novel);
    final busy = store.isBusy(novel.id);
    final count = compactCount(store.bookmarkCount(novel));
    return PixivHeart(
      key: ValueKey('pixiv-novel-bookmark-${novel.id}'),
      bookmarked: bookmarked,
      busy: busy,
      longPressHint: L10n.of(context).plugin_pixiv_novel_bookmark_private,
      value: count,
      onTap: () => togglePixivNovelBookmark(context, novel),
      onLongPress: () => bookmarkPixivNovelPrivately(context, novel),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          PixivHeartIcon(bookmarked: bookmarked, busy: busy, size: 22),
          Text(count, style: theme.textTheme.labelSmall!.copyWith(color: theme.colorScheme.onSurfaceVariant)),
        ],
      ),
    );
  }
}
