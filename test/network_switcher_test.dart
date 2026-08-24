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
        FeedTab(pluginIdPixiv),
      ]);
      expect(
        overflowFeedTabs(
          available: available,
          visible: visible,
        ),
        isEmpty,
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
}
