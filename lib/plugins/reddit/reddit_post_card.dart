import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/plugins/reddit/reddit_votes_store.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/reddit/reddit_subreddit_avatar.dart';
import 'package:xta/plugins/reddit/reddit_client.dart';
import 'package:xta/plugins/reddit/reddit_listing_screen.dart';
import 'package:xta/plugins/reddit/reddit_post_media.dart';
import 'package:xta/plugins/reddit/reddit_post_sheet.dart';
import 'package:xta/plugins/reddit/reddit_thread_screen.dart';
import 'package:xta/tweet/tweet_chrome.dart';
import 'package:xta/tweet/tweet_footer.dart';
import 'package:xta/ui/dates.dart';
import 'package:xta/utils/urls.dart';

/// The avatar a Reddit post gets, matching the one a tweet gets.
const double kRedditAvatarSize = 48;

/// A Reddit post, built like a tweet.
///
/// Avatar, then who and when, then the text, then the media edge to edge, then
/// a row of actions — the same shape and the same tap targets as everything
/// else in the timeline, because in a mixed group feed it sits directly between
/// tweets and reading it should not mean changing gear.
class RedditPostCard extends StatelessWidget {
  final RedditPost post;

  /// Set in a mixed timeline so the card says where it came from.
  final bool showSourceBadge;

  const RedditPostCard({
    super.key,
    required this.post,
    this.showSourceBadge = true,
  });

  void _open(BuildContext context) {
    Navigator.push(
      context,
      MaterialPageRoute(builder: (_) => RedditThreadScreen(post: post)),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        InkWell(
          onTap: () => _open(context),
          // Everywhere else the post can lead. A long press is where Android
          // readers look for it, and the author line offers it outright.
          onLongPress: () => openRedditPostSheet(context, post),
          child: Padding(
            padding: const EdgeInsets.only(top: 12),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                _RedditPostHeader(post: post, showSourceBadge: showSourceBadge),
                if (post.showsTitle) _title(context),
                if (post.flair != null) _RedditFlair(label: post.flair!),
                // On a discussion subreddit the body is most of the post; a
                // card that showed only the title said almost nothing.
                if (post.isSelf && (post.selfText?.isNotEmpty ?? false))
                  Padding(
                    padding: const EdgeInsets.fromLTRB(16, 6, 16, 0),
                    child: Text(
                      post.selfText!,
                      maxLines: 4,
                      overflow: TextOverflow.ellipsis,
                      style: Theme.of(context).textTheme.bodyMedium!.copyWith(
                        color: Theme.of(context).colorScheme.onSurfaceVariant,
                      ),
                    ),
                  ),
                RedditPostMedia(post: post),
                _RedditPostFooter(post: post, onComments: () => _open(context)),
              ],
            ),
          ),
        ),
        tweetHairlineDivider(context),
      ],
    );
  }

  Widget _title(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 8, 16, 0),
      child: Text(
        post.title,
        maxLines: 6,
        overflow: TextOverflow.ellipsis,
        style: Theme.of(context).textTheme.titleMedium!.copyWith(
          fontWeight: FontWeight.w700,
          height: 1.25,
        ),
      ),
    );
  }
}

class _RedditPostHeader extends StatelessWidget {
  final RedditPost post;
  final bool showSourceBadge;

