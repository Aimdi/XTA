import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:share_plus/share_plus.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/threads/threads_conversation.dart';
import 'package:xta/plugins/threads/threads_direct_client.dart';
import 'package:xta/plugins/threads/threads_models.dart';
import 'package:xta/plugins/threads/threads_post_card.dart';
import 'package:xta/plugins/threads/threads_profile_screen.dart';
import 'package:xta/plugins/threads/threads_thread_store.dart';
import 'package:xta/tweet/tweet_skeleton.dart';
import 'package:xta/ui/conversation_sort.dart';
import 'package:xta/ui/feed_list.dart';
import 'package:xta/utils/urls.dart';
import 'package:xta/ui/reload_status.dart';
import 'package:xta/ui/reader_failure.dart';
import 'package:xta/ui/sort_menu_button.dart';
import 'package:xta/constants.dart';

/// One Threads post in its conversation — what it answers, the rest of its
/// author's thread, and each reply thread — read from the public page.
class ThreadsThreadScreen extends StatefulWidget {
  final ThreadsPost post;

  const ThreadsThreadScreen({super.key, required this.post});

  @override
  State<ThreadsThreadScreen> createState() => _ThreadsThreadScreenState();
}

class _ThreadsThreadScreenState extends State<ThreadsThreadScreen> {
  late final ThreadsThreadStore _thread = ThreadsThreadStore(
    context.read<ThreadsDirectClient>(),
    widget.post,
  );
  late final _sorts = context.read<ConversationSortStore>();
  late final _view = ThreadsThreadViewStore(_sorts.state.replies);
  final _focusKey = GlobalKey();
  late final Disposer _stopLanding;
  bool _landed = false;

  @override
  void initState() {
    super.initState();
    _stopLanding = _thread.observer(onState: _landOnFocus);
    _thread.load();
  }

  @override
  void dispose() {
    _stopLanding();
    _thread.destroy();
    _view.destroy();
    super.dispose();
  }

  /// With posts above it, the opened post is brought to the top once, as X
  /// opens a reply: what it answers stays a scroll up.
  void _landOnFocus(ThreadsConversation conversation) {
    if (_landed || conversation.ancestors.isEmpty) return;
    _landed = true;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      final focus = _focusKey.currentContext;
      if (focus != null && focus.mounted) Scrollable.ensureVisible(focus);
    });
  }

  void _selectOrder(ReplySort order) {
    _sorts.selectReplies(order);
    _view.selectOrder(order);
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);

    return Scaffold(
      appBar: AppBar(
        title: Text(l10n.plugin_threads_thread),
        actions: [
          ScopedBuilder<ThreadsThreadStore, ThreadsConversation>(
            store: _thread,
            onState: (context, conversation) =>
                _ThreadActions(url: conversation.focus.url),
          ),
        ],
      ),
      body: RefreshIndicator(
        onRefresh: () => _thread.load(force: true),
        child: TripleBuilder<ThreadsThreadStore, ThreadsConversation>(
          store: _thread,
          builder: (context, triple) =>
              ScopedBuilder<ThreadsThreadViewStore, ThreadsThreadView>(
                store: _view,
                onState: (context, view) => _ThreadsConversationList(
                  focusKey: _focusKey,
                  conversation: triple.state,
                  view: view,
                  loading: triple.isLoading,
                  error: triple.error,
                  onRetry: () => _thread.load(force: true),
                  onToggleAuthorOnly: _view.toggleAuthorOnly,
                  onOrder: _selectOrder,
                  onExpand: _view.expand,
                ),
              ),
        ),
      ),
    );
  }
}

class _ThreadActions extends StatelessWidget {
  final String? url;

  const _ThreadActions({required this.url});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final url = this.url;
    if (url == null) {
      return const SizedBox.shrink();
    }
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        IconButton(
          tooltip: l10n.share_link,
          icon: const Icon(Icons.share_outlined),
          onPressed: () => SharePlus.instance.share(ShareParams(text: url)),
        ),
        IconButton(
          tooltip: l10n.open_in_browser,
          onPressed: () => openUri(context, url),
          icon: const Icon(Icons.open_in_new),
        ),
      ],
    );
  }
}

