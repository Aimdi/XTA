import 'package:flutter/material.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_viewing_prefs.dart';

/// The labels laid over a tile's image: page count, ugoira, R-18 and, unless switched off, AI.
class PixivTileBadges extends StatelessWidget {
  final PixivIllust illust;

  const PixivTileBadges({super.key, required this.illust});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final ai = illust.isAi && pixivShowsAiBadge(pixivPrefsOf(context));
    return Stack(
      fit: StackFit.expand,
      children: [
        if (illust.pageCount > 1)
          Positioned(
            top: 6,
            right: 6,
            child: PixivTileBadge(icon: Icons.collections_outlined, label: '${illust.pageCount}'),
          ),
        if (illust.isUgoira)
          Positioned(
            top: 6,
            left: 6,
            child: PixivTileBadge(icon: Icons.play_circle_outline, label: l10n.plugin_pixiv_ugoira),
          ),
        if (illust.isR18 || ai)
          Positioned(
            bottom: 6,
            left: 6,
            child: Row(
              mainAxisSize: MainAxisSize.min,
              spacing: 4,
              children: [
                if (illust.isR18) PixivTileBadge(icon: Icons.eighteen_up_rating_outlined, label: l10n.plugin_pixiv_r18),
                if (ai) PixivTileBadge(icon: Icons.auto_awesome_outlined, label: l10n.plugin_pixiv_ai),
              ],
            ),
          ),
      ],
    );
  }
}

/// One dark pill over an image; white on black reads on any artwork.
class PixivTileBadge extends StatelessWidget {
  final IconData icon;
  final String label;

  const PixivTileBadge({super.key, required this.icon, required this.label});

  @override
  Widget build(BuildContext context) {
    return DecoratedBox(
      decoration: BoxDecoration(color: Colors.black.withValues(alpha: 0.55), borderRadius: BorderRadius.circular(4)),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 5, vertical: 2),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(icon, size: 12, color: Colors.white),
            const SizedBox(width: 3),
            Text(
              label,
              style: const TextStyle(color: Colors.white, fontSize: 11, fontWeight: FontWeight.w600),
            ),
          ],
        ),
      ),
    );
  }
}
