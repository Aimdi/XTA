import 'package:flutter_test/flutter_test.dart';
import 'package:xta/client/client.dart';
import 'package:xta/group/feed_catch_up.dart';
import 'package:xta/tweet/catch_up_split.dart';

TweetChain chain(String id) => TweetChain(id: id, tweets: [TweetWithCard()], isPinned: false);

void main() {
  bool isSeen(TweetChain c) => c.id.startsWith('seen');

  group('splitAtFirstSeen', () {
    test('keeps a page of nothing but new chains and pages on', () {
      final page = [chain('a'), chain('b')];
      final split = splitAtFirstSeen(page, isSeen);

      expect(split.reachedBoundary, isFalse);
      expect(split.keep, page);
      expect(split.held, isEmpty);
    });

    test('cuts at the first already-read chain and hands the rest back', () {
      final page = [chain('a'), chain('b'), chain('seen1'), chain('c')];
      final split = splitAtFirstSeen(page, isSeen);

      expect(split.reachedBoundary, isTrue);
      expect(split.keep.map((e) => e.id), ['a', 'b']);
      // Nothing is dropped: "show older posts" puts these straight back.
      expect(split.held.map((e) => e.id), ['seen1', 'c']);
    });

    test('a page whose very first chain is read keeps nothing', () {
      final split = splitAtFirstSeen([chain('seen1'), chain('a')], isSeen);

      expect(split.reachedBoundary, isTrue);
      expect(split.keep, isEmpty);
      expect(split.held.map((e) => e.id), ['seen1', 'a']);
    });

    test('an empty page never claims a boundary', () {
      final split = splitAtFirstSeen(const <TweetChain>[], isSeen);

      expect(split.reachedBoundary, isFalse);
      expect(split.keep, isEmpty);
      expect(split.held, isEmpty);
    });
  });

  group('catchUpMessageFor', () {
    test('an unfinished gap-fill outranks every other claim', () {
      final incomplete = catchUpMessageFor(mayBeIncomplete: true, nothingNew: false);
      final both = catchUpMessageFor(mayBeIncomplete: true, nothingNew: true);

      expect(incomplete, CatchUpMessage.mayBeIncomplete);
      expect(both, CatchUpMessage.mayBeIncomplete);
    });

    test('separates "nothing arrived" from "you read it all"', () {
      expect(catchUpMessageFor(mayBeIncomplete: false, nothingNew: true), CatchUpMessage.nothingNew);
      expect(catchUpMessageFor(mayBeIncomplete: false, nothingNew: false), CatchUpMessage.caughtUp);
    });
  });

  test('feedCatchUpModeKey is per feed', () {
    expect(feedCatchUpModeKey('-1'), isNot(feedCatchUpModeKey('7')));
    expect(feedCatchUpModeKey('7'), 'feed.catch_up_mode.7');
  });
}
