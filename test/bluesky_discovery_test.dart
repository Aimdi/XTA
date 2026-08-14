import 'package:flutter_test/flutter_test.dart';
import 'package:xta/plugins/bluesky/bluesky_discovery.dart';
import 'package:xta/plugins/bluesky/bluesky_models.dart';

BlueskyPost _post({
  required String handle,
  String name = '',
  String? repostedBy,
  BlueskyPost? quote,
}) {
  return BlueskyPost(
    uri: 'at://did:plc:$handle/app.bsky.feed.post/1',
    cid: 'cid',
    handle: handle,
    did: 'did:plc:$handle',
    authorName: name.isEmpty ? handle : name,
    text: 'hi',
    url: 'https://bsky.app/profile/$handle/post/1',
    repostedByHandle: repostedBy,
    quotedPost: quote,
  );
}

void main() {
  test('reposted authors come first; quotes fill the rest', () {
    final people = peopleToFollowFromBluesky(
      posts: [
        _post(handle: 'alice.bsky.social'),
        _post(
          handle: 'bob.bsky.social',
          name: 'Bob',
          repostedBy: 'alice.bsky.social',
        ),
        _post(
          handle: 'alice.bsky.social',
          quote: _post(handle: 'cara.bsky.social', name: 'Cara'),
        ),
      ],
      alreadyFollows: (handle) => handle == 'alice.bsky.social',
    );

    expect(people.map((e) => e.handle), [
      'bob.bsky.social',
      'cara.bsky.social',
    ]);
    expect(people.first.fromRepost, isTrue);
    expect(people.last.fromRepost, isFalse);
    expect(people.last.name, 'Cara');
  });

  test('parseBlueskyStarterPackRef reads web and at:// forms', () {
    final web = parseBlueskyStarterPackRef(
      'https://bsky.app/starter-pack/alice.bsky.social/3abc',
    );
    expect(web?.actor, 'alice.bsky.social');
    expect(web?.rkey, '3abc');
    expect(web?.atUri, isNull);

    final at = parseBlueskyStarterPackRef(
      'at://did:plc:abc/app.bsky.graph.starterpack/3abc',
    );
    expect(at?.atUri, 'at://did:plc:abc/app.bsky.graph.starterpack/3abc');

    expect(parseBlueskyStarterPackRef('https://go.bsky.app/xyz'), isNull);
    expect(
      parseBlueskyStarterPackRef('https://bsky.app/profile/alice.bsky.social'),
      isNull,
    );
    expect(parseBlueskyStarterPackRef(''), isNull);
  });

  test('starterPackListUri reads list.uri, list string, or record.list', () {
    expect(
      starterPackListUri({
        'starterPack': {
          'list': {'uri': 'at://did:plc:abc/app.bsky.graph.list/1'},
        },
      }),
      'at://did:plc:abc/app.bsky.graph.list/1',
    );
    expect(
      starterPackListUri({
        'starterPack': {'list': 'at://did:plc:abc/app.bsky.graph.list/2'},
      }),
      'at://did:plc:abc/app.bsky.graph.list/2',
    );
    expect(
      starterPackListUri({
        'starterPack': {
          'record': {'list': 'at://did:plc:abc/app.bsky.graph.list/3'},
        },
      }),
      'at://did:plc:abc/app.bsky.graph.list/3',
    );
    expect(starterPackListUri({'starterPack': {}}), isNull);
  });
}
