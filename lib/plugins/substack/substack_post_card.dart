import 'package:extended_image/extended_image.dart';
import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/substack/substack_models.dart';
import 'package:xta/plugins/substack/substack_reader_screen.dart';
import 'package:xta/plugins/substack/substack_store.dart';
import 'package:xta/tweet/tweet_chrome.dart';
import 'package:xta/ui/dates.dart';

/// A Substack Home-style post card: cover first when present, then title.
class SubstackPostCard extends StatelessWidget {
  final SubstackPost post;

  /// Set in a mixed timeline so the card says where it came from. In the
  /// Substack tab itself the answer is never in doubt.
  final bool showSourceBadge;

  /// The publication's logo. The post payload does not carry one, so it comes
  /// from the subscription that produced the post when there is one.
  final String? logoUrl;

  const SubstackPostCard({
    super.key,
    required this.post,
    this.showSourceBadge = true,
    this.logoUrl,
  });

  void _open(BuildContext context) {
    Navigator.push(
      context,
      MaterialPageRoute(builder: (_) => SubstackReaderScreen(post: post)),
    );
  }

  @override
  Widget build(BuildContext context) {
    return ScopedBuilder<SubstackReadStore, Set<String>>(
      store: context.read<SubstackReadStore>(),
      distinct: (_) =>
          !context.read<SubstackReadStore>().state.contains(post.id),
      onState: (context, readIds) =>
          _build(context, unread: !readIds.contains(post.id)),
    );
  }

