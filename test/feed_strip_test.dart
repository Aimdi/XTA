import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/home/_feed.dart';
import 'package:xta/home/feed_strip_store.dart';

void main() {
  group('feedStripPluginIds', () {
    test(
      'legacy Reddit tab appears when the plugin is on and strip is unset',
      () {
        final prefs = PrefServiceCache(
          cache: {optionPluginRedditEnabled: true},
        );

        expect(feedStripPluginIds(prefs), [pluginIdReddit]);
      },
    );

    test('an empty saved list is intentional — no auto Reddit tab', () {
      final prefs = PrefServiceCache(
        cache: {
          optionPluginRedditEnabled: true,
          optionHomeFeedStripPlugins: <String>[],
        },
      );

      expect(feedStripPluginIds(prefs), isEmpty);
    });

    test('saved pins are returned as-is', () {
      final prefs = PrefServiceCache(
        cache: {
          optionHomeFeedStripPlugins: [pluginIdMastodon, pluginIdPixiv],
        },
      );

      expect(feedStripPluginIds(prefs), [pluginIdMastodon, pluginIdPixiv]);
    });
  });

  group('availableFeedTabsFromIds', () {
    test('always starts with Following and For you', () {
      final prefs = PrefServiceCache(cache: {});
      final tabs = availableFeedTabsFromIds(const [], prefs);

      expect(tabs.map((e) => e.id), [FeedTab.following, FeedTab.foryou]);
    });

    test('skips plugins that are not enabled', () {
      final prefs = PrefServiceCache(
        cache: {optionPluginMastodonEnabled: false},
      );
      final tabs = availableFeedTabsFromIds([pluginIdMastodon], prefs);

      expect(tabs.map((e) => e.id), [FeedTab.following, FeedTab.foryou]);
    });

    test('includes an enabled pin', () {
      final prefs = PrefServiceCache(cache: {optionPluginRedditEnabled: true});
      final tabs = availableFeedTabsFromIds([pluginIdReddit], prefs);

      expect(tabs.map((e) => e.id), [
        FeedTab.following,
        FeedTab.foryou,
        FeedTab.reddit,
      ]);
      expect(tabs.first.icon, Icons.home_outlined);
      expect(tabs.last.icon, isNotNull);
    });
  });

  group('FeedTab', () {
    test('equality is by id, not identity', () {
      expect(FeedTab('mastodon'), FeedTab(pluginIdMastodon));
      expect(FeedTab.reddit.name, pluginIdReddit);
    });
  });
}
