import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/plugin_comment_bubble.dart';
import 'package:xta/plugins/reddit/reddit_archive.dart';
import 'package:xta/plugins/reddit/reddit_avatar.dart';
import 'package:xta/plugins/reddit/reddit_subreddit_avatar.dart';
import 'package:xta/plugins/reddit/reddit_client.dart';
import 'package:xta/plugins/reddit/reddit_comments.dart';
import 'package:xta/plugins/reddit/reddit_media_urls.dart';
import 'package:xta/plugins/reddit/reddit_listing_screen.dart';
import 'package:xta/plugins/reddit/reddit_post_media.dart';
import 'package:xta/plugins/reddit/reddit_post_sheet.dart' show redditPostUrl;
import 'package:xta/plugins/reddit/reddit_read_session.dart';
import 'package:xta/plugins/reddit/reddit_screen.dart' show redditErrorMessage;
import 'package:xta/plugins/reddit/reddit_store.dart';
import 'package:xta/plugins/reddit/reddit_votes_store.dart';
import 'package:xta/saved/saved_tweet_model.dart';
import 'package:xta/plugins/reddit/reddit_text.dart';
import 'package:xta/tweet/tweet_footer.dart';
import 'package:xta/ui/dates.dart';
import 'package:xta/utils/urls.dart';
import 'package:xta/ui/errors.dart';
import 'package:xta/ui/feed_list.dart';

/// A post and its comments.
class RedditThreadScreen extends StatefulWidget {
  final RedditPost post;

  const RedditThreadScreen({super.key, required this.post});

  @override
  State<RedditThreadScreen> createState() => _RedditThreadScreenState();
}

class _RedditThreadScreenState extends State<RedditThreadScreen> {
  late RedditPost _post = widget.post;
  List<FlatComment>? _comments;
  String? _selfText;
  Object? _error;

  /// Reddit's comment orders; null is the site's default (best).
  String? _sort;
  final _collapsed = <String>{};

  @override
  void initState() {
    super.initState();
    _selfText = widget.post.selfText;
    _load();
  }

  Future<void> _load() async {
    setState(() => _error = null);
    try {
      final client = context.read<RedditClient>();
      final prefs = PrefService.of(context, listen: false);
      final session = await RedditReadSession.resolve(prefs: prefs);
      final result = await session.fetchComments(
        client,
        _post.permalink,
        sort: _sort,
      );
      if (!mounted) return;
      setState(() {
        _comments = flattenComments(result.comments);
        _selfText ??= result.selfText;
        _adoptPageMedia(result.postUrl, result.postImages);
      });
    } catch (e) {
      if (mounted) {
        setState(() => _error = e);
      }
    }
  }

  /// Fills in what the listing never carried.
  ///
  /// A post that arrived through search names no link and no media — the search
  /// page simply does not have them — so its own page is where they come from.
  /// A link that is just the post's permalink says nothing and is not adopted;
  /// everything else is, which is what turns "the post contained a file but it
  /// isn't here" into the file being here.
  void _adoptPageMedia(String? url, List<String> images) {
    if (_post.imageUrl != null) {
      return;
    }

    final external = url != null && !_isOwnPermalink(url);
    final gallery = collapseRedditImageUrls(images);
    if (!external && gallery.isEmpty) {
      return;
    }

    // data-url already is the picture — expando imgs are preview variants of
    // that same file, not a gallery. Real galleries use reddit.com/gallery/…
    // which does not resolve as an image URL.
    final urlIsImage = external && redditImageUrl(url) != null;

    _post = _post.copyWith(
      url: external ? url : _post.url,
      isSelf: external ? false : _post.isSelf,
      galleryImages: urlIsImage || gallery.isEmpty ? null : gallery,
    );
  }

  static String _trimSlash(String path) =>
      path.endsWith('/') ? path.substring(0, path.length - 1) : path;

  bool _isOwnPermalink(String url) {
    final uri = Uri.tryParse(url);
    return uri != null &&
        uri.host.endsWith('reddit.com') &&
        uri.path.contains('/comments/');
  }

