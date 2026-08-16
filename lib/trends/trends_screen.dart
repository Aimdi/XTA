import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/search/search_scope.dart';
import 'package:xta/trends/_list.dart';
import 'package:xta/trends/_search_scope.dart';
import 'package:xta/trends/_settings.dart';
import 'package:xta/trends/_tabs.dart';
import 'package:xta/trends/discover_shortcuts.dart';

class TrendsScreen extends StatefulWidget {
  final ScrollController scrollController;
  final FocusNode focusNode;

  const TrendsScreen({
    super.key,
    required this.scrollController,
    required this.focusNode,
  });

  @override
  State<TrendsScreen> createState() => _TrendsScreenState();
}

class _TrendsScreenState extends State<TrendsScreen>
    with AutomaticKeepAliveClientMixin<TrendsScreen> {
  @override
  bool get wantKeepAlive => true;
  final TextEditingController _queryController = TextEditingController();

  /// One meaning for the magnifier and the keyboard's search key. An empty
  /// query focuses the field instead of opening a search for nothing.
  void _submit(BuildContext context, String query) {
    if (query.trim().isEmpty) {
      widget.focusNode.requestFocus();
      return;
    }
    submitScopedSearch(context, query);
  }

  @override
  Widget build(BuildContext context) {
    super.build(context);

    return Scaffold(
      appBar: AppBar(
        automaticallyImplyLeading: false,
        flexibleSpace: Padding(
          // flexibleSpace is not inset for the status bar; the hardcoded 36 it
          // had rode under taller ones.
          padding: EdgeInsets.fromLTRB(
            8,
            MediaQuery.paddingOf(context).top + 4,
            8,
            8,
          ),
          child: TripleBuilder<SearchScopeStore, String>(
            store: context.read<SearchScopeStore>(),
            builder: (context, _) => SearchBar(
              controller: _queryController,
              focusNode: widget.focusNode,
              textInputAction: TextInputAction.search,
              hintText: searchBarHint(context),
              // The magnifier used to be a dead 48dp target (`() => {}` — a no-op
              // that also ate the tap meant for the field). It now does what the
              // keyboard's search key does.
              leading: IconButton(
                icon: const Icon(Icons.search),
                tooltip: L10n.of(context).search,
                onPressed: () => _submit(context, _queryController.text),
              ),
              onSubmitted: (query) => _submit(context, query),
            ),
          ),
        ),
        bottom: TrendsTabBar(),
      ),
      floatingActionButton: FloatingActionButton(
        child: const Icon(Icons.add),
        onPressed: () async => showModalBottomSheet(
          context: context,
          builder: (context) => const TrendsSettings(),
        ),
      ),
      body: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          const DiscoverShortcuts(),
          const SearchScopeChips(),
          Expanded(
            child: TrendsList(scrollController: widget.scrollController),
          ),
        ],
      ),
    );
  }
}
