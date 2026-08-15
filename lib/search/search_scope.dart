import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/plugin.dart';
import 'package:xta/plugins/plugin_registry.dart';
import 'package:xta/search/search.dart';

/// Search-tab scope for X itself (not a plugin id).
const searchScopeX = 'x';

/// Which network the Search tab should query.
class SearchScopeStore extends Store<String> {
  SearchScopeStore() : super(searchScopeX);

  void select(String id) => update(id);
}

/// Enabled plugins that can take a query from the Search tab.
List<XtaPlugin> searchablePlugins(BasePrefService prefs) => [
  for (final plugin in builtInPlugins)
    if (plugin.supportsSearch && plugin.isEnabled(prefs)) plugin,
];

/// The scope to search, falling back to X when the stored plugin is off.
String effectiveSearchScope(String scope, List<XtaPlugin> plugins) {
  if (scope == searchScopeX) {
    return searchScopeX;
  }
  return plugins.any((plugin) => plugin.id == scope) ? scope : searchScopeX;
}

/// Sends [query] to X search or the selected plugin's search UI.
Future<void> submitScopedSearch(BuildContext context, String query) async {
  final trimmed = query.trim();
  if (trimmed.isEmpty) {
    return;
  }

  final scope = _scopeOf(context);
  if (scope != searchScopeX) {
    final plugin = pluginById(scope);
    if (plugin != null && plugin.supportsSearch) {
      await plugin.openSearch(context, initialQuery: trimmed);
      return;
    }
  }

  if (!context.mounted) {
    return;
  }
  await Navigator.pushNamed(
    context,
    routeSearch,
    arguments: SearchArguments(0, focusInputOnOpen: false, query: trimmed),
  );
}

String _scopeOf(BuildContext context) {
  try {
    final store = context.read<SearchScopeStore>();
    final plugins = searchablePlugins(PrefService.of(context, listen: false));
    return effectiveSearchScope(store.state, plugins);
  } catch (_) {
    return searchScopeX;
  }
}
