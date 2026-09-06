import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/hackernews/hn_models.dart';
import 'package:xta/plugins/hackernews/hn_store.dart';
import 'package:xta/plugins/hackernews/hn_story_screen.dart';
import 'package:xta/plugins/hackernews/hn_user_screen.dart';
import 'package:xta/plugins/plugin_card_row.dart';
import 'package:xta/ui/dates.dart';

/// A story is the primary target. Author, comments and local actions have their
/// own separate row, leaving the headline the full reading width.
class HnStoryCard extends StatelessWidget {
  final HnStory story;
  final int? rank;

  const HnStoryCard({super.key, required this.story, this.rank});

  void _openStory(BuildContext context) => Navigator.push(
    context, MaterialPageRoute(builder: (_) => HnStoryScreen(story: story)),
  );

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final theme = Theme.of(context);
    final likes = context.read<HnLikesStore>();
    final saved = context.read<HnSavedStore>();
    return RepaintBoundary(child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
      InkWell(onTap: () => _openStory(context), child: Padding(
        padding: const EdgeInsetsDirectional.fromSTEB(16, 14, 16, 4),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          PluginMetaLine(parts: [
            if (rank != null) '$rank',
            if (story.host != null) story.host!,
            l10n.plugin_hn_points(story.score),
            if (story.createdAt != null) createCompactDate(story.createdAt!),
          ], style: theme.textTheme.bodySmall),
          const SizedBox(height: 6),
          Text(story.title, maxLines: 4, overflow: TextOverflow.ellipsis,
            style: theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w600)),
        ]),
      )),
      Padding(padding: const EdgeInsetsDirectional.fromSTEB(8, 0, 8, 4),
        child: Wrap(crossAxisAlignment: WrapCrossAlignment.center, spacing: 4, children: [
          if (story.author != null)
            TextButton(onPressed: () => openHnUser(context, story.author!),
              style: TextButton.styleFrom(minimumSize: const Size(48, 48)),
              child: Text(l10n.plugin_hn_by(story.author!))),
          TextButton.icon(onPressed: () => _openStory(context),
            style: TextButton.styleFrom(minimumSize: const Size(48, 48)),
            icon: const Icon(Icons.chat_bubble_outline, size: 18),
            label: Text(l10n.plugin_hn_comment_count(story.commentCount))),
          ScopedBuilder<HnLikesStore, Set<String>>(store: likes,
            onState: (_, _) => IconButton(
              tooltip: likes.isLiked(story.id) ? l10n.unlike_on_this_device : l10n.like_on_this_device,
              icon: Icon(likes.isLiked(story.id) ? Icons.favorite : Icons.favorite_border, size: 20),
              onPressed: () => likes.toggle(story.id),
            )),
          ScopedBuilder<HnSavedStore, List<HnStory>>(store: saved,
            onState: (_, _) => IconButton(
              tooltip: saved.isSaved(story.id) ? l10n.unsave_from_this_device : l10n.save_on_this_device,
              icon: Icon(saved.isSaved(story.id) ? Icons.bookmark : Icons.bookmark_border, size: 20),
              onPressed: () => saved.toggle(story),
            )),
        ])),
      const Divider(height: 1),
    ]));
  }
}

void openHnUser(BuildContext context, String author) {
  Navigator.push(context, MaterialPageRoute(builder: (_) => HnUserScreen(userId: author)));
}