  /// Folding every top-level argument turns a thousand-comment page into the
  /// list of discussions it is made of; unfolding restores the reading flow.
  bool get _allFolded {
    final comments = _comments;
    if (comments == null) return false;
    final foldable = foldableTopLevelIds(comments);
    return foldable.isNotEmpty && _collapsed.containsAll(foldable);
  }

  void _toggleFoldAll() {
    final comments = _comments;
    if (comments == null) return;
    final foldable = foldableTopLevelIds(comments);
    setState(() {
      if (_collapsed.containsAll(foldable)) {
        _collapsed.removeAll(foldable);
      } else {
        _collapsed.addAll(foldable);
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final comments = _comments;

    return Scaffold(
      appBar: AppBar(
        title: Text('r/${widget.post.subreddit}'),
        actions: [
          if (comments != null && foldableTopLevelIds(comments).isNotEmpty)
            IconButton(
              icon: Icon(_allFolded ? Icons.unfold_more : Icons.unfold_less),
              tooltip: _allFolded
                  ? l10n.plugin_reddit_expand_all
                  : l10n.plugin_reddit_collapse_all,
              onPressed: _toggleFoldAll,
            ),
          _ThreadSaveButton(post: _post),
          PopupMenuButton<String>(
            tooltip: l10n.plugin_reddit_sort,
            icon: const Icon(Icons.sort),
            initialValue: _sort ?? '',
            onSelected: (value) {
              setState(() {
                _sort = value.isEmpty ? null : value;
                _comments = null;
                _collapsed.clear();
              });
              _load();
            },
            itemBuilder: (context) => [
              PopupMenuItem(
                value: '',
                child: Text(l10n.plugin_reddit_sort_best),
              ),
              PopupMenuItem(
                value: 'top',
                child: Text(l10n.plugin_reddit_sort_top),
              ),
              PopupMenuItem(
                value: 'new',
                child: Text(l10n.plugin_reddit_sort_new),
              ),
              PopupMenuItem(
                value: 'controversial',
                child: Text(l10n.plugin_reddit_sort_controversial),
              ),
              PopupMenuItem(
                value: 'old',
                child: Text(l10n.plugin_reddit_sort_old),
              ),
              PopupMenuItem(
                value: 'qa',
                child: Text(l10n.plugin_reddit_sort_qa),
              ),
            ],
          ),
        ],
      ),
      body: RefreshIndicator(
        onRefresh: _load,
        child: Builder(
          builder: (context) {
            // Walked once per build pass — inside itemBuilder this O(n) walk
            // ran again for every row, ~n² work on a long thread's frame.
            final rows = comments == null
                ? const <VisibleComment>[]
                : visibleComments(comments, _collapsed);
            return FeedListView(
              // One header plus the flattened tree: nesting the widgets
              // instead would build every reply of every collapsed branch
              // up front.
              itemCount: 1 + (comments == null ? 1 : rows.length),
              itemBuilder: (context, index) {
                if (index == 0) {
                  return _header(context);
                }
                if (comments == null) {
                  return _pending(context, l10n);
                }
                final visible = rows[index - 1];
                if (visible.entry.comment.isStub) {
                  return _stubRow(context, visible.entry);
                }
                return _commentRow(
                  context,
                  visible.entry,
                  hidden: visible.hidden,
                );
              },
            );
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
    final post = _post;
    final date = post.createdAt;

    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 8),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if (post.showsTitle) ...[
            Text(
              post.displayTitle,
              style: theme.textTheme.titleLarge!.copyWith(
                fontWeight: FontWeight.w700,
              ),
            ),
            const SizedBox(height: 6),
          ],
          DefaultTextStyle.merge(
            style: theme.textTheme.bodySmall!.copyWith(
              color: theme.colorScheme.onSurfaceVariant,
            ),
            child: Row(
              children: [
                RedditSubredditAvatar(subreddit: post.subreddit, size: 22),
                const SizedBox(width: 6),
                Expanded(
                  child: Wrap(
                    spacing: 8,
                    runSpacing: 2,
                    children: [
                      if (post.author != null) Text('u/${post.author}'),
                      if (date != null) Text(createRelativeDate(date)),
                    ],
                  ),
                ),
                _ThreadUpvoteButton(post: post),
                const SizedBox(width: 8),
                Text('${post.commentCount}'),
              ],
            ),
          ),
          // The same block the feed card uses, so a picture post opens on its
          // picture rather than on a link to one.
          RedditPostMedia(post: post, padding: const EdgeInsets.only(top: 10)),
          if (_visibleSelfText(post) case final selfText?) ...[
            const SizedBox(height: 10),
            RedditRichText(text: selfText, style: theme.textTheme.bodyMedium),
          ],
          const Divider(height: 24),
        ],
      ),
    );
  }

  /// Selftext worth printing under the media. CDN URLs that the picture
  /// already shows are stripped first, so a leftover empty string is dropped.
  String? _visibleSelfText(RedditPost post) {
    final raw = _selfText;
    if (raw == null || raw.isEmpty) {
      return null;
    }
    final text = post.hasVisualMedia ? stripRedditMediaLinksFromText(raw) : raw;
    if (text.isEmpty || isRedditMediaPlaceholderTitle(text)) {
      return null;
    }
    return text;
  }

  void _toggleFold(String id) => setState(
    () => _collapsed.contains(id) ? _collapsed.remove(id) : _collapsed.add(id),
  );

  /// A comment in its depth's bubble; tapping it folds the subtree. [hidden]
  /// is how many replies the fold is holding, shown as a chip so a collapsed
  /// argument says how big it was.
  Widget _commentRow(
    BuildContext context,
    FlatComment entry, {
    int hidden = 0,
  }) {
    final theme = Theme.of(context);
    final comment = entry.comment;
    final folded = _collapsed.contains(comment.id);

    return CommentBubble(
      depth: entry.depth,
      outlined: comment.isRemoved,
      onTap: () => _toggleFold(comment.id),
      builder: (context, colors) => Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          _CommentHeader(
            comment: comment,
            colors: colors,
            foldedCount: folded ? hidden + 1 : null,
          ),
          if (!folded) ...[
            if (comment.body.isNotEmpty)
              Padding(
                padding: const EdgeInsets.only(top: 4),
                child: RedditRichText(
                  text: comment.body,
                  style: theme.textTheme.bodyMedium!.copyWith(
                    color: colors.text,
                  ),
                ),
              ),
            RedditCommentImages(urls: comment.mediaUrls),
          ],
        ],
      ),
    );
  }

  /// Replies Reddit held back. The row says how many and opens the subtree's
  /// own page, rather than the thread ending mid-air with no sign anything is
  /// missing — which is what silently dropping these rows did. Outlined, not
  /// filled: it is a way onward, not a comment.
  Widget _stubRow(BuildContext context, FlatComment entry) {
    final theme = Theme.of(context);
    final comment = entry.comment;
    final count = (comment.moreCount ?? -1) > 0
        ? ' · ${comment.moreCount}'
        : '';

    return CommentBubble(
      depth: entry.depth,
      outlined: true,
      shrinkWrap: true,
      onTap: _stubTap(context, comment.permalink),
      builder: (context, colors) => Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(Icons.subdirectory_arrow_right, size: 16, color: colors.accent),
          const SizedBox(width: 6),
          Flexible(
            child: Text(
              '${L10n.of(context).plugin_reddit_more_replies}$count',
              style: theme.textTheme.bodySmall!.copyWith(
                color: colors.accent,
                fontWeight: FontWeight.w600,
              ),
            ),
          ),
        ],
      ),
    );
  }

  /// The subtree's page has the held-back replies; the post's own page is
  /// this one, so a root-level stub can only continue on Reddit itself.
  VoidCallback? _stubTap(BuildContext context, String? permalink) {
    if (permalink == null) {
      return null;
    }
    if (_trimSlash(permalink) == _trimSlash(_post.permalink)) {
      return () => openUri(context, redditPostUrl(_post));
    }
    return () => Navigator.push(
      context,
      MaterialPageRoute(
        builder: (_) =>
            RedditThreadScreen(post: _post.copyWith(permalink: permalink)),
      ),
    );
  }
}

