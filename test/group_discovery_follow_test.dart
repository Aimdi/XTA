import 'package:flutter_test/flutter_test.dart';
import 'package:xta/group/group_discovery.dart';
import 'package:xta/group/group_discovery_follow.dart';

class _Writer implements DiscoveryGroupWriter {
  final followed = <String>[];
  final memberships = <String, List<String>>{};
  final existing = <String, List<String>>{};

  @override
  Future<String> follow(DiscoveryAccount account) async {
    followed.add(account.key);
    return discoverySubscription(account).id;
  }

  @override
  Future<List<String>> listGroupsForUser(String id) async => memberships[id] ?? existing[id] ?? const [];

  @override
  Future<void> saveUserGroupMembership(String id, List<String> value) async => memberships[id] = value;
}

DiscoveryAccount _account(DiscoverySource source, String id, String handle) =>
    DiscoveryAccount(source: source, id: id, handle: handle, name: 'Name');

void main() {
  test('adding to a group follows first and keeps the memberships the account already has', () async {
    final writer = _Writer()..existing['123'] = ['books', 'art'];
    final artist = _account(DiscoverySource.x, '123', 'artist');
    await addDiscoveryToGroup(writer, artist, 'art');
    expect(writer.memberships['123'], ['books', 'art']);
    await addDiscoveryToGroup(writer, artist, 'new');
    expect(writer.followed, ['x:123', 'x:123']);
    expect(writer.memberships['123'], ['books', 'art', 'new']);
  });

  test('group rows are keyed the way each network keys its own follows', () {
    expect(discoverySubscription(_account(DiscoverySource.x, '123', 'artist')).id, '123');
    expect(
      discoverySubscription(_account(DiscoverySource.bluesky, 'did:plc:abc', 'artist.bsky.social')).id,
      'artist.bsky.social',
    );
    expect(
      discoverySubscription(_account(DiscoverySource.mastodon, 'artist@server.test', 'artist@server.test')).id,
      'artist@server.test',
    );
    expect(discoverySubscription(_account(DiscoverySource.pixiv, '42', 'artist')).id, 'pixiv:42');
  });

  test('the add store marks a row as adding, then added, and forgets a failed one', () async {
    final store = DiscoveryAddStore();
    addTearDown(store.destroy);
    expect(await store.add('x:1', () async {}), isTrue);
    expect(store.state.added, {'x:1'});
    expect(await store.add('x:1', () async {}), isFalse);
    expect(await store.add('x:2', () async => throw StateError('offline')), isFalse);
    expect(store.state.adding, isEmpty);
    expect(store.state.added, {'x:1'});
  });
}
