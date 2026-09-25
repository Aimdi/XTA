import 'dart:async';

import 'package:extended_image/extended_image.dart';
import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/plugin_filter_row.dart';
import 'package:xta/plugins/plugin_search_history.dart';
import 'package:xta/plugins/substack/substack_archive_screen.dart';
import 'package:xta/plugins/substack/substack_client.dart';
import 'package:xta/plugins/substack/substack_group.dart';
import 'package:xta/plugins/substack/substack_models.dart';
import 'package:xta/plugins/substack/substack_post_card.dart';
import 'package:xta/plugins/substack/substack_search_store.dart';
import 'package:xta/plugins/substack/substack_store.dart';
import 'package:xta/subscriptions/users_model.dart';
import 'package:xta/subscriptions/widgets/fallback_avatar.dart';

/// Compatible entry point; the route now leaves room for articles and large text.
Future<bool?> showSubstackSearchSheet(BuildContext context, {String? initialQuery}) async {
  var changed = false;
  await Navigator.push<void>(
    context,
    MaterialPageRoute(
      builder: (_) => SubstackSearchScreen(initialQuery: initialQuery, onChanged: () => changed = true),
    ),
  );
  return changed;
}

class SubstackSearchScreen extends StatefulWidget {
  final String? initialQuery;
  final VoidCallback? onChanged;
  const SubstackSearchScreen({super.key, this.initialQuery, this.onChanged});
  @override
  State<SubstackSearchScreen> createState() => _SubstackSearchScreenState();
}

class _SubstackSearchScreenState extends State<SubstackSearchScreen> {
  late final TextEditingController _query;
  late final SubstackSearchStore _store;
  late final _SearchHistoryStore _history;
  final _actions = _FollowActions();

  @override
  void initState() {
    super.initState();
    _query = TextEditingController(text: widget.initialQuery ?? '');
    final publications = context.read<SubstackPublicationsStore>();
    _store = SubstackSearchStore(context.read<SubstackClient>(), followed: () => publications.state);
    _history = _SearchHistoryStore(
      readPluginSearchHistory(PrefService.of(context, listen: false), substackSearchHistoryKey),
    );
    unawaited(_store.search(_query.text));
  }

  @override
  void dispose() {
    _query.dispose();
    unawaited(_store.destroy());
    unawaited(_history.destroy());
    unawaited(_actions.destroy());
    super.dispose();
  }

  Future<void> _search() async {
    FocusScope.of(context).unfocus();
    final query = _query.text.trim();
    unawaited(_store.search(query));
    final prefs = PrefService.of(context);
    await rememberPluginSearch(prefs, substackSearchHistoryKey, query);
    if (mounted) _history.update(readPluginSearchHistory(prefs, substackSearchHistoryKey));
  }

  Future<void> _clearHistory() async {
    await clearPluginSearchHistory(PrefService.of(context), substackSearchHistoryKey);
    if (mounted) _history.update(const []);
  }