/// Who wrote a comment, its score and age, and — when folded — how many
/// comments the fold is holding. Wraps rather than overflowing when a long
/// name meets large text deep in a thread.
class _CommentHeader extends StatelessWidget {
  final RedditComment comment;
  final CommentBubbleColors colors;
  final int? foldedCount;

  const _CommentHeader({
    required this.comment,
    required this.colors,
    this.foldedCount,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final author = comment.author;
    final count = foldedCount;

    return DefaultTextStyle.merge(
      style: theme.textTheme.bodySmall!.copyWith(color: colors.muted),
      child: Wrap(
        spacing: 8,
        runSpacing: 2,
        crossAxisAlignment: WrapCrossAlignment.center,
        children: [
          GestureDetector(
            // A name in a thread is a way to the rest of what they posted, the
            // same as it is on the card.
            onTap: author == null
                ? null
                : () => Navigator.push(
                    context,
                    MaterialPageRoute(
                      builder: (_) => RedditListingScreen.user(author),
                    ),
                  ),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                RedditAvatar(name: author, size: 20),
                const SizedBox(width: 6),
                Flexible(
                  child: Text(
                    author == null ? '' : 'u/$author',
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: TextStyle(
                      fontWeight: FontWeight.w600,
                      color: comment.isSubmitter ? colors.accent : colors.text,
                    ),
                  ),
                ),
              ],
            ),
          ),
          if (comment.score != null) Text('${comment.score}'),
          if (comment.createdAt != null)
            Text(createRelativeDate(comment.createdAt!)),
          if (count != null) _FoldCount(count: count, colors: colors),
        ],
      ),
    );
  }
}

