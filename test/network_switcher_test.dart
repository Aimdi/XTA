import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/group/group_model.dart';
import 'package:xta/home/_feed.dart';
import 'package:xta/home/feed_strip_store.dart';
import 'package:xta/home/home_model.dart';
import 'package:xta/home/network_switcher.dart';

void main() {
  group('recentPluginTabIds', () {
    test('keeps pin order and always includes the open network', () {
      final visible = recentPluginTabIds(
        pinned: const ['reddit', 'mastodon', 'pixiv', 'bluesky'],
        recent: const ['pixiv'],
        currentPluginId: 'bluesky',
        limit: 2,
      );

      expect(visible, ['pixiv', 'bluesky']);
    });

    test('does not shuffle when switching among already-visible tabs', () {
      final pinned = const ['reddit', 'mastodon', 'pixiv'];
      final recent = const ['reddit', 'mastodon'];
      expect(
        recentPluginTabIds(
          pinned: pinned,
          recent: recent,
          currentPluginId: 'reddit',
        ),
        recentPluginTabIds(
          pinned: pinned,
          recent: recent,
          currentPluginId: 'mastodon',
        ),
      );
    });
  });

  group('visibleFeedTabs', () {
    test('Following and For you stay first when many plugins are pinned', () {
      final prefs = PrefServiceCache(
        cache: {
          optionPluginRedditEnabled: true,
          optionPluginMastodonEnabled: true,
          optionPluginPixivEnabled: true,
        },
      );
      final available = availableFeedTabsFromIds([
        pluginIdReddit,
        pluginIdMastodon,
        pluginIdPixiv,
      ], prefs);
      final visible = visibleFeedTabs(
        available: available,
        recent: const [],
        current: FeedTab.following,
      );

      expect(visible.map((e) => e.id), [
        FeedTab.following,
        FeedTab.foryou,
        FeedTab.reddit,
        FeedTab(pluginIdMastodon),
      ]);
      expect(
        overflowFeedTabs(
          available: available,
          visible: visible,
        ).map((e) => e.id),
        [FeedTab(pluginIdPixiv)],
      );
    });
  });

  group('layoutBottomBar', () {
    test('X destinations stay visible when many plugins join the bar', () {
      final slots = layoutBottomBar([
        'feed',
        'subscriptions',
        pluginIdReddit,
        pluginIdMastodon,
        pluginIdPixiv,
      ], recentPluginId: pluginIdMastodon);

      expect(slots.where((s) => !s.isOverflow).map((s) => s.pageIndex), [
        0,
        1,
        3,
      ]);
      expect(slots.last.isOverflow, isTrue);
    });

    test('a single plugin page does not collapse into Networks', () {
      final slots = layoutBottomBar(['feed', pluginIdReddit]);
      expect(slots.map((s) => s.isOverflow), [false, false]);
      expect(destinationIndexForPage(slots, 1), 1);
    });
  });

  group('seedFeedStripPlugins', () {
    test('an enabled plugin is pinned on the home strip', () async {
      final prefs = PrefServiceCache(
        cache: {optionPluginMastodonEnabled: true},
      );

      final pinned = await seedFeedStripPlugins(prefs);

      expect(pinned, contains(pluginIdMastodon));
      expect(
        prefs.getStringList(optionHomeFeedStripPlugins),
        contains(pluginIdMastodon),
      );
      expect(
        prefs.getStringList(optionSeededStripPlugins),
        contains(pluginIdMastodon),
      );
    });

    test('a pin the reader removed stays removed', () async {
      final prefs = PrefServiceCache(
        cache: {
          optionPluginMastodonEnabled: true,
          optionHomeFeedStripPlugins: <String>[],
          optionSeededStripPlugins: [pluginIdMastodon],
        },
      );

      expect(await seedFeedStripPlugins(prefs), isEmpty);
    });
  });

  group('HomeModel and the home strip', () {
    test('enabling a plugin selects its home destination', () async {
      final prefs = PrefServiceCache(
        cache: {
          optionHomePages: ['feed', 'subscriptions', 'trending', 'saved'],
          optionSeededPluginTabs: <String>[],
          optionPluginRedditEnabled: true,
        },
      );
      final model = HomeModel(prefs, GroupsModel(prefs));
      await model.loadPages();

      expect(
        model.state.any((page) => page.id == pluginIdReddit && page.selected),
        isTrue,
      );
      expect(feedStripPluginIds(prefs), contains(pluginIdReddit));
    });
  });

  group('switching does not remount Following', () {
    test('visible row identity stays stable across plugin taps', () {
      final prefs = PrefServiceCache(
        cache: {
          optionPluginRedditEnabled: true,
          optionPluginMastodonEnabled: true,
        },
      );
      final available = availableFeedTabsFromIds([
        pluginIdReddit,
        pluginIdMastodon,
      ], prefs);
      final followingKey = visibleFeedTabs(
        available: available,
        recent: const [pluginIdReddit, pluginIdMastodon],
        current: FeedTab.reddit,
      ).map((e) => e.id.id).join(',');
      final afterSwitch = visibleFeedTabs(
        available: available,
        recent: const [pluginIdMastodon, pluginIdReddit],
        current: FeedTab(pluginIdMastodon),
      ).map((e) => e.id.id).join(',');

      expect(followingKey, afterSwitch);
    });
  });
}
