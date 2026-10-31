import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/websources/web_source_client.dart';
import 'package:xta/plugins/websources/web_source_definition.dart';
import 'package:xta/plugins/websources/web_source_feed_store.dart';
import 'package:xta/plugins/websources/web_source_list_pane.dart';

/// The results of the latest query, as a list of their own.
class _ResultsStore extends Store<WebSourceFeedStore?> {
  _ResultsStore() : super(null);

  void show(WebSourceFeedStore results) {
    state?.destroy();
    update(results);
  }

  @override
  Future destroy() async {
    await state?.destroy();
    await super.destroy();
  }
}

/// A source's search, for a source whose file names one.
class WebSourceSearchScreen extends StatefulWidget {
  final WebSourceDefinition source;

  const WebSourceSearchScreen({super.key, required this.source});

  @override
  State<WebSourceSearchScreen> createState() => _WebSourceSearchScreenState();
}

class _WebSourceSearchScreenState extends State<WebSourceSearchScreen> {
  final _results = _ResultsStore();
  final _scroll = ScrollController();

  @override
  void dispose() {
    _results.destroy();
    _scroll.dispose();
    super.dispose();
  }

  void _search(String query) {
    final search = widget.source.search;
    if (search == null || query.trim().isEmpty) return;
    _results.show(WebSourceFeedStore(context.read<WebSourceClient>(), widget.source, search, query: query.trim()));
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(
      title: TextField(
        autofocus: true,
        textInputAction: TextInputAction.search,
        decoration: InputDecoration(
          hintText: L10n.of(context).search_in_plugin(widget.source.name),
          border: InputBorder.none,
        ),
        onSubmitted: _search,
      ),
    ),
    body: ScopedBuilder<_ResultsStore, WebSourceFeedStore?>(
      store: _results,
      onState: (_, results) => results == null
          ? const SizedBox.shrink()
          : WebSourceListPane(key: ObjectKey(results), store: results, scrollController: _scroll),
    ),
  );
}
