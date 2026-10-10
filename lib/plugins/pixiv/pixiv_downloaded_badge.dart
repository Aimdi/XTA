import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_download_index.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';

/// A small check over a tile once any page of its work is saved on this device.
class PixivDownloadedBadge extends StatelessWidget {
  final PixivIllust illust;

  const PixivDownloadedBadge({super.key, required this.illust});

  @override
  Widget build(BuildContext context) {
    final index = PixivDownloadIndex.maybeOf(context);
    if (index == null) return const SizedBox.shrink();
    return ScopedBuilder<PixivDownloadIndex, Set<String>>(
      store: index,
      distinct: (_) => index.savedCount(illust) > 0,
      onState: (context, _) => index.savedCount(illust) == 0 ? const SizedBox.shrink() : _mark(context),
    );
  }

  Widget _mark(BuildContext context) => Semantics(
    key: ValueKey('pixiv-downloaded-${illust.id}'),
    label: L10n.of(context).plugin_pixiv_saved_badge,
    child: DecoratedBox(
      decoration: BoxDecoration(color: Colors.black.withValues(alpha: 0.55), shape: BoxShape.circle),
      child: const Padding(
        padding: EdgeInsets.all(4),
        child: Icon(Icons.download_done, size: 14, color: Colors.white),
      ),
    ),
  );
}

/// Saves the page on screen; the icon fills once that page is saved here.
class PixivSavePageButton extends StatelessWidget {
  final PixivIllust illust;
  final int page;
  final VoidCallback onPressed;

  const PixivSavePageButton({super.key, required this.illust, required this.page, required this.onPressed});

  @override
  Widget build(BuildContext context) {
    final index = PixivDownloadIndex.maybeOf(context);
    if (index == null) return _button(context, saved: false);
    return ScopedBuilder<PixivDownloadIndex, Set<String>>(
      store: index,
      distinct: (_) => index.isSaved(illust.id, page),
      onState: (context, _) => _button(context, saved: index.isSaved(illust.id, page)),
    );
  }

  Widget _button(BuildContext context, {required bool saved}) {
    final l10n = L10n.of(context);
    return IconButton(
      tooltip: saved ? l10n.plugin_pixiv_download_page_saved : l10n.plugin_pixiv_download_page,
      onPressed: onPressed,
      color: saved ? Theme.of(context).colorScheme.primary : null,
      icon: Icon(saved ? Icons.download_done : Icons.download_outlined),
    );
  }
}
