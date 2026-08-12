import 'package:flutter_test/flutter_test.dart';
import 'package:xta/plugins/plugin_brand.dart';
import 'package:xta/plugins/plugin_category.dart';
import 'package:xta/plugins/plugin_registry.dart';

void main() {
  test('every built-in plugin has a store category', () {
    for (final plugin in builtInPlugins) {
      expect(pluginCategoryOrder, contains(plugin.category), reason: plugin.id);
    }
  });

  test('the store groups plugins under focused categories', () {
    final groups = groupPluginsByCategory(builtInPlugins);
    expect(groups.map((g) => g.category), [
      PluginCategory.social,
      PluginCategory.communities,
      PluginCategory.newsletters,
      PluginCategory.art,
      PluginCategory.markets,
      PluginCategory.bookmarks,
      PluginCategory.media,
    ]);
    expect(
      groups[0].plugins.map((p) => p.id),
      containsAll(['threads', 'bluesky', 'mastodon']),
    );
    expect(groups[1].plugins.single.id, 'reddit');
    expect(groups[2].plugins.single.id, 'substack');
    expect(groups[3].plugins.map((p) => p.id), ['pixiv', 'booru', 'ehviewer']);
    expect(groups[4].plugins.single.id, 'stocks');
  });

  test('the store lists installed plugins above available categories', () {
    final installedIds = {'reddit', 'pixiv'};
    final sections = pluginStoreSections(
      builtInPlugins,
      isInstalled: (plugin) => installedIds.contains(plugin.id),
    );

    expect(sections.installed.map((p) => p.id), ['reddit', 'pixiv']);
    expect(sections.availableByCategory.map((g) => g.category), [
      PluginCategory.social,
      PluginCategory.newsletters,
      PluginCategory.art,
      PluginCategory.markets,
      PluginCategory.bookmarks,
      PluginCategory.media,
    ]);
    expect(
      sections.availableByCategory
          .firstWhere((g) => g.category == PluginCategory.art)
          .plugins
          .map((p) => p.id),
      ['booru', 'ehviewer'],
    );
    expect(
      sections.availableByCategory.any(
        (g) => g.category == PluginCategory.communities,
      ),
      isFalse,
    );
  });
}