  Widget _build(BuildContext context, {required bool unread}) {
    final theme = Theme.of(context);
    final date = post.publishedAt;
    final hasCover = post.coverImage != null && post.coverImage!.isNotEmpty;

    return RepaintBoundary(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          tweetFlatCard(
            color: Theme.of(context).cardColor,
            child: InkWell(
              onTap: () => _open(context),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  if (hasCover) _cover(context),
                  Padding(
                    padding: EdgeInsets.fromLTRB(
                      16,
                      hasCover ? 12 : 12,
                      16,
                      12,
                    ),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        _header(context, date, unread: unread),
                        const SizedBox(height: 8),
                        Text(
                          post.title,
                          maxLines: hasCover ? 4 : 3,
                          overflow: TextOverflow.ellipsis,
                          style: theme.textTheme.titleMedium!.copyWith(
                            fontWeight: unread
                                ? FontWeight.w800
                                : FontWeight.w600,
                            height: 1.25,
                          ),
                        ),
                        if (post.excerpt != null) ...[
                          const SizedBox(height: 6),
                          Text(
                            post.excerpt!,
                            maxLines: hasCover ? 2 : 3,
                            overflow: TextOverflow.ellipsis,
                            style: theme.textTheme.bodyMedium!.copyWith(
                              color: theme.colorScheme.onSurfaceVariant,
                              height: 1.35,
                            ),
                          ),
                        ],
                        const SizedBox(height: 10),
                        _counts(context),
                      ],
                    ),
                  ),
                ],
              ),
            ),
          ),
          tweetHairlineDivider(context),
        ],
      ),
    );
  }

  Widget _header(BuildContext context, DateTime? date, {required bool unread}) {
    final theme = Theme.of(context);
    final logo = logoUrl;

    return Row(
      children: [
        if (unread)
          Container(
            width: 8,
            height: 8,
            margin: const EdgeInsets.only(right: 8),
            decoration: BoxDecoration(
              color: theme.colorScheme.primary,
              shape: BoxShape.circle,
            ),
          ),
        ClipRRect(
          borderRadius: BorderRadius.circular(8),
          child: logo == null
              ? Container(
                  width: 28,
                  height: 28,
                  color: theme.colorScheme.surfaceContainerHighest,
                  child: Icon(
                    Icons.article_outlined,
                    size: 16,
                    color: theme.colorScheme.onSurfaceVariant,
                  ),
                )
              : ExtendedImage.network(
                  logo,
                  width: 28,
                  height: 28,
                  fit: BoxFit.cover,
                  cacheWidth: (28 * MediaQuery.devicePixelRatioOf(context))
                      .ceil(),
                ),
        ),
        const SizedBox(width: 8),
        Flexible(
          child: Text(
            post.publicationName,
            overflow: TextOverflow.ellipsis,
            style: theme.textTheme.labelLarge!.copyWith(
              fontWeight: FontWeight.w700,
            ),
          ),
        ),
        if (showSourceBadge) ...[
          const SizedBox(width: 6),
          _badge(context, L10n.of(context).plugin_substack_title),
        ],
        if (post.isPaywalled) ...[
          const SizedBox(width: 6),
          _badge(context, L10n.of(context).plugin_substack_paywalled),
        ],
        if (post.isPodcast) ...[
          const SizedBox(width: 6),
          Icon(
            Icons.podcasts,
            size: 16,
            color: theme.colorScheme.onSurfaceVariant,
          ),
        ],
        const Spacer(),
        if (date != null)
          Text(createRelativeDate(date), style: theme.textTheme.bodySmall),
      ],
    );
  }

  Widget _counts(BuildContext context) {
    final theme = Theme.of(context);
    final muted = theme.colorScheme.onSurfaceVariant;
    final style = theme.textTheme.bodySmall!.copyWith(color: muted);
    final likes = context.read<SubstackLikesStore>();
    final saved = context.read<SubstackSavedStore>();

    return ScopedBuilder<SubstackLikesStore, List<SubstackPost>>(
      store: likes,
      distinct: (_) => likes.isLiked(post.id),
      onState: (context, liked) {
        final isLiked = liked.any((p) => p.id == post.id);
        final remote = post.reactionCount ?? 0;
        final shown = remote + (isLiked ? 1 : 0);

        return ScopedBuilder<SubstackSavedStore, List<SubstackPost>>(
          store: saved,
          distinct: (_) => saved.isSaved(post.id),
          onState: (context, savedPosts) {
            final isSaved = savedPosts.any((p) => p.id == post.id);
            return Row(
              children: [
                InkWell(
                  onTap: () => likes.toggle(post),
                  borderRadius: BorderRadius.circular(16),
                  child: Padding(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 4,
                      vertical: 2,
                    ),
                    child: Row(
                      children: [
                        Icon(
                          isLiked ? Icons.favorite : Icons.favorite_outline,
                          size: 16,
                          color: isLiked ? theme.colorScheme.primary : muted,
                        ),
                        if (shown > 0) ...[
                          const SizedBox(width: 4),
                          Text('$shown', style: style),
                        ],
                      ],
                    ),
                  ),
                ),
                const SizedBox(width: 10),
                InkWell(
                  onTap: () => saved.toggle(post),
                  borderRadius: BorderRadius.circular(16),
                  child: Padding(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 4,
                      vertical: 2,
                    ),
                    child: Icon(
                      isSaved ? Icons.bookmark : Icons.bookmark_outline,
                      size: 16,
                      color: isSaved ? theme.colorScheme.primary : muted,
                    ),
                  ),
                ),
                if ((post.commentCount ?? 0) > 0) ...[
                  const SizedBox(width: 14),
                  Icon(Icons.mode_comment_outlined, size: 15, color: muted),
                  const SizedBox(width: 4),
                  Text('${post.commentCount}', style: style),
                ],
              ],
            );
          },
        );
      },
    );
  }

  Widget _badge(BuildContext context, String label) {
    final theme = Theme.of(context);

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 1),
      decoration: BoxDecoration(
        border: Border.all(color: theme.colorScheme.outline),
        borderRadius: BorderRadius.circular(4),
      ),
      child: Text(label, style: theme.textTheme.labelSmall),
    );
  }

  Widget _cover(BuildContext context) {
    return Stack(
      alignment: Alignment.center,
      children: [
        AspectRatio(
          aspectRatio: 16 / 9,
          child: ExtendedImage.network(
            post.coverImage!,
            fit: BoxFit.cover,
            cacheWidth:
                (MediaQuery.sizeOf(context).width *
                        MediaQuery.devicePixelRatioOf(context))
                    .ceil(),
          ),
        ),
        if (post.isVideo)
          Container(
            decoration: const BoxDecoration(
              color: Colors.black54,
              shape: BoxShape.circle,
            ),
            padding: const EdgeInsets.all(12),
            child: const Icon(Icons.play_arrow, color: Colors.white, size: 32),
          ),
      ],
    );
  }
}
