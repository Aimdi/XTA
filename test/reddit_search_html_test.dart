import 'package:flutter_test/flutter_test.dart';
import 'package:xta/plugins/reddit/reddit_search_html.dart';
import 'package:xta/plugins/reddit/reddit_search_json.dart';

/// old.reddit's search page: `.search-result-*` blocks, not the `div.thing` a
/// subreddit listing uses.
const _searchPost = '''
<div class="search-result search-result-link" data-fullname="t3_abc123">
  <header class="search-result-header">
    <a class="search-title may-blank" href="https://old.reddit.com/r/dartlang/comments/abc123/a_title/">A title</a>
    <span class="search-result-meta">
      <span class="search-score">42 points</span>
      <span class="search-time">submitted <time datetime="2026-07-01T10:00:00+00:00">1 hour ago</time></span>
      <a class="author may-blank" href="/user/someone">someone</a>
      <a class="search-subreddit-link may-blank" href="/r/dartlang/">r/dartlang</a>
      <a class="search-comments may-blank" href="#">7 comments</a>
    </span>
  </header>
</div>
''';

/// The shape a subreddit listing page has, which search sometimes returns too.
const _listingPost = '''
<div class="thing" data-fullname="t3_zzz" data-subreddit="dartlang"
     data-permalink="/r/dartlang/comments/zzz/x/" data-score="3" data-comments-count="1">
  <a class="title" href="/r/dartlang/comments/zzz/x/">From a listing</a>
</div>
''';

String _page(String body) => '<!doctype html><html><body>$body</body></html>';

void main() {
  group('post results', () {
    test('are read out of the search markup', () {
      final post = parseSearchPosts(_page(_searchPost)).single;

      expect(post.id, 'abc123');
      expect(post.title, 'A title');
      expect(post.subreddit, 'dartlang');
      expect(post.author, 'someone');
      expect(post.score, 42);
      expect(post.commentCount, 7);
      expect(post.permalink, '/r/dartlang/comments/abc123/a_title/');
      expect(post.createdAt, DateTime.parse('2026-07-01T10:00:00Z').toLocal());
    });

    test(
      'a page rendered as a listing is read by the listing parser instead',
      () {
        final post = parseSearchPosts(_page(_listingPost)).single;

        expect(post.id, 'zzz');
        expect(post.title, 'From a listing');
      },
    );

    test('the id survives a result with no data-fullname', () {
      final withoutId = _searchPost.replaceAll('data-fullname="t3_abc123"', '');

      expect(
        parseSearchPosts(_page(withoutId)).single.id,
        'abc123',
        reason: 'the comments path carries it',
      );
    });

    test('a score with a comma is still a number', () {
      final busy = _searchPost.replaceAll('42 points', '1,234 points');

      expect(parseSearchPosts(_page(busy)).single.score, 1234);
    });

    test('a spoiler result carries the flag', () {
      final spoiled = _searchPost.replaceFirst(
        'search-result-link',
        'search-result-link spoiler',
      );

      expect(parseSearchPosts(_page(spoiled)).single.spoiler, isTrue);
    });

    test('a result with no title is skipped rather than guessed at', () {
      expect(
        parseSearchPosts(_page('<div class="search-result-link"></div>')),
        isEmpty,
      );
      expect(parseSearchPosts('not html at all'), isEmpty);
      expect(parseSearchPosts(''), isEmpty);
    });
  });

  group('subreddit results', () {
    test('are read from either markup, without repeating one', () {
      const results = '''
<div class="search-result search-result-subreddit">
  <a class="search-subreddit-link" href="/r/dartlang/">r/dartlang</a>
  <span class="search-subscribers">12,345 subscribers</span>
  <div class="search-result-body">A place for Dart</div>
</div>
<div class="subreddit thing">
  <a class="title" href="/r/dartlang/">r/dartlang</a>
</div>
<div class="subreddit thing">
  <a class="title" href="/r/flutterdev/">r/flutterdev</a>
</div>
''';

      final parsed = parseSubredditResults(_page(results));

      expect(parsed.map((e) => e.name), ['dartlang', 'flutterdev']);
      expect(parsed.first.subscribers, 12345);
      expect(parsed.first.description, 'A place for Dart');
    });

    test('an entry with no name is skipped', () {
      expect(
        parseSubredditResults(
          _page('<div class="search-result-subreddit"></div>'),
        ),
        isEmpty,
      );
    });
  });

  group('user results', () {
    test('are read and deduplicated', () {
      const results = '''
<div class="search-result search-result-user">
  <a href="/user/someone">someone</a>
  <span class="search-result-user-karma">3,210 karma</span>
</div>
<div class="search-result search-result-user"><a href="/user/someone">someone</a></div>
<div class="search-result search-result-user"><a href="/user/another">another</a></div>
''';

      final parsed = parseUserResults(_page(results));

      expect(parsed.map((e) => e.name), ['someone', 'another']);
      expect(parsed.first.karma, 3210);
    });

    test('a page with no users yields none rather than throwing', () {
      expect(parseUserResults(_page('')), isEmpty);
      expect(parseUserResults('junk'), isEmpty);
    });
  });

  group('JSON search listings', () {
    test('posts are read from t3 children', () {
      final posts = parseSearchPostsJson({
        'kind': 'Listing',
        'data': {
          'children': [
            {
              'kind': 't3',
              'data': {
                'id': 'abc123',
                'title': 'Hu Tao build',
                'subreddit': 'Genshin_Impact',
                'permalink': '/r/Genshin_Impact/comments/abc123/hu_tao/',
                'author': 'someone',
                'score': 12,
                'num_comments': 4,
              },
            },
          ],
        },
      });

      expect(posts.single.id, 'abc123');
      expect(posts.single.title, 'Hu Tao build');
      expect(posts.single.subreddit, 'Genshin_Impact');
      expect(posts.single.commentCount, 4);
    });

    test('subreddits are read from t5 children', () {
      final results = parseSubredditResultsJson({
        'kind': 'Listing',
        'data': {
          'children': [
            {
              'kind': 't5',
              'data': {
                'display_name': 'HuTaoMains',
                'public_description': 'For Hu Tao',
                'subscribers': 12000,
              },
            },
          ],
        },
      });

      expect(results.single.name, 'HuTaoMains');
      expect(results.single.subscribers, 12000);
      expect(results.single.description, 'For Hu Tao');
    });

    test('users are read from t2 children', () {
      final results = parseUserResultsJson({
        'kind': 'Listing',
        'data': {
          'children': [
            {
              'kind': 't2',
              'data': {'name': 'hutao', 'total_karma': 321},
            },
          ],
        },
      });

      expect(results.single.name, 'hutao');
      expect(results.single.karma, 321);
    });

    test('a missing listing yields none rather than throwing', () {
      expect(parseSearchPostsJson(null), isEmpty);
      expect(parseSubredditResultsJson('nope'), isEmpty);
      expect(parseUserResultsJson(<String, Object?>{}), isEmpty);
    });
  });
}
