import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/plugins/mastodon/mastodon_search_store.dart';
import 'package:flutter/material.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/mastodon/mastodon_client.dart';
import 'package:xta/plugins/mastodon/mastodon_models.dart';
import 'package:xta/plugins/mastodon/mastodon_post_card.dart';
import 'package:xta/plugins/mastodon/mastodon_profile_screen.dart';
import 'package:xta/plugins/mastodon/mastodon_store.dart';
import 'package:xta/plugins/mastodon/mastodon_tag_store.dart';
import 'package:xta/ui/feed_list.dart';
import 'package:xta/plugins/mastodon/mastodon_search_results.dart';
import 'package:xta/search/recent_searches_store.dart';
import 'package:xta/search/recent_searches_bar.dart';

/// Kept as an entry-point alias for plugin search; discovery now has a full route.
Future<void> showMastodonSearchSheet(BuildContext context, {String? initialQuery}) =>
    Navigator.push<void>(context, MaterialPageRoute(builder: (_) => MastodonSearchScreen(initialQuery: initialQuery)));

List<String> _discoveryInstances(BuildContext context) {
  final configured = mastodonConfiguredInstances(PrefService.of(context, listen: false));
  final ordered = [...configured, ...kMastodonDefaultInstances];
  final seen = <String>{};
  return [
    for (final candidate in ordered)
      if (normaliseMastodonInstance(candidate) case final instance? when seen.add(instance)) instance,
  ];
}

class MastodonSearchScreen extends StatefulWidget {
  final String? initialQuery;

  const MastodonSearchScreen({super.key, this.initialQuery});

  @override
  State<MastodonSearchScreen> createState() => _MastodonSearchScreenState();
}

class _MastodonSearchScreenState extends State<MastodonSearchScreen> {
  late final TextEditingController _controller;
  late final MastodonSearchStore _store;
  final _positions = [ScrollController(), ScrollController(), ScrollController()];
  late final RecentSearchesStore _history;

