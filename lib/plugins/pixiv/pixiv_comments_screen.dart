import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_comment_models.dart';
import 'package:xta/plugins/pixiv/pixiv_comment_tile.dart';
import 'package:xta/plugins/pixiv/pixiv_comments_api.dart';
import 'package:xta/plugins/pixiv/pixiv_comments_store.dart';
import 'package:xta/plugins/pixiv/pixiv_mute_sheet.dart';
import 'package:xta/plugins/pixiv/pixiv_mute_store.dart';
import 'package:xta/plugins/pixiv/pixiv_settings.dart';
import 'package:xta/plugins/plugin_feed_skeleton.dart';
import 'package:xta/plugins/plugin_view_store.dart';
import 'package:xta/ui/empty_pane.dart';
import 'package:xta/ui/errors.dart';
import 'package:xta/ui/feed_list.dart';

/// Opens [target]'s comments, or the replies under [parent]. [revealed] are
/// the comments the reader already showed past their outside-link note.
Future<void> openPixivComments(
  BuildContext context,
  PixivCommentTarget target, {
  PixivComment? parent,
  Set<int> revealed = const {},
}) => Navigator.push(
  context,
  MaterialPageRoute<void>(
    builder: (_) => PixivCommentsScreen(target: target, parent: parent, revealed: revealed),
  ),
);

/// How close to the end of the list the next page is asked for.
const _loadMoreReach = 800.0;

/// A work's comments to read, or one comment's replies with that comment on
/// top. There is no composer: XTA does not post to Pixiv.
class PixivCommentsScreen extends StatefulWidget {
  final PixivCommentTarget target;

  /// Set for a reply thread: the comment shown above its replies.
  final PixivComment? parent;

  /// Comments shown past their outside-link note before this screen opened,
  /// so a thread opened from a shown comment keeps it shown.
  final Set<int> revealed;

  const PixivCommentsScreen({super.key, required this.target, this.parent, this.revealed = const {}});

  @override
  State<PixivCommentsScreen> createState() => _PixivCommentsScreenState();
}

class _PixivCommentsScreenState extends State<PixivCommentsScreen> {
  late final PixivCommentsStore _comments;
  late final PixivMuteStore _mutes;
  late final PluginViewStore<Set<int>> _revealed;

  /// Loads still landing; the stores are destroyed only once they have.
  Future<void> _settled = Future.value();

  @override
  void initState() {
    super.initState();
    _mutes = context.read<PixivMuteStore>();
    _revealed = PluginViewStore<Set<int>>(widget.revealed);
    _comments = PixivCommentsStore(
      PixivCommentsApi.of(context),
      widget.target,
      parent: widget.parent,
      mutes: () => _mutes.state,
    );
    _refresh();
  }

  @override
  void dispose() {
    unawaited(_settled.whenComplete(_destroyStores));
    super.dispose();
  }

  void _destroyStores() {
    _comments.destroy();
    _revealed.destroy();
  }

  Future<void> _track(Future<void> work) {
    _settled = Future.wait([_settled, work]);
    return work;
  }

  Future<void> _refresh() => _track(_comments.refresh());

  void _loadMore() {
    if (_comments.hasMore && !_comments.loadingMore) _track(_comments.loadMore());
  }

  /// After a failed page only Retry asks again, so scrolling at the end does
  /// not keep hitting a connection that is down.
  void _nearEnd() {
    if (_comments.moreError == null) _loadMore();
  }

  /// Muting the comment a thread hangs off, or its author, leaves the thread.
  Future<void> _mute(PixivMuteChoice choice) async {
    final navigator = Navigator.of(context);
    final muted = await confirmPixivMute(context, choice);
    final parent = widget.parent;
    if (muted && mounted && parent != null && !pixivCommentVisible(parent, _mutes.state)) navigator.pop();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return Scaffold(
      appBar: AppBar(
        title: Text(
          widget.parent == null ? l10n.plugin_pixiv_comments_title : l10n.plugin_pixiv_comments_replies_title,
        ),
      ),
      body: ScopedBuilder<PixivCommentsStore, List<PixivComment>>(
        store: _comments,
        onLoading: (context) => const PluginFeedSkeleton(applyFeedInsets: false),
        onError: (context, error) => FullPageErrorWidget(
          error: error,
          stackTrace: null,
          prefix: pixivErrorMessage(l10n, error ?? Exception()),
          onRetry: _refresh,
        ),
        onState: (context, _) => ScopedBuilder<PixivMuteStore, PixivMuteState>(
          store: _mutes,
          onState: (context, mutes) => ScopedBuilder<PluginViewStore<Set<int>>, Set<int>>(
            store: _revealed,
            onState: (context, revealed) => _thread(context, mutes, revealed),
          ),
        ),
      ),
    );
  }

