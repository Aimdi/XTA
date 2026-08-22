import 'package:extended_image/extended_image.dart';
import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/plugins/rss/rss_models.dart';
import 'package:xta/plugins/rss/rss_reader_screen.dart';
import 'package:xta/plugins/rss/rss_store.dart';
import 'package:xta/tweet/tweet_chrome.dart';
import 'package:xta/ui/dates.dart';

class RssItemCard extends StatelessWidget {
  final RssItem item;
  final bool showSourceBadge;

  const RssItemCard({
    super.key,
    required this.item,
    this.showSourceBadge = true,
  });

  void _open(BuildContext context) {
    Navigator.push(
      context,
      MaterialPageRoute(builder: (_) => RssReaderScreen(item: item)),
    );
  }

  @override
  Widget build(BuildContext context) {
    return ScopedBuilder<RssReadStore, Set<String>>(
      store: context.read<RssReadStore>(),
      distinct: (_) => !context.read<RssReadStore>().state.contains(item.id),
      onState: (context, readIds) =>
          _build(context, unread: !readIds.contains(item.id)),
    );
  }

  Widget _build(BuildContext context, {required bool unread}) {
    final theme = Theme.of(context);
    final hasCover = item.imageUrl != null && item.imageUrl!.isNotEmpty;

    return RepaintBoundary(
      child: tweetFlatCard(
        color: theme.cardColor,
        child: InkWell(
          onTap: () => _open(context),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              if (hasCover) _cover(context),
              Padding(
                padding: const EdgeInsets.fromLTRB(16, 12, 16, 12),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    _header(context, unread: unread),
                    const SizedBox(height: 8),
                    Text(
                      item.title,
                      maxLines: hasCover ? 4 : 3,
                      overflow: TextOverflow.ellipsis,
                      style: theme.textTheme.titleMedium!.copyWith(
                        fontWeight: unread ? FontWeight.w800 : FontWeight.w600,
                        height: 1.25,
                      ),
                    ),
                    if (item.excerpt != null) ...[
                      const SizedBox(height: 6),
                      Text(
                        item.excerpt!,
                        maxLines: hasCover ? 2 : 3,
                        overflow: TextOverflow.ellipsis,
                        style: theme.textTheme.bodyMedium!.copyWith(
                          color: theme.colorScheme.onSurfaceVariant,
                          height: 1.35,
                        ),
                      ),
                    ],
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _header(BuildContext context, {required bool unread}) {
    final theme = Theme.of(context);
    final date = item.publishedAt;
    return Row(
      children: [
        if (unread)
          Padding(
            padding: const EdgeInsets.only(right: 8),
            child: Container(
              width: 8,
              height: 8,
              decoration: BoxDecoration(
                color: theme.colorScheme.primary,
                shape: BoxShape.circle,
              ),
            ),
          ),
        if (showSourceBadge)
          Expanded(
            child: Text(
              item.feedTitle,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: theme.textTheme.labelMedium!.copyWith(
                color: theme.colorScheme.onSurfaceVariant,
                fontWeight: FontWeight.w600,
              ),
            ),
          )
        else
          const Spacer(),
        if (date != null)
          Text(
            createCompactDate(date),
            style: theme.textTheme.labelSmall!.copyWith(
              color: theme.colorScheme.onSurfaceVariant,
            ),
          ),
      ],
    );
  }

  Widget _cover(BuildContext context) {
    return AspectRatio(
      aspectRatio: 16 / 9,
      child: ExtendedImage.network(
        item.imageUrl!,
        fit: BoxFit.cover,
        cache: true,
        loadStateChanged: (state) {
          if (state.extendedImageLoadState == LoadState.failed) {
            return const SizedBox.shrink();
          }
          return null;
        },
      ),
    );
  }
}
