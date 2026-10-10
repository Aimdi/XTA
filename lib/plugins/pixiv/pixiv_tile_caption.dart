import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/plugins/pixiv/pixiv_bookmark_store.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/plugin_counts.dart';

/// Title, author and bookmark count under a tile's image.
class PixivTileCaption extends StatelessWidget {
  final PixivIllust illust;

  const PixivTileCaption({super.key, required this.illust});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.fromLTRB(8, 6, 8, 8),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if (illust.title.isNotEmpty)
            Text(
              illust.title,
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
              style: theme.textTheme.bodyMedium!.copyWith(fontWeight: FontWeight.w600, height: 1.2),
            ),
          const SizedBox(height: 2),
          Row(
            children: [
              Expanded(
                child: Text(
                  illust.userName,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: theme.textTheme.labelSmall!.copyWith(color: theme.colorScheme.onSurfaceVariant),
                ),
              ),
              _bookmarkCount(context),
            ],
          ),
        ],
      ),
    );
  }

  Widget _bookmarkCount(BuildContext context) {
    final theme = Theme.of(context);
    final bookmarks = context.read<PixivBookmarkStore>();
    return ScopedBuilder<PixivBookmarkStore, Map<int, bool>>(
      store: bookmarks,
      distinct: (_) => bookmarks.isBookmarked(illust),
      onState: (context, _) {
        final bookmarked = bookmarks.isBookmarked(illust);
        return Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(
              bookmarked ? Icons.favorite : Icons.favorite_border,
              size: 12,
              color: bookmarked ? theme.colorScheme.primary : theme.colorScheme.onSurfaceVariant,
            ),
            const SizedBox(width: 2),
            Text(
              compactCount(bookmarks.bookmarkCount(illust)),
              style: theme.textTheme.labelSmall!.copyWith(color: theme.colorScheme.onSurfaceVariant),
            ),
          ],
        );
      },
    );
  }
}
