import 'package:flutter_test/flutter_test.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/plugins/plugin_registry.dart';
import 'package:xta/plugins/subscription_source.dart';
import 'package:xta/subscriptions/subscription_look.dart';

Subscription _reddit(String name) =>
    RedditSubscription(id: name.toLowerCase(), name: name, createdAt: DateTime.utc(2026), inFeed: true);

Subscription _threads(String handle) =>
    ThreadsSubscription(id: handle, name: handle, avatarUrl: null, createdAt: DateTime.utc(2026), inFeed: true);

Subscription _x() => UserSubscription(
  id: '1',
  screenName: 'reader',
  name: 'Reader',
  profileImageUrlHttps: null,
  verified: false,
  createdAt: DateTime.utc(2026),
  inFeed: true,
);

void main() {
  group('the registry of subscription sources', () {
    test('every network that can join a group is one', () {
      final tables = subscriptionSources.map((e) => e.subscriptionTable).toSet();

      expect(tables, hasLength(subscriptionSources.length), reason: 'two sources claim the same table');
      expect(subscriptionSources.length, greaterThanOrEqualTo(5));
    });

    test('exactly one source owns a given subscription', () {
      for (final subscription in [_reddit('flutter'), _threads('zuck')]) {
        expect(subscriptionSources.where((s) => s.owns(subscription)), hasLength(1));
      }
    });

    test("no source claims X's own subscriptions", () {
      expect(subscriptionSources.where((s) => s.owns(_x())), isEmpty);
    });

    test('a stored row round-trips through the source that owns it', () {
      final original = _reddit('Flutter');
      final source = subscriptionSources.firstWhere((s) => s.owns(original));

      final restored = source.subscriptionFromMap(original.toMap());

      expect(source.owns(restored), isTrue);
      expect(restored.id, original.id);
    });
  });

  group('what the app draws for a subscription', () {
    test('comes from the source that owns it', () {
      expect(subscriptionSubtitle(_reddit('flutter')), 'r/flutter');
      expect(sourceOf(_reddit('flutter')), isA<SubscriptionSource>());
    });

    test('falls back to a handle for X accounts', () {
      expect(subscriptionSubtitle(_x()), '@reader');
      expect(sourceOf(_x()), isNull);
    });

    test('every source leads somewhere when tapped', () {
      for (final subscription in [_reddit('flutter'), _threads('zuck')]) {
        expect(subscriptionDestination(subscription), isNotNull);
      }
    });

    test('an X account has no plugin screen to open', () {
      expect(subscriptionDestination(_x()), isNull);
    });
  });
}