  const _RedditPostHeader({required this.post, required this.showSourceBadge});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final date = post.createdAt;

    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // The picture and the name are one target: both mean "this community",
          // and a tap on either goes straight there rather than into a menu.
          GestureDetector(
            onTap: () => _openSubreddit(context),
            child: RedditSubredditAvatar(
              subreddit: post.subreddit,
              size: kRedditAvatarSize,
            ),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Flexible(
                      child: GestureDetector(
                        onTap: () => _openSubreddit(context),
                        child: Text(
                          'r/${post.subreddit}',
                          overflow: TextOverflow.ellipsis,
                          style: theme.textTheme.titleSmall!.copyWith(
                            fontWeight: FontWeight.w800,
                          ),
                        ),
                      ),
                    ),
                    if (date != null) ...[
                      const SizedBox(width: 6),
                      Text(
                        '· ${createRelativeDate(date)}',
                        style: theme.textTheme.bodySmall,
                      ),
                    ],
                  ],
                ),
                _subtitle(context),
              ],
            ),
          ),
        ],
      ),
    );
  }

  void _openSubreddit(BuildContext context) {
    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (_) => RedditListingScreen.subreddit(post.subreddit),
      ),
    );
  }

  Widget _subtitle(BuildContext context) {
    final theme = Theme.of(context);
    final author = post.author;

    return DefaultTextStyle.merge(
      style: theme.textTheme.bodySmall!.copyWith(
        color: theme.colorScheme.onSurfaceVariant,
      ),
      child: Row(
        children: [
          // A deleted account has no name to show; the row simply starts with
          // the badge rather than announcing the absence.
          if (author != null)
            Flexible(
              child: GestureDetector(
                onTap: () => openRedditPostSheet(context, post),
                child: Text('u/$author', overflow: TextOverflow.ellipsis),
              ),
            ),
          if (showSourceBadge) ...[
            if (author != null) const SizedBox(width: 6),
            _RedditBadge(label: L10n.of(context).plugin_reddit_title),
          ],
          if (post.over18) ...[
            const SizedBox(width: 6),
            _RedditBadge(
              label: L10n.of(context).plugin_reddit_nsfw,
              tint: theme.colorScheme.error,
            ),
          ],
          if (post.spoiler) ...[
            const SizedBox(width: 6),
            _RedditBadge(
              label: L10n.of(context).plugin_reddit_spoiler,
              tint: theme.colorScheme.tertiary,
            ),
          ],
        ],
      ),
    );
  }
}

/// Score, comments and a way out to the browser, at the footer's tap size.
class _RedditPostFooter extends StatelessWidget {
  final RedditPost post;
  final VoidCallback onComments;

  const _RedditPostFooter({required this.post, required this.onComments});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final muted = theme.colorScheme.onSurfaceVariant;

    return Padding(
      padding: const EdgeInsets.only(left: 8, right: 8, top: 4),
      child: Row(
        children: [
          _UpvoteButton(post: post),
          TextButton.icon(
            style: footerButtonStyle,
            onPressed: onComments,
            icon: Icon(Icons.mode_comment_outlined, size: 18, color: muted),
            label: Text(
              '${post.commentCount}',
              style: theme.textTheme.bodySmall!.copyWith(color: muted),
            ),
          ),
          const Spacer(),
          IconButton(
            tooltip: L10n.of(context).open_in_browser,
            onPressed: () => openUri(context, redditPostUrl(post)),
            icon: Icon(Icons.open_in_new, size: 18, color: muted),
          ),
        ],
      ),
    );
  }
}

/// The upvote, kept on the device the way X likes are: Reddit is never told,
/// the arrow remembers. The shown score includes the reader's own vote, which
/// is what the number would be if the vote had been cast.
class _UpvoteButton extends StatelessWidget {
  final RedditPost post;

  const _UpvoteButton({required this.post});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final muted = theme.colorScheme.onSurfaceVariant;
    final votes = context.read<RedditVotesStore>();

    return ScopedBuilder<RedditVotesStore, Set<String>>(
      store: votes,
      onState: (context, state) {
        final upvoted = state.contains(post.id);
        final color = upvoted ? theme.colorScheme.primary : muted;

        return TextButton.icon(
          style: footerButtonStyle,
          onPressed: () => votes.toggle(post.id),
          icon: Icon(
            upvoted ? Icons.arrow_circle_up : Icons.arrow_upward,
            size: 18,
            color: color,
          ),
          label: Text(
            '${post.score + (upvoted ? 1 : 0)}',
            style: theme.textTheme.bodySmall!.copyWith(
              color: color,
              fontWeight: upvoted ? FontWeight.w700 : null,
            ),
          ),
        );
      },
    );
  }
}

class _RedditFlair extends StatelessWidget {
  final String label;

  const _RedditFlair({required this.label});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 8, 16, 0),
      child: Align(
        alignment: Alignment.centerLeft,
        child: Container(
          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
          decoration: BoxDecoration(
            color: theme.colorScheme.secondaryContainer,
            borderRadius: BorderRadius.circular(6),
          ),
          child: Text(
            label,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: theme.textTheme.labelMedium!.copyWith(
              color: theme.colorScheme.onSecondaryContainer,
            ),
          ),
        ),
      ),
    );
  }
}

class _RedditBadge extends StatelessWidget {
  final String label;
  final Color? tint;

  const _RedditBadge({required this.label, this.tint});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 5, vertical: 1),
      decoration: BoxDecoration(
        border: Border.all(color: tint ?? theme.colorScheme.outline),
        borderRadius: BorderRadius.circular(4),
      ),
      child: Text(
        label,
        style: theme.textTheme.labelSmall!.copyWith(color: tint),
      ),
    );
  }
}
