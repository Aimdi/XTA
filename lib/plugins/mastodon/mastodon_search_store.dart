import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/plugins/mastodon/mastodon_client.dart';
import 'package:xta/plugins/mastodon/mastodon_models.dart';

({String instance, String id})? mastodonSearchStatusTarget(String input) {
  final uri = Uri.tryParse(input.trim());
  if (uri == null || !const ['https', 'http'].contains(uri.scheme) || uri.host.isEmpty || uri.userInfo.isNotEmpty) {
    return null;
  }
  final segments = uri.pathSegments.toList();
  if (segments.isNotEmpty && segments.last.isEmpty) segments.removeLast();
  final shortPath = segments.length == 2 && segments.first.startsWith('@') && segments.first.length > 1;
  final longPath =
      segments.length == 4 && segments[0] == 'users' && segments[1].isNotEmpty && segments[2] == 'statuses';
  if ((!shortPath && !longPath) || !RegExp(r'^\d+$').hasMatch(segments.last)) return null;
  return (instance: uri.origin, id: segments.last);
}

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

  void select(int tab) {
    if (_closed) return;
    update(
      MastodonSearchState(
        query: state.query,
        loading: state.loading,
        error: state.error,
        tab: tab,
        results: state.results,
        tags: state.tags,
      ),
    );
  }

  Future<void> search(String input) async {
    if (_closed) return;
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
    final target = mastodonSearchStatusTarget(query);
    if (target != null) {
      final post = await client.getStatus(target.instance, target.id);
      return MastodonSearchState(
        query: query,
        tab: 1,
        results: MastodonSearchPage(posts: [post]),
      );
    }
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
