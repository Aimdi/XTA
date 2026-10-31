import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/plugins/reddit/reddit_archive.dart';
import 'package:xta/plugins/reddit/reddit_votes_store.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/saved/saved_tweet_model.dart';
import 'package:xta/plugins/plugin_card_row.dart';
import 'package:xta/plugins/reddit/reddit_avatar.dart';
import 'package:xta/plugins/reddit/reddit_subreddit_avatar.dart';
import 'package:xta/plugins/reddit/reddit_client.dart';
import 'package:xta/plugins/reddit/reddit_listing_screen.dart';
import 'package:xta/plugins/reddit/reddit_post_media.dart';
import 'package:xta/plugins/reddit/reddit_post_sheet.dart';
import 'package:xta/plugins/reddit/reddit_text.dart';
import 'package:xta/plugins/reddit/reddit_thread_screen.dart';
import 'package:xta/tweet/tweet_chrome.dart';
import 'package:xta/tweet/tweet_footer.dart';
import 'package:xta/tweet/tweet_header.dart';
import 'package:xta/tweet/tweet_ink_layout.dart';
import 'package:xta/ui/dates.dart';
import 'package:xta/ui/provenance_accent.dart';
import 'package:xta/utils/urls.dart';

/// A Reddit post, built like a tweet: the subreddit's picture in a column of
/// its own, and who and when, the title and body, the media and a row of
/// actions beside it (docs/specs/feed-ink.md). In a mixed group feed it sits
/// directly between tweets, and reading it should not mean changing gear.
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

  void _openSubreddit(BuildContext context) {
    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (_) => RedditListingScreen.subreddit(post.subreddit),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final date = post.createdAt;
    final subreddit = 'r/${post.subreddit}';
    return RepaintBoundary(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          TweetInkLayout(
            // The picture and the name are one target: both mean "this
            // community", and a tap on either goes straight there.
            avatar: TweetAvatarTarget(
              avatar: ProvenanceAvatarBadge(
                pluginId: pluginIdReddit,
                show: showSourceBadge,
                child: RedditSubredditAvatar(
                  subreddit: post.subreddit,
                  size: kTweetAvatarSize,
                ),
              ),
              size: kTweetAvatarSize,
              label: [
                subreddit,
                if (showSourceBadge) L10n.of(context).plugin_reddit_title,
              ].join(', '),
              shape: RoundedRectangleBorder(
                borderRadius: redditAvatarBorder(kTweetTouchTarget),
              ),
              onTap: () => _openSubreddit(context),
            ),
            authorLine: TweetAuthorLine(
              displayName: subreddit,
              // A deleted account has no name to show; the line simply ends
              // with the time rather than announcing the absence.
              handle: post.author,
              handlePrefix: 'u/',
              verified: false,
              timestamp: date == null
                  ? null
                  : Timestamp(timestamp: date, compact: true),
              onTapName: () => _openSubreddit(context),
              onTapHandle: () => openRedditPostSheet(context, post),
            ),
            body: [
              InkWell(
                onTap: () => _open(context),
                // Everywhere else the post can lead. A long press is where
                // Android readers look for it, and the author offers it
                // outright.
                onLongPress: () => openRedditPostSheet(context, post),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    if (post.showsTitle) _title(context),
                    // On a discussion subreddit the body is most of the post;
                    // a card that showed only the title said almost nothing.
                    if (post.isSelf && post.showsSelfText)
                      Padding(
                        padding: const EdgeInsets.fromLTRB(16, 6, 16, 0),
                        child: RedditRichText(
                          text: post.displaySelfText!,
                          maxLines: 4,
                          style: Theme.of(context).textTheme.bodyMedium!
                              .copyWith(
                                color: Theme.of(
                                  context,
                                ).colorScheme.onSurfaceVariant,
                              ),
                        ),
                      ),
                    _RedditLabels(post: post),
                  ],
                ),
              ),
              // Outside the InkWell so a tap opens the picture, not the thread.
              RedditPostMedia(post: post),
              _RedditPostFooter(post: post, onComments: () => _open(context)),
            ],
          ),
          tweetHairlineDivider(context),
        ],
      ),
    );
  }

  Widget _title(BuildContext context) {
    return Padding(
      padding: const EdgeInsetsDirectional.fromSTEB(
        kTweetHorizontalPadding,
        kInkTextGap,
        kTweetHorizontalPadding,
        0,
      ),
      child: Text(
        post.displayTitle,
        maxLines: 6,
        overflow: TextOverflow.ellipsis,
        style: tweetBodyStyle(context),
      ),
    );
  }
}

