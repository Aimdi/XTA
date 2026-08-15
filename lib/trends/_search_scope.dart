import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/plugin.dart';
import 'package:xta/search/search_scope.dart';

/// Horizontal chips that pick which network the Search tab queries.
class SearchScopeChips extends StatelessWidget {
  const SearchScopeChips({super.key});

  @override
  Widget build(BuildContext context) {
    final plugins = searchablePlugins(PrefService.of(context));
    if (plugins.isEmpty) {
      return const SizedBox.shrink();
    }

    return TripleBuilder<SearchScopeStore, String>(
      store: context.read<SearchScopeStore>(),
      builder: (context, triple) {
        final selected = effectiveSearchScope(triple.state, plugins);
        return SizedBox(
          height: 44,
          child: ListView(
            scrollDirection: Axis.horizontal,
            padding: const EdgeInsets.fromLTRB(12, 4, 12, 4),
            children: [
              _chip(
                context,
                id: searchScopeX,
                label: L10n.of(context).source_x,
                selected: selected == searchScopeX,
              ),
              for (final plugin in plugins)
                _chip(
                  context,
                  id: plugin.id,
                  label: plugin.title(context),
                  selected: selected == plugin.id,
                ),
            ],
          ),
        );
      },
    );
  }

  Widget _chip(
    BuildContext context, {
    required String id,
    required String label,
    required bool selected,
  }) {
    return Padding(
      padding: const EdgeInsets.only(right: 8),
      child: ChoiceChip(
        label: Text(label),
        selected: selected,
        onSelected: (_) => context.read<SearchScopeStore>().select(id),
      ),
    );
  }
}

String searchBarHint(BuildContext context) {
  try {
    final plugins = searchablePlugins(PrefService.of(context, listen: false));
    final scope = effectiveSearchScope(
      context.read<SearchScopeStore>().state,
      plugins,
    );
    if (scope != searchScopeX) {
      final plugin = plugins.cast<XtaPlugin?>().firstWhere(
        (item) => item?.id == scope,
        orElse: () => null,
      );
      if (plugin != null) {
        return L10n.of(context).search_in_plugin(plugin.title(context));
      }
    }
  } catch (_) {}
  return L10n.of(context).search;
}