  @override
  void initState() {
    super.initState();
    _controller = TextEditingController(text: widget.initialQuery ?? '');
    _store = MastodonSearchStore(context.read<MastodonClient>(), _discoveryInstances(context));
    _history = RecentSearchesStore(PrefService.of(context, listen: false));
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) _search();
    });
  }

  @override
  void dispose() {
    _controller.dispose();
    for (final controller in _positions) {
      controller.dispose();
    }
    _store.destroy();
    _history.destroy();
    super.dispose();
  }

  Future<void> _search() async {
    final query = _controller.text;
    FocusScope.of(context).unfocus();
    for (final controller in _positions) {
      if (controller.hasClients) controller.jumpTo(0);
    }
    await _history.remember('mastodon', query);
    if (!mounted || _controller.text != query) return;
    await _store.search(query);
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return Scaffold(
      appBar: AppBar(title: Text(l10n.plugin_mastodon_search)),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 12),
            child: TextField(
              key: const ValueKey('mastodon-search-field'),
              controller: _controller,
              textInputAction: TextInputAction.search,
              decoration: InputDecoration(
                hintText: l10n.plugin_mastodon_search_hint,
                prefixIcon: const Icon(Icons.search),
                filled: true,
                border: OutlineInputBorder(borderRadius: BorderRadius.circular(28), borderSide: BorderSide.none),
                suffixIcon: IconButton(
                  icon: const Icon(Icons.arrow_forward),
                  tooltip: l10n.plugin_mastodon_search,
                  onPressed: _search,
                ),
              ),
              onSubmitted: (_) => _search(),
            ),
          ),
          Expanded(
            child: ScopedBuilder<MastodonSearchStore, MastodonSearchState>(
              store: _store,
              onState: (context, state) => KeyedSubtree(
                key: PageStorageKey('mastodon-search-${state.query}'),
                child: Column(
                  children: [
                    if (state.query.isEmpty)
                      RecentSearchesBar(
                        store: _history,
                        scope: 'mastodon',
                        onSelected: (query) {
                          _controller.text = query;
                          _search();
                        },
                      ),
                    Expanded(
                      child: MastodonSearchResults(
                        state: state,
                        onRetry: _search,
                        onSelected: _store.select,
                        positions: _positions,
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

/// Public posts for one hashtag on the reader's discovery instances.
class MastodonTagScreen extends StatefulWidget {
  final String tag;

  const MastodonTagScreen({super.key, required this.tag});

  @override
  State<MastodonTagScreen> createState() => _MastodonTagScreenState();
}

class _MastodonTagScreenState extends State<MastodonTagScreen> {
  late final MastodonTagStore _store;
  final _scroll = ScrollController();

  @override
  void initState() {
    super.initState();
    _store = MastodonTagStore(context.read<MastodonClient>(), _discoveryInstances(context), widget.tag);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) _store.refresh();
    });
  }

  @override
  void dispose() {
    _store.destroy();
    _scroll.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final title = '#${widget.tag.replaceFirst(RegExp(r'^#'), '')}';
    return Scaffold(
      appBar: AppBar(
        title: Text(title),
        actions: [
          IconButton(
            key: const ValueKey('mastodon-tag-refresh'),
            tooltip: l10n.retry,
            onPressed: _store.refresh,
            icon: const Icon(Icons.refresh),
          ),
        ],
      ),
      body: ScopedBuilder<MastodonTagStore, MastodonTagState>(
        store: _store,
        onState: (context, state) => _body(context, state),
      ),
    );
  }

  Widget _body(BuildContext context, MastodonTagState state) {
    final l10n = L10n.of(context);
    if (state.posts.isEmpty && state.loading) return const Center(child: CircularProgressIndicator());
    if (state.posts.isEmpty) {
      return RefreshIndicator(
        onRefresh: _store.refresh,
        child: ListView(
          physics: const AlwaysScrollableScrollPhysics(),
          padding: const EdgeInsets.all(24),
          children: [
            Text(
              state.error == null ? l10n.plugin_mastodon_no_posts : mastodonErrorMessage(l10n, state.error!),
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 16),
            Center(
              child: FilledButton.icon(
                onPressed: _store.refresh,
                icon: const Icon(Icons.refresh),
                label: Text(l10n.retry),
              ),
            ),
          ],
        ),
      );
    }
    return Column(
      children: [
        if (state.loading) const LinearProgressIndicator(),
        if (state.error != null) _notice(l10n.plugin_mastodon_refresh_failed, _store.refresh),
        Expanded(
          child: NotificationListener<ScrollNotification>(
            onNotification: (notification) {
              if (notification.depth == 0 &&
                  notification is ScrollUpdateNotification &&
                  notification.metrics.extentAfter < 800 &&
                  state.moreError == null) {
                _store.loadMore();
              }
              return false;
            },
            child: RefreshIndicator(
              onRefresh: _store.refresh,
              child: FeedListView(
                controller: _scroll,
                physics: const AlwaysScrollableScrollPhysics(),
                itemCount: state.posts.length + 1,
                itemBuilder: (context, index) => index == state.posts.length
                    ? _footer(state)
                    : MastodonPostCard(
                        key: ValueKey(canonicalMastodonPostKey(state.posts[index])),
                        post: state.posts[index],
                        showSourceBadge: false,
                      ),
              ),
            ),
          ),
        ),
      ],
    );
  }

  Widget _notice(String message, VoidCallback retry) => Padding(
    padding: const EdgeInsets.fromLTRB(16, 8, 16, 8),
    child: Column(
      children: [
        Text(message, textAlign: TextAlign.center),
        TextButton.icon(onPressed: retry, icon: const Icon(Icons.refresh), label: Text(L10n.of(context).retry)),
      ],
    ),
  );

  Widget _footer(MastodonTagState state) {
    final l10n = L10n.of(context);
    if (state.loadingMore) {
      return const Padding(
        padding: EdgeInsets.all(16),
        child: Center(child: CircularProgressIndicator()),
      );
    }
    if (state.moreError != null) return _notice(l10n.plugin_mastodon_load_more_failed, _store.loadMore);
    if (!state.hasMore) return const SizedBox(height: 24);
    return Padding(
      padding: const EdgeInsets.all(16),
      child: Center(
        child: OutlinedButton.icon(
          key: const ValueKey('mastodon-tag-load-more'),
          onPressed: state.canLoadMore ? _store.loadMore : null,
          icon: const Icon(Icons.expand_more),
          label: Text(l10n.plugin_mastodon_load_more),
        ),
      ),
    );
  }
}
