import 'package:extended_image/extended_image.dart';
import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:quax/generated/l10n.dart';
import 'package:quax/plugins/substack/substack_models.dart';
import 'package:quax/plugins/substack/substack_reader_screen.dart';
import 'package:quax/plugins/substack/substack_store.dart';
import 'package:quax/tweet/tweet_chrome.dart';
import 'package:quax/ui/dates.dart';

/// A Substack post as a timeline card.
///
/// Shaped like the posts around it, because it sits among them: a publication
/// line where the author would be, the cover image where a post's media would
/// be, and the same card chrome and hairline. A row in a list read as a
/// different app bolted onto the side of this one.
class SubstackPostCard extends StatelessWidget {
  final SubstackPost post;

  /// Set in a mixed timeline so the card says where it came from. In the
  /// Substack tab itself the answer is never in doubt.
  final bool showSourceBadge;

  /// The publication's logo. The post payload does not carry one, so it comes
  /// from the subscription that produced the post when there is one.
  final String? logoUrl;

  const SubstackPostCard({super.key, required this.post, this.showSourceBadge = true, this.logoUrl});

  void _open(BuildContext context) {
    Navigator.push(context, MaterialPageRoute(builder: (_) => SubstackReaderScreen(post: post)));
  }

  @override
  Widget build(BuildContext context) {
    return ScopedBuilder<SubstackReadStore, Set<String>>(
      store: context.read<SubstackReadStore>(),
      onState: (context, readIds) => _build(context, unread: !readIds.contains(post.id)),
    );
  }

  Widget _build(BuildContext context, {required bool unread}) {
    final theme = Theme.of(context);
    final date = post.publishedAt;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Container(
          decoration: BoxDecoration(
            border: Border(
              top: BorderSide(color: theme.colorScheme.outline, width: 0.5),
            ),
          ),
          child: tweetFlatCard(
            color: Theme.of(context).cardColor,
            child: InkWell(
              onTap: () => _open(context),
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    _header(context, date),
                    const SizedBox(height: 8),
                    Text(
                      post.title,
                      maxLines: 3,
                      overflow: TextOverflow.ellipsis,
                      style: theme.textTheme.titleMedium!
                          .copyWith(fontWeight: unread ? FontWeight.w700 : FontWeight.w500),
                    ),
                    if (post.excerpt != null) ...[
                      const SizedBox(height: 4),
                      Text(post.excerpt!,
                          maxLines: 3,
                          overflow: TextOverflow.ellipsis,
                          style: theme.textTheme.bodyMedium!.copyWith(color: theme.colorScheme.onSurfaceVariant)),
                    ],
                    if (post.coverImage != null) ...[
                      const SizedBox(height: 12),
                      _cover(context),
                    ],
                  ],
                ),
              ),
            ),
          ),
        ),
        tweetHairlineDivider(context),
      ],
    );
  }

  Widget _header(BuildContext context, DateTime? date) {
    final theme = Theme.of(context);
    final logo = logoUrl;

    return Row(
      children: [
        ClipRRect(
          borderRadius: BorderRadius.circular(6),
          child: logo == null
              ? Container(
                  width: 24,
                  height: 24,
                  color: theme.colorScheme.surfaceContainerHighest,
                  child: Icon(Icons.article_outlined, size: 14, color: theme.colorScheme.onSurfaceVariant),
                )
              : ExtendedImage.network(logo, width: 24, height: 24, fit: BoxFit.cover),
        ),
        const SizedBox(width: 8),
        Flexible(
          child: Text(post.publicationName,
              overflow: TextOverflow.ellipsis, style: const TextStyle(fontWeight: FontWeight.w700)),
        ),
        if (showSourceBadge) ...[
          const SizedBox(width: 6),
          _badge(context, L10n.of(context).plugin_substack_title),
        ],
        if (post.isPaywalled) ...[
          const SizedBox(width: 6),
          _badge(context, L10n.of(context).plugin_substack_paywalled),
        ],
        const Spacer(),
        if (date != null)
          Text(createRelativeDate(date), style: theme.textTheme.bodySmall),
      ],
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

  /// The cover, with a play badge when the post is a video — otherwise a video
  /// post looked exactly like any other and the only way to find out was to
  /// open it.
  Widget _cover(BuildContext context) {
    return ClipRRect(
      borderRadius: BorderRadius.circular(kTweetMediaRadius),
      child: Stack(
        alignment: Alignment.center,
        children: [
          AspectRatio(
            aspectRatio: 16 / 9,
            child: ExtendedImage.network(post.coverImage!, fit: BoxFit.cover),
          ),
          if (post.isVideo)
            Container(
              decoration: const BoxDecoration(color: Colors.black54, shape: BoxShape.circle),
              padding: const EdgeInsets.all(12),
              child: const Icon(Icons.play_arrow, color: Colors.white, size: 32),
            ),
        ],
      ),
    );
  }
}
