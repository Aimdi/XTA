import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_bookmark_actions.dart';
import 'package:xta/plugins/pixiv/pixiv_haptics.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_models.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_store.dart';
import 'package:xta/plugins/plugin_counts.dart';

Future<void> _runNovelBookmarkWrite(
  BuildContext context,
  Future<bool?> Function(PixivNovelBookmarkActions actions) write,
) async {
  final feedback = PixivBookmarkFeedback.of(context);
  try {
    if (await write(PixivNovelBookmarkActions.of(context)) != null) feedback.landed();
  } catch (error) {
    feedback.failed(error);
  }
}

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
    final l10n = L10n.of(context);
    final bookmarked = store.isBookmarked(novel);
    final busy = store.isBusy(novel.id);
    final label = bookmarked ? l10n.plugin_pixiv_unbookmark : l10n.plugin_pixiv_bookmark;
    return Semantics(
      button: true,
      enabled: !busy,
      label: label,
      value: compactCount(store.bookmarkCount(novel)),
      onLongPressHint: l10n.plugin_pixiv_novel_bookmark_private,
      child: Tooltip(
        message: label,
        triggerMode: TooltipTriggerMode.manual,
        excludeFromSemantics: true,
        child: InkResponse(
          key: ValueKey('pixiv-novel-bookmark-${novel.id}'),
          onTap: busy ? null : () => togglePixivNovelBookmark(context, novel),
          onLongPress: busy ? null : () => _bookmarkPrivately(context),
          radius: kMinInteractiveDimension / 2,
          child: ConstrainedBox(
            constraints: const BoxConstraints(minWidth: kMinInteractiveDimension, minHeight: kMinInteractiveDimension),
            child: ExcludeSemantics(
              child: _face(context, store, bookmarked: bookmarked, busy: busy),
            ),
          ),
        ),
      ),
    );
  }

  void _bookmarkPrivately(BuildContext context) {
    playPixivHaptic(context, PixivHaptic.medium);
    bookmarkPixivNovelPrivately(context, novel);
  }

  Widget _face(BuildContext context, PixivNovelBookmarkStore store, {required bool bookmarked, required bool busy}) {
    final theme = Theme.of(context);
    final color = bookmarked ? theme.colorScheme.primary : theme.colorScheme.onSurfaceVariant;
    return Column(
      mainAxisSize: MainAxisSize.min,
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        Icon(
          bookmarked ? Icons.favorite : Icons.favorite_border,
          color: busy ? color.withValues(alpha: 0.5) : color,
          size: 22,
        ),
        Text(
          compactCount(store.bookmarkCount(novel)),
          style: theme.textTheme.labelSmall!.copyWith(color: theme.colorScheme.onSurfaceVariant),
        ),
      ],
    );
  }
}
