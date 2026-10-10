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
    final icon = PixivHeartIcon(
      bookmarked: bookmarked,
      busy: busy,
      idleColor: compact ? Colors.white : null,
      size: compact ? 22 : 24,
    );
    return PixivHeart(
      key: ValueKey('pixiv-bookmark-${illust.id}'),
      bookmarked: bookmarked,
      busy: busy,
      longPressHint: L10n.of(context).plugin_pixiv_bookmark_edit,
      onTap: () => togglePixivBookmark(context, illust),
      onLongPress: () => showPixivBookmarkEditor(context, illust),
      child: compact ? _scrim(icon) : icon,
    );
  }

  /// Keeps a white heart readable over any image, true black included.
  Widget _scrim(Widget icon) => DecoratedBox(
    decoration: BoxDecoration(color: Colors.black.withValues(alpha: 0.45), shape: BoxShape.circle),
    child: SizedBox.square(dimension: 36, child: Center(child: icon)),
  );
}

/// The heart works and novels bookmark with: a 48 dp target labelled for what
/// a tap does, with [longPressHint] for the long press, which buzzes firmly
/// first. Inert while [busy]. [child] is what it shows, read as [value] if set.
class PixivHeart extends StatelessWidget {
  final bool bookmarked;
  final bool busy;
  final String longPressHint;
  final String? value;
  final VoidCallback onTap;
  final VoidCallback onLongPress;
  final Widget child;

  const PixivHeart({
    super.key,
    required this.bookmarked,
    required this.busy,
    required this.longPressHint,
    this.value,
    required this.onTap,
    required this.onLongPress,
    required this.child,
  });

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final label = bookmarked ? l10n.plugin_pixiv_unbookmark : l10n.plugin_pixiv_bookmark;
    return Semantics(
      button: true,
      enabled: !busy,
      label: label,
      value: value,
      onLongPressHint: longPressHint,
      child: Tooltip(
        message: label,
        triggerMode: TooltipTriggerMode.manual,
        excludeFromSemantics: true,
        child: InkResponse(
          onTap: busy ? null : onTap,
          onLongPress: busy ? null : () => _longPress(context),
          radius: kMinInteractiveDimension / 2,
          child: ConstrainedBox(
            constraints: const BoxConstraints(minWidth: kMinInteractiveDimension, minHeight: kMinInteractiveDimension),
            child: Center(widthFactor: 1, heightFactor: 1, child: ExcludeSemantics(child: child)),
          ),
        ),
      ),
    );
  }

  void _longPress(BuildContext context) {
    playPixivHaptic(context, PixivHaptic.medium);
    onLongPress();
  }
}

/// A filled heart in the accent colour or an outlined one in [idleColor],
/// faded while its write is on its way.
class PixivHeartIcon extends StatelessWidget {
  final bool bookmarked;
  final bool busy;
  final Color? idleColor;
  final double size;

  const PixivHeartIcon({super.key, required this.bookmarked, required this.busy, this.idleColor, this.size = 24});

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final color = bookmarked ? scheme.primary : idleColor ?? scheme.onSurfaceVariant;
    return Icon(
      bookmarked ? Icons.favorite : Icons.favorite_border,
      color: busy ? color.withValues(alpha: 0.5) : color,
      size: size,
    );
  }
}
