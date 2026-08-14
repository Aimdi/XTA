import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/plugins/plugin_search_history.dart';

void main() {
  test('remembers newest first, dedupes, and caps', () async {
    final prefs = PrefServiceCache();
    const key = 'plugin.test.search_history';

    expect(readPluginSearchHistory(prefs, key), isEmpty);

    await rememberPluginSearch(prefs, key, ' alice ');
    await rememberPluginSearch(prefs, key, 'bob');
    await rememberPluginSearch(prefs, key, 'alice');
    expect(readPluginSearchHistory(prefs, key), ['alice', 'bob']);

    for (var i = 0; i < 25; i++) {
      await rememberPluginSearch(prefs, key, 'q$i');
    }
    final history = readPluginSearchHistory(prefs, key);
    expect(history, hasLength(pluginSearchHistoryCap));
    expect(history.first, 'q24');
    expect(history.contains('alice'), isFalse);

    await clearPluginSearchHistory(prefs, key);
    expect(readPluginSearchHistory(prefs, key), isEmpty);
  });
}
