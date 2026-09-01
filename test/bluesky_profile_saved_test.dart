import 'package:flutter_test/flutter_test.dart';
import 'package:xta/plugins/bluesky/bluesky_likes_store.dart';
import 'package:xta/plugins/bluesky/bluesky_models.dart';

BlueskyPost _post(String id, {required String handle, required String did}) {
  return BlueskyPost(
    uri: 'at://$did/app.bsky.feed.post/$id',
    cid: 'cid-$id',
    handle: handle,
    did: did,
    authorName: handle,
    text: id,
    url: 'https://bsky.app/profile/$handle/post/$id',
  );
}

void main() {
  final alice = _post(
    'one',
    handle: 'alice.bsky.social',
    did: 'did:plc:alice',
  );
  final bob = _post('two', handle: 'bob.bsky.social', did: 'did:plc:bob');
  final aliceLater = _post(
    'three',
    handle: 'alice.bsky.social',
    did: 'did:plc:alice',
  );

  test('Saved on a profile is only that author\'s local likes', () {
    final liked = [bob, alice, aliceLater];
    expect(
      blueskyLikesByAuthor(
        liked,
        did: 'did:plc:alice',
        handle: 'alice.bsky.social',
      ).map((p) => p.uri),
      [
        'at://did:plc:alice/app.bsky.feed.post/one',
        'at://did:plc:alice/app.bsky.feed.post/three',
      ],
    );
  });

  test('a DID match still counts after a handle rename', () {
    final renamed = _post(
      'one',
      handle: 'alice.new.bsky.social',
      did: 'did:plc:alice',
    );
    expect(
      blueskyLikesByAuthor(
        [renamed, bob],
        did: 'did:plc:alice',
        handle: 'alice.bsky.social',
      ).map((p) => p.uri),
      ['at://did:plc:alice/app.bsky.feed.post/one'],
    );
  });

  test('handle match is case-insensitive', () {
    expect(
      blueskyLikesByAuthor(
        [alice],
        did: '',
        handle: 'Alice.bsky.social',
      ).single.uri,
      alice.uri,
    );
  });

  test('blank actor does not dump every local like onto the tab', () {
    expect(blueskyLikesByAuthor([alice, bob], did: '', handle: ''), isEmpty);
  });
}
