import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
// The real store is the point: only it refuses a List<dynamic>. It ships with pref, so no dependency is added.
// ignore: depend_on_referenced_packages
import 'package:shared_preferences/shared_preferences.dart';
import 'package:xta/constants.dart';
import 'package:xta/utils/pref_lists.dart';

const _backup =
    '{"home.feed_strip_plugins": ["bluesky", "pixiv"], "plugins.seeded_tabs": ["reddit"], "theme.mode": "dark"}';

Future<BasePrefService> _freshPrefs() async {
  SharedPreferences.setMockInitialValues({});
  return PrefServiceShared.init(prefix: 'pref_');
}

void main() {
  test('a restored backup keeps the Home timelines it lists', () async {
    final prefs = await _freshPrefs();

    await prefs.fromMap(prefsForImport(jsonDecode(_backup) as Map<String, dynamic>));

    expect(stringListPref(prefs, optionHomeFeedStripPlugins), ['bluesky', 'pixiv']);
    expect(stringListPref(prefs, optionSeededPluginTabs), ['reddit']);
    expect(prefs.get<String>(optionThemeMode), 'dark');
  });

  test('without the conversion shared preferences drops those lists', () async {
    final prefs = await _freshPrefs();

    await prefs.fromMap(jsonDecode(_backup) as Map<String, dynamic>);

    expect(stringListPref(prefs, optionHomeFeedStripPlugins), isNull);
  });

  test('a value is removed from a stored list, and an unset list stays unset', () async {
    final prefs = await _freshPrefs();
    await prefs.set(optionSeededPluginTabs, ['bluesky', 'reddit']);

    await removeFromStringListPref(prefs, optionSeededPluginTabs, 'bluesky');
    await removeFromStringListPref(prefs, optionHomeFeedStripPlugins, 'bluesky');

    expect(stringListPref(prefs, optionSeededPluginTabs), ['reddit']);
    expect(prefs.get<Object?>(optionHomeFeedStripPlugins), isNull);
  });
}
