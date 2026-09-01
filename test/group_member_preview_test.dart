import 'package:flutter_test/flutter_test.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/plugins/plugin_registry.dart';
import 'package:xta/plugins/subscription_source.dart';

SubscriptionSource _sourceFor(Subscription subscription) => subscriptionSources.firstWhere((s) => s.owns(subscription));

DateTime get _at => DateTime.utc(2026);

void main() {
  group('the face a group shows for one of its members', () {
    // The gap this closes: the cover query joined the Reddit table and nothing
    // else, so a group of only Threads, Bluesky or Fediverse accounts had no
    // cover at all — even though every one of them stores an avatar.
    test('a Threads account brings its avatar', () {
      final account = ThreadsSubscription(
        id: 'zuck',
        name: 'Zuck',
        avatarUrl: 'https://example.test/z.jpg',
        createdAt: _at,
        inFeed: true,
      );

      final preview = _sourceFor(account).previewOf(account);

      expect(preview.avatarUrl, 'https://example.test/z.jpg');
      expect(preview.name, 'Zuck');
      expect(preview.subreddit, isNull);
    });

    test('a Bluesky account brings its avatar', () {
      final account = BlueskySubscription(
        id: 'alice.bsky.social',
        name: 'Alice',
        avatarUrl: 'https://example.test/a.jpg',
        createdAt: _at,
        inFeed: true,
      );

      expect(_sourceFor(account).previewOf(account).avatarUrl, 'https://example.test/a.jpg');
    });

    test('a Fediverse account brings its avatar', () {
      final account = MastodonSubscription(
        id: 'alice@example.social',
        name: 'Alice',
        avatarUrl: 'https://example.test/m.jpg',
        createdAt: _at,
        inFeed: true,
      );

      expect(_sourceFor(account).previewOf(account).avatarUrl, 'https://example.test/m.jpg');
    });

    // A subreddit's picture is not a URL this app holds — it is fetched and
    // cached separately, and drawn from the name.
    test('a subreddit is marked as one instead of carrying a URL', () {
      final subreddit = RedditSubscription(id: 'flutter', name: 'flutter', createdAt: _at, inFeed: true);

      final preview = _sourceFor(subreddit).previewOf(subreddit);

      expect(preview.subreddit, 'flutter');
      expect(preview.avatarUrl, isNull);
    });

    test('a publication brings its logo', () {
      final publication = SubstackSubscription(
        id: 'astral',
        baseUrl: 'https://astral.substack.com',
        name: 'Astral',
        logoUrl: 'https://example.test/l.png',
        createdAt: _at,
        inFeed: true,
      );

      expect(_sourceFor(publication).previewOf(publication).avatarUrl, 'https://example.test/l.png');
    });

    test('every source can describe one of its members', () {
      for (final source in subscriptionSources) {
        expect(source.previewOf, isNotNull, reason: '${source.subscriptionTable} has no preview');
      }
    });
  });
}
