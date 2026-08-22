import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/plugins/plugin_registry.dart';
import 'package:xta/plugins/rss/rss_plugin.dart';

void main() {
  test('RSS is a built-in newsletter plugin with a subscription table', () {
    final plugin = builtInPlugins.whereType<RssPlugin>().single;

    expect(plugin.id, pluginIdRss);
    expect(plugin.subscriptionTable, 'rss_subscription');
    expect(
      plugin.owns(
        RssSubscription(
          id: 'https://example.com/feed',
          feedUrl: 'https://example.com/feed',
          name: 'Example',
          iconUrl: null,
          createdAt: DateTime.utc(2026),
          inFeed: true,
        ),
      ),
      isTrue,
    );
    expect(plugin.showsHomeTab(PrefServiceCache(cache: {})), isTrue);
  });
}
