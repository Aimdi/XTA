import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:share_plus/share_plus.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/reddit/reddit_avatar.dart';
import 'package:xta/plugins/reddit/reddit_comments.dart';
import 'package:xta/plugins/reddit/reddit_listing_screen.dart';
import 'package:xta/plugins/reddit/reddit_post_media.dart';
import 'package:xta/plugins/reddit/reddit_text.dart';
import 'package:xta/plugins/reddit/reddit_thread_chrome.dart';
import 'package:xta/plugins/reddit/reddit_thread_post.dart';
import 'package:xta/plugins/reddit/reddit_votes_store.dart';
import 'package:xta/tweet/tweet_chrome.dart';
import 'package:xta/ui/dates.dart';
import 'package:xta/utils/urls.dart';

/// What happened to a comment whose words are gone, in the reader's language.
String redditRemovedReason(L10n l10n, RedditComment comment) => switch (comment.body.trim().toLowerCase()) {
  '[removed]' => l10n.plugin_reddit_comment_removed,
  '[ removed by reddit ]' => l10n.plugin_reddit_comment_removed_by_reddit,
  '[unavailable]' => l10n.plugin_reddit_comment_unavailable,
  _ => l10n.plugin_reddit_comment_deleted,
};

/// A comment's page on Reddit, or null when Reddit did not name one.
String? redditCommentUrl(RedditComment comment) => switch (comment.permalink) {
  null => null,
  final link when link.startsWith('http') => link,
  final link => 'https://www.reddit.com$link',
};

/// The lines of the levels above a reply, one 20 dp column each, with [child]
/// starting after them.
class RedditThreadLines extends StatelessWidget {
  final int depth;
  final Widget child;

  const RedditThreadLines({super.key, required this.depth, required this.child});

  @override
  Widget build(BuildContext context) {
    final lines = redditThreadLines(depth);
    return CustomPaint(
      painter: _ThreadLinesPainter(
        colours: [for (var level = 0; level < lines; level++) redditThreadLineColour(context, level)],
        direction: Directionality.of(context),
      ),
      child: Padding(
        padding: EdgeInsetsDirectional.only(
          start: kTweetHorizontalPadding + kRedditThreadColumn * lines,
          end: kTweetSpace2,
        ),
        child: child,
      ),
    );
  }
}

class _ThreadLinesPainter extends CustomPainter {
  final List<Color> colours;
  final TextDirection direction;

  _ThreadLinesPainter({required this.colours, required this.direction});

  @override
  void paint(Canvas canvas, Size size) {
    for (final (level, colour) in colours.indexed) {
      final centre = kTweetHorizontalPadding + kRedditThreadColumn * level + kRedditThreadColumn / 2;
      final x = direction == TextDirection.rtl ? size.width - centre : centre;
      canvas.drawRRect(
        RRect.fromRectAndRadius(Rect.fromLTWH(x - 1, 0, 2, size.height), const Radius.circular(1)),
        Paint()..color = colour,
      );
    }
  }

  @override
  bool shouldRepaint(_ThreadLinesPainter old) => !listEquals(old.colours, colours) || old.direction != direction;
}

/// A comment in its thread: its header, and unless it is folded its words,
/// pictures and upvote. A top-level comment after the first has a hairline
/// above it.
class RedditCommentRow extends StatelessWidget {
  final RedditComment comment;
  final int depth;
  final bool folded;
  final bool divided;

  /// Written since the reader last opened the thread.
  final bool isNew;
  final VoidCallback onToggle;