  Future<void> _follow(SubstackPublication publication) async {
    final publications = context.read<SubstackPublicationsStore>();
    final subscriptions = context.read<SubscriptionsModel>();
    final success = await _actions.run(publication.id, () async {
      await publications.add(publication);
      if (!publications.state.any((item) => item.id == publication.id)) throw StateError('Follow was not saved');
      await subscriptions.reloadSubscriptions();
    });
    if (!mounted) return;
    if (success) widget.onChanged?.call();
    final l10n = L10n.of(context);
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(
          success ? l10n.plugin_substack_followed(publication.displayName) : l10n.plugin_substack_add_error,
        ),
      ),
    );
  }

  Future<void> _group(SubstackPublication publication) async {
    try {
      await addSubstackPublicationToGroup(context, publication);
      if (mounted) widget.onChanged?.call();
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(L10n.of(context).plugin_substack_add_error)));
      }
    }
  }

  @override
  Widget build(BuildContext context) => ScopedBuilder<SubstackSearchStore, SubstackSearchState>(
    store: _store,
    onState: (context, state) {
      final l10n = L10n.of(context);
      return Scaffold(
        appBar: AppBar(title: Text(l10n.plugin_substack_discover)),
        body: SafeArea(
          top: false,
          child: Column(
            children: [
              Padding(
                padding: const EdgeInsets.fromLTRB(16, 0, 16, 8),
                child: TextField(
                  key: const ValueKey('substack-search-input'),
                  controller: _query,
                  textInputAction: TextInputAction.search,
                  decoration: InputDecoration(
                    hintText: state.tab == SubstackSearchTab.publications
                        ? l10n.plugin_substack_search_hint
                        : l10n.substack_search_posts_hint,
                    prefixIcon: const Icon(Icons.search),
                    suffixIcon: IconButton(
                      onPressed: _search,
                      icon: const Icon(Icons.arrow_forward),
                      tooltip: l10n.search,
                    ),
                  ),
                  onSubmitted: (_) => _search(),
                ),
              ),
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 16),
                child: Row(
                  children: [
                    Expanded(
                      child: ChoiceChip(
                        label: Center(child: Text(l10n.substack_search_publications)),
                        selected: state.tab == SubstackSearchTab.publications,
                        onSelected: (_) => _store.select(SubstackSearchTab.publications),
                      ),
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: ChoiceChip(
                        label: Center(child: Text(l10n.plugin_substack_tab_posts)),
                        selected: state.tab == SubstackSearchTab.posts,
                        onSelected: (_) => _store.select(SubstackSearchTab.posts),
                      ),
                    ),
                  ],
                ),
              ),
              Expanded(child: _results(context, state)),
            ],
          ),
        ),
      );
    },
  );

  Widget _results(BuildContext context, SubstackSearchState state) {
    final l10n = L10n.of(context);
    final items = <Widget>[
      if (state.query.isEmpty) _recent(context),
      if (state.tab == SubstackSearchTab.publications && state.query.isEmpty) ...[
        Padding(padding: const EdgeInsets.all(16), child: Text(l10n.plugin_substack_discover_intro)),
        if (state.categoriesLoading) const LinearProgressIndicator(),
        if (state.categoriesError != null) _failure(l10n.plugin_substack_load_error, _store.loadCategories),
        if (state.categories.isNotEmpty)
          PluginFilterRow(
            children: [
              for (final category in state.categories)
                ChoiceChip(
                  label: Text(category.name),
                  selected: category.id == state.category?.id,
                  onSelected: (_) {
                    _query.clear();
                    _store.browse(category);
                  },
                ),
            ],
          ),
      ],
      if (state.tab == SubstackSearchTab.posts && !state.direct)
        Padding(padding: const EdgeInsets.all(16), child: Text(l10n.substack_search_followed_posts)),
      if (state.loading) const LinearProgressIndicator(),
      if (state.error != null && !state.retryMore)
        _failure(
          state.direct && state.publications.isNotEmpty
              ? l10n.substack_preview_post_failed
              : l10n.plugin_substack_load_error,
          _store.refresh,
        ),
      if (state.failedCount > 0) _failure(l10n.substack_search_partial, _store.retryFailedPosts),
      if (state.direct || state.tab == SubstackSearchTab.publications)
        for (final publication in state.publications) _publication(publication),
      if (state.tab == SubstackSearchTab.posts)
        for (final post in state.posts) SubstackPostCard(post: post),
      if (_empty(state))
        Padding(
          padding: const EdgeInsets.all(32),
          child: Text(
            state.tab == SubstackSearchTab.publications
                ? l10n.plugin_substack_search_empty
                : context.read<SubstackPublicationsStore>().state.isEmpty && !state.direct
                ? l10n.substack_search_no_follows
                : state.query.isEmpty
                ? l10n.substack_search_posts_hint
                : l10n.no_results,
            textAlign: TextAlign.center,
          ),
        ),
      if (state.loadingMore)
        const Padding(
          padding: EdgeInsets.all(20),
          child: Center(child: CircularProgressIndicator()),
        ),
      if (state.error != null && state.retryMore)
        _failure(l10n.plugin_substack_load_error, _store.loadMore)
      else if (state.hasMore && !state.loadingMore)
        Padding(
          padding: const EdgeInsets.all(16),
          child: OutlinedButton(
            onPressed: state.loading ? null : _store.loadMore,
            child: Text(l10n.plugin_substack_load_more),
          ),
        ),
    ];
    return RefreshIndicator(
      onRefresh: _store.refresh,
      child: ListView.builder(
        physics: const AlwaysScrollableScrollPhysics(),
        itemCount: items.length,
        itemBuilder: (_, index) => items[index],
      ),
    );
  }

  bool _empty(SubstackSearchState state) =>
      !state.loading &&
      !state.categoriesLoading &&
      state.error == null &&
      state.failedCount == 0 &&
      (state.tab == SubstackSearchTab.publications
          ? state.publications.isEmpty && state.query.isNotEmpty
          : state.posts.isEmpty);

  Widget _recent(BuildContext context) => ScopedBuilder<_SearchHistoryStore, List<String>>(
    store: _history,
    onState: (context, recent) => recent.isEmpty
        ? const SizedBox.shrink()
        : Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(L10n.of(context).substack_recent_searches, style: Theme.of(context).textTheme.titleSmall),
                Wrap(
                  spacing: 8,
                  children: [
                    for (final query in recent.take(8))
                      ActionChip(
                        label: Text(query),
                        onPressed: () {
                          _query.text = query;
                          _search();
                        },
                      ),
                  ],
                ),
                TextButton.icon(
                  onPressed: _clearHistory,
                  icon: const Icon(Icons.delete_outline),
                  label: Text(L10n.of(context).clear_recent_searches),
                ),
              ],
            ),
          ),
  );

  Widget _publication(SubstackPublication publication) => ScopedBuilder<_FollowActions, Set<String>>(
    store: _actions,
    onState: (context, busy) => SubstackDiscoveryPublicationTile(
      publication: publication,
      busy: busy.contains(publication.id),
      onFollow: () => _follow(publication),
      onGroup: () => _group(publication),
    ),
  );

  Widget _failure(String message, VoidCallback retry) => Padding(
    padding: const EdgeInsets.all(16),
    child: Column(
      children: [
        Text(message, textAlign: TextAlign.center),
        TextButton.icon(onPressed: retry, icon: const Icon(Icons.refresh), label: Text(L10n.of(context).retry)),
      ],
    ),
  );
}

