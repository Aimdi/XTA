import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_bookmark_store.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/plugin_counts.dart';
import 'package:xta/ui/dates.dart';

/// Bookmarks, views, date, the R-18 / AI marks, the work's ID and its size
/// under a work's title.
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
      crossAxisAlignment: WrapCrossAlignment.center,
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
        PixivIllustIdStat(illustId: illust.id),
        if (illust.width > 0 && illust.height > 0)
          PixivDetailStat(icon: Icons.aspect_ratio, label: l10n.plugin_pixiv_resolution(illust.width, illust.height)),
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
        Flexible(
          child: Text(label, style: theme.textTheme.bodySmall!.copyWith(color: muted)),
        ),
      ],
    );
  }
}

/// The work's ID; tapping copies it.
class PixivIllustIdStat extends StatelessWidget {
  final int illustId;

  const PixivIllustIdStat({super.key, required this.illustId});

  Future<void> _copy(BuildContext context) async {
    final messenger = ScaffoldMessenger.of(context);
    final copied = L10n.of(context).plugin_pixiv_illust_id_copied;
    await Clipboard.setData(ClipboardData(text: '$illustId'));
    messenger.showSnackBar(SnackBar(content: Text(copied)));
  }

  @override
  Widget build(BuildContext context) => Semantics(
    button: true,
    onTapHint: L10n.of(context).plugin_pixiv_copy_illust_id,
    child: InkWell(
      key: const ValueKey('pixiv-illust-id'),
      borderRadius: BorderRadius.circular(6),
      onTap: () => _copy(context),
      child: ConstrainedBox(
        constraints: const BoxConstraints(minHeight: kMinInteractiveDimension),
        child: Center(
          widthFactor: 1,
          child: PixivDetailStat(icon: Icons.tag, label: L10n.of(context).plugin_pixiv_illust_id('$illustId')),
        ),
      ),
    ),
  );
}
