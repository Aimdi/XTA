import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/bluesky/bluesky_client.dart';
import 'package:xta/plugins/bluesky/bluesky_models.dart';
import 'package:xta/plugins/bluesky/bluesky_post_card.dart';
import 'package:xta/plugins/bluesky/bluesky_profile_screen.dart';
import 'package:xta/plugins/bluesky/bluesky_thread_store.dart';
import 'package:xta/ui/errors.dart';
import 'package:xta/ui/feed_list.dart';
import 'package:xta/utils/urls.dart';

class BlueskyThreadScreen extends StatefulWidget {
  final BlueskyPost post;
  const BlueskyThreadScreen({super.key, required this.post});
  @override
  State<BlueskyThreadScreen> createState() => _BlueskyThreadScreenState();
}

class _BlueskyThreadScreenState extends State<BlueskyThreadScreen> {
  late final BlueskyThreadStore _store;
  final _scroll = ScrollController();

  @override
  void initState() {
    super.initState();
    _store = BlueskyThreadStore(context.read<BlueskyClient>(), widget.post);
    _store.refresh();
  }

  @override
  void dispose() {
    _store.destroy();
    _scroll.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => ScopedBuilder<BlueskyThreadStore, BlueskyThreadState>(
    store: _store,
    onState: (context, state) => Scaffold(
      appBar: AppBar(
        title: Text(L10n.of(context).thread),
        actions: [
          IconButton(
            key: const ValueKey('bluesky-thread-jump'),
            tooltip: L10n.of(context).mastodon_thread_selected,
            onPressed: _focusSelected,
            icon: const Icon(Icons.vertical_align_top),
          ),
          IconButton(
            tooltip: L10n.of(context).open_in_browser,
            onPressed: () => openUri(context, state.thread.post.url),
            icon: const Icon(Icons.open_in_new),
          ),
          PopupMenuButton<bool>(
            key: const ValueKey('bluesky-thread-branches'),
            enabled: state.branches.isNotEmpty,
            onSelected: _store.setAllExpanded,
            itemBuilder: (context) => [
              PopupMenuItem(value: true, child: Text(L10n.of(context).mastodon_thread_expand_all)),
              PopupMenuItem(value: false, child: Text(L10n.of(context).mastodon_thread_collapse_all)),
            ],
          ),
        ],
      ),
      body: RefreshIndicator(onRefresh: _store.refresh, child: _conversation(context, state)),
    ),
  );

  void _focusSelected() {
    _store.focusSelected();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted || !_scroll.hasClients) return;
      if (MediaQuery.disableAnimationsOf(context)) {
        _scroll.jumpTo(0);
      } else {
        _scroll.animateTo(0, duration: const Duration(milliseconds: 250), curve: Curves.easeOut);
      }
    });
  }

  Widget _conversation(BuildContext context, BlueskyThreadState state) {
    final l10n = L10n.of(context);
    final thread = state.thread;
    final rows = blueskyVisibleReplies(state.branches, state.collapsed);
    final beforeReplies = <Widget>[
      if (thread.ancestors.isNotEmpty) _contextToggle(context, state),
      if (state.contextOpen) ...thread.ancestors.map(_post),
      _selected(context, thread.post),
      if (state.loading)
        const Padding(
          padding: EdgeInsets.all(24),
          child: Center(child: CircularProgressIndicator()),
        ),
      if (state.error != null)
        Padding(
          padding: const EdgeInsets.all(16),
          child: FullPageErrorWidget(
            error: state.error,
            stackTrace: null,
            prefix: blueskyErrorMessage(l10n, state.error!),
            onRetry: _store.refresh,
          ),
        ),
      if (thread.replies.isNotEmpty) _controls(context, state),
      if (!state.loading && state.error == null && rows.isEmpty)
        Padding(
          key: const ValueKey('bluesky-thread-empty'),
          padding: const EdgeInsets.all(24),
          child: Text(
            state.authorOnly ? l10n.mastodon_thread_no_author_replies : l10n.mastodon_thread_no_replies,
            textAlign: TextAlign.center,
          ),
        ),
    ];
    return FeedListView(
      controller: _scroll,
      physics: const AlwaysScrollableScrollPhysics(),
      padding: const EdgeInsets.only(bottom: 24),
      itemCount: beforeReplies.length + rows.length,
      itemBuilder: (context, index) =>
          index < beforeReplies.length ? beforeReplies[index] : _reply(context, rows[index - beforeReplies.length]),
    );
  }

  String _orderLabel(L10n l10n, BlueskyReplyOrder order) => switch (order) {
    BlueskyReplyOrder.original => l10n.bluesky_thread_sort_default,
    BlueskyReplyOrder.newest => l10n.plugin_mastodon_order_newest,
    BlueskyReplyOrder.oldest => l10n.plugin_mastodon_order_oldest,
    BlueskyReplyOrder.popular => l10n.bluesky_thread_sort_popular,
  };

  Widget _controls(BuildContext context, BlueskyThreadState state) {
    final l10n = L10n.of(context);
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 8),
      child: Wrap(
        spacing: 8,
        runSpacing: 4,
        crossAxisAlignment: WrapCrossAlignment.center,
        children: [
          ChoiceChip(
            key: const ValueKey('bluesky-thread-all'),
            label: Text(l10n.all),
            selected: !state.authorOnly,
            onSelected: (_) => _store.selectAuthor(false),
          ),
          ChoiceChip(
            key: const ValueKey('bluesky-thread-author'),
            label: Text(l10n.mastodon_thread_author),
            selected: state.authorOnly,
            onSelected: (_) => _store.selectAuthor(true),
          ),
          PopupMenuButton<BlueskyReplyOrder>(
            key: const ValueKey('bluesky-thread-sort'),
            tooltip: l10n.plugin_reddit_sort,
            initialValue: state.order,
            onSelected: _store.selectOrder,
            itemBuilder: (context) => [
              for (final order in BlueskyReplyOrder.values)
                CheckedPopupMenuItem(
                  value: order,
                  checked: order == state.order,
                  child: Text(_orderLabel(l10n, order)),
                ),
            ],
            child: Padding(
              padding: const EdgeInsets.symmetric(vertical: 12, horizontal: 8),
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  const Icon(Icons.sort, size: 20),
                  const SizedBox(width: 8),
                  Flexible(child: Text(_orderLabel(l10n, state.order))),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _contextToggle(BuildContext context, BlueskyThreadState state) => ListTile(
    key: const ValueKey('bluesky-thread-context'),
    leading: const Icon(Icons.subdirectory_arrow_right),
    title: Text(L10n.of(context).replying_to),
    subtitle: Text('@${state.thread.ancestors.last.handle}', maxLines: 1, overflow: TextOverflow.ellipsis),
    trailing: Icon(state.contextOpen ? Icons.expand_less : Icons.expand_more),
    onTap: _store.toggleContext,
  );

  Widget _selected(BuildContext context, BlueskyPost post) => Semantics(
    selected: true,
    child: Container(
      key: const ValueKey('bluesky-thread-selected'),
      decoration: BoxDecoration(
        border: BorderDirectional(start: BorderSide(width: 3, color: Theme.of(context).colorScheme.primary)),
      ),
      child: _post(post, selected: true),
    ),
  );

  Widget _reply(BuildContext context, BlueskyReplyRow row) {
    final l10n = L10n.of(context);
    final colors = Theme.of(context).colorScheme;
    final branch = row.branch;
    return Padding(
      key: ValueKey('bluesky-reply-${branch.post.uri}'),
      padding: EdgeInsetsDirectional.only(start: 8.0 + 12.0 * row.depth.clamp(0, 2)),
      child: DecoratedBox(
        decoration: BoxDecoration(
          border: BorderDirectional(start: BorderSide(color: colors.outlineVariant, width: 2)),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            if (branch.contextOnly)
              Padding(
                padding: const EdgeInsetsDirectional.fromSTEB(16, 8, 16, 0),
                child: Text(
                  l10n.mastodon_thread_context,
                  style: Theme.of(context).textTheme.labelMedium?.copyWith(color: colors.onSurfaceVariant),
                ),
              ),
            _post(branch.post),
            if (branch.children.isNotEmpty)
              Align(
                alignment: AlignmentDirectional.centerStart,
                child: TextButton.icon(
                  key: ValueKey('bluesky-collapse-${branch.post.uri}'),
                  onPressed: () => _store.toggle(branch.post.uri),
                  icon: Icon(row.collapsed ? Icons.expand_more : Icons.expand_less),
                  label: Text(
                    '${row.collapsed ? l10n.show : l10n.hide} · ${l10n.plugin_profile_replies} · ${branch.descendants}',
                  ),
                ),
              ),
          ],
        ),
      ),
    );
  }

  Widget _post(BlueskyPost post, {bool selected = false}) => BlueskyPostCard(
    key: ValueKey('thread-post-${post.uri}'),
    post: post,
    showSourceBadge: false,
    openOnTap: !selected,
    onOpen: selected ? () {} : null,
  );
}
