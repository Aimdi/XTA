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
      'a hidden-tab plugin stays on the strip even if pins were cleared',
      () {
        final prefs = PrefServiceCache(
          cache: {
            optionPluginRedditEnabled: true,
            optionPluginRedditShowTab: false,
            optionHomeFeedStripPlugins: <String>[],
          },
        );

        expect(feedStripPluginIds(prefs), [pluginIdReddit]);
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

    test('hidden-tab plugins are merged onto saved pins', () {
      final prefs = PrefServiceCache(
        cache: {
          optionHomeFeedStripPlugins: [pluginIdMastodon],
          optionPluginSubstackEnabled: true,
          optionPluginSubstackShowTab: false,
        },
      );

      expect(feedStripPluginIds(prefs), [pluginIdMastodon, pluginIdSubstack]);
    });
  });

  group('pinPluginOnFeedStrip', () {
    test('installing a feed plugin writes it onto the strip', () async {
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
    });

    test('empty pins still show a plugin that hid its bottom tab', () {
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
        pluginIdSubstack,
      ]);
    });
  });

  group('FeedTab', () {
    test('equality is by id, not identity', () {
      expect(FeedTab('mastodon'), FeedTab(pluginIdMastodon));
      expect(FeedTab.reddit.name, pluginIdReddit);
    });
  });
}
