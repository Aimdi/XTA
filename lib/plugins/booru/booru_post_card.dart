import 'package:flutter/material.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/booru/booru_image.dart';
import 'package:xta/plugins/booru/booru_models.dart';
import 'package:xta/plugins/booru/booru_post_screen.dart';
import 'package:xta/ui/provenance_accent.dart';
import 'package:xta/tweet/interleaved_items.dart';

/// Compact card for mixing a booru post into a group / Following feed.
class BooruPostCard extends StatelessWidget {
  final BooruPost post;

  const BooruPostCard({super.key, required this.post});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final l10n = L10n.of(context);
    final preview = post.tags.take(6).join(' ');

    return InkWell(
      onTap: () => Navigator.push(
        context,
        MaterialPageRoute(builder: (_) => BooruPostScreen(post: post)),
      ),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            ClipRRect(
              borderRadius: BorderRadius.circular(6),
              child: SizedBox(
                width: 72,
                height: 72,
                child: BooruNetworkImage(
                  url: post.thumbnailUrl,
                  fit: BoxFit.cover,
                  cacheWidth: (72 * MediaQuery.devicePixelRatioOf(context))
                      .ceil(),
                ),
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    l10n.plugin_booru_card_title(post.id),
                    style: theme.textTheme.titleSmall,
                  ),
                  if (preview.isNotEmpty) ...[
                    const SizedBox(height: 4),
                    Text(
                      preview,
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                      style: theme.textTheme.bodySmall,
                    ),
                  ],
                  if (post.score != null) ...[
                    const SizedBox(height: 4),
                    Text(
                      l10n.plugin_booru_score(post.score!),
                      style: theme.textTheme.labelSmall,
                    ),
                  ],
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

List<InterleavedItem> booruInterleavedItems(Iterable<BooruPost> posts) => [
  for (final post in posts)
    provenanceInterleavedItem(
      // Gelbooru sometimes omits dates — still show the card rather than drop it.
      date: post.createdAt ?? DateTime.fromMillisecondsSinceEpoch(0),
      pluginId: pluginIdBooru,
      build: (_) => BooruPostCard(post: post),
    ),
];