class SubstackDiscoveryPublicationTile extends StatelessWidget {
  final SubstackPublication publication;
  final bool busy;
  final VoidCallback? onFollow;
  final VoidCallback? onGroup;
  const SubstackDiscoveryPublicationTile({
    super.key,
    required this.publication,
    this.busy = false,
    this.onFollow,
    this.onGroup,
  });
  @override
  Widget build(BuildContext context) => ScopedBuilder<SubstackPublicationsStore, List<SubstackPublication>>(
    store: context.read<SubstackPublicationsStore>(),
    onState: (context, followed) {
      final l10n = L10n.of(context);
      final following = followed.any((pub) => pub.id == publication.id);
      return ListTile(
        leading: _logo(context),
        title: Text(publication.displayName, maxLines: 2, overflow: TextOverflow.ellipsis),
        subtitle: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(Uri.tryParse(publication.baseUrl)?.host ?? publication.subdomain),
            if (publication.description?.isNotEmpty == true)
              Text(publication.description!, maxLines: 3, overflow: TextOverflow.ellipsis),
            if (onFollow != null || onGroup != null)
              Wrap(
                spacing: 8,
                children: [
                  if (onFollow != null)
                    TextButton.icon(
                      onPressed: following || busy ? null : onFollow,
                      icon: Icon(following ? Icons.check_circle_outline : Icons.add_circle_outline),
                      label: Text(
                        following
                            ? l10n.plugin_substack_followed(publication.displayName)
                            : l10n.plugin_substack_follow,
                      ),
                    ),
                  if (onGroup != null)
                    TextButton.icon(
                      onPressed: busy ? null : onGroup,
                      icon: const Icon(Icons.group_add_outlined),
                      label: Text(l10n.add_to_group),
                    ),
                ],
              ),
          ],
        ),
        onTap: () =>
            Navigator.push(context, MaterialPageRoute(builder: (_) => SubstackArchiveScreen(publication: publication))),
      );
    },
  );

  Widget _logo(BuildContext context) => ClipOval(
    child: publication.logoUrl?.isNotEmpty == true
        ? ExtendedImage.network(
            publication.logoUrl!,
            width: 40,
            height: 40,
            fit: BoxFit.cover,
            cacheWidth: (40 * MediaQuery.devicePixelRatioOf(context)).ceil(),
          )
        : FallbackAvatar(
            seed: publication.id,
            displayName: publication.displayName,
            size: 40,
            accent: Theme.of(context).colorScheme.primary,
          ),
  );
}

class _SearchHistoryStore extends Store<List<String>> {
  _SearchHistoryStore(super.initialState);
}

class _FollowActions extends Store<Set<String>> {
  var _closed = false;
  _FollowActions() : super(const {});
  Future<bool> run(String id, Future<void> Function() action) async {
    if (_closed || state.contains(id)) return false;
    update({...state, id});
    try {
      await action();
      return true;
    } catch (_) {
      return false;
    } finally {
      if (!_closed) update({...state}..remove(id));
    }
  }

  @override
  Future<void> destroy() {
    _closed = true;
    return super.destroy();
  }
}