  const RedditCommentRow({
    super.key,
    required this.comment,
    required this.depth,
    required this.folded,
    required this.onToggle,
    this.divided = false,
    this.isNew = false,
  });

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        if (divided)
          const Padding(
            padding: EdgeInsets.fromLTRB(kTweetHorizontalPadding, 6, kTweetHorizontalPadding, 0),
            child: Divider(height: 1, thickness: 1),
          ),
        RedditThreadLines(
          depth: depth,
          child: Padding(
            padding: EdgeInsets.only(top: depth == 0 ? kTweetSpace2 : 0),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                _CommentHeader(comment: comment, folded: folded, isNew: isNew, onToggle: onToggle),
                if (!folded) ..._content(context),
              ],
            ),
          ),
        ),
      ],
    );
  }

  List<Widget> _content(BuildContext context) {
    if (comment.isRemoved) {
      return [
        Padding(
          padding: const EdgeInsetsDirectional.only(start: 28, bottom: kTweetSpace3),
          child: Text(
            redditRemovedReason(L10n.of(context), comment),
            style: TextStyle(
              fontSize: 14,
              height: 20 / 14,
              fontStyle: FontStyle.italic,
              color: tweetSecondaryColor(context),
            ),
          ),
        ),
      ];
    }
    return [
      if (comment.body.isNotEmpty) RedditRichText(text: comment.body, style: redditThreadBodyStyle(context)),
      RedditCommentImages(urls: comment.mediaUrls),
      Align(
        alignment: AlignmentDirectional.centerEnd,
        child: _CommentUpvote(comment: comment),
      ),
    ];
  }
}

/// The row that folds and unfolds a comment: avatar, name, OP, flair, age or
/// "new", and when folded how many replies it holds; its menu at the end.
class _CommentHeader extends StatelessWidget {
  final RedditComment comment;
  final bool folded;
  final bool isNew;
  final VoidCallback onToggle;

  const _CommentHeader({required this.comment, required this.folded, required this.isNew, required this.onToggle});

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Expanded(
          child: MergeSemantics(
            child: Semantics(
              button: true,
              expanded: !folded,
              child: InkWell(
                onTap: onToggle,
                child: ConstrainedBox(
                  constraints: const BoxConstraints(minHeight: kTweetTouchTarget),
                  child: Align(
                    alignment: AlignmentDirectional.centerStart,
                    child: Padding(
                      padding: const EdgeInsets.symmetric(vertical: kTweetSpace1),
                      child: Wrap(
                        spacing: kTweetSpace2,
                        runSpacing: 2,
                        crossAxisAlignment: WrapCrossAlignment.center,
                        children: _parts(context),
                      ),
                    ),
                  ),
                ),
              ),
            ),
          ),
        ),
        if (!comment.isRemoved) _CommentMenu(comment: comment),
      ],
    );
  }

  List<Widget> _parts(BuildContext context) {
    final l10n = L10n.of(context);
    final flair = comment.authorFlair;
    final created = comment.createdAt;
    final replies = comment.replyCount;
    return [
      _Name(comment: comment, muted: folded || comment.isRemoved),
      if (comment.isSubmitter) RedditThreadChip(kind: RedditChipKind.op, label: l10n.plugin_reddit_op),
      if (flair != null && !comment.isRemoved) RedditThreadChip(kind: RedditChipKind.flair, label: flair),
      if (created != null) _Age(created: created, isNew: isNew),
      if (folded && replies > 0)
        RedditThreadChip(kind: RedditChipKind.replies, label: l10n.plugin_reddit_folded_replies(replies)),
    ];
  }
}

class _Name extends StatelessWidget {
  final RedditComment comment;
  final bool muted;

  const _Name({required this.comment, required this.muted});

  @override
  Widget build(BuildContext context) {
    final author = comment.author;
    final colour = comment.isSubmitter
        ? redditThreadAccent(context)
        : (muted ? tweetSecondaryColor(context) : tweetPrimaryColor(context));
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        RedditAvatar(name: comment.hasAuthor ? author : null, size: 20, round: comment.isSubmitter),
        const SizedBox(width: kTweetSpace2),
        Flexible(
          child: Text(
            author == null ? '' : (comment.hasAuthor ? 'u/$author' : author),
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: TextStyle(fontSize: 13.5, height: 1.3, fontWeight: FontWeight.w600, color: colour),
          ),
        ),
      ],
    );
  }
}

class _Age extends StatelessWidget {
  final DateTime created;
  final bool isNew;

  const _Age({required this.created, required this.isNew});

  @override
  Widget build(BuildContext context) {
    final age = createCompactDate(created);
    if (!isNew) {
      return Text(age, style: TextStyle(fontSize: 13, height: 1.3, color: tweetSecondaryColor(context)));
    }
    final accent = redditThreadAccent(context);
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Container(
          width: 7,
          height: 7,
          decoration: BoxDecoration(color: accent, shape: BoxShape.circle),
        ),
        const SizedBox(width: 6),
        Flexible(
          child: Text(
            L10n.of(context).plugin_reddit_comment_new(age),
            style: TextStyle(fontSize: 13, height: 1.3, fontWeight: FontWeight.w500, color: accent),
          ),
        ),
      ],
    );
  }
}

