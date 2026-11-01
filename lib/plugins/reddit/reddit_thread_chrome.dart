import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/plugin_counts.dart';
import 'package:xta/plugins/reddit/reddit_votes_store.dart';
import 'package:xta/tweet/tweet_chrome.dart';
import 'package:xta/ui/contrast.dart';
import 'package:xta/ui/x_look_theme.dart';

/// The thread lines by level: blue, violet, amber and green, repeating.
const kRedditThreadLineColours = <Color>[Color(0xFF3F78C4), Color(0xFF9A7FE0), Color(0xFFD9A646), Color(0xFF56B88A)];

/// Reddit's orange for an upvote.
const kRedditUpvoteColour = Color(0xFFFF8B4D);

/// How far a reply sits in per level: the column its line is centred in.
const double kRedditThreadColumn = 20;

/// The most levels drawn, so a deep reply keeps room for its words.
const int kRedditThreadMaxLines = 6;

/// How many lines a comment at [depth] draws.
int redditThreadLines(int depth) => math.min(depth, kRedditThreadMaxLines);

Color _page(BuildContext context) => Theme.of(context).scaffoldBackgroundColor;

/// The line for [level], at 3:1 against the page, so a light theme darkens it.
Color redditThreadLineColour(BuildContext context, int level) =>
    ensureContrast(kRedditThreadLineColours[level % kRedditThreadLineColours.length], _page(context), minRatio: 3);

/// [kRedditUpvoteColour], readable as text on the page.
Color redditUpvoteColour(BuildContext context) => ensureContrast(kRedditUpvoteColour, _page(context));

/// The accent, readable as text on the page.
Color redditThreadAccent(BuildContext context) => tweetReadableAccentColor(context, background: _page(context));

enum RedditChipKind { op, flair, replies }

/// A small label beside a commenter's name: OP, their flair, or how many
/// replies a folded comment holds.
class RedditThreadChip extends StatelessWidget {
  final RedditChipKind kind;
  final String label;

  const RedditThreadChip({super.key, required this.kind, required this.label});

  @override
  Widget build(BuildContext context) {
    final fill = _fill(context);
    final behind = Color.alphaBlend(fill, _page(context));
    return DecoratedBox(
      decoration: BoxDecoration(
        color: fill,
        borderRadius: BorderRadius.circular(kind == RedditChipKind.replies ? 10 : 6),
      ),
      child: Padding(
        padding: EdgeInsets.symmetric(horizontal: kind == RedditChipKind.replies ? 8 : 6, vertical: 2),
        child: Text(
          label,
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
          style: TextStyle(
            fontSize: kind == RedditChipKind.op ? 11 : 12,
            height: 1.35,
            fontWeight: kind == RedditChipKind.op ? FontWeight.w700 : FontWeight.w500,
            color: ensureContrast(_ink(context), behind),
          ),
        ),
      ),
    );
  }

  Color _fill(BuildContext context) => switch (kind) {
    RedditChipKind.op => redditThreadAccent(context).withValues(alpha: 0.16),
    RedditChipKind.flair || RedditChipKind.replies => xLookFloatingSurfaceOf(context),
  };

  Color _ink(BuildContext context) => switch (kind) {
    RedditChipKind.op => redditThreadAccent(context),
    RedditChipKind.flair => tweetPrimaryColor(context),
    RedditChipKind.replies => tweetSecondaryColor(context),
  };
}

/// A rounded touch target the thread's actions share: filled, it is a pill
/// under the post; unfilled, a quiet button in a comment.
class RedditThreadPill extends StatelessWidget {
  final Widget child;
  final VoidCallback? onTap;
  final bool filled;

  const RedditThreadPill({super.key, required this.child, required this.onTap, this.filled = true});

  @override
  Widget build(BuildContext context) {
    return Material(
      color: filled ? xLookInsetSurfaceOf(context) : Colors.transparent,
      shape: const StadiumBorder(),
      child: InkWell(
        customBorder: const StadiumBorder(),
        onTap: onTap,
        child: ConstrainedBox(
          constraints: const BoxConstraints(minWidth: kTweetTouchTarget, minHeight: kTweetTouchTarget),
          child: Padding(
            padding: EdgeInsets.symmetric(horizontal: filled ? 14 : 10),
            child: Center(widthFactor: 1, child: child),
          ),
        ),
      ),
    );
  }
}

/// Whether [id] is upvoted on this device, rebuilt only when that changes.
class RedditUpvoteScope extends StatelessWidget {
  final String id;
  final Widget Function(BuildContext context, bool upvoted, RedditVotesStore votes) builder;

  const RedditUpvoteScope({super.key, required this.id, required this.builder});

  @override
  Widget build(BuildContext context) {
    final votes = context.read<RedditVotesStore>();
    return ScopedBuilder<RedditVotesStore, Set<String>>(
      store: votes,
      distinct: (_) => votes.isUpvoted(id),
      onState: (context, _) => builder(context, votes.isUpvoted(id), votes),
    );
  }
}

/// The up arrow and the score, in Reddit orange once upvoted. One button: a
/// screen reader hears "Upvote", the score, and whether it is on.
class RedditUpvoteButton extends StatelessWidget {
  final bool upvoted;

  /// Reddit's score before this device's vote; null when Reddit hides it.
  final int? score;
  final VoidCallback onTap;

  /// A pill under the post rather than a quiet button in a comment.
  final bool filled;

  const RedditUpvoteButton({
    super.key,
    required this.upvoted,
    required this.score,
    required this.onTap,
    this.filled = false,
  });

  @override
  Widget build(BuildContext context) {
    final score = this.score;
    final idle = filled ? tweetPrimaryColor(context) : tweetSecondaryColor(context);
    final colour = upvoted ? redditUpvoteColour(context) : idle;
    return MergeSemantics(
      child: Semantics(
        button: true,
        toggled: upvoted,
        label: L10n.of(context).plugin_reddit_upvote,
        child: RedditThreadPill(
          filled: filled,
          onTap: onTap,
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(
                Icons.arrow_upward_rounded,
                size: filled ? 20 : 18,
                color: upvoted ? colour : tweetSecondaryColor(context),
              ),
              if (score != null) ...[
                const SizedBox(width: 6),
                Text(
                  compactCount(score + (upvoted ? 1 : 0)),
                  style: TextStyle(fontSize: filled ? 14 : 13, fontWeight: FontWeight.w600, color: colour),
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }
}
