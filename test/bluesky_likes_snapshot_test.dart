import 'package:flutter_test/flutter_test.dart';
import 'package:xta/plugins/bluesky/bluesky_models.dart';

void main() {
  test('BlueskyPost snapshot round-trips all liked-post fields', () {
    final post = BlueskyPost(
      uri: 'at://did:plc:a/app.bsky.feed.post/abc',
      cid: 'bafytest',
      handle: 'alice.bsky.social',
      did: 'did:plc:a',
      authorName: 'Alice',
      avatarUrl: 'https://example.com/avatar.jpg',
      text: 'A saved Bluesky post',
      images: const [
        'https://example.com/one.jpg',
        'https://example.com/two.jpg',
      ],
      publishedAt: DateTime.utc(2026, 8, 7, 12, 30),
      url: 'https://bsky.app/profile/alice.bsky.social/post/abc',
      likeCount: 12,
      replyCount: 3,
      repostCount: 4,
      quoteCount: 1,
      linkCard: const BlueskyLinkCard(
        url: 'https://example.com/story',
        title: 'Story title',
        description: 'Story summary',
        imageUrl: 'https://example.com/card.jpg',
      ),
      repostedByHandle: 'bob.bsky.social',
      repostedByName: 'Bob',
      quotedPost: const BlueskyPost(
        uri: 'at://did:plc:b/app.bsky.feed.post/quote',
        cid: 'bafyquote',
        handle: 'carol.bsky.social',
        did: 'did:plc:b',
        authorName: 'Carol',
        text: 'Quoted',
        url: 'https://bsky.app/profile/carol.bsky.social/post/quote',
      ),
    );

    final raw = BlueskyPost.listToPrefs([post]);
    final restored = BlueskyPost.listFromPrefs(raw).single;

    expect(restored.uri, post.uri);
    expect(restored.cid, post.cid);
    expect(restored.handle, post.handle);
    expect(restored.did, post.did);
    expect(restored.authorName, post.authorName);
    expect(restored.avatarUrl, post.avatarUrl);
    expect(restored.text, post.text);
    expect(restored.images, post.images);
    expect(restored.publishedAt, post.publishedAt?.toLocal());
    expect(restored.url, post.url);
    expect(restored.likeCount, post.likeCount);
    expect(restored.replyCount, post.replyCount);
    expect(restored.repostCount, post.repostCount);
    expect(restored.quoteCount, post.quoteCount);
    expect(restored.linkCard?.url, post.linkCard?.url);
    expect(restored.linkCard?.title, post.linkCard?.title);
    expect(restored.repostedByHandle, post.repostedByHandle);
    expect(restored.repostedByName, post.repostedByName);
    expect(restored.quotedPost?.uri, post.quotedPost?.uri);
    expect(restored.quotedPost?.text, 'Quoted');
  });
}
