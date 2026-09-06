import 'package:extended_image/extended_image.dart';
import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/rss/rss_models.dart';
import 'package:xta/plugins/rss/rss_reader_screen.dart';
import 'package:xta/plugins/rss/rss_store.dart';
import 'package:xta/tweet/tweet_chrome.dart';
import 'package:xta/ui/dates.dart';

/// An article row: publication first, headline/excerpt, optional thumbnail.
class RssItemCard extends StatelessWidget {
  final RssItem item;
  final bool showSourceBadge;

  const RssItemCard({super.key, required this.item, this.showSourceBadge = true});

  @override
  Widget build(BuildContext context) => ScopedBuilder<RssReadStore, Set<String>>(
    store: context.read<RssReadStore>(),
    distinct: (_) => !context.read<RssReadStore>().state.contains(item.id),
    onState: (context, readIds) => _article(context, !readIds.contains(item.id)),
  );

  Widget _article(BuildContext context, bool unread) {
    final theme = Theme.of(context);
    final hasCover = item.imageUrl?.isNotEmpty ?? false;
    final largeText = MediaQuery.textScalerOf(context).scale(14) > 20;
    final copy = Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          item.title,
          maxLines: 4,
          overflow: TextOverflow.ellipsis,
          style: theme.textTheme.titleMedium?.copyWith(
            fontWeight: unread ? FontWeight.w800 : FontWeight.w500,
            height: 1.3,
          ),
        ),
        if (item.excerpt?.isNotEmpty ?? false) ...[
          const SizedBox(height: 6),
          Text(
            item.excerpt!,
            maxLines: 3,
            overflow: TextOverflow.ellipsis,
            style: theme.textTheme.bodyMedium?.copyWith(color: tweetSecondaryColor(context), height: 1.4),
          ),
        ],
      ],
    );
    return RepaintBoundary(
      child: Semantics(
        button: true,
        value: unread ? L10n.of(context).plugin_rss_unread : null,
        child: tweetFlatCard(
          color: theme.scaffoldBackgroundColor,
          child: InkWell(
            onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => RssReaderScreen(item: item))),
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  _source(context, unread),
                  const SizedBox(height: 8),
                  Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Expanded(child: copy),
                      if (hasCover && !largeText) ...[
                        const SizedBox(width: 16),
                        SizedBox(width: 88, height: 88, child: _cover(context)),
                      ],
                    ],
                  ),
                  if (hasCover && largeText) ...[
                    const SizedBox(height: 12),
                    SizedBox(height: 140, child: _cover(context)),
                  ],
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _source(BuildContext context, bool unread) => Row(
    children: [
      if (unread) ...[Icon(Icons.circle, size: 8, color: tweetReadableAccentColor(context)), const SizedBox(width: 8)],
      Expanded(
        child: Text(
          [if (showSourceBadge) item.feedTitle, if (item.author?.isNotEmpty ?? false) item.author!].join(' · '),
          maxLines: 2,
          overflow: TextOverflow.ellipsis,
          style: Theme.of(context).textTheme.labelMedium?.copyWith(color: tweetSecondaryColor(context)),
        ),
      ),
      if (item.publishedAt != null) ...[
        const SizedBox(width: 8),
        Text(
          createCompactDate(item.publishedAt!),
          style: Theme.of(context).textTheme.labelSmall?.copyWith(color: tweetSecondaryColor(context)),
        ),
      ],
    ],
  );

  Widget _cover(BuildContext context) => ClipRRect(
    borderRadius: BorderRadius.circular(8),
    child: ExtendedImage.network(
      item.imageUrl!,
      fit: BoxFit.cover,
      cache: true,
      loadStateChanged: (state) => state.extendedImageLoadState == LoadState.failed
          ? ColoredBox(
              color: Theme.of(context).colorScheme.surfaceContainerHighest,
              child: const Icon(Icons.article_outlined),
            )
          : null,
    ),
  );
}
