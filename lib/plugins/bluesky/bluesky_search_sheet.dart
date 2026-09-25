import 'dart:async';

import 'package:extended_image/extended_image.dart';
import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/bluesky/bluesky_client.dart';
import 'package:xta/plugins/bluesky/bluesky_import_follows_screen.dart';
import 'package:xta/plugins/bluesky/bluesky_import_list_screen.dart';
import 'package:xta/plugins/bluesky/bluesky_import_starter_screen.dart';
import 'package:xta/plugins/bluesky/bluesky_models.dart';
import 'package:xta/plugins/bluesky/bluesky_post_card.dart';
import 'package:xta/plugins/bluesky/bluesky_profile_screen.dart';
import 'package:xta/plugins/bluesky/bluesky_search_store.dart';
import 'package:xta/plugins/bluesky/bluesky_store.dart';
import 'package:xta/plugins/plugin_search_history.dart';
import 'package:xta/subscriptions/widgets/fallback_avatar.dart';

export 'bluesky_search_store.dart' show BlueskySearchTab;

/// Keeps the existing plugin entry point while giving results a full reading route.
Future<void> showBlueskySearchSheet(
  BuildContext context, {
  String? initialQuery,
  String? initialAuthor,
  BlueskySearchTab initialTab = BlueskySearchTab.people,
}) => Navigator.of(context).push<void>(
  MaterialPageRoute(
    builder: (_) =>
        BlueskySearchScreen(initialQuery: initialQuery, initialAuthor: initialAuthor, initialTab: initialTab),
  ),
);

class BlueskySearchScreen extends StatefulWidget {
  final String? initialQuery;
  final String? initialAuthor;
  final BlueskySearchTab initialTab;
  const BlueskySearchScreen({
    super.key,
    this.initialQuery,
    this.initialAuthor,
    this.initialTab = BlueskySearchTab.people,
  });

  @override
  State<BlueskySearchScreen> createState() => _BlueskySearchScreenState();
}

class _BlueskySearchScreenState extends State<BlueskySearchScreen> {
  late final TextEditingController _controller;
  late final BlueskySearchStore _store;
  late final _BlueskySearchHistoryStore _history;

  @override
  void initState() {
    super.initState();
    _controller = TextEditingController(text: widget.initialQuery ?? '');
    _store = BlueskySearchStore(
      context.read<BlueskyClient>(),
      initialTab: widget.initialTab,
      initialAuthor: widget.initialAuthor ?? '',
    );
    _history = _BlueskySearchHistoryStore(
      readPluginSearchHistory(PrefService.of(context, listen: false), optionPluginBlueskySearchHistory),
    );
    unawaited(_store.search(_controller.text));
  }

  @override
  void dispose() {
    _controller.dispose();
    unawaited(_store.destroy());
    unawaited(_history.destroy());
    super.dispose();
  }

  Future<void> _search() async {
    FocusScope.of(context).unfocus();
    final query = _controller.text.trim();
    final prefs = PrefService.of(context);
    unawaited(_store.search(query));
    await rememberPluginSearch(prefs, optionPluginBlueskySearchHistory, query);
    if (mounted) _history.update(readPluginSearchHistory(prefs, optionPluginBlueskySearchHistory));
  }

  Future<void> _clearHistory() async {
    await clearPluginSearchHistory(PrefService.of(context), optionPluginBlueskySearchHistory);
    if (mounted) _history.update(const []);
  }

  Future<void> _open(Widget page) => Navigator.of(context).push<void>(MaterialPageRoute(builder: (_) => page));

