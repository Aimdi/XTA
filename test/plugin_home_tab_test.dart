import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/plugin.dart';
import 'package:xta/plugins/plugin_category.dart';
import 'package:xta/plugins/plugin_registry.dart';
import 'package:xta/plugins/substack/substack_plugin.dart';

/// A plugin that adds an action rather than a feed, so it never claims a tab.
class _TablessPlugin extends XtaPlugin {
  @override
  String get id => 'tabless';

  @override
  String get enabledPrefKey => 'plugin.tabless.enabled';

  @override
  IconData get icon => const IconData(0);

  @override
  PluginCategory get category => PluginCategory.social;

  @override
  Color get brandColor => const Color(0xFF808080);

  @override
  String title(BuildContext context) => 'Tabless';

  @override
  String description(BuildContext context) => 'No feed of its own';
}

BasePrefService _prefs({bool substackEnabled = true, bool? showTab}) => PrefServiceCache(cache: {
      optionPluginSubstackEnabled: substackEnabled,
      if (showTab != null) optionPluginSubstackShowTab: showTab,
    });

void main() {
  group('optional home tabs', () {
    test('Substack declares its tab optional; a plugin without a feed does not', () {
      expect(SubstackPlugin().homeTabPrefKey, optionPluginSubstackShowTab);
      expect(_TablessPlugin().homeTabPrefKey, isNull);
    });

    test('the tab is shown unless the preference says otherwise', () {
      final plugin = SubstackPlugin();

      expect(plugin.showsHomeTab(_prefs(showTab: true)), isTrue);
      expect(plugin.showsHomeTab(_prefs(showTab: false)), isFalse);
    });

    test('an unset preference keeps the current behaviour', () {
      // Existing installs must not silently lose the tab they already have.
      expect(SubstackPlugin().showsHomeTab(_prefs()), isTrue);
    });

    test('a plugin with no optional tab always reports true', () {
      expect(_TablessPlugin().showsHomeTab(_prefs()), isTrue);
    });

    test('every plugin offering to hide its tab actually has a tab screen', () {
      for (final plugin in builtInPlugins) {
        if (plugin.homeTabPrefKey != null) {
          final controller = ScrollController();
          addTearDown(controller.dispose);
          expect(plugin.homeScreen(scrollController: controller), isNotNull,
              reason: '${plugin.id} offers to hide a tab it does not have');
        }
      }
    });
  });
}
