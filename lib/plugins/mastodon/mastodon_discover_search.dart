import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/plugins/mastodon/mastodon_client.dart';
import 'package:xta/plugins/mastodon/mastodon_store.dart';
import 'package:xta/plugins/mastodon/mastodon_search_results.dart';
import 'package:xta/plugins/mastodon/mastodon_search_store.dart';

class MastodonDiscoverSearch extends StatefulWidget {
  final String query;
  const MastodonDiscoverSearch({super.key, required this.query});
  @override
  State<MastodonDiscoverSearch> createState() => _MastodonDiscoverSearchState();
}

class _MastodonDiscoverSearchState extends State<MastodonDiscoverSearch> {
  late final MastodonSearchStore _store;
  final _positions = [ScrollController(), ScrollController(), ScrollController()];
  @override
  void initState() {
    super.initState();
    _store = MastodonSearchStore(
      context.read<MastodonClient>(),
      mastodonDiscoveryInstances(PrefService.of(context, listen: false)),
    );
    _search();
  }

  @override
  void didUpdateWidget(covariant MastodonDiscoverSearch oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.query != widget.query) _search();
  }

  void _search() {
    for (final position in _positions) {
      if (position.hasClients) position.jumpTo(0);
    }
    _store.search(widget.query);
  }

  @override
  void dispose() {
    _store.destroy();
    for (final position in _positions) {
      position.dispose();
    }
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => ScopedBuilder<MastodonSearchStore, MastodonSearchState>(
    store: _store,
    onState: (context, state) =>
        MastodonSearchResults(state: state, onRetry: _search, onSelected: _store.select, positions: _positions),
  );
}