  @override
  Widget build(BuildContext context) => ScopedBuilder<BlueskySearchStore, BlueskySearchState>(
    store: _store,
    onState: (context, state) {
      final l10n = L10n.of(context);
      return Scaffold(
        appBar: AppBar(title: Text(l10n.plugin_bluesky_search)),
        body: SafeArea(
          top: false,
          child: Column(
            children: [
              Padding(
                padding: const EdgeInsets.fromLTRB(16, 0, 16, 8),
                child: TextField(
                  key: const ValueKey('bluesky-search-input'),
                  controller: _controller,
                  autofocus: widget.initialQuery == null,
                  textInputAction: TextInputAction.search,
                  decoration: InputDecoration(
                    hintText: state.tab == BlueskySearchTab.people
                        ? l10n.plugin_bluesky_search_hint
                        : l10n.bluesky_search_link_hint,
                    prefixIcon: const Icon(Icons.search),
                    suffixIcon: IconButton(
                      icon: const Icon(Icons.arrow_forward),
                      tooltip: l10n.plugin_bluesky_search,
                      onPressed: _search,
                    ),
                  ),
                  onSubmitted: (_) => _search(),
                ),
              ),
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 16),
                child: Row(
                  children: [
                    Expanded(child: _tab(l10n.plugin_bluesky_search_people, BlueskySearchTab.people, state)),
                    Expanded(child: _tab(l10n.plugin_bluesky_search_posts, BlueskySearchTab.posts, state)),
                  ],
                ),
              ),
              if (state.tab == BlueskySearchTab.posts) _postFilters(context, state),
              Expanded(child: _body(context, state)),
            ],
          ),
        ),
      );
    },
  );

  Widget _tab(String label, BlueskySearchTab tab, BlueskySearchState state) => Padding(
    padding: const EdgeInsets.symmetric(horizontal: 4),
    child: ChoiceChip(
      label: Center(child: Text(label)),
      selected: state.tab == tab,
      onSelected: (_) => _store.select(tab),
    ),
  );

  Widget _postFilters(BuildContext context, BlueskySearchState state) {
    final l10n = L10n.of(context);
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
      child: Wrap(
        spacing: 8,
        runSpacing: 4,
        crossAxisAlignment: WrapCrossAlignment.center,
        children: [
          ChoiceChip(
            label: Text(l10n.bluesky_search_latest),
            selected: state.sort == BlueskySearchSort.latest,
            onSelected: (_) => _store.setFilters(sort: BlueskySearchSort.latest),
          ),
          ChoiceChip(
            label: Text(l10n.bluesky_search_top),
            selected: state.sort == BlueskySearchSort.top,
            onSelected: (_) => _store.setFilters(sort: BlueskySearchSort.top),
          ),
          ActionChip(
            avatar: const Icon(Icons.tune, size: 18),
            label: Text(l10n.filters),
            onPressed: () => _showFilters(state),
          ),
          if (state.author.isNotEmpty)
            InputChip(
              label: Text('@${state.author}'),
              onDeleted: () => _store.setFilters(author: ''),
            ),
          if (state.tag.isNotEmpty)
            InputChip(
              label: Text('#${state.tag}'),
              onDeleted: () => _store.setFilters(tag: ''),
            ),
        ],
      ),
    );
  }

  Future<void> _showFilters(BlueskySearchState state) async {
    final result = await showDialog<({String author, String tag})>(
      context: context,
      builder: (_) => _BlueskySearchFiltersDialog(author: state.author, tag: state.tag),
    );
    if (mounted && result != null) await _store.setFilters(author: result.author, tag: result.tag);
  }

  Widget _body(BuildContext context, BlueskySearchState state) {
    if (state.query.isEmpty) return _landing(context, state);
    if (state.tab == BlueskySearchTab.people) {
      return _results(context, state.people, (person) => _personTile(context, person));
    }
    return _results(context, state.posts, (post) => BlueskyPostCard(post: post));
  }

  Widget _results<T>(BuildContext context, BlueskySearchPage<T> page, Widget Function(T) item) {
    final l10n = L10n.of(context);
    return RefreshIndicator(
      onRefresh: _store.refresh,
      child: ListView.builder(
        key: PageStorageKey('bluesky-search-${_store.state.tab.name}'),
        physics: const AlwaysScrollableScrollPhysics(),
        itemCount: page.items.length + 2,
        itemBuilder: (context, index) {
          if (index == 0) {
            return Column(
              children: [
                if (page.loading) const LinearProgressIndicator(),
                if (page.error != null && !page.retryMore)
                  _error(context, page.error!, page.retryMore ? _store.loadMore : _store.refresh),
                if (!page.loading && page.error == null && page.items.isEmpty)
                  Padding(padding: const EdgeInsets.all(32), child: Text(l10n.plugin_bluesky_no_results)),
              ],
            );
          }
          if (index <= page.items.length) return item(page.items[index - 1]);
          if (page.loadingMore) {
            return const Padding(
              padding: EdgeInsets.all(20),
              child: Center(child: CircularProgressIndicator()),
            );
          }
          if (page.error != null && page.retryMore) return _error(context, page.error!, _store.loadMore);
          if (page.cursor != null) {
            return Padding(
              padding: const EdgeInsets.all(16),
              child: OutlinedButton(
                onPressed: page.loading ? null : _store.loadMore,
                child: Text(l10n.bluesky_search_more),
              ),
            );
          }
          if (!page.loading && page.items.isNotEmpty) {
            return Padding(
              padding: const EdgeInsets.all(24),
              child: Center(child: Text(l10n.bluesky_search_end)),
            );
          }
          return const SizedBox.shrink();
        },
      ),
    );
  }

  Widget _landing(BuildContext context, BlueskySearchState state) {
    final l10n = L10n.of(context);
    return ListView(
      children: [
        ScopedBuilder<_BlueskySearchHistoryStore, List<String>>(
          store: _history,
          onState: (context, recent) => recent.isEmpty
              ? const SizedBox.shrink()
              : Padding(
                  padding: const EdgeInsets.fromLTRB(16, 12, 16, 8),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(l10n.plugin_bluesky_recent_searches, style: Theme.of(context).textTheme.titleSmall),
                      Wrap(
                        spacing: 8,
                        children: [
                          for (final query in recent.take(8))
                            ActionChip(
                              label: Text(query),
                              onPressed: () {
                                _controller.text = query;
                                _search();
                              },
                            ),
                        ],
                      ),
                      TextButton.icon(
                        onPressed: _clearHistory,
                        icon: const Icon(Icons.delete_outline),
                        label: Text(l10n.clear_recent_searches),
                      ),
                    ],
                  ),
                ),
        ),
        if (state.tab == BlueskySearchTab.posts)
          Padding(padding: const EdgeInsets.all(24), child: Text(l10n.bluesky_search_link_hint))
        else ...[
          ListTile(
            leading: const Icon(Icons.group_add_outlined),
            title: Text(l10n.plugin_bluesky_import_following),
            onTap: () => _open(const BlueskyImportFollowsScreen()),
          ),
          ListTile(
            leading: const Icon(Icons.list_alt_outlined),
            title: Text(l10n.plugin_bluesky_import_list),
            onTap: () => _open(const BlueskyImportListScreen()),
          ),
          ListTile(
            leading: const Icon(Icons.auto_awesome_outlined),
            title: Text(l10n.plugin_bluesky_import_starter),
            onTap: () => _open(const BlueskyImportStarterPackScreen()),
          ),
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 16, 16, 4),
            child: Text(l10n.plugin_bluesky_suggested, style: Theme.of(context).textTheme.titleSmall),
          ),
          if (state.suggestions.loading) const LinearProgressIndicator(),
          if (state.suggestions.error != null) _error(context, state.suggestions.error!, _store.loadSuggestions),
          for (final person in state.suggestions.items) _personTile(context, person),
        ],
      ],
    );
  }

  Widget _error(BuildContext context, Object error, VoidCallback retry) => Padding(
    padding: const EdgeInsets.all(16),
    child: Column(
      children: [
        Text(blueskyErrorMessage(L10n.of(context), error), textAlign: TextAlign.center),
        TextButton.icon(onPressed: retry, icon: const Icon(Icons.refresh), label: Text(L10n.of(context).retry)),
      ],
    ),
  );

  Widget _personTile(BuildContext context, BlueskyProfile profile) =>
      ScopedBuilder<BlueskyAccountsStore, List<BlueskyAccount>>(
        store: context.read<BlueskyAccountsStore>(),
        onState: (context, _) {
          final l10n = L10n.of(context);
          final accounts = context.read<BlueskyAccountsStore>();
          final following = accounts.follows(profile.handle);
          return ListTile(
            leading: _avatar(context, profile),
            title: Text(profile.displayName, maxLines: 2, overflow: TextOverflow.ellipsis),
            subtitle: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('@${profile.handle}', maxLines: 1, overflow: TextOverflow.ellipsis),
                Align(
                  alignment: AlignmentDirectional.centerStart,
                  child: TextButton(
                    onPressed: () => following ? accounts.remove(profile.handle) : accounts.add(profile.toAccount()),
                    child: Text(following ? l10n.plugin_bluesky_unfollow : l10n.plugin_bluesky_follow),
                  ),
                ),
              ],
            ),
            onTap: () => _open(BlueskyProfileScreen(actor: profile.did.isNotEmpty ? profile.did : profile.handle)),
          );
        },
      );

  Widget _avatar(BuildContext context, BlueskyProfile profile) => ClipOval(
    child: profile.avatarUrl == null
        ? FallbackAvatar(
            seed: profile.handle,
            displayName: profile.displayName,
            size: 40,
            accent: Theme.of(context).colorScheme.primary,
          )
        : ExtendedImage.network(
            profile.avatarUrl!,
            width: 40,
            height: 40,
            fit: BoxFit.cover,
            cacheWidth: (40 * MediaQuery.devicePixelRatioOf(context)).ceil(),
          ),
  );
}

