import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_bookmark_store.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/plugin_counts.dart';
import 'package:xta/ui/dates.dart';

/// Bookmarks, views, date and the R-18 / AI marks under a work's title.
class PixivDetailStats extends StatelessWidget {
  final PixivIllust illust;

  const PixivDetailStats({super.key, required this.illust});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final theme = Theme.of(context);
    final created = illust.createdAt;
    return Wrap(
      spacing: 12,
      runSpacing: 4,
      children: [
        _bookmarks(context),
        PixivDetailStat(icon: Icons.visibility_outlined, label: compactCount(illust.totalViews)),
        if (created != null)
          Text(
            createCompactDate(created),
            style: theme.textTheme.bodySmall!.copyWith(color: theme.colorScheme.onSurfaceVariant),
          ),
        if (illust.isR18)
          Text(l10n.plugin_pixiv_r18, style: theme.textTheme.labelMedium!.copyWith(color: theme.colorScheme.error)),
        if (illust.isAi) PixivDetailStat(icon: Icons.auto_awesome_outlined, label: l10n.plugin_pixiv_ai),
      ],
    );
  }

  Widget _bookmarks(BuildContext context) {
    final bookmarks = context.read<PixivBookmarkStore>();
    return ScopedBuilder<PixivBookmarkStore, Map<int, bool>>(
      store: bookmarks,
      distinct: (_) => bookmarks.isBookmarked(illust),
      onState: (context, _) => PixivDetailStat(
        icon: bookmarks.isBookmarked(illust) ? Icons.favorite : Icons.favorite_border,
        label: compactCount(bookmarks.bookmarkCount(illust)),
      ),
    );
  }
}

/// A quiet icon and figure.
class PixivDetailStat extends StatelessWidget {
  final IconData icon;
  final String label;

  const PixivDetailStat({super.key, required this.icon, required this.label});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final muted = theme.colorScheme.onSurfaceVariant;
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Icon(icon, size: 16, color: muted),
        const SizedBox(width: 4),
        Text(label, style: theme.textTheme.bodySmall!.copyWith(color: muted)),
      ],
    );
  }
}
