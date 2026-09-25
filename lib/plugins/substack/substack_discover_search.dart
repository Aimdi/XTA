import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/substack/substack_archive_screen.dart';
import 'package:xta/plugins/substack/substack_client.dart';
import 'package:xta/plugins/substack/substack_post_card.dart';
import 'package:xta/plugins/substack/substack_search_store.dart';

/// The Discover hub shares the guarded publication and pasted-article search.
class SubstackDiscoverSearch extends StatefulWidget {
  final String query;
  const SubstackDiscoverSearch({super.key, required this.query});
  @override
  State<SubstackDiscoverSearch> createState() => _SubstackDiscoverSearchState();
}

class _SubstackDiscoverSearchState extends State<SubstackDiscoverSearch> {
  late final SubstackSearchStore _store;
  @override
  void initState() {
    super.initState();
    _store = SubstackSearchStore(context.read<SubstackClient>());
    _store.search(widget.query);
  }

  @override
  void didUpdateWidget(SubstackDiscoverSearch old) {
    super.didUpdateWidget(old);
    if (old.query != widget.query) _store.search(widget.query);
  }

  @override
  void dispose() {
    _store.destroy();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => ScopedBuilder<SubstackSearchStore, SubstackSearchState>(
    store: _store,
    onState: (context, state) {
      final l10n = L10n.of(context);
      return RefreshIndicator(
        onRefresh: _store.refresh,
        child: ListView(
          children: [
            if (state.loading) const LinearProgressIndicator(),
            if (state.error != null)
              Padding(
                padding: const EdgeInsets.all(24),
                child: Column(
                  children: [
                    Text(l10n.plugin_substack_load_error),
                    TextButton(onPressed: state.retryMore ? _store.loadMore : _store.refresh, child: Text(l10n.retry)),
                  ],
                ),
              ),
            for (final publication in state.publications)
              ListTile(
                title: Text(publication.displayName),
                subtitle: Text(publication.baseUrl),
                onTap: () => Navigator.push(
                  context,
                  MaterialPageRoute(builder: (_) => SubstackArchiveScreen(publication: publication)),
                ),
              ),
            for (final post in state.posts) SubstackPostCard(post: post),
            if (!state.loading && state.error == null && state.publications.isEmpty && state.posts.isEmpty)
              Padding(padding: const EdgeInsets.all(24), child: Text(l10n.no_results)),
            if (state.loadingMore)
              const Center(child: CircularProgressIndicator())
            else if (state.hasMore)
              TextButton(onPressed: _store.loadMore, child: Text(l10n.plugin_substack_load_more)),
          ],
        ),
      );
    },
  );
}
