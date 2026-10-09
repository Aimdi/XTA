import 'package:flutter/widgets.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/group/group_discovery_follows.dart';
import 'package:xta/profile/profile_followed_by.dart';
import 'support/memory_json_store.dart';

UserSubscription _sub(String id) => UserSubscription(
  id: id,
  screenName: id,
  name: id.toUpperCase(),
  profileImageUrlHttps: null,
  verified: false,
  createdAt: DateTime(2026),
  inFeed: true,
);

DiscoveryFollow _follow(String id) => DiscoveryFollow(id: id, handle: id, name: id);

RememberedFollows _read(List<String> ids) => (at: DateTime(2026, 10), follows: ids.map(_follow).toList());

void main() {
  group('profileFollowedBy', () {
    test('orders by how recently each followed, then subscription order', () {
      final result = profileFollowedBy(
        'p',
        {
          'a': _read(['x', 'y', 'p']),
          'b': _read(['p']),
          'c': _read(['x', 'p']),
          'd': _read(['p', 'z']),
        },
        [_sub('a'), _sub('b'), _sub('c'), _sub('d')],
      );
      expect(result.followers.map((s) => s.id), ['b', 'd', 'c', 'a']);
      expect(result.checked, 4);
      expect(result.total, 4);
    });

    test('counts unread subscriptions as unchecked, not as non-followers', () {
      final result = profileFollowedBy(
        'p',
        {
          'a': _read(['p']),
          'b': _read(['q']),
        },
        [_sub('a'), _sub('b'), _sub('c'), _sub('d')],
      );
      expect(result.followers.map((s) => s.id), ['a']);
      expect(result.checked, 2);
      expect(result.total, 4);
    });

    test('ignores former subscriptions, other networks and the profile', () {
      final result = profileFollowedBy(
        'p',
        {
          'gone': _read(['p']),
          'p': _read(['p']),
          'a': _read(['p']),
        },
        [_sub('a'), _sub('p'), SearchSubscription(id: 'q', createdAt: DateTime(2026))],
      );
      expect(result.followers.map((s) => s.id), ['a']);
      expect(result.checked, 1);
      expect(result.total, 1);
    });

    test('nothing remembered means nothing to show', () {
      final result = profileFollowedBy('p', const {}, [_sub('a')]);
      expect(result.followers, isEmpty);
      expect(result.checked, 0);
      expect(result.total, 1);
    });
  });

  group('profileFollowedByLabel', () {
    late L10n l10n;
    setUpAll(() async => l10n = await L10n.load(const Locale('en')));

    test('names up to three, then two and a count', () {
      expect(profileFollowedByLabel(l10n, []), '');
      expect(profileFollowedByLabel(l10n, ['A']), 'Followed by A');
      expect(profileFollowedByLabel(l10n, ['A', 'B']), 'Followed by A and B');
      expect(profileFollowedByLabel(l10n, ['A', 'B', 'C']), 'Followed by A, B and C');
      expect(profileFollowedByLabel(l10n, ['A', 'B', 'C', 'D']), 'Followed by A, B and 2 others you subscribe to');
      expect(
        profileFollowedByLabel(l10n, List.generate(14, (i) => 'N$i')),
        'Followed by N0, N1 and 12 others you subscribe to',
      );
    });
  });

  group('remembered follows', () {
    test('keep a month of reads across launches, without asking X', () async {
      final storage = MemoryJsonStore();
      var now = DateTime(2026, 10, 1);
      var asked = 0;
      DiscoveryFollowsCache cache() => DiscoveryFollowsCache(
        storage: storage,
        now: () => now,
        fetch: (member) async {
          asked++;
          return [_follow('f-$member')];
        },
      );

      await cache().read(['a'], maxFetches: 1);
      await cache().remember('b', [_follow('p')]);
      expect(asked, 1);

      now = now.add(const Duration(days: 10));
      final restored = await cache().remembered();
      expect(restored.keys, unorderedEquals(['a', 'b']));
      expect(restored['b']!.follows.single.id, 'p');
      expect(asked, 1);

      now = now.add(const Duration(days: 25));
      expect(await cache().remembered(), isEmpty);
    });

    test('a stale read still asks X again for Discover', () async {
      final storage = MemoryJsonStore();
      var now = DateTime(2026, 10, 1);
      final asked = <String>[];
      DiscoveryFollowsCache cache() => DiscoveryFollowsCache(
        storage: storage,
        now: () => now,
        fetch: (member) async {
          asked.add(member);
          return const [];
        },
      );

      await cache().remember('a', [_follow('p')]);
      now = now.add(const Duration(days: 5));
      final result = await cache().read(['a'], maxFetches: 1);
      expect(asked, ['a']);
      expect(result.follows['a'], isEmpty);
    });

    test('the store answers from what is remembered', () async {
      final store = ProfileFollowedByStore(
        remembered: () async => {
          'a': _read(['p']),
        },
      );
      await store.load('p', [_sub('a'), _sub('b')]);
      expect(store.state.followers.single.id, 'a');
      expect(store.state.checked, 1);
      expect(store.state.total, 2);
      await store.destroy();
    });
  });
}
