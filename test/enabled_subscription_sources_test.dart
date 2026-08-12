import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/bluesky/bluesky_plugin.dart';
import 'package:xta/plugins/plugin.dart';
import 'package:xta/plugins/plugin_registry.dart';
import 'package:xta/plugins/reddit/reddit_plugin.dart';
import 'package:xta/plugins/threads/threads_plugin.dart';

void main() {
  test('disabled plugins stay out of mixed-feed fan-out', () {
    final prefs = PrefServiceCache(
      cache: {
        optionPluginThreadsEnabled: false,
        optionPluginBlueskyEnabled: true,
        optionPluginMastodonEnabled: false,
        optionPluginRedditEnabled: true,
        optionPluginSubstackEnabled: false,
      },
    );

    final enabled = enabledSubscriptionSources(prefs);

    expect(enabled, isNotEmpty);
    expect(enabled.every((s) => (s as XtaPlugin).isEnabled(prefs)), isTrue);
    expect(enabled.whereType<BlueskyPlugin>(), isNotEmpty);
    expect(enabled.whereType<RedditPlugin>(), isNotEmpty);
    expect(enabled.whereType<ThreadsPlugin>(), isEmpty);
    expect(enabled.length, lessThan(subscriptionSources.length));
  });
}
