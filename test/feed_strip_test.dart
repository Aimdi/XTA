import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/home/_feed.dart';
import 'package:xta/home/feed_strip_store.dart';

void main() {
  group('feedStripPluginIds', () {
    test('an unset strip lists no network until the reader adds one', () {
      final prefs = PrefServiceCache(
        cache: {
          optionPluginRedditEnabled: true,
          optionPluginMastodonEnabled: true,
        },
      );

      expect(feedStripPluginIds(prefs), isEmpty);
      expect(
        legacyFeedStripIds(prefs),
        containsAll([pluginIdReddit, pluginIdMastodon]),
      );
    });

    test(
      'an empty saved list is intentional when the plugin still has a tab',
      () {
        final prefs = PrefServiceCache(
          cache: {
            optionPluginRedditEnabled: true,
            optionPluginRedditShowTab: true,
            optionHomeFeedStripPlugins: <String>[],
          },
        );

        expect(feedStripPluginIds(prefs), isEmpty);
      },
    );

    test(
      'a hidden-tab plugin off the strip stays one Add timeline away',
      () {
        final prefs = PrefServiceCache(
          cache: {
            optionPluginRedditEnabled: true,
            optionPluginRedditShowTab: false,
            optionHomeFeedStripPlugins: <String>[],
          },
        );

        expect(feedStripPluginIds(prefs), isEmpty);
        expect(
          feedStripCandidates(prefs, const []).map((p) => p.id),
          [pluginIdReddit],
        );
      },
    );

    test('saved pins are returned as-is', () {
      final prefs = PrefServiceCache(
        cache: {
          optionHomeFeedStripPlugins: [pluginIdMastodon, pluginIdPixiv],
        },
      );

      expect(feedStripPluginIds(prefs), [pluginIdMastodon, pluginIdPixiv]);
    });

    test('hidden-tab plugins Home used to add are kept by the migration', () {
      final prefs = PrefServiceCache(
        cache: {
          optionHomeFeedStripPlugins: [pluginIdMastodon],
          optionPluginSubstackEnabled: true,
          optionPluginSubstackShowTab: false,
        },
      );

      expect(feedStripPluginIds(prefs), [pluginIdMastodon]);
      expect(legacyFeedStripIds(prefs), [pluginIdMastodon, pluginIdSubstack]);
    });
  });

  group('pinPluginOnFeedStrip', () {
    test('pinning a feed plugin writes it onto the strip', () async {
      final prefs = PrefServiceCache(cache: {});
      await pinPluginOnFeedStrip(prefs, pluginIdBluesky);
      expect(
        prefs.getStringList(optionHomeFeedStripPlugins),
        contains(pluginIdBluesky),
      );
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
      expect(tabs.first.icon, followingTabIcon);
      expect(tabs.last.icon, isNotNull);
    });

    test('empty pins show no plugin, even one that hid its bottom tab', () {
      final prefs = PrefServiceCache(
        cache: {
          optionPluginSubstackEnabled: true,
          optionPluginSubstackShowTab: false,
        },
      );
      final tabs = availableFeedTabsFromIds(const [], prefs);

      expect(tabs.map((e) => e.id.id), [
        FeedTab.following.id,
        FeedTab.foryou.id,
      ]);
    });
  });

  group('FeedTab', () {
    test('equality is by id, not identity', () {
      expect(FeedTab('mastodon'), FeedTab(pluginIdMastodon));
      expect(FeedTab.reddit.name, pluginIdReddit);
    });

    test('X tabs use house and spark; plugins reuse XtaPlugin.icon', () {
      expect(FeedTab.following.icon, followingTabIcon);
      expect(FeedTab.x.icon, Icons.close);
      expect(FeedTab(pluginIdSubstack).icon, Icons.newspaper);
      expect(FeedTab(pluginIdPixiv).icon, Icons.brush);
      expect(FeedTab(pluginIdBooru).icon, Icons.inventory_2);
      expect(FeedTab(pluginIdThreads).icon, Icons.alternate_email);
      expect(FeedTab(pluginIdBluesky).icon, Icons.cloud);
    });
  });

  group('reorderFeedStripIds', () {
    test('moves a pin to the slot after it is taken out', () {
      expect(reorderFeedStripIds(['a', 'b', 'c'], 0, 1), ['b', 'a', 'c']);
      expect(reorderFeedStripIds(['a', 'b', 'c'], 0, 2), ['b', 'c', 'a']);
      expect(reorderFeedStripIds(['a', 'b', 'c'], 2, 0), ['c', 'a', 'b']);
    });

    test('a no-op or out-of-range index leaves the list', () {
      expect(reorderFeedStripIds(['a', 'b'], 0, 0), ['a', 'b']);
      expect(reorderFeedStripIds(['a', 'b'], -1, 0), ['a', 'b']);
      expect(reorderFeedStripIds(['a', 'b'], 2, 0), ['a', 'b']);
    });
  });

  group('FeedStripStore.reorder', () {
    test('persists the new pin order', () async {
      final prefs = PrefServiceCache(
        cache: {
          optionHomeFeedStripPlugins: [pluginIdReddit, pluginIdMastodon],
        },
      );
      final store = FeedStripStore(prefs);
      await store.reorder(0, 1);
      expect(store.state, [pluginIdMastodon, pluginIdReddit]);
      expect(prefs.getStringList(optionHomeFeedStripPlugins), [
        pluginIdMastodon,
        pluginIdReddit,
      ]);
    });
  });
}
