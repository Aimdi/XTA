import 'package:extended_image/extended_image.dart';
import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/substack/substack_archive_screen.dart';
import 'package:xta/plugins/substack/substack_comments_screen.dart';
import 'package:xta/plugins/substack/substack_models.dart';
import 'package:xta/plugins/substack/substack_reader_screen.dart';
import 'package:xta/plugins/substack/substack_store.dart';
import 'package:xta/tweet/_like_button.dart';
import 'package:xta/tweet/tweet_chrome.dart';
import 'package:xta/tweet/tweet_footer.dart';
import 'package:xta/ui/dates.dart';

const double kSubstackLogoSize = 40;

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

  void _openPublication(BuildContext context) {
    openSubstackPublication(
      context,
      publicationForPost(post, logoUrl: logoUrl),
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
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                if (hasCover)
                  InkWell(onTap: () => _open(context), child: _cover(context)),
                Padding(
                  padding: const EdgeInsets.fromLTRB(16, 12, 16, 4),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      _header(context, date, unread: unread),
                      const SizedBox(height: 8),
                      InkWell(
                        onTap: () => _open(context),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.stretch,
                          children: [
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
                          ],
                        ),
                      ),
                    ],
                  ),
                ),
                Padding(
                  padding: const EdgeInsets.fromLTRB(8, 0, 8, 4),
                  child: _counts(context),
                ),
              ],
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
    final hasBadges = showSourceBadge || post.isPaywalled || post.isPodcast;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
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
            Expanded(
              child: InkWell(
                onTap: () => _openPublication(context),
                child: Tooltip(
                  message: L10n.of(context).plugin_substack_publication,
                  child: Row(
                    children: [
                      ClipRRect(
                        borderRadius: BorderRadius.circular(8),
                        child: logo == null
                            ? Container(
                                width: kSubstackLogoSize,
                                height: kSubstackLogoSize,
                                color:
                                    theme.colorScheme.surfaceContainerHighest,
                                child: Icon(
                                  Icons.article_outlined,
                                  size: 20,
                                  color: theme.colorScheme.onSurfaceVariant,
                                ),
                              )
                            : ExtendedImage.network(
                                logo,
                                width: kSubstackLogoSize,
                                height: kSubstackLogoSize,
                                fit: BoxFit.cover,
                                cacheWidth:
                                    (kSubstackLogoSize *
                                            MediaQuery.devicePixelRatioOf(
                                              context,
                                            ))
                                        .ceil(),
                              ),
                      ),
                      const SizedBox(width: 10),
                      Expanded(
                        child: Text(
                          post.publicationName,
                          overflow: TextOverflow.ellipsis,
                          style: theme.textTheme.titleSmall!.copyWith(
                            fontWeight: FontWeight.w800,
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ),
            if (date != null)
              Text(createRelativeDate(date), style: theme.textTheme.bodySmall),
          ],
        ),
        if (hasBadges) ...[
          const SizedBox(height: 6),
          Wrap(
            spacing: 6,
            runSpacing: 4,
            crossAxisAlignment: WrapCrossAlignment.center,
            children: [
              if (showSourceBadge)
                _badge(context, L10n.of(context).plugin_substack_title),
              if (post.isPaywalled)
                _badge(context, L10n.of(context).plugin_substack_paywalled),
              if (post.isPodcast)
                Icon(
                  Icons.podcasts,
                  size: 16,
                  color: theme.colorScheme.onSurfaceVariant,
                ),
            ],
          ),
        ],
      ],
    );
  }

  Widget _counts(BuildContext context) {
    final theme = Theme.of(context);
    final muted = theme.colorScheme.onSurfaceVariant;
    final likes = context.read<SubstackLikesStore>();
    final saved = context.read<SubstackSavedStore>();
    final comments = post.commentCount ?? 0;

    return ScopedBuilder<SubstackLikesStore, List<SubstackPost>>(
      store: likes,
      onState: (context, _) {
        final isLiked = likes.isLiked(post.id);
        final remote = post.reactionCount ?? 0;
        final shown = remote + (isLiked ? 1 : 0);

        return ScopedBuilder<SubstackSavedStore, List<SubstackPost>>(
          store: saved,
          onState: (context, _) {
            final isSaved = saved.isSaved(post.id);
            return Row(
              children: [
                LikeButton(
                  isLiked: isLiked,
                  label: shown > 0 ? '$shown' : '',
                  color: isLiked ? theme.colorScheme.primary : muted,
                  onPressed: () async {
                    final wasLiked = isLiked;
                    await likes.toggle(post);
                    if (!wasLiked && context.mounted) {
                      maybeShowLikeToast(context);
                    }
                  },
                ),
                tweetFooterIconButton(
                  context,
                  isSaved ? Icons.bookmark : Icons.bookmark_outline,
                  isSaved ? theme.colorScheme.primary : muted,
                  isSaved ? 1 : 0,
                  () => saved.toggle(post),
                  L10n.of(context).saved,
                ),
                TextButton.icon(
                  style: footerButtonStyle,
                  onPressed: () => Navigator.push(
                    context,
                    MaterialPageRoute(
                      builder: (_) => SubstackCommentsScreen(post: post),
                    ),
                  ),
                  icon: Icon(
                    Icons.mode_comment_outlined,
                    size: 20,
                    color: muted,
                  ),
                  label: Text(
                    comments > 0
                        ? '$comments'
                        : L10n.of(context).plugin_substack_comments,
                    style: theme.textTheme.bodySmall!.copyWith(color: muted),
                  ),
                ),
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
