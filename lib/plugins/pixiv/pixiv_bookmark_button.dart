import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_bookmark_actions.dart';
import 'package:xta/plugins/pixiv/pixiv_bookmark_editor.dart';
import 'package:xta/plugins/pixiv/pixiv_bookmark_store.dart';
import 'package:xta/plugins/pixiv/pixiv_haptics.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';

/// Heart that bookmarks on Pixiv — same write as follow, not a local-only like.
/// A tap bookmarks with the reader's defaults or removes the bookmark; a long
/// press opens the bookmark editor.
class PixivBookmarkButton extends StatelessWidget {
  final PixivIllust illust;

  /// The round, image-overlaid heart of a grid tile.
  final bool compact;

  const PixivBookmarkButton({super.key, required this.illust, this.compact = false});

  @override
  Widget build(BuildContext context) {
    final store = context.read<PixivBookmarkStore>();
    return ScopedBuilder<PixivBookmarkStore, Map<int, bool>>(
      store: store,
      distinct: (_) => (store.isBookmarked(illust), store.isBusy(illust.id)),
      onState: (context, _) => _button(context, store.isBookmarked(illust), store.isBusy(illust.id)),
    );
  }

  Widget _button(BuildContext context, bool bookmarked, bool busy) {
    final l10n = L10n.of(context);
    final label = bookmarked ? l10n.plugin_pixiv_unbookmark : l10n.plugin_pixiv_bookmark;
    return Semantics(
      button: true,
      enabled: !busy,
      label: label,
      onLongPressHint: l10n.plugin_pixiv_bookmark_edit,
      child: Tooltip(
        message: label,
        triggerMode: TooltipTriggerMode.manual,
        excludeFromSemantics: true,
        child: InkResponse(
          key: ValueKey('pixiv-bookmark-${illust.id}'),
          onTap: busy ? null : () => togglePixivBookmark(context, illust),
          onLongPress: busy ? null : () => _edit(context),
          radius: kMinInteractiveDimension / 2,
          child: SizedBox.square(
            dimension: kMinInteractiveDimension,
            child: Center(child: compact ? _scrim(_icon(context, bookmarked, busy)) : _icon(context, bookmarked, busy)),
          ),
        ),
      ),
    );
  }

  void _edit(BuildContext context) {
    playPixivHaptic(context, PixivHaptic.medium);
    showPixivBookmarkEditor(context, illust);
  }

  Widget _icon(BuildContext context, bool bookmarked, bool busy) {
    final scheme = Theme.of(context).colorScheme;
    final idle = compact ? Colors.white : scheme.onSurfaceVariant;
    final color = bookmarked ? scheme.primary : idle;
    return Icon(
      bookmarked ? Icons.favorite : Icons.favorite_border,
      color: busy ? color.withValues(alpha: 0.5) : color,
      size: compact ? 22 : 24,
    );
  }

  /// Keeps a white heart readable over any image, true black included.
  Widget _scrim(Widget icon) => DecoratedBox(
    decoration: BoxDecoration(color: Colors.black.withValues(alpha: 0.45), shape: BoxShape.circle),
    child: SizedBox.square(dimension: 36, child: Center(child: icon)),
  );
}