class _BlueskySearchHistoryStore extends Store<List<String>> {
  _BlueskySearchHistoryStore(super.initialState);
}

class _BlueskySearchFiltersDialog extends StatefulWidget {
  final String author;
  final String tag;
  const _BlueskySearchFiltersDialog({required this.author, required this.tag});
  @override
  State<_BlueskySearchFiltersDialog> createState() => _BlueskySearchFiltersDialogState();
}

class _BlueskySearchFiltersDialogState extends State<_BlueskySearchFiltersDialog> {
  late final _author = TextEditingController(text: widget.author);
  late final _tag = TextEditingController(text: widget.tag);
  final _form = GlobalKey<FormState>();
  @override
  void dispose() {
    _author.dispose();
    _tag.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return AlertDialog(
      title: Text(l10n.filters),
      content: SingleChildScrollView(
        child: Form(
          key: _form,
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              TextFormField(
                controller: _author,
                decoration: InputDecoration(labelText: l10n.bluesky_search_author),
                validator: (value) => (value ?? '').trim().isNotEmpty && blueskySearchActor(value!) == null
                    ? l10n.bluesky_search_invalid_author
                    : null,
              ),
              const SizedBox(height: 16),
              TextFormField(
                controller: _tag,
                decoration: InputDecoration(labelText: l10n.bluesky_search_tag),
                textInputAction: TextInputAction.done,
              ),
            ],
          ),
        ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(context, (author: '', tag: '')),
          child: Text(l10n.plugin_reader_reset_filters),
        ),
        TextButton(onPressed: () => Navigator.pop(context), child: Text(l10n.cancel)),
        FilledButton(
          onPressed: () {
            if (!_form.currentState!.validate()) return;
            Navigator.pop(context, (
              author: blueskySearchActor(_author.text) ?? '',
              tag: _tag.text.trim().replaceFirst(RegExp(r'^#+'), ''),
            ));
          },
          child: Text(l10n.search),
        ),
      ],
    );
  }
}