class _ThreadsConversationList extends StatelessWidget {
  final GlobalKey focusKey;
  final ThreadsConversation conversation;
  final ThreadsThreadView view;
  final bool loading;
  final Object? error;
  final Future<void> Function() onRetry;
  final VoidCallback onToggleAuthorOnly;
  final ValueChanged<ReplySort> onOrder;
  final void Function(List<ThreadsPost> chain) onExpand;

  const _ThreadsConversationList({
    required this.focusKey,
    required this.conversation,
    required this.view,
    required this.loading,
    required this.error,
    required this.onRetry,
    required this.onToggleAuthorOnly,
    required this.onOrder,
    required this.onExpand,
  });

  @override
  Widget build(BuildContext context) {
    final rows = [
      ..._threadRows(context),
      ..._statusRows(context),
      ..._replyRows(context),
    ];
    final error = this.error;
    return ReloadStatus(
      loading: loading && _hasReplies,
      error: _hasReplies ? error : null,
      source: pluginIdThreads,
      contextMessage: error == null ? null : threadsApiErrorMessage(L10n.of(context), error),
      onRetry: onRetry,
      child: FeedListView(
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.only(bottom: 24),
        itemCount: rows.length,
        itemBuilder: (context, index) => rows[index],
      ),
    );
  }

  /// The ancestors, the post, and the author's continuation, joined by rails.
  List<Widget> _threadRows(BuildContext context) {
    final focus = conversation.focus;
    if (conversation.focusIsStub && !conversation.loaded) {
      return const [TweetSkeletonTile()];
    }
    final chain = [
      ...conversation.ancestors,
      focus,
      ...conversation.continuation,
    ];
    return [
      for (final (index, post) in chain.indexed)
        if (identical(post, focus))
          KeyedSubtree(
            key: focusKey,
            child: ThreadsPostCard(
              key: ValueKey('chain-${post.id}'),
              post: post,
              showSourceBadge: false,
              openOnTap: false,
              focal: true,
              threadConnectTop: index > 0,
              threadConnectBottom: index < chain.length - 1,
            ),
          )
        else
          _card(
            context,
            post,
            key: ValueKey('chain-${post.id}'),
            connectTop: index > 0,
            connectBottom: index < chain.length - 1,
          ),
    ];
  }

  bool get _hasReplies => conversation.replies.isNotEmpty;

  List<Widget> _statusRows(BuildContext context) {
    final l10n = L10n.of(context);
    // Replies on screen keep their place: the line and a floating notice over
    // them say a reload is under way or failed.
    if (_hasReplies) return const [];
    if (loading) {
      return const [
        Padding(
          padding: EdgeInsets.all(24),
          child: Center(child: CircularProgressIndicator()),
        ),
      ];
    }
    final error = this.error;
    if (error != null) {
      return [
        ReaderFailureNotice(
          source: pluginIdThreads,
          error: error,
          contextMessage: threadsApiErrorMessage(l10n, error),
          onRetry: onRetry,
        ),
      ];
    }
    if (conversation.loaded && conversation.replies.isEmpty) {
      return [_NoReplies(url: conversation.focus.url)];
    }
    return const [];
  }

  List<Widget> _replyRows(BuildContext context) {
    if (conversation.replies.isEmpty) {
      return const [];
    }
    return [
      _RepliesHeader(
        authorOnly: view.authorOnly,
        showAuthorFilter: threadsAuthorReplies(conversation).isNotEmpty,
        order: view.order,
        onToggleAuthorOnly: onToggleAuthorOnly,
        onOrder: onOrder,
      ),
      for (final row in threadsReplyRows(conversation, view))
        switch (row) {
          ThreadsReplyPostRow() => _card(
            context,
            row.post,
            key: ValueKey('reply-${row.post.id}'),
            connectTop: row.connectTop,
            connectBottom: row.connectBottom,
          ),
          ThreadsReplyMoreRow() => _MoreRepliesRow(
            key: ValueKey('more-${row.chain.first.id}'),
            hidden: row.hidden,
            onTap: () => onExpand(row.chain),
          ),
        },
    ];
  }