  Widget _thread(BuildContext context, PixivMuteState mutes, Set<int> revealed) {
    final comments = pixivVisibleComments(_comments.state, mutes);
    final parent = widget.parent;
    if (parent == null && comments.isEmpty && !_comments.hasMore) {
      return EmptyPane(
        icon: Icons.forum_outlined,
        message: L10n.of(context).plugin_pixiv_comments_empty,
        onRefresh: _refresh,
      );
    }
    final rows = <Widget Function(BuildContext)>[
      if (parent != null) (context) => _tile(parent, revealed, depth: 0, pinned: true),
      for (final comment in comments) (context) => _tile(comment, revealed, depth: parent == null ? 0 : 1),
      if (parent != null && comments.isEmpty && !_comments.hasMore) _noReplies,
      if (_comments.hasMore) _tail,
    ];
    return RefreshIndicator(
      onRefresh: _refresh,
      child: NotificationListener<ScrollNotification>(
        onNotification: (notification) {
          if (notification.metrics.extentAfter < _loadMoreReach) _nearEnd();
          return false;
        },
        child: FeedListView(
          physics: const AlwaysScrollableScrollPhysics(),
          padding: EdgeInsets.only(top: 6, bottom: MediaQuery.paddingOf(context).bottom + 24),
          itemCount: rows.length,
          itemBuilder: (context, index) => rows[index](context),
        ),
      ),
    );
  }

  Widget _tile(PixivComment comment, Set<int> revealed, {required int depth, bool pinned = false}) => PixivCommentTile(
    key: ValueKey('pixiv-comment-${comment.id}'),
    comment: comment,
    depth: depth,
    revealed: revealed.contains(comment.id),
    onReveal: () => _revealed.select({...revealed, comment.id}),
    onMute: _mute,
    onViewReplies: pinned || !comment.hasReplies
        ? null
        : () => openPixivComments(context, widget.target, parent: comment, revealed: revealed),
  );

  /// The row under the last comment while more pages remain.
  Widget _tail(BuildContext context) => switch (_comments.moreError) {
    final error? => _PageFailed(message: pixivErrorMessage(L10n.of(context), error), onRetry: _loadMore),
    null when _comments.loadingMore => const _PageSpinner(),
    null => _PageTail(onShown: _loadMore),
  };

  Widget _noReplies(BuildContext context) => Padding(
    padding: const EdgeInsets.fromLTRB(32, 24, 32, 24),
    child: Text(
      L10n.of(context).plugin_pixiv_comments_replies_empty,
      textAlign: TextAlign.center,
      style: Theme.of(context).textTheme.bodyMedium!.copyWith(color: Theme.of(context).colorScheme.onSurfaceVariant),
    ),
  );
}

/// The list's last row while more pages remain and none has failed: asks for
/// the next one as soon as it is built, so a short first page still fills the screen.
class _PageTail extends StatefulWidget {
  final VoidCallback onShown;

  const _PageTail({required this.onShown});

  @override
  State<_PageTail> createState() => _PageTailState();
}

class _PageTailState extends State<_PageTail> {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) widget.onShown();
    });
  }

  @override
  Widget build(BuildContext context) => const _PageSpinner();
}

class _PageSpinner extends StatelessWidget {
  const _PageSpinner();

  @override
  Widget build(BuildContext context) => const Padding(
    padding: EdgeInsets.all(16),
    child: Center(child: CircularProgressIndicator()),
  );
}

/// A later page that failed: what went wrong and Retry, under the comments
/// that did load.
class _PageFailed extends StatelessWidget {
  final String message;
  final VoidCallback onRetry;

  const _PageFailed({required this.message, required this.onRetry});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 8, 16, 16),
      child: Wrap(
        alignment: WrapAlignment.center,
        crossAxisAlignment: WrapCrossAlignment.center,
        spacing: 8,
        children: [
          Text(
            message,
            textAlign: TextAlign.center,
            style: theme.textTheme.bodyMedium!.copyWith(color: theme.colorScheme.onSurfaceVariant),
          ),
          TextButton.icon(onPressed: onRetry, icon: const Icon(Icons.refresh), label: Text(L10n.of(context).retry)),
        ],
      ),
    );
  }
}
