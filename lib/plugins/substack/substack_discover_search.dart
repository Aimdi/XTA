import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/substack/substack_archive_screen.dart';
import 'package:xta/plugins/substack/substack_client.dart';
import 'package:xta/plugins/substack/substack_models.dart';
import 'package:xta/ui/empty_pane.dart';
import 'package:xta/ui/errors.dart';

/// Substack publications for the Discover hub.
class SubstackDiscoverSearch extends StatefulWidget {
  final String query;

  const SubstackDiscoverSearch({super.key, required this.query});

  @override
  State<SubstackDiscoverSearch> createState() => _SubstackDiscoverSearchState();
}

class _SubstackDiscoverSearchState extends State<SubstackDiscoverSearch> {
  late final _SubstackDiscoverStore _store;

  @override
  void initState() {
    super.initState();
    _store = _SubstackDiscoverStore(context.read<SubstackClient>());
    _store.search(widget.query);
  }

  @override
  void didUpdateWidget(SubstackDiscoverSearch old) {
    super.didUpdateWidget(old);
    if (old.query != widget.query) {
      _store.search(widget.query);
    }
  }

  @override
  void dispose() {
    _store.destroy();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return ScopedBuilder<_SubstackDiscoverStore, List<SubstackPublication>>(
      store: _store,
      onLoading: (_) => const Center(child: CircularProgressIndicator()),
      onError: (_, error) => FullPageErrorWidget(
        error: error,
        stackTrace: null,
        prefix: l10n.unable_to_load_the_search_results,
        onRetry: () => _store.search(widget.query),
      ),
      onState: (context, results) {
        if (results.isEmpty) {
          return EmptyPane(icon: Icons.search_off, message: l10n.no_results);
        }
        return ListView(
          children: [
            for (final publication in results)
              ListTile(
                title: Text(publication.name),
                subtitle: Text(publication.subdomain),
                onTap: () => Navigator.push(
                  context,
                  MaterialPageRoute(
                    builder: (_) =>
                        SubstackArchiveScreen(publication: publication),
                  ),
                ),
              ),
          ],
        );
      },
    );
  }
}

class _SubstackDiscoverStore extends Store<List<SubstackPublication>> {
  final SubstackClient client;

  _SubstackDiscoverStore(this.client) : super(const []);

  Future<void> search(String value) async {
    final query = value.trim();
    if (query.isEmpty) {
      update(const []);
      return;
    }
    await execute(() => client.discoverPublications(query));
  }
}
