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
import 'package:xta/tweet/threaded_conversation.dart';
import 'package:xta/tweet/tweet_skeleton.dart';
import 'package:xta/ui/errors.dart';
import 'package:xta/ui/feed_list.dart';
import 'package:xta/utils/urls.dart';

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
  final _view = ThreadsThreadViewStore();

  @override
  void initState() {
    super.initState();
    _thread.load();
  }

  @override
  void dispose() {
    _thread.destroy();
    _view.destroy();
    super.dispose();
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
                  conversation: triple.state,
                  view: view,
                  loading: triple.isLoading,
                  error: triple.error,
                  onRetry: () => _thread.load(force: true),
                  onToggleAuthorOnly: _view.toggleAuthorOnly,
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
  final ThreadsConversation conversation;
  final ThreadsThreadView view;
  final bool loading;
  final Object? error;
  final Future<void> Function() onRetry;
  final VoidCallback onToggleAuthorOnly;
  final void Function(List<ThreadsPost> chain) onExpand;

  const _ThreadsConversationList({
    required this.conversation,
    required this.view,
    required this.loading,
    required this.error,
    required this.onRetry,
    required this.onToggleAuthorOnly,
    required this.onExpand,
  });

  @override
  Widget build(BuildContext context) {
    final rows = [
      ..._threadRows(context),
      ..._statusRows(context),
      ..._replyRows(context),
    ];
    return FeedListView(
      physics: const AlwaysScrollableScrollPhysics(),
      padding: const EdgeInsets.only(bottom: 24),
      itemCount: rows.length,
      itemBuilder: (context, index) => rows[index],
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
        ThreadIndent(
          key: ValueKey('chain-${post.id}'),
          depth: 1,
          connectTop: index > 0,
          connectBottom: index < chain.length - 1,
          child: identical(post, focus)
              ? ThreadsPostCard(
                  post: post,
                  showSourceBadge: false,
                  openOnTap: false,
                )
              : _card(context, post),
        ),
    ];
  }

  List<Widget> _statusRows(BuildContext context) {
    final l10n = L10n.of(context);
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
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 8, 16, 16),
          child: FullPageErrorWidget(
            error: error,
            stackTrace: null,
            prefix: threadsApiErrorMessage(l10n, error),
            onRetry: onRetry,
          ),
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
        onToggleAuthorOnly: onToggleAuthorOnly,
      ),
      for (final row in threadsReplyRows(conversation, view))
        switch (row) {
          ThreadsReplyPostRow() => ThreadIndent(
            key: ValueKey('reply-${row.post.id}'),
            depth: 1,
            connectTop: row.connectTop,
            connectBottom: row.connectBottom,
            child: _card(context, row.post),
          ),
          ThreadsReplyMoreRow() => _MoreRepliesRow(
            key: ValueKey('more-${row.chain.first.id}'),
            hidden: row.hidden,
            onTap: () => onExpand(row.chain),
          ),
        },
    ];
  }

  Widget _card(BuildContext context, ThreadsPost post) => ThreadsPostCard(
    key: ValueKey(post.id),
    post: post,
    showSourceBadge: false,
    onOpen: () => Navigator.push(
      context,
      MaterialPageRoute(builder: (_) => ThreadsThreadScreen(post: post)),
    ),
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
  final VoidCallback onToggleAuthorOnly;

  const _RepliesHeader({
    required this.authorOnly,
    required this.showAuthorFilter,
    required this.onToggleAuthorOnly,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final l10n = L10n.of(context);
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 8, 16, 4),
      child: Row(
        children: [
          Expanded(
            child: Text(
              l10n.plugin_threads_replies,
              style: theme.textTheme.titleSmall!.copyWith(
                color: theme.colorScheme.onSurfaceVariant,
              ),
            ),
          ),
          if (showAuthorFilter)
            FilterChip(
              label: Text(l10n.plugin_threads_author_replies),
              selected: authorOnly,
              onSelected: (_) => onToggleAuthorOnly(),
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
