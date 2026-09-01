import 'package:flutter_test/flutter_test.dart';
import 'package:xta/plugins/threads/threads_models.dart';

void main() {
  test('ThreadsPost snapshot round-trips all liked-post fields', () {
    final post = ThreadsPost(
      id: 'post-1',
      handle: 'author',
      authorName: 'Author Name',
      avatarUrl: 'https://example.com/avatar.jpg',
      text: 'A saved Threads post',
      images: const [
        'https://example.com/one.jpg',
        'https://example.com/two.jpg',
      ],
      publishedAt: DateTime.utc(2026, 8, 7, 12, 30),
      url: 'https://www.threads.com/@author/post/abc',
      likeCount: 12,
      replyCount: 3,
      repostCount: 4,
      linkCard: const ThreadsLinkCard(
        url: 'https://example.com/story',
        title: 'Story title',
        description: 'Story summary',
        imageUrl: 'https://example.com/card.jpg',
        providerName: 'example.com',
      ),
      repostedByHandle: 'reader',
      repostedByName: 'Reader',
      isVerified: true,
    );

    final raw = ThreadsPost.listToPrefs([post]);
    final restored = ThreadsPost.listFromPrefs(raw).single;

    expect(restored.id, post.id);
    expect(restored.handle, post.handle);
    expect(restored.authorName, post.authorName);
    expect(restored.avatarUrl, post.avatarUrl);
    expect(restored.text, post.text);
    expect(restored.images, post.images);
    expect(restored.publishedAt, post.publishedAt?.toLocal());
    expect(restored.url, post.url);
    expect(restored.likeCount, post.likeCount);
    expect(restored.replyCount, post.replyCount);
    expect(restored.repostCount, post.repostCount);
    expect(restored.linkCard?.url, post.linkCard?.url);
    expect(restored.linkCard?.title, post.linkCard?.title);
    expect(restored.linkCard?.description, post.linkCard?.description);
    expect(restored.linkCard?.imageUrl, post.linkCard?.imageUrl);
    expect(restored.linkCard?.providerName, post.linkCard?.providerName);
    expect(restored.repostedByHandle, post.repostedByHandle);
    expect(restored.repostedByName, post.repostedByName);
    expect(restored.isVerified, isTrue);
  });
}
