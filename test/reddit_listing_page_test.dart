import 'package:flutter_test/flutter_test.dart';
import 'package:xta/plugins/reddit/reddit_client.dart';
import 'package:xta/plugins/reddit/reddit_listing_page.dart';

RedditPost _post(String id) => RedditPost(
      id: id,
      title: id,
      subreddit: 'test',
      permalink: '/r/test/comments/$id/',
    );

void main() {
  group('appendRedditPosts', () {
    test('appends new posts in order', () {
      final result = appendRedditPosts([_post('a')], [_post('b'), _post('c')]);
      expect(result.map((p) => p.id), ['a', 'b', 'c']);
    });

    test('skips duplicates by id, keeping the first', () {
      final first = _post('a');
      final dup = _post('a');
      final result = appendRedditPosts([first], [dup, _post('b')]);
      expect(result.map((p) => p.id), ['a', 'b']);
      expect(identical(result.first, first), isTrue);
    });

    test('returns existing unchanged when next is empty', () {
      final existing = [_post('a')];
      expect(appendRedditPosts(existing, const []), existing);
    });
  });
}
