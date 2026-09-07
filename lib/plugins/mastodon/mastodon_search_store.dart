import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/plugins/mastodon/mastodon_client.dart';
import 'package:xta/plugins/mastodon/mastodon_models.dart';

class MastodonSearchState {
  final String query;
  final bool loading;
  final Object? error;
  final int tab;
  final MastodonSearchPage results;
  final List<MastodonTrendingTag> tags;
  const MastodonSearchState({
    this.query = '',
    this.loading = false,
    this.error,
    this.tab = 0,
    this.results = const MastodonSearchPage(),
    this.tags = const [],
  });
}

/// A slower response must never replace a newer search or update a closed route.
class MastodonSearchStore extends Store<MastodonSearchState> {
  final MastodonClient client;
  final List<String> instances;
  int _request = 0;
  bool _closed = false;
  MastodonSearchStore(this.client, this.instances) : super(const MastodonSearchState());

  void select(int tab) => update(
    MastodonSearchState(
      query: state.query,
      loading: state.loading,
      error: state.error,
      tab: tab,
      results: state.results,
      tags: state.tags,
    ),
  );

  Future<void> search(String input) async {
    final query = input.trim();
    final request = ++_request;
    update(MastodonSearchState(query: query, loading: true));
    try {
      final result = await _load(query);
      if (_closed || request != _request) return;
      update(result);
    } catch (error) {
      if (_closed || request != _request) return;
      update(MastodonSearchState(query: query, error: error));
    }
  }

  Future<MastodonSearchState> _load(String query) async {
    if (query.isEmpty) return MastodonSearchState(tags: await client.getTrendingTagsAnywhere(instances));
    final acct = normaliseMastodonAcct(query);
    final MastodonSearchPage results;
    if (acct != null && query.contains('@')) {
      final profile = await client.lookupAnywhere(mastodonInstanceCandidates(acct, configured: instances), acct);
      results = MastodonSearchPage(accounts: [profile]);
    } else {
      results = await client.searchAnywhere(instances, query);
    }
    return MastodonSearchState(
      query: query,
      results: results,
      tab: results.accounts.isNotEmpty
          ? 0
          : results.posts.isNotEmpty
          ? 1
          : results.tags.isNotEmpty
          ? 2
          : 0,
    );
  }

  @override
  Future<void> destroy() {
    _closed = true;
    _request++;
    return super.destroy();
  }
}
