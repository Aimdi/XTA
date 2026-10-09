import 'package:flutter_test/flutter_test.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/group/group_discovery.dart';
import 'package:xta/group/group_discovery_follows.dart';
import 'package:xta/group/group_discovery_x.dart';
import 'support/memory_json_store.dart';

UserSubscription _member(String id) => UserSubscription(
  id: id,
  screenName: id,
  name: id,
  profileImageUrlHttps: null,
  verified: false,
  createdAt: DateTime(2026),
  inFeed: true,
);

void main() {
  test('reads never-asked members first, remembers answers across launches and asks again after the TTL', () async {
    final storage = MemoryJsonStore();
    var now = DateTime(2026, 10, 1);
    final asked = <String>[];
    DiscoveryFollowsCache cache() => DiscoveryFollowsCache(
      storage: storage,
      now: () => now,
      fetch: (member) async {
        asked.add(member);
        return [DiscoveryFollow(id: 'f-$member', handle: 'f$member', name: 'F $member')];
      },
    );
    const members = ['a', 'b', 'c', 'd'];

    final first = cache();
    final result = await first.read(members, maxFetches: 2);
    expect(asked, ['a', 'b']);
    expect(result.follows.keys, unorderedEquals(['a', 'b']));
    expect(result.error, isNull);

    await first.read(members, maxFetches: 2);
    expect(asked, ['a', 'b', 'c', 'd']);

    final restored = cache();
    final again = await restored.read(members, maxFetches: 2);
    expect(asked, hasLength(4));
    expect(again.follows.keys, unorderedEquals(members));
    expect(again.follows['c']!.single.handle, 'fc');

    now = now.add(const Duration(days: 4));
    await restored.read(members, maxFetches: 2);
    expect(asked.sublist(4), ['a', 'b']);
  });

  test('a member whose read fails is reported without losing the others', () async {
    final cache = DiscoveryFollowsCache(
      storage: MemoryJsonStore(),
      fetch: (member) async {
        if (member == 'b') throw StateError('offline');
        return [const DiscoveryFollow(id: 'f', handle: 'f', name: 'F')];
      },
    );
    final result = await cache.read(['a', 'b'], maxFetches: 2);
    expect(result.follows.keys, ['a']);
    expect(result.error, isA<StateError>());
  });

  test('co-follows become candidates vouched for by each member following them, never a member itself', () {
    final members = {'m1': _member('m1'), 'm2': _member('m2')};
    final accounts = xFollowDiscoveryAccounts({
      'm1': [
        const DiscoveryFollow(id: 'c1', handle: 'c1', name: 'C1', bio: 'Bio'),
        const DiscoveryFollow(id: 'm2', handle: 'm2', name: 'M2'),
      ],
      'm2': [const DiscoveryFollow(id: 'c1', handle: 'c1', name: 'C1')],
    }, members);
    expect(accounts.map((account) => account.id), ['c1', 'c1']);
    final merged = mergeDiscoveryAccounts(accounts).single;
    expect(merged.memberCount, 2);
    expect(merged.supporters.every((supporter) => supporter.kind == DiscoverySignal.followed), isTrue);
    expect(merged.snippet, 'Bio');
    expect(merged.link, 'https://x.com/c1');
  });
}
