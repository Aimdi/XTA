import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/home/feed_strip_store.dart';
import 'package:xta/home/network_recents_store.dart';
import 'package:xta/utils/pref_lists.dart';

void main() {
  test('stringListPref reads a real string list', () {
    final prefs = PrefServiceCache(
      cache: {
        optionHomeFeedStripPlugins: [pluginIdReddit, pluginIdRss],
      },
    );

    expect(stringListPref(prefs, optionHomeFeedStripPlugins), [
      pluginIdReddit,
      pluginIdRss,
    ]);
  });

  test('stringListPref accepts a JSON array string', () {
    final prefs = PrefServiceCache(
      cache: {optionHomePages: '["feed","saved"]'},
    );

    expect(stringListPref(prefs, optionHomePages), ['feed', 'saved']);
  });

  test('stringListPref accepts a List<dynamic> from a backup restore', () {
    final prefs = PrefServiceCache(
      cache: {
        optionSeededStripPlugins: <dynamic>[pluginIdBluesky, pluginIdRss],
      },
    );

    expect(stringListPref(prefs, optionSeededStripPlugins), [
      pluginIdBluesky,
      pluginIdRss,
    ]);
  });

  test('stringListPref is null when the key was never set', () {
    expect(
      stringListPref(PrefServiceCache(cache: {}), optionHomePages),
      isNull,
    );
  });

  test('FeedStripStore survives a JSON-string strip pref', () {
    final prefs = PrefServiceCache(
      cache: {
        optionHomeFeedStripPlugins: '["reddit","rss"]',
        optionPluginRedditEnabled: true,
        optionPluginRssEnabled: true,
      },
    );

    final store = FeedStripStore(prefs);
    expect(store.state, [pluginIdReddit, pluginIdRss]);
  });

  test('NetworkRecentsStore survives a JSON-string recents pref', () {
    final prefs = PrefServiceCache(
      cache: {optionHomeRecentNetworks: '["mastodon"]'},
    );

    expect(() => NetworkRecentsStore(prefs), returnsNormally);
    expect(NetworkRecentsStore(prefs).state, [pluginIdMastodon]);
  });
}
