import 'package:dart_twitter_api/twitter_api.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/client/client.dart';
import 'package:xta/profile/media_grid/media_grid_items/media_grid_item.dart';

/// Built from API-shaped JSON rather than by hand: these are the payloads the
/// grid is handed in the app, so the mapping is exercised as it really runs.
TweetWithCard _tweetWith(String id, List<String> types, {bool broadcast = false}) {
  final tweet = TweetWithCard();
  tweet.idStr = id;
  tweet.user = User.fromJson({'id_str': 'u$id', 'screen_name': 'someone'});
  tweet.extendedEntities = Entities.fromJson({
    'media': [
      for (final (i, type) in types.indexed)
        {
          'type': type,
          'media_url_https': 'https://pbs.example/$id/$i.jpg',
          'sizes': {
            'large': {'w': 4, 'h': 3}
          },
          'video_info': {
            'aspect_ratio': [16, 9]
          },
        }
    ]
  });
  if (broadcast) {
    tweet.entities = Entities.fromJson({
      'urls': [
        {
          'url': 'https://t.co/b',
          'expanded_url': 'https://x.com/i/broadcasts/1abc',
          'display_url': 'x.com/i/broadcasts/1…',
          'indices': [0, 23],
        }
      ]
    });
  }
  return tweet;
}

TweetChain _chain(TweetWithCard tweet) => TweetChain(id: tweet.idStr!, tweets: [tweet], isPinned: false);

TweetStatus _status(List<TweetChain> chains, String? cursorBottom) =>
    TweetStatus(chains: chains, cursorBottom: cursorBottom, cursorTop: null);

void main() {
  group('mediaPageFromStatus', () {
    test('keeps the media of the last page instead of discarding it', () {
      // X repeats the bottom cursor once a timeline runs out. The page it
      // arrives with still holds real media — throwing it away lost the final
      // screenful of every profile's media tab.
      final status = _status([_chain(_tweetWith('1', ['photo']))], 'SAME');

      final page = mediaPageFromStatus(status, 'SAME');

      expect(page.items, hasLength(1));
      expect(page.nextCursor, isNull, reason: 'the repeated cursor still marks the end');
    });

    test('carries the cursor on while the timeline keeps moving', () {
      final status = _status([_chain(_tweetWith('1', ['photo']))], 'NEXT');

      final page = mediaPageFromStatus(status, 'PREVIOUS');

      expect(page.items, hasLength(1));
      expect(page.nextCursor, 'NEXT');
    });

    test('a missing cursor is the end', () {
      expect(mediaPageFromStatus(_status(const [], null), null).nextCursor, isNull);
    });
  });

  group('mediaPageWithLookahead', () {
    test('reads past pages with no media rather than calling it the end', () async {
      // Three pages of text-only posts, then one with a photo. Returning the
      // first empty page would have told the paging controller the profile had
      // no more media at all.
      final asked = <String?>[];
      final pages = <String?, ChainPage>{
        null: (chains: [_chain(_tweetWith('1', const []))], nextCursor: 'a'),
        'a': (chains: [_chain(_tweetWith('2', const []))], nextCursor: 'b'),
        'b': (chains: [_chain(_tweetWith('3', ['photo']))], nextCursor: 'c'),
      };

      final page = await mediaPageWithLookahead(null, (cursor) async {
        asked.add(cursor);
        return pages[cursor]!;
      }, mediaItemsFromChains);

      expect(asked, [null, 'a', 'b']);
      expect(page.items, hasLength(1));
      expect(page.nextCursor, 'c');
    });

    test('gives up after the look-ahead so a dry feed still ends', () async {
      var calls = 0;
      final page = await mediaPageWithLookahead(null, (cursor) async {
        calls++;
        return (chains: [_chain(_tweetWith('$calls', const []))], nextCursor: 'more');
      }, mediaItemsFromChains, maxLookahead: 2);

      expect(calls, 3, reason: 'the first page plus two more');
      expect(page.items, isEmpty);
    });

    test('stops at the end of the timeline rather than looking past it', () async {
      var calls = 0;
      await mediaPageWithLookahead(null, (cursor) async {
        calls++;
        return (chains: [_chain(_tweetWith('1', const []))], nextCursor: null);
      }, mediaItemsFromChains);

      expect(calls, 1);
    });
  });

  group('MediaFilter', () {
    final items = mediaItemsFromChains([
      _chain(_tweetWith('1', ['photo', 'video', 'animated_gif'])),
    ]);

    test('all keeps everything', () {
      expect(items.where(MediaFilter.all.accepts), hasLength(3));
    });

    test('photos keeps only stills', () {
      final kept = items.where(MediaFilter.photos.accepts).toList();
      expect(kept, hasLength(1));
      expect(kept.single, isA<PhotoGridItem>());
    });

    test('videos keeps GIFs too, since X serves them as video', () {
      expect(items.where(MediaFilter.videos.accepts), hasLength(2));
    });

    test('a video with a broadcasts link is a broadcast, not a video', () {
      final broadcast = mediaItemsFromChains([
        _chain(_tweetWith('b', ['video'], broadcast: true)),
      ]);
      expect(broadcast, hasLength(1));
      expect(broadcast.single, isA<BroadcastGridItem>());
      expect(MediaFilter.videos.accepts(broadcast.single), isFalse);
      expect(MediaFilter.broadcasts.accepts(broadcast.single), isTrue);
      expect(MediaFilter.all.accepts(broadcast.single), isTrue);
    });
  });

  test('a first page that is only a cursor is followed, not shown as empty', () async {
    // Every leading entry tombstoned away: no chains, but the feed goes on.
    final pages = <String?, ChainPage>{
      null: (chains: <TweetChain>[], nextCursor: 'c1'),
      'c1': (chains: [_chain(_tweetWith('1', ['photo']))], nextCursor: 'c2'),
    };

    final page = await mediaPageWithLookahead(null, (cursor) async => pages[cursor]!, mediaItemsFromChains);

    expect(page.items, hasLength(1));
    expect(page.nextCursor, 'c2');
  });

  test('a feed that is cursors all the way down still ends at the bound', () async {
    var calls = 0;
    final page = await mediaPageWithLookahead(null, (cursor) async {
      calls++;
      return (chains: <TweetChain>[], nextCursor: 'c$calls');
    }, mediaItemsFromChains);

    expect(page.items, isEmpty);
    expect(calls, 5, reason: 'the first page plus maxLookahead more');
  });
}