/// Flair, NSFW and spoiler under the text, where they are read before the
/// picture they qualify.
class _RedditLabels extends StatelessWidget {
  final RedditPost post;

  const _RedditLabels({required this.post});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final l10n = L10n.of(context);
    final labels = [
      if (post.flair != null) _RedditFlair(label: post.flair!),
      if (post.over18)
        PluginCardBadge(
          label: l10n.plugin_reddit_nsfw,
          tint: theme.colorScheme.error,
        ),
      if (post.spoiler)
        PluginCardBadge(
          label: l10n.plugin_reddit_spoiler,
          tint: theme.colorScheme.tertiary,
        ),
    ];
    if (labels.isEmpty) return const SizedBox.shrink();
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 8, 16, 0),
      child: Wrap(
        spacing: kPluginMetaGap,
        runSpacing: 4,
        crossAxisAlignment: WrapCrossAlignment.center,
        children: labels,
      ),
    );
  }
}

/// Score, comments, save and a way out to the browser, spread across the
/// column as the canvas draws them, at the footer's tap size.
class _RedditPostFooter extends StatelessWidget {
  final RedditPost post;
  final VoidCallback onComments;

  const _RedditPostFooter({required this.post, required this.onComments});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final muted = theme.colorScheme.onSurfaceVariant;

    // No end margin: the last glyph, centred in its target, lines up with an
    // X post's share glyph. With large text on a narrow phone the actions
    // take a second row rather than shrink their targets.
    return Padding(
      padding: const EdgeInsetsDirectional.only(start: kTweetSpace2, top: 4),
      child: Wrap(
        alignment: WrapAlignment.spaceBetween,
        crossAxisAlignment: WrapCrossAlignment.center,
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
          _RedditBookmarkButton(post: post),
          tweetFooterIconButton(
            context,
            Icons.open_in_new,
            muted,
            null,
            () => openUri(context, redditPostUrl(post)),
            L10n.of(context).open_in_browser,
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
      distinct: (_) => votes.isUpvoted(post.id),
      onState: (context, state) {
        final upvoted = state.contains(post.id);
        final color = upvoted ? theme.colorScheme.primary : muted;

        return TextButton.icon(
          style: footerButtonStyle,
          onPressed: () async {
            await votes.toggle(post.id);
            if (!context.mounted) {
              return;
            }
            await syncRedditLikeToArchive(
              context,
              post,
              upvoted: votes.isUpvoted(post.id),
            );
          },
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

class _RedditBookmarkButton extends StatelessWidget {
  final RedditPost post;

  const _RedditBookmarkButton({required this.post});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final muted = theme.colorScheme.onSurfaceVariant;
    SavedTweetModel saved;
    try {
      saved = context.read<SavedTweetModel>();
    } on ProviderNotFoundException {
      return const SizedBox.shrink();
    }

    return ScopedBuilder<SavedTweetModel, List<SavedTweet>>(
      store: saved,
      distinct: (_) => saved.isSaved(redditArchiveId(post.id)),
      onState: (context, _) {
        final isSaved = saved.isSaved(redditArchiveId(post.id));
        final button = tweetFooterIconButton(
          context,
          isSaved ? Icons.bookmark : Icons.bookmark_border,
          isSaved ? theme.colorScheme.primary : muted,
          isSaved ? 1 : 0,
          () async {
            if (isSaved) {
              await unfileRedditPost(context, post);
            } else {
              await fileRedditPost(context, post);
              if (context.mounted) {
                maybeShowFolderHint(context);
              }
            }
          },
          isSaved
              ? L10n.of(context).unsave_from_this_device
              : L10n.of(context).save_on_this_device,
        );
        return GestureDetector(
          onLongPress: () => pickRedditPostFolder(context, post),
          child: button,
        );
      },
    );
  }
}
