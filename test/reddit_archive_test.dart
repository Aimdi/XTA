import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:xta/plugins/reddit/reddit_archive.dart';
import 'package:xta/plugins/reddit/reddit_client.dart';
import 'package:xta/saved/saved_content_index.dart';

RedditPost _post() => RedditPost(
      id: 'abc',
      title: 'Who to pull on reunion banner',
      subreddit: 'GirlsFrontline2',
      permalink: '/r/GirlsFrontline2/comments/abc/who/',
      author: 'Jikikato01',
      selfText: 'Q1 Quihua',
    );

void main() {
  test('archive ids cannot collide with an X snowflake', () {
    expect(redditArchiveId('abc'), 'reddit:abc');
    expect(isRedditArchiveId('reddit:abc'), isTrue);
    expect(isRedditArchiveId('1234567890'), isFalse);
  });

  test('the blob round-trips and is recognised as Reddit', () {
    final blob = redditArchiveBlob(_post());
    expect(blob['xtaPlugin'], 'reddit');
    final encoded = jsonEncode(blob);
    expect(isRedditArchiveBlob(encoded), isTrue);
    expect(isRedditArchiveBlob('{"id_str":"1"}'), isFalse);

    final parsed = redditPostFromArchive(jsonDecode(encoded));
    expect(parsed, isNotNull);
    expect(parsed!.id, 'abc');
    expect(parsed.subreddit, 'GirlsFrontline2');
    expect(parsed.title, 'Who to pull on reunion banner');
  });

  test('parseSavedContent renders a Reddit blob, not an X tweet', () {
    final stored = parseSavedContent(jsonEncode(redditArchiveBlob(_post())));
    expect(stored.tweet, isNull);
    expect(stored.reddit, isNotNull);
    expect(stored.reddit!.id, 'abc');
    expect(stored.matches('quiHua'.toLowerCase()), isTrue);
    expect(stored.matches('girlsfrontline2'), isTrue);
    expect(stored.matches('nope'), isFalse);
  });

  test('an ordinary tweet blob still parses as a tweet', () {
    final stored = parseSavedContent(jsonEncode({
      'id_str': '99',
      'full_text': 'hello from x',
      'user': {'screen_name': 'someone', 'id_str': '1'},
    }));
    expect(stored.reddit, isNull);
    expect(stored.tweet, isNotNull);
    expect(stored.tweet!.idStr, '99');
    expect(stored.matches('hello from x'), isTrue);
  });

  test('an upvote stores the same blob Archiv likes will render', () {
    final blob = redditArchiveBlob(_post());
    final stored = parseSavedContent(jsonEncode(blob));
    expect(redditArchiveId(_post().id), 'reddit:abc');
    expect(stored.reddit, isNotNull);
    expect(stored.reddit!.title, 'Who to pull on reunion banner');
  });
}
