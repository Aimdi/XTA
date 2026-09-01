import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/search/search_scope.dart';
import 'package:xta/trends/_list.dart';
import 'package:xta/trends/_search_scope.dart';
import 'package:xta/trends/_settings.dart';
import 'package:xta/trends/_tabs.dart';
import 'package:xta/trends/discover_plugin_search.dart';
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

  void _commitQuery(String query) {
    context.read<DiscoverQueryStore>().commit(query);
  }

  /// One meaning for the magnifier and the keyboard's search key. An empty
  /// query focuses the field instead of opening a search for nothing.
  void _submit(BuildContext context, String query) {
    _commitQuery(query);
    if (query.trim().isEmpty) {
      widget.focusNode.requestFocus();
      return;
    }
    if (_scopeOf(context) != searchScopeX) {
      return;
    }
    submitScopedSearch(context, query);
  }

  String _scopeOf(BuildContext context) {
    try {
      final plugins = searchablePlugins(PrefService.of(context, listen: false));
      return effectiveSearchScope(
        context.read<SearchScopeStore>().state,
        plugins,
      );
    } catch (_) {
      return searchScopeX;
    }
  }

  @override
  Widget build(BuildContext context) {
    super.build(context);

    return TripleBuilder<SearchScopeStore, String>(
      store: context.read<SearchScopeStore>(),
      builder: (context, scopeTriple) {
        final plugins = searchablePlugins(
          PrefService.of(context, listen: false),
        );
        final scope = effectiveSearchScope(scopeTriple.state, plugins);
        final showX = discoverBodyKind(scope, '') == DiscoverBodyKind.xTrends;

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
              child: SearchBar(
                controller: _queryController,
                focusNode: widget.focusNode,
                textInputAction: TextInputAction.search,
                hintText: searchBarHint(context),
                leading: IconButton(
                  icon: const Icon(Icons.search),
                  tooltip: L10n.of(context).search,
                  onPressed: () => _submit(context, _queryController.text),
                ),
                onChanged: (value) {
                  if (scope != searchScopeX) {
                    context.read<DiscoverQueryStore>().type(value);
                  }
                },
                onSubmitted: (query) => _submit(context, query),
              ),
            ),
            bottom: showX ? const TrendsTabBar() : null,
          ),
          floatingActionButton: showX
              ? FloatingActionButton(
                  child: const Icon(Icons.add),
                  onPressed: () async => showModalBottomSheet(
                    context: context,
                    builder: (context) => const TrendsSettings(),
                  ),
                )
              : null,
          body: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              if (showX) const DiscoverShortcuts(),
              SearchScopeChips(
                onSelected: (id) {
                  context.read<SearchScopeStore>().select(id);
                  context.read<DiscoverQueryStore>().commit(
                    _queryController.text,
                  );
                },
              ),
              Expanded(
                child: showX
                    ? TrendsList(scrollController: widget.scrollController)
                    : DiscoverPluginPane(
                        scrollController: widget.scrollController,
                      ),
              ),
            ],
          ),
        );
      },
    );
  }
}