class _FoldCount extends StatelessWidget {
  final int count;
  final CommentBubbleColors colors;

  const _FoldCount({required this.count, required this.colors});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
      decoration: BoxDecoration(
        border: Border.all(color: colors.border),
        borderRadius: BorderRadius.circular(10),
      ),
      child: Text(
        '+$count',
        style: Theme.of(context).textTheme.labelSmall!.copyWith(
          color: colors.text,
          fontWeight: FontWeight.w600,
        ),
      ),
    );
  }
}

class _ThreadUpvoteButton extends StatelessWidget {
  final RedditPost post;

  const _ThreadUpvoteButton({required this.post});

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

class _ThreadSaveButton extends StatelessWidget {
  final RedditPost post;

  const _ThreadSaveButton({required this.post});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    SavedTweetModel? archive;
    try {
      archive = context.read<SavedTweetModel>();
    } on ProviderNotFoundException {
      archive = null;
    }
    if (archive != null) {
      return ScopedBuilder<SavedTweetModel, List<SavedTweet>>(
        store: archive,
        distinct: (_) => archive!.isSaved(redditArchiveId(post.id)),
        onState: (context, _) {
          final isSaved = archive!.isSaved(redditArchiveId(post.id));
          return GestureDetector(
            onLongPress: () => pickRedditPostFolder(context, post),
            child: IconButton(
              tooltip: isSaved
                  ? l10n.unsave_from_this_device
                  : l10n.save_on_this_device,
              icon: Icon(isSaved ? Icons.bookmark : Icons.bookmark_border),
              onPressed: () async {
                if (isSaved) {
                  await unfileRedditPost(context, post);
                } else {
                  await fileRedditPost(context, post);
                }
              },
            ),
          );
        },
      );
    }

    final saved = context.read<RedditSavedStore>();
    return ScopedBuilder<RedditSavedStore, List<RedditPost>>(
      store: saved,
      onState: (context, posts) {
        final isSaved = saved.isSaved(post);
        return IconButton(
          tooltip: isSaved ? l10n.action_unsave_post : l10n.action_save_post,
          icon: Icon(isSaved ? Icons.bookmark : Icons.bookmark_border),
          onPressed: () => saved.toggle(post),
        );
      },
    );
  }
}
