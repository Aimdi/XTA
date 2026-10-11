import 'package:dart_twitter_api/twitter_api.dart' show User;
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/client/client.dart';
import 'package:xta/profile/merged_timeline.dart';

/// A chain posted (or reposted) [hour] hours into the day; higher is newer.
TweetChain _at(String id, int hour, {bool pinned = false}) => TweetChain(
  id: id,
  isPinned: pinned,
  tweets: [
    TweetWithCard()
      ..idStr = id
      ..createdAt = DateTime.utc(2026, 10, 11, hour)
      ..user = (User()..idStr = 'u'),
  ],
);

/// A timeline served in [pages], recording the cursors it was asked for.
TimelineFetch _pages(List<List<TweetChain>> pages, List<String?> asked) => (cursor) async {
  asked.add(cursor);
  final index = cursor == null ? 0 : int.parse(cursor);
  final chains = index < pages.length ? pages[index] : const <TweetChain>[];
  return (chains: chains, next: index + 1 < pages.length ? '${index + 1}' : null);
};

List<String> _ids(List<TweetChain> chains) => chains.map((c) => c.id).toList();

Future<List<List<String>>> _readAll(MergedTimeline merged) async {
  final pages = <List<String>>[];
  while (merged.hasMore) {
    pages.add(_ids(await merged.next()));
  }
  return pages;
}

void main() {
  group('mergeReady', () {
    test('shows only what no open timeline can still beat', () {
      final step = mergeReady([_at('p9', 9), _at('p5', 5)], true, [_at('r8', 8), _at('r7', 7)], true);
      // The reposts may still hold something from 6 o'clock, so p5 waits.
      expect(_ids(step.ready), ['p9', 'r8', 'r7']);
      expect(_ids(step.restA), ['p5']);
      expect(step.restB, isEmpty);
    });

    test('a finished timeline no longer holds the other back', () {
      final step = mergeReady([_at('p9', 9), _at('p5', 5)], true, [_at('r8', 8)], false);
      expect(_ids(step.ready), ['p9', 'r8', 'p5']);
    });
  });

  group('MergedTimeline', () {
    test('posts and reposts come out newest first across pages', () async {
      final postsAsked = <String?>[];
      final repostsAsked = <String?>[];
      final merged = MergedTimeline(
        _pages([
          [_at('p10', 10), _at('p6', 6)],
          [_at('p3', 3), _at('p1', 1)],
        ], postsAsked),
        _pages([
          [_at('r9', 9), _at('r8', 8)],
          [_at('r4', 4), _at('r2', 2)],
        ], repostsAsked),
      );

      final pages = await _readAll(merged);
      expect(pages.expand((p) => p), ['p10', 'r9', 'r8', 'p6', 'r4', 'p3', 'r2', 'p1']);
      expect(postsAsked, [null, '1']);
      expect(repostsAsked, [null, '1']);
    });

    test('a profile without reposts reads like its posts alone', () async {
      final merged = MergedTimeline(
        _pages([
          [_at('p9', 9), _at('p8', 8)],
          [_at('p2', 2)],
        ], []),
        _pages([], []),
      );
      expect((await _readAll(merged)).expand((p) => p), ['p9', 'p8', 'p2']);
    });

    test('the pinned post stays first, whatever its age', () async {
      final merged = MergedTimeline(
        _pages([
          [_at('pinned', 1, pinned: true), _at('p9', 9)],
        ], []),
        _pages([
          [_at('r10', 10)],
        ], []),
      );
      expect((await _readAll(merged)).expand((p) => p), ['pinned', 'r10', 'p9']);
    });

    test('a post in both timelines appears once', () async {
      final merged = MergedTimeline(
        _pages([
          [_at('same', 9), _at('p5', 5)],
        ], []),
        _pages([
          [_at('same', 9), _at('r7', 7)],
        ], []),
      );
      expect((await _readAll(merged)).expand((p) => p), ['same', 'r7', 'p5']);
    });

    test('a failed page is asked for again, nothing skipped', () async {
      var fail = true;
      final asked = <String?>[];
      final merged = MergedTimeline((cursor) async {
        asked.add(cursor);
        if (fail) {
          fail = false;
          throw StateError('offline');
        }
        return (chains: [_at('p9', 9)], next: null);
      }, _pages([], []));

      await expectLater(merged.next(), throwsStateError);
      expect(_ids(await merged.next()), ['p9']);
      expect(asked, [null, null]);
      expect(merged.hasMore, isFalse);
    });
  });
}
