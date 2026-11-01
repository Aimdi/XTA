import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/plugin_view_store.dart';
import 'package:xta/plugins/threads/threads_direct_client.dart';
import 'package:xta/plugins/threads/threads_models.dart';
import 'package:xta/plugins/threads/threads_post_card.dart';
import 'package:xta/plugins/threads/threads_profile_screen.dart';
import 'package:xta/plugins/threads/threads_search_store.dart';
import 'package:xta/tweet/tweet_skeleton.dart';
import 'package:xta/ui/feed_list.dart';
import 'package:xta/ui/reader_failure.dart';
import 'package:xta/utils/urls.dart';

/// Posts Threads' public search finds for words, or files under a tag, top or most recent, as a guest sees them
/// (docs/specs/threads-x-parity.md, Search). One page each: Threads names no cursor a guest can follow.
///
/// [profile] is a handle the words also spell: a row on top opens it, so typing a handle without its @ still gets
/// there in one tap.
class ThreadsSearchScreen extends StatefulWidget {
  final String query;
  final bool tag;
  final String? profile;

  const ThreadsSearchScreen({super.key, required this.query, this.tag = false, this.profile});

  @override
  State<ThreadsSearchScreen> createState() => _ThreadsSearchScreenState();
}

class _ThreadsSearchScreenState extends State<ThreadsSearchScreen> {
  final _order = PluginViewStore(ThreadsSearchOrder.top);
  late final Map<ThreadsSearchOrder, ThreadsSearchStore> _results;

  String get _query => widget.tag ? widget.query.replaceFirst(RegExp('^#'), '') : widget.query;

  @override
  void initState() {
    super.initState();
    final direct = context.read<ThreadsDirectClient>();
    _results = {
      for (final order in ThreadsSearchOrder.values)
        order: ThreadsSearchStore(direct, query: _query, tag: widget.tag, order: order),
    };
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) _results[ThreadsSearchOrder.top]!.load();
    });
  }

  @override
  void dispose() {
    _order.destroy();
    for (final store in _results.values) {
      store.destroy();
    }
    super.dispose();
  }

  void _select(ThreadsSearchOrder order) {
    _order.select(order);
    final store = _results[order]!;
    if (store.state == null && !store.isLoading) store.load();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return Scaffold(
      appBar: AppBar(
        title: Text(_query, maxLines: 1, overflow: TextOverflow.ellipsis),
        actions: [
          IconButton(
            tooltip: l10n.open_in_browser,
            icon: const Icon(Icons.open_in_new),
            onPressed: () => openUri(
              context,
              threadsSearchUri(_query, tag: widget.tag, recent: _order.state == ThreadsSearchOrder.recent).toString(),
            ),
          ),
        ],
      ),
      body: ScopedBuilder<PluginViewStore<ThreadsSearchOrder>, ThreadsSearchOrder>(
        store: _order,
        onState: (context, order) => Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 8, 16, 4),
              child: Wrap(
                spacing: 8,
                children: [
                  for (final (option, label) in [
                    (ThreadsSearchOrder.top, l10n.sort_top),
                    (ThreadsSearchOrder.recent, l10n.recent),
                  ])
                    ChoiceChip(label: Text(label), selected: order == option, onSelected: (_) => _select(option)),
                ],
              ),
            ),
            if (widget.profile case final handle?) _ProfileRow(handle: handle),
            Expanded(
              child: _ThreadsSearchResults(key: ValueKey(order), store: _results[order]!),
            ),
          ],
        ),
      ),
    );
  }
}

/// The profile the words also spell.
class _ProfileRow extends StatelessWidget {
  final String handle;

  const _ProfileRow({required this.handle});

  @override
  Widget build(BuildContext context) => ListTile(
    leading: const Icon(Icons.person_outline),
    title: Text(L10n.of(context).plugin_threads_open_handle(handle)),
    trailing: const Icon(Icons.chevron_right),
    onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => ThreadsProfileScreen(username: handle))),
  );
}

class _ThreadsSearchResults extends StatelessWidget {
  final ThreadsSearchStore store;

  const _ThreadsSearchResults({super.key, required this.store});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return TripleBuilder<ThreadsSearchStore, List<ThreadsPost>?>(
      store: store,
      builder: (context, triple) {
        final posts = triple.state;
        final error = triple.error;
        if (posts == null && error != null) {
          return ListView(
            children: [
              ReaderFailureNotice(
                source: pluginIdThreads,
                error: error,
                contextMessage: threadsApiErrorMessage(l10n, error),
                onRetry: store.load,
              ),
            ],
          );
        }
        if (posts == null) return const TweetFeedSkeleton();
        return RefreshIndicator(
          onRefresh: store.load,
          child: posts.isEmpty
              ? ListView(
                  physics: const AlwaysScrollableScrollPhysics(),
                  padding: const EdgeInsets.all(24),
                  children: [Text(l10n.no_results, textAlign: TextAlign.center)],
                )
              : FeedListView(
                  physics: const AlwaysScrollableScrollPhysics(),
                  itemCount: posts.length,
                  itemBuilder: (context, index) =>
                      ThreadsPostCard(key: ValueKey(posts[index].id), post: posts[index], showSourceBadge: false),
                ),
        );
      },
    );
  }
}
