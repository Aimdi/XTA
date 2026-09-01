import 'package:flutter_test/flutter_test.dart';
import 'package:xta/plugins/bluesky/bluesky_interleaved.dart';
import 'package:xta/plugins/bluesky/bluesky_models.dart';

void main() {
  BlueskyPost post({
    required String uri,
    required DateTime publishedAt,
  }) =>
      BlueskyPost(
        uri: uri,
        cid: 'cid',
        handle: 'alice.bsky.social',
        did: 'did:plc:a',
        authorName: 'Alice',
        text: 'hi',
        url: 'https://bsky.app/profile/alice.bsky.social/post/x',
        publishedAt: publishedAt,
      );

  test('blueskyInterleavedItems keeps dated posts up to the limit', () {
    final items = blueskyInterleavedItems(
      [
        post(uri: 'at://1', publishedAt: DateTime.utc(2026, 8, 1)),
        post(uri: 'at://2', publishedAt: DateTime.utc(2026, 8, 2)),
        post(uri: 'at://3', publishedAt: DateTime.utc(2026, 8, 3)),
      ],
      limit: 2,
    );

    expect(items, hasLength(2));
    expect(items.first.date, DateTime.utc(2026, 8, 1));
    expect(items.last.date, DateTime.utc(2026, 8, 2));
  });

  test('blueskyInterleavedItems drops posts without a date', () {
    final undated = BlueskyPost(
      uri: 'at://undated',
      cid: 'cid',
      handle: 'alice.bsky.social',
      did: 'did:plc:a',
      authorName: 'Alice',
      text: 'no date',
      url: 'https://bsky.app/profile/alice.bsky.social/post/y',
    );

    final items = blueskyInterleavedItems([
      undated,
      post(uri: 'at://ok', publishedAt: DateTime.utc(2026, 8, 1)),
    ]);

    expect(items, hasLength(1));
    expect(items.single.date, DateTime.utc(2026, 8, 1));
  });
}
