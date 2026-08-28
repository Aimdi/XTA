import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/reddit/reddit_client.dart';
import 'package:xta/plugins/reddit/reddit_media_frame.dart';
import 'package:xta/plugins/reddit/reddit_post_media.dart';
import 'package:xta/plugins/reddit/reddit_sort_sheet.dart';

RedditPost _post({bool spoiler = false, bool over18 = false}) => RedditPost(
  id: 'a',
  title: 'look',
  subreddit: 'x',
  permalink: '/r/x/comments/a/',
  url: 'https://i.redd.it/abc.jpg',
  domain: 'i.redd.it',
  spoiler: spoiler,
  over18: over18,
);

void main() {
  test('spoilers stay covered until the setting is on', () {
    final off = PrefServiceCache();
    final on = PrefServiceCache(cache: {optionPluginRedditShowSpoilers: true});

    expect(storedRedditShowSpoilers(off), isFalse);
    expect(redditMediaShouldGate(_post(spoiler: true), off), isTrue);
    expect(
      redditMediaGateKind(_post(spoiler: true), off),
      RedditSensitiveGateKind.spoiler,
    );

    expect(storedRedditShowSpoilers(on), isTrue);
    expect(redditMediaShouldGate(_post(spoiler: true), on), isFalse);
  });

  test('showing spoilers still covers NSFW when that mode asks for a tap', () {
    final prefs = PrefServiceCache(
      cache: {
        optionPluginRedditShowSpoilers: true,
        optionPluginRedditNsfwMode: RedditNsfwMode.tap.name,
      },
    );

    expect(
      redditMediaShouldGate(_post(spoiler: true, over18: true), prefs),
      isTrue,
    );
    expect(
      redditMediaGateKind(_post(spoiler: true, over18: true), prefs),
      RedditSensitiveGateKind.nsfw,
    );
    expect(redditMediaShouldGate(_post(over18: true), prefs), isTrue);
  });

  test('always-show NSFW and spoilers leaves ordinary media uncovered', () {
    final prefs = PrefServiceCache(
      cache: {
        optionPluginRedditShowSpoilers: true,
        optionPluginRedditNsfwMode: RedditNsfwMode.show.name,
      },
    );

    expect(
      redditMediaShouldGate(_post(spoiler: true, over18: true), prefs),
      isFalse,
    );
    expect(redditMediaShouldGate(_post(), prefs), isFalse);
  });
}
