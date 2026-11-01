import 'dart:async';
import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/reddit/reddit_client.dart';
import 'package:xta/plugins/reddit/reddit_comments.dart';
import 'package:xta/plugins/reddit/reddit_media_urls.dart';
import 'package:xta/plugins/reddit/reddit_post_sheet.dart';
import 'package:xta/plugins/reddit/reddit_read_session.dart';
import 'package:xta/plugins/reddit/reddit_screen.dart' show redditErrorMessage;
import 'package:xta/plugins/reddit/reddit_thread_comment.dart';
import 'package:xta/plugins/reddit/reddit_thread_navigation.dart';
import 'package:xta/plugins/reddit/reddit_thread_post.dart';
import 'package:xta/plugins/reddit/reddit_thread_visits.dart';
import 'package:xta/tweet/tweet_chrome.dart';
import 'package:xta/ui/feed_list.dart';
import 'package:xta/ui/motion.dart';
import 'package:xta/ui/reader_failure.dart';
import 'package:xta/utils/urls.dart';

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

  /// Stubs whose held-back comments are on their way.
  final _loadingMore = <String>{};

  /// Reddit refused to hand over held-back comments once; the rest of them
  /// are read on Reddit itself.
  bool _moreRefused = false;

  /// When the reader last opened this thread before now: comments written
  /// since then say they are new.
  late final DateTime? _lastVisit;

  final _scroll = ScrollController();
  final _commentsKey = GlobalKey();

  /// Each top-level comment's row, for the jump button to find.
  final _topLevelKeys = <String, GlobalKey>{};

  @override
  void initState() {
    super.initState();
    _selfText = widget.post.selfText;
    _visit();
    _load();
  }

  @override
  void dispose() {
    _scroll.dispose();
    super.dispose();
  }

  /// Reads the last visit, and notes this one once the first frame is built:
  /// the preferences tell their listeners, which may not rebuild mid-build.
  void _visit() {
    final prefs = PrefService.of(context, listen: false);
    final permalink = widget.post.permalink;
    final now = DateTime.now();
    _lastVisit = redditLastThreadVisit(prefs, permalink);
    WidgetsBinding.instance.addPostFrameCallback((_) => unawaited(redditRecordThreadVisit(prefs, permalink, now)));
  }

  Future<void> _load() async {
    setState(() => _error = null);
    try {
      final client = context.read<RedditClient>();
      final prefs = PrefService.of(context, listen: false);
      final session = await RedditReadSession.resolve(prefs: prefs);
      if (_post.arrivedBare) {
        unawaited(_fillPost(session, client));
      }
      final result = await session.fetchComments(client, _post.permalink, sort: _sort);
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

  /// The post as Reddit serves it on its own, for one that arrived with
  /// nothing but a title. The comments do not wait for it, and a post Reddit
  /// will not serve that way stays as it arrived.
  Future<void> _fillPost(RedditReadSession session, RedditClient client) async {
    final fetched = await session.fetchPost(client, _post.permalink);
    if (!mounted || fetched == null) {
      return;
    }
    setState(() {
      _post = _post.filledFrom(fetched);
      _selfText ??= fetched.selfText;
    });
  }

  /// The comments a root-level [stub] held back, in its place.
  Future<void> _loadMore(RedditComment stub) async {
    setState(() => _loadingMore.add(stub.id));
    try {
      final more = await context.read<RedditClient>().fetchMoreComments(stub);
      if (!mounted) return;
      setState(() => _comments = _replacing(stub, withoutShownThreads(_comments ?? const [], flattenComments(more))));
    } on RedditException {
      if (!mounted) return;
      setState(() => _moreRefused = true);
      await openUri(context, redditPostUrl(_post));
    } finally {
      if (mounted) {
        setState(() => _loadingMore.remove(stub.id));
      }
    }
  }

  /// The thread with [stub]'s row swapped for [rows].
  List<FlatComment>? _replacing(RedditComment stub, List<FlatComment> rows) => _comments == null
      ? null
      : [
          for (final entry in _comments!)
            if (entry.comment.id == stub.id) ...rows else entry,
        ];

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

  bool _isOwnPermalink(String url) {
    final uri = Uri.tryParse(url);
    return uri != null && uri.host.endsWith('reddit.com') && uri.path.contains('/comments/');
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

  void _resort(String? sort) {
    setState(() {
      _sort = sort;
      _comments = null;
      _collapsed.clear();
    });
    _load();
  }

  /// Reddit's count, or what has loaded when that is more: a post from search
  /// arrives without one.
  int get _commentCount {
    final loaded = _comments?.where((entry) => !entry.comment.isStub).length ?? 0;
    return math.max(_post.commentCount, loaded);
  }

  @override
  Widget build(BuildContext context) {
    final comments = _comments;
    // Walked once per build pass — inside itemBuilder this O(n) walk ran again
    // for every row, ~n² work on a long thread's frame.
    final rows = comments == null ? const <VisibleComment>[] : visibleComments(comments, _collapsed);
    return Scaffold(
      appBar: _appBar(context),
      floatingActionButton: _jumpButton(context, rows),
      body: RefreshIndicator(
        onRefresh: _load,
        child: FeedListView(
          controller: _scroll,
          padding: const EdgeInsets.only(bottom: 96),
          // The post, the comments header, then the flattened tree: nesting
          // the widgets instead would build every reply of every collapsed
          // branch up front.
          itemCount: 2 + (comments == null ? 1 : rows.length),
          itemBuilder: (context, index) => switch (index) {
            0 => _postItem(context),
            1 => _commentsHeader(context),
            _ when comments == null => _pending(context, L10n.of(context)),
            _ => _row(context, rows, index - 2),
          },
        ),
      ),
    );
  }

  PreferredSizeWidget _appBar(BuildContext context) {
    return AppBar(
      title: Text('r/${widget.post.subreddit}', style: const TextStyle(fontSize: 17, fontWeight: FontWeight.w600)),
      shape: Border(bottom: BorderSide(color: tweetDividerColor(context))),
      actions: [
        IconButton(
          tooltip: MaterialLocalizations.of(context).moreButtonTooltip,
          icon: const Icon(Icons.more_vert),
          onPressed: () => openRedditPostSheet(context, _post),
        ),
      ],
    );
  }

  Widget _postItem(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        RedditThreadPost(post: _post, selfText: _visibleSelfText(_post)),
        RedditThreadPostActions(post: _post, commentCount: _commentCount, onComments: _scrollToComments),
      ],
    );
  }

  Widget _commentsHeader(BuildContext context) {
    final comments = _comments;
    final foldable = comments != null && foldableTopLevelIds(comments).isNotEmpty;
    return RedditCommentsHeader(
      key: _commentsKey,
      count: _commentCount,
      sort: _sort,
      onSort: _resort,
      allFolded: foldable ? _allFolded : null,
      onToggleAll: _toggleFoldAll,
    );
  }

  void _scrollToComments() {
    final header = _commentsKey.currentContext;
    if (header == null) return;
    Scrollable.ensureVisible(
      header,
      duration: xtaReduceMotion(context) ? Duration.zero : const Duration(milliseconds: 300),
      curve: Curves.easeOutCubic,
    );
  }

  /// The top-level comments in their order, for the jump button.
  List<String> _topLevelIds(List<VisibleComment> rows) => [
    for (final row in rows)
      if (row.entry.depth == 0 && !row.entry.comment.isStub) row.entry.comment.id,
  ];

  GlobalKey _keyOf(String id) => _topLevelKeys.putIfAbsent(id, GlobalKey.new);

  Widget? _jumpButton(BuildContext context, List<VisibleComment> rows) {
    final ids = _topLevelIds(rows);
    if (ids.length < 2) return null;
    return RedditJumpButton(
      onPressed: () =>
          redditJumpToNext(_scroll, [for (final id in ids) _keyOf(id)], reduceMotion: xtaReduceMotion(context)),
    );
  }

  Widget _pending(BuildContext context, L10n l10n) {
    final error = _error;
    if (error != null) {
      // One row where the comments would have been.
      return ReaderFailureNotice(
        source: pluginIdReddit,
        error: error,
        contextMessage: redditErrorMessage(l10n, error),
        onRetry: _load,
      );
    }

    return const Padding(
      padding: EdgeInsets.symmetric(vertical: 48),
      child: Center(child: CircularProgressIndicator()),
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

  void _toggleFold(String id) => setState(() => _collapsed.contains(id) ? _collapsed.remove(id) : _collapsed.add(id));

  /// A comment, or the row standing for replies Reddit held back. A top-level
  /// comment keeps its key so the jump button can find it.
  Widget _row(BuildContext context, List<VisibleComment> rows, int index) {
    final entry = rows[index].entry;
    final comment = entry.comment;
    if (comment.isStub) {
      return _heldBack(context, entry);
    }
    final row = RedditCommentRow(
      comment: comment,
      depth: entry.depth,
      folded: _collapsed.contains(comment.id),
      divided: entry.depth == 0 && index > 0,
      isNew: redditCommentIsNew(comment, _lastVisit),
      onToggle: () => _toggleFold(comment.id),
    );
    return entry.depth == 0 ? KeyedSubtree(key: _keyOf(comment.id), child: row) : row;
  }

  /// Replies Reddit held back. Rather than the thread ending mid-air with no
  /// sign anything is missing, the row loads them in place when Reddit said
  /// where they are, and otherwise continues on their own page or on Reddit.
  Widget _heldBack(BuildContext context, FlatComment entry) {
    final l10n = L10n.of(context);
    final stub = entry.comment;
    final inPlace = _loadsInPlace(stub);
    return RedditHeldBackRow(
      depth: entry.depth,
      label: inPlace ? l10n.plugin_reddit_load_more_comments : l10n.plugin_reddit_continue_thread,
      trailing: inPlace ? null : (_isRoot(stub) ? Icons.open_in_new : Icons.chevron_right),
      loading: _loadingMore.contains(stub.id),
      onTap: _stubTap(context, stub),
    );
  }

  bool _isRoot(RedditComment stub) {
    final permalink = stub.permalink;
    return permalink != null && redditThreadKey(permalink) == redditThreadKey(_post.permalink);
  }

  bool _loadsInPlace(RedditComment stub) => _isRoot(stub) && stub.nextPage != null && !_moreRefused;

  /// What tapping a held-back row does: load the next comments in place, open
  /// the thread on Reddit once it will not hand them over here, or open the
  /// subtree's own page.
  VoidCallback? _stubTap(BuildContext context, RedditComment stub) {
    final permalink = stub.permalink;
    if (permalink == null) {
      return null;
    }
    if (_loadsInPlace(stub)) {
      return () => _loadMore(stub);
    }
    if (_isRoot(stub)) {
      return () => openUri(context, redditPostUrl(_post));
    }
    return () => Navigator.push(
      context,
      MaterialPageRoute(
        builder: (_) => RedditThreadScreen(post: _post.copyWith(permalink: permalink)),
      ),
    );
  }
}
