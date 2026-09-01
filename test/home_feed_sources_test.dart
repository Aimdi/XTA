import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/bluesky/bluesky_interleaved.dart';
import 'package:xta/plugins/mastodon/mastodon_interleaved.dart';
import 'package:xta/plugins/reddit/reddit_interleaved.dart';
import 'package:xta/plugins/threads/threads_interleaved.dart';

BasePrefService _prefs(Map<String, Object> values) => PrefServiceCache(defaults: values);

void main() {
  group('whether a source joins the home timeline', () {
    // Every source answers the same two-part question: is the plugin on, and
    // did the reader ask for it here. Pinned per source so the next one added
    // cannot quietly default to being in everybody's Following feed.
    final cases = <String, ({bool Function(BasePrefService) predicate, String enabled, String inHome})>{
      'Reddit': (predicate: redditInHomeFeed, enabled: optionPluginRedditEnabled, inHome: optionPluginRedditInHomeFeed),
      'Threads': (
        predicate: threadsInHomeFeed,
        enabled: optionPluginThreadsEnabled,
        inHome: optionPluginThreadsInHomeFeed,
      ),
      'Bluesky': (
        predicate: blueskyInHomeFeed,
        enabled: optionPluginBlueskyEnabled,
        inHome: optionPluginBlueskyInHomeFeed,
      ),
      'Fediverse': (
        predicate: fediverseInHomeFeed,
        enabled: optionPluginMastodonEnabled,
        inHome: optionPluginMastodonInHomeFeed,
      ),
    };

    for (final entry in cases.entries) {
      final name = entry.key;
      final c = entry.value;

      test('$name joins it only when the plugin is on and the switch is on', () {
        expect(c.predicate(_prefs({c.enabled: true, c.inHome: true})), isTrue);
      });

      test('$name stays out when the reader has not asked for it', () {
        expect(c.predicate(_prefs({c.enabled: true, c.inHome: false})), isFalse);
      });

      test('$name stays out when the plugin is off, however the switch is set', () {
        expect(c.predicate(_prefs({c.enabled: false, c.inHome: true})), isFalse);
      });

      test('$name stays out of a feed that has never been configured', () {
        expect(c.predicate(_prefs(const {})), isFalse);
      });
    }
  });
}
