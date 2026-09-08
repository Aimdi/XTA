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
  void dispose() { _store.destroy(); _scroll.dispose(); super.dispose(); }

  @override
  Widget build(BuildContext context) => ScopedBuilder<BlueskyThreadStore, BlueskyThreadState>(
    store: _store,
    onState: (context, state) => Scaffold(
      appBar: AppBar(title: Text(L10n.of(context).thread), actions: [
        IconButton(tooltip: L10n.of(context).open_in_browser,
          onPressed: () => openUri(context, state.thread.post.url), icon: const Icon(Icons.open_in_new)),
      ]),
      body: RefreshIndicator(onRefresh: _store.refresh, child: _conversation(context, state)),
    ),
  );

  Widget _conversation(BuildContext context, BlueskyThreadState state) {
    final l10n = L10n.of(context);
    final thread = state.thread;
    final rows = blueskyVisibleReplies(state.branches, state.collapsed);
    final contextCount = state.contextOpen ? thread.ancestors.length : 0;
    final hasContext = thread.ancestors.isNotEmpty;
    final selectedIndex = contextCount + (hasContext ? 1 : 0);
    final statusCount = state.loading || state.error != null ? 1 : 0;
    final repliesStart = selectedIndex + 1 + statusCount;
    return FeedListView(
      controller: _scroll, physics: const AlwaysScrollableScrollPhysics(),
      padding: const EdgeInsets.only(bottom: 24),
      itemCount: repliesStart + rows.length,
      itemBuilder: (context, index) {
        if (hasContext && index == 0) return _contextToggle(context, state);
        if (index < selectedIndex) return _post(thread.ancestors[index - 1]);
        if (index == selectedIndex) return _selected(context, thread.post);
        if (index < repliesStart) {
          if (state.error == null) return const Padding(padding: EdgeInsets.all(24),
            child: Center(child: CircularProgressIndicator()));
          return Padding(padding: const EdgeInsets.all(16), child: FullPageErrorWidget(
            error: state.error, stackTrace: null,
            prefix: blueskyErrorMessage(l10n, state.error!), onRetry: _store.refresh));
        }
        return _reply(context, rows[index - repliesStart]);
      },
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
      decoration: BoxDecoration(border: BorderDirectional(
        start: BorderSide(width: 3, color: Theme.of(context).colorScheme.primary))),
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
        decoration: BoxDecoration(border: BorderDirectional(start: BorderSide(color: colors.outlineVariant, width: 2))),
        child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
          _post(branch.post),
          if (branch.children.isNotEmpty) Align(alignment: AlignmentDirectional.centerStart,
            child: TextButton.icon(
              key: ValueKey('bluesky-collapse-${branch.post.uri}'),
              onPressed: () => _store.toggle(branch.post.uri),
              icon: Icon(row.collapsed ? Icons.expand_more : Icons.expand_less),
              label: Text('${row.collapsed ? l10n.show : l10n.hide} · ${l10n.plugin_profile_replies} · ${branch.descendants}'),
            )),
        ]),
      ),
    );
  }

  Widget _post(BlueskyPost post, {bool selected = false}) => BlueskyPostCard(
    key: ValueKey('thread-post-${post.uri}'), post: post, showSourceBadge: false,
    openOnTap: !selected, onOpen: selected ? () {} : null,
  );
}
