import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:quax/generated/l10n.dart';
import 'package:quax/plugins/reddit/reddit_avatar.dart';
import 'package:quax/plugins/reddit/reddit_subreddit_avatar.dart';
import 'package:quax/plugins/reddit/reddit_client.dart';
import 'package:quax/plugins/reddit/reddit_comments.dart';
import 'package:quax/plugins/reddit/reddit_listing_screen.dart';
import 'package:quax/plugins/reddit/reddit_post_media.dart';
import 'package:quax/plugins/reddit/reddit_screen.dart' show redditErrorMessage;
import 'package:quax/ui/dates.dart';
import 'package:quax/ui/errors.dart';

/// How far each level of replies is indented, and how deep that goes.
///
/// Reddit threads nest without limit; a phone cannot. Past this depth replies
/// keep their thread line but stop moving right, so a deep argument stays
/// readable instead of collapsing into a column one word wide.
const double kRedditIndentPerLevel = 12;
const int kRedditMaxIndentDepth = 8;

/// A post and its comments.
class RedditThreadScreen extends StatefulWidget {
  final RedditPost post;

  const RedditThreadScreen({super.key, required this.post});

  @override
  State<RedditThreadScreen> createState() => _RedditThreadScreenState();
}

class _RedditThreadScreenState extends State<RedditThreadScreen> {
  List<FlatComment>? _comments;
  String? _selfText;
  Object? _error;

  @override
  void initState() {
    super.initState();
    _selfText = widget.post.selfText;
    _load();
  }

  Future<void> _load() async {
    setState(() => _error = null);
    try {
      final result = await context.read<RedditClient>().fetchComments(widget.post.permalink);
      if (!mounted) return;
      setState(() {
        _comments = flattenComments(result.comments);
        _selfText ??= result.selfText;
      });
    } catch (e) {
      if (mounted) {
        setState(() => _error = e);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final comments = _comments;

    return Scaffold(
      appBar: AppBar(title: Text('r/${widget.post.subreddit}')),
      body: RefreshIndicator(
        onRefresh: _load,
        child: ListView.builder(
          // One header plus the flattened tree: nesting the widgets instead
          // would build every reply of every collapsed branch up front.
          itemCount: 1 + (comments?.length ?? 1),
          itemBuilder: (context, index) {
            if (index == 0) {
              return _header(context);
            }
            if (comments == null) {
              return _pending(context, l10n);
            }
            return _commentRow(context, comments[index - 1]);
          },
        ),
      ),
    );
  }

  Widget _pending(BuildContext context, L10n l10n) {
    final error = _error;
    if (error != null) {
      return Padding(
        padding: const EdgeInsets.all(24),
        child: FullPageErrorWidget(
          error: error,
          stackTrace: null,
          prefix: redditErrorMessage(l10n, error),
          onRetry: _load,
        ),
      );
    }

    return const Padding(
      padding: EdgeInsets.symmetric(vertical: 48),
      child: Center(child: CircularProgressIndicator()),
    );
  }

  Widget _header(BuildContext context) {
    final theme = Theme.of(context);
    final post = widget.post;
    final date = post.createdAt;

    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 8),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(post.title, style: theme.textTheme.titleLarge!.copyWith(fontWeight: FontWeight.w700)),
          const SizedBox(height: 6),
          DefaultTextStyle.merge(
            style: theme.textTheme.bodySmall!.copyWith(color: theme.colorScheme.onSurfaceVariant),
            child: Row(
              children: [
                RedditSubredditAvatar(subreddit: post.subreddit, size: 22),
                const SizedBox(width: 6),
                if (post.author != null) Text('u/${post.author}'),
                if (date != null) ...[
                  const SizedBox(width: 8),
                  Text(createRelativeDate(date)),
                ],
                const Spacer(),
                Text('${post.score} · ${post.commentCount}'),
              ],
            ),
          ),
          // The same block the feed card uses, so a picture post opens on its
          // picture rather than on a link to one.
          RedditPostMedia(post: post, padding: const EdgeInsets.only(top: 10)),
          if (_selfText != null) ...[
            const SizedBox(height: 10),
            Text(_selfText!, style: theme.textTheme.bodyMedium),
          ],
          const Divider(height: 24),
        ],
      ),
    );
  }

  Widget _commentRow(BuildContext context, FlatComment entry) {
    final theme = Theme.of(context);
    final comment = entry.comment;
    final depth = entry.depth;
    final indent = kRedditIndentPerLevel * (depth > kRedditMaxIndentDepth ? kRedditMaxIndentDepth : depth);

    return Padding(
      padding: EdgeInsets.fromLTRB(16 + indent, 6, 16, 6),
      child: Container(
        decoration: depth == 0
            ? null
            : BoxDecoration(
                border: Border(left: BorderSide(color: theme.colorScheme.outlineVariant, width: 2)),
              ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            DefaultTextStyle.merge(
              style: theme.textTheme.bodySmall!.copyWith(color: theme.colorScheme.onSurfaceVariant),
              child: Row(
                children: [
                  RedditAvatar(name: comment.author, size: 20),
                  const SizedBox(width: 6),
                  Flexible(
                    child: GestureDetector(
                      // A name in a thread is a way to the rest of what they
                      // posted, the same as it is on the card.
                      onTap: comment.author == null
                          ? null
                          : () => Navigator.push(context,
                              MaterialPageRoute(builder: (_) => RedditListingScreen.user(comment.author!))),
                      child: Text(
                        comment.author == null ? '' : 'u/${comment.author}',
                        overflow: TextOverflow.ellipsis,
                        style: TextStyle(
                          fontWeight: FontWeight.w600,
                          color: comment.isSubmitter ? theme.colorScheme.primary : null,
                        ),
                      ),
                    ),
                  ),
                  if (comment.score != null) ...[
                    const SizedBox(width: 8),
                    Text('${comment.score}'),
                  ],
                  if (comment.createdAt != null) ...[
                    const SizedBox(width: 8),
                    Text(createRelativeDate(comment.createdAt!)),
                  ],
                ],
              ),
            ),
            const SizedBox(height: 2),
            if (comment.body.isNotEmpty) Text(comment.body, style: theme.textTheme.bodyMedium),
            RedditCommentImages(urls: comment.mediaUrls),
          ],
        ),
      ),
    );
  }
}
