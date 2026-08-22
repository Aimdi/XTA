import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/hackernews/hn_models.dart';
import 'package:xta/plugins/hackernews/hn_plugin.dart';
import 'package:xta/plugins/hackernews/hn_store.dart';
import 'package:xta/plugins/hackernews/hn_story_screen.dart';
import 'package:xta/plugins/hackernews/hn_user_screen.dart';
import 'package:xta/plugins/plugin_card_row.dart';
import 'package:xta/ui/dates.dart';

class HnStoryCard extends StatelessWidget {
  final HnStory story;
  final int? rank;

  const HnStoryCard({super.key, required this.story, this.rank});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final theme = Theme.of(context);
    final likes = context.read<HnLikesStore>();
    final saved = context.read<HnSavedStore>();

    return RepaintBoundary(
      child: Column(
        children: [
          InkWell(
            onTap: () => Navigator.push(
              context,
              MaterialPageRoute(builder: (_) => HnStoryScreen(story: story)),
            ),
            child: Padding(
              padding: const EdgeInsets.fromLTRB(12, 10, 8, 10),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  if (rank != null)
                    SizedBox(
                      width: 28,
                      child: Text(
                        '$rank',
                        textAlign: TextAlign.right,
                        style: theme.textTheme.labelLarge?.copyWith(
                          color: hackerNewsBrand,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                    ),
                  if (rank != null) const SizedBox(width: 10),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          story.title,
                          maxLines: 4,
                          overflow: TextOverflow.ellipsis,
                          style: theme.textTheme.titleSmall?.copyWith(
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                        if (story.host != null) ...[
                          const SizedBox(height: 2),
                          Text(
                            story.host!,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: theme.textTheme.bodySmall?.copyWith(
                              color: theme.hintColor,
                            ),
                          ),
                        ],
                        const SizedBox(height: 4),
                        PluginMetaLine(
                          parts: _meta(l10n, story),
                          style: theme.textTheme.bodySmall?.copyWith(
                            color: theme.hintColor,
                          ),
                        ),
                      ],
                    ),
                  ),
                  Column(
                    children: [
                      ScopedBuilder<HnLikesStore, Set<String>>(
                        store: likes,
                        onState: (_, _) => IconButton(
                          tooltip: likes.isLiked(story.id)
                              ? l10n.unlike_on_this_device
                              : l10n.like_on_this_device,
                          icon: Icon(
                            likes.isLiked(story.id)
                                ? Icons.favorite
                                : Icons.favorite_border,
                            size: 20,
                          ),
                          onPressed: () => likes.toggle(story.id),
                        ),
                      ),
                      ScopedBuilder<HnSavedStore, List<HnStory>>(
                        store: saved,
                        onState: (_, _) => IconButton(
                          tooltip: saved.isSaved(story.id)
                              ? l10n.unsave_from_this_device
                              : l10n.save_on_this_device,
                          icon: Icon(
                            saved.isSaved(story.id)
                                ? Icons.bookmark
                                : Icons.bookmark_border,
                            size: 20,
                          ),
                          onPressed: () => saved.toggle(story),
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ),
          const Divider(height: 1),
        ],
      ),
    );
  }
}

List<String> _meta(L10n l10n, HnStory story) => [
  l10n.plugin_hn_points(story.score),
  if (story.author != null) l10n.plugin_hn_by(story.author!),
  if (story.createdAt != null) createCompactDate(story.createdAt!),
  l10n.plugin_hn_comment_count(story.commentCount),
];

void openHnUser(BuildContext context, String author) {
  Navigator.push(
    context,
    MaterialPageRoute(builder: (_) => HnUserScreen(userId: author)),
  );
}