  Widget _card(
    BuildContext context,
    ThreadsPost post, {
    required Key key,
    required bool connectTop,
    required bool connectBottom,
  }) => ThreadsPostCard(
    key: key,
    post: post,
    showSourceBadge: false,
    threadConnectTop: connectTop,
    threadConnectBottom: connectBottom,
    onOpen: () => openThreadsPost(context, post),
    onAuthorTap: () => Navigator.push(
      context,
      MaterialPageRoute(
        builder: (_) => ThreadsProfileScreen(username: post.handle),
      ),
    ),
  );
}

class _RepliesHeader extends StatelessWidget {
  final bool authorOnly;
  final bool showAuthorFilter;
  final ReplySort order;
  final VoidCallback onToggleAuthorOnly;
  final ValueChanged<ReplySort> onOrder;

  const _RepliesHeader({
    required this.authorOnly,
    required this.showAuthorFilter,
    required this.order,
    required this.onToggleAuthorOnly,
    required this.onOrder,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final l10n = L10n.of(context);
    // One line, as Bluesky's thread controls; a narrow phone or large text
    // moves what does not fit to the next.
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 4, 16, 4),
      child: Wrap(
        crossAxisAlignment: WrapCrossAlignment.center,
        spacing: 8,
        children: [
          Text(
            l10n.plugin_threads_replies,
            style: theme.textTheme.titleSmall!.copyWith(
              color: theme.colorScheme.onSurfaceVariant,
            ),
          ),
          if (showAuthorFilter)
            FilterChip(
              label: Text(l10n.plugin_threads_author_replies),
              selected: authorOnly,
              onSelected: (_) => onToggleAuthorOnly(),
            ),
          ReplySortButton(
            key: const ValueKey('threads-thread-sort'),
            value: order,
            options: threadsReplySorts,
            onSelected: onOrder,
          ),
        ],
      ),
    );
  }
}

class _MoreRepliesRow extends StatelessWidget {
  final int hidden;
  final VoidCallback onTap;

  const _MoreRepliesRow({super.key, required this.hidden, required this.onTap});

  @override
  Widget build(BuildContext context) {
    final color = Theme.of(context).colorScheme.primary;
    return InkWell(
      onTap: onTap,
      child: ConstrainedBox(
        constraints: const BoxConstraints(minHeight: 48),
        child: Padding(
          padding: const EdgeInsetsDirectional.fromSTEB(76, 8, 16, 8),
          child: Row(
            children: [
              Icon(Icons.subdirectory_arrow_right, size: 18, color: color),
              const SizedBox(width: 8),
              Flexible(
                child: Text(
                  L10n.of(context).plugin_threads_more_replies(hidden),
                  style: Theme.of(
                    context,
                  ).textTheme.labelLarge?.copyWith(color: color),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _NoReplies extends StatelessWidget {
  final String? url;

  const _NoReplies({required this.url});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final url = this.url;
    return Padding(
      padding: const EdgeInsets.fromLTRB(24, 8, 24, 24),
      child: Column(
        children: [
          Text(
            l10n.plugin_threads_replies_empty,
            textAlign: TextAlign.center,
            style: Theme.of(context).textTheme.bodyMedium!.copyWith(
              color: Theme.of(context).colorScheme.onSurfaceVariant,
            ),
          ),
          if (url != null)
            TextButton.icon(
              onPressed: () => openUri(context, url),
              icon: const Icon(Icons.open_in_new),
              label: Text(l10n.open_in_browser),
            ),
        ],
      ),
    );
  }
}
