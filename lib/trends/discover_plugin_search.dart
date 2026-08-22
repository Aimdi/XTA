import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/instagram/instagram_discover_search.dart';
import 'package:xta/plugins/plugin.dart';
import 'package:xta/plugins/plugin_registry.dart';
import 'package:xta/plugins/reddit/reddit_search_body.dart';
import 'package:xta/plugins/substack/substack_discover_search.dart';
import 'package:xta/search/search_scope.dart';
import 'package:xta/ui/empty_pane.dart';

/// Discover body owned by the selected plugin chip — never X worldwide trends.
class DiscoverPluginPane extends StatelessWidget {
  final ScrollController scrollController;

  const DiscoverPluginPane({super.key, required this.scrollController});

  @override
  Widget build(BuildContext context) {
    return TripleBuilder<SearchScopeStore, String>(
      store: context.read<SearchScopeStore>(),
      builder: (context, scopeTriple) {
        return TripleBuilder<DiscoverQueryStore, String>(
          store: context.read<DiscoverQueryStore>(),
          builder: (context, queryTriple) {
            final plugins = searchablePlugins(
              PrefService.of(context, listen: false),
            );
            final scope = effectiveSearchScope(scopeTriple.state, plugins);
            final plugin = pluginById(scope);
            if (plugin == null || scope == searchScopeX) {
              return const SizedBox.shrink();
            }

            final query = queryTriple.state;
            if (discoverBodyKind(scope, query) ==
                DiscoverBodyKind.pluginEmpty) {
              return DiscoverPluginEmpty(
                plugin: plugin,
                scrollController: scrollController,
              );
            }
            return discoverPluginSearch(
              plugin: plugin,
              query: query,
              scrollController: scrollController,
            );
          },
        );
      },
    );
  }
}

/// Plugin-flavored empty Discover — not the X worldwide list.
class DiscoverPluginEmpty extends StatelessWidget {
  final XtaPlugin plugin;
  final ScrollController scrollController;

  const DiscoverPluginEmpty({
    super.key,
    required this.plugin,
    required this.scrollController,
  });

  @override
  Widget build(BuildContext context) {
    return EmptyPane(
      icon: plugin.icon,
      message: L10n.of(context).discover_plugin_empty(plugin.title(context)),
      scrollController: scrollController,
    );
  }
}

/// Inline results for the networks Discover can search without a new route.
Widget discoverPluginSearch({
  required XtaPlugin plugin,
  required String query,
  required ScrollController scrollController,
}) {
  return switch (plugin.id) {
    pluginIdReddit => RedditSearchBody(query: query),
    pluginIdInstagram => InstagramDiscoverSearch(query: query),
    pluginIdSubstack => SubstackDiscoverSearch(query: query),
    _ => _DiscoverPluginFallback(
      plugin: plugin,
      query: query,
      scrollController: scrollController,
    ),
  };
}

class _DiscoverPluginFallback extends StatelessWidget {
  final XtaPlugin plugin;
  final String query;
  final ScrollController scrollController;

  const _DiscoverPluginFallback({
    required this.plugin,
    required this.query,
    required this.scrollController,
  });

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return EmptyPane(
      icon: plugin.icon,
      message: l10n.discover_plugin_empty(plugin.title(context)),
      scrollController: scrollController,
      action: FilledButton.icon(
        onPressed: () => plugin.openSearch(context, initialQuery: query),
        icon: const Icon(Icons.search),
        label: Text(l10n.search),
      ),
    );
  }
}
