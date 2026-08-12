import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_bookmark_store.dart';
import 'package:xta/plugins/pixiv/pixiv_client.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_settings.dart';

/// Heart that bookmarks on Pixiv — same write as follow, not a local-only like.
class PixivBookmarkButton extends StatefulWidget {
  final PixivIllust illust;
  final bool compact;

  const PixivBookmarkButton({
    super.key,
    required this.illust,
    this.compact = false,
  });

  @override
  State<PixivBookmarkButton> createState() => _PixivBookmarkButtonState();
}

class _PixivBookmarkButtonState extends State<PixivBookmarkButton> {
  var _busy = false;

  Future<void> _toggle() async {
    if (_busy) {
      return;
    }
    final l10n = L10n.of(context);
    final messenger = ScaffoldMessenger.of(context);
    final client = context.read<PixivClient>();
    final store = context.read<PixivBookmarkStore>();
    setState(() => _busy = true);
    try {
      await store.toggle(client, widget.illust);
    } catch (e) {
      messenger.showSnackBar(
        SnackBar(content: Text(pixivErrorMessage(l10n, e))),
      );
    }
    if (mounted) {
      setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final store = context.read<PixivBookmarkStore>();
    return ScopedBuilder<PixivBookmarkStore, Map<int, bool>>(
      store: store,
      distinct: (_) => store.isBookmarked(widget.illust),
      onState: (context, _) {
        final bookmarked = store.isBookmarked(widget.illust);
        final l10n = L10n.of(context);
        final color = bookmarked
            ? Theme.of(context).colorScheme.primary
            : (widget.compact
                  ? Colors.white
                  : Theme.of(context).colorScheme.onSurfaceVariant);
        final button = IconButton(
          tooltip: bookmarked
              ? l10n.plugin_pixiv_unbookmark
              : l10n.plugin_pixiv_bookmark,
          onPressed: _busy ? null : _toggle,
          visualDensity: widget.compact ? VisualDensity.compact : null,
          padding: widget.compact ? EdgeInsets.zero : null,
          constraints: widget.compact
              ? const BoxConstraints(minWidth: 40, minHeight: 40)
              : null,
          icon: Icon(
            bookmarked ? Icons.favorite : Icons.favorite_border,
            color: color,
            size: widget.compact ? 22 : 24,
          ),
        );
        if (!widget.compact) {
          return button;
        }
        return DecoratedBox(
          decoration: BoxDecoration(
            color: Colors.black.withValues(alpha: 0.45),
            shape: BoxShape.circle,
          ),
          child: button,
        );
      },
    );
  }
}