class _CommentUpvote extends StatelessWidget {
  final RedditComment comment;

  const _CommentUpvote({required this.comment});

  @override
  Widget build(BuildContext context) {
    final id = redditCommentVoteId(comment.id);
    return RedditUpvoteScope(
      id: id,
      builder: (context, upvoted, votes) =>
          RedditUpvoteButton(upvoted: upvoted, score: comment.score, onTap: () => votes.toggle(id)),
    );
  }
}

enum _CommentAction { author, copy, share, open }

/// A comment's ⋮: its author's posts, copy its words, share or open its link.
class _CommentMenu extends StatelessWidget {
  final RedditComment comment;

  const _CommentMenu({required this.comment});

  @override
  Widget build(BuildContext context) {
    final items = _items(context);
    if (items.isEmpty) {
      return const SizedBox.shrink();
    }
    return PopupMenuButton<_CommentAction>(
      tooltip: MaterialLocalizations.of(context).moreButtonTooltip,
      icon: Icon(Icons.more_horiz, size: 20, color: tweetSecondaryColor(context)),
      onSelected: (action) => _run(context, action),
      itemBuilder: (context) => [
        for (final (action, icon, label) in items)
          PopupMenuItem(
            value: action,
            child: ListTile(leading: Icon(icon), title: Text(label), contentPadding: EdgeInsets.zero),
          ),
      ],
    );
  }

  List<(_CommentAction, IconData, String)> _items(BuildContext context) {
    final l10n = L10n.of(context);
    final linked = redditCommentUrl(comment) != null;
    return [
      if (comment.hasAuthor) (_CommentAction.author, Icons.person_outline, 'u/${comment.author}'),
      if (comment.body.isNotEmpty) (_CommentAction.copy, Icons.copy, MaterialLocalizations.of(context).copyButtonLabel),
      if (linked) (_CommentAction.share, Icons.share_outlined, l10n.share_link),
      if (linked) (_CommentAction.open, Icons.open_in_new, l10n.open_in_browser),
    ];
  }

  void _run(BuildContext context, _CommentAction action) {
    final url = redditCommentUrl(comment);
    switch (action) {
      case _CommentAction.author:
        Navigator.push(context, MaterialPageRoute(builder: (_) => RedditListingScreen.user(comment.author!)));
      case _CommentAction.copy:
        Clipboard.setData(ClipboardData(text: comment.body));
      case _CommentAction.share:
        SharePlus.instance.share(ShareParams(text: url));
      case _CommentAction.open:
        openUri(context, url!);
    }
  }
}

/// Where Reddit held comments back, at the depth they belong to: accent text
/// that loads them here or continues the thread on its own page.
class RedditHeldBackRow extends StatelessWidget {
  final int depth;
  final String label;

  /// After the label: where a tap leads, when it leaves this thread.
  final IconData? trailing;
  final bool loading;
  final VoidCallback? onTap;

  const RedditHeldBackRow({
    super.key,
    required this.depth,
    required this.label,
    required this.onTap,
    this.trailing,
    this.loading = false,
  });

  @override
  Widget build(BuildContext context) {
    final accent = redditThreadAccent(context);
    final trailing = this.trailing;
    return RedditThreadLines(
      depth: depth,
      child: Align(
        alignment: AlignmentDirectional.centerStart,
        child: TextButton(
          onPressed: loading ? null : onTap,
          style: TextButton.styleFrom(
            foregroundColor: accent,
            minimumSize: const Size(kTweetTouchTarget, kTweetTouchTarget),
            padding: const EdgeInsets.symmetric(horizontal: kTweetSpace2),
            textStyle: Theme.of(context).textTheme.labelLarge!.copyWith(fontSize: 13.5, fontWeight: FontWeight.w600),
          ),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              if (loading) ...[
                SizedBox.square(dimension: 16, child: CircularProgressIndicator(strokeWidth: 2, color: accent)),
                const SizedBox(width: kTweetSpace2),
              ],
              Flexible(child: Text(label)),
              if (trailing != null && !loading) ...[const SizedBox(width: 4), Icon(trailing, size: 16)],
            ],
          ),
        ),
      ),
    );
  }
}
