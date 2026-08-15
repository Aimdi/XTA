import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/search/search_scope.dart';

void main() {
  test('SearchScopeStore starts on X and select updates it', () {
    final store = SearchScopeStore();
    expect(store.state, searchScopeX);
    store.select(pluginIdReddit);
    expect(store.state, pluginIdReddit);
  });

  test('searchablePlugins lists only enabled search plugins', () {
    final prefs = PrefServiceCache(
      cache: {
        optionPluginRedditEnabled: true,
        optionPluginBlueskyEnabled: true,
        optionPluginThreadsEnabled: false,
      },
    );
    final ids = searchablePlugins(prefs).map((plugin) => plugin.id).toList();
    expect(ids, containsAll([pluginIdReddit, pluginIdBluesky]));
    expect(ids, isNot(contains(pluginIdThreads)));
  });

  test('effectiveSearchScope falls back to X when the plugin is off', () {
    final prefs = PrefServiceCache(cache: {optionPluginRedditEnabled: true});
    final plugins = searchablePlugins(prefs);
    expect(effectiveSearchScope(pluginIdReddit, plugins), pluginIdReddit);
    expect(effectiveSearchScope(pluginIdBluesky, plugins), searchScopeX);
    expect(effectiveSearchScope(searchScopeX, plugins), searchScopeX);
  });
}
