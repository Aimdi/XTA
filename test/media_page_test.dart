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

    test('Livestreams reads posts, not UserMedia', () {
      expect(mediaTimelineTypeFor(MediaFilter.broadcasts), 'profile');
      expect(mediaTimelineTypeFor(MediaFilter.all), 'media');
      expect(mediaTimelineTypeFor(MediaFilter.photos), 'media');
      expect(mediaTimelineTypeFor(MediaFilter.videos), 'media');
    });

    test('Livestreams looks further because broadcasts are sparse', () {
      expect(mediaLookaheadFor(MediaFilter.broadcasts), 12);
      expect(mediaLookaheadFor(MediaFilter.all), 4);
    });

    test('a photo post that is a broadcast is one live tile, not a still', () {
      final tweet = _tweetWith('p1', ['photo'], broadcast: true);
      final items = mediaItemsFromChains([_chain(tweet)]);
      expect(items, hasLength(1));
      expect(items.single, isA<BroadcastGridItem>());
      expect((items.single as BroadcastGridItem).broadcastId, '1abc');
      expect(MediaFilter.photos.accepts(items.single), isFalse);
      expect(MediaFilter.broadcasts.accepts(items.single), isTrue);
    });

    test('a video with a broadcasts link is a broadcast, not a video', () {
      final broadcast = mediaItemsFromChains([
        _chain(_tweetWith('b', ['video'], broadcast: true)),
      ]);
      expect(broadcast, hasLength(1));
      expect(broadcast.single, isA<BroadcastGridItem>());
      expect((broadcast.single as BroadcastGridItem).broadcastId, '1abc');
      expect(
        (broadcast.single as BroadcastGridItem).broadcastUrl,
        'https://x.com/i/broadcasts/1abc',
      );
      expect(MediaFilter.videos.accepts(broadcast.single), isFalse);
      expect(MediaFilter.broadcasts.accepts(broadcast.single), isTrue);
      expect(MediaFilter.all.accepts(broadcast.single), isTrue);
    });

    test('UserMedia video whose expanded_url is the broadcast is not a clip', () {
      final tweet = TweetWithCard();
      tweet.idStr = 'vod1';
      tweet.user = User.fromJson({'id_str': 'u1', 'screen_name': 'someone'});
      tweet.extendedEntities = Entities.fromJson({
        'media': [
          {
            'type': 'video',
            'media_url_https': 'https://pbs.example/vod.jpg',
            'expanded_url': 'https://x.com/i/broadcasts/1usermedia',
            'sizes': {
              'large': {'w': 16, 'h': 9},
            },
            'video_info': {
              'aspect_ratio': [16, 9],
            },
          },
        ],
      });
      final items = mediaItemsFromChains([
        TweetChain(id: 'vod1', tweets: [tweet], isPinned: false),
      ]);
      expect(items, hasLength(1));
      expect(items.single, isA<BroadcastGridItem>());
      expect((items.single as BroadcastGridItem).broadcastId, '1usermedia');
      expect(MediaFilter.videos.accepts(items.single), isFalse);
    });

    test('a card-only broadcast still gets a tile', () {
      final tweet = TweetWithCard();
      tweet.idStr = 'c1';
      tweet.user = User.fromJson({'id_str': 'u1', 'screen_name': 'someone'});
      tweet.card = {
        'name': '745291183405076480:broadcast',
        'binding_values': {
          'broadcast_id': {'string_value': '1solo'},
          'broadcast_thumbnail': {
            'image_value': {'url': 'https://pbs.example/live.jpg'},
          },
        },
      };
      final items = mediaItemsFromChains([
        TweetChain(id: 'c1', tweets: [tweet], isPinned: false),
      ]);
      expect(items, hasLength(1));
      expect(items.single, isA<BroadcastGridItem>());
      expect((items.single as BroadcastGridItem).broadcastId, '1solo');
    });

    test('a spaces link on a video is a live tile, not a clip', () {
      final tweet = TweetWithCard();
      tweet.idStr = 's1';
      tweet.user = User.fromJson({'id_str': 'u1', 'screen_name': 'someone'});
      tweet.entities = Entities.fromJson({
        'urls': [
          {
            'url': 'https://t.co/s',
            'expanded_url': 'https://x.com/i/spaces/1room',
            'display_url': 'x.com/i/spaces/1room',
            'indices': [0, 23],
          }
        ]
      });
      tweet.extendedEntities = Entities.fromJson({
        'media': [
          {
            'type': 'video',
            'media_url_https': 'https://pbs.example/s.jpg',
            'sizes': {
              'large': {'w': 16, 'h': 9}
            },
            'video_info': {
              'aspect_ratio': [16, 9]
            },
          }
        ]
      });
      final items = mediaItemsFromChains([
        TweetChain(id: 's1', tweets: [tweet], isPinned: false),
      ]);
      expect(items, hasLength(1));
      expect(items.single, isA<BroadcastGridItem>());
      expect((items.single as BroadcastGridItem).spaceId, '1room');
      expect((items.single as BroadcastGridItem).watchUrl, 'https://x.com/i/spaces/1room');
      expect(MediaFilter.videos.accepts(items.single), isFalse);
      expect(MediaFilter.broadcasts.accepts(items.single), isTrue);
    });

    test('a card-only Space still gets a tile', () {
      final tweet = TweetWithCard();
      tweet.idStr = 's2';
      tweet.user = User.fromJson({'id_str': 'u1', 'screen_name': 'someone'});
      tweet.card = {
        'name': '326813241626781184:audiospace',
        'binding_values': {
          'id': {'string_value': '1soloSpace'},
          'thumbnail_image': {
            'image_value': {'url': 'https://pbs.example/space.jpg'},
          },
        },
      };
      final items = mediaItemsFromChains([
        TweetChain(id: 's2', tweets: [tweet], isPinned: false),
      ]);
      expect(items, hasLength(1));
      expect(items.single, isA<BroadcastGridItem>());
      expect((items.single as BroadcastGridItem).spaceId, '1soloSpace');
      expect((items.single as BroadcastGridItem).watchUrl, 'https://x.com/i/spaces/1soloSpace');
      expect((items.single as BroadcastGridItem).canPlayInApp, isFalse);
    });

    test('a VOD with variants plays in-app; a card-only Space does not', () {
      final vod = _tweetWith('v1', ['video'], broadcast: true);
      vod.extendedEntities = Entities.fromJson({
        'media': [
          {
            'type': 'video',
            'media_url_https': 'https://pbs.example/v.jpg',
            'expanded_url': 'https://x.com/i/broadcasts/1abc',
            'video_info': {
              'aspect_ratio': [16, 9],
              'variants': [
                {
                  'url': 'https://video.twimg.com/ext_tw_video/1.mp4',
                  'content_type': 'video/mp4',
                  'bitrate': 832000,
                }
              ],
            },
          }
        ]
      });
      final vodItem = mediaItemsFromChains([_chain(vod)]).single as BroadcastGridItem;
      expect(vodItem.canPlayInApp, isTrue);

      final cardOnly = mediaItemsFromChains([
        _chain(_tweetWith('c1', const [], broadcast: true)),
      ]).single as BroadcastGridItem;
      expect(cardOnly.canPlayInApp, isFalse);
      expect(cardOnly.watchUrl, 'https://x.com/i/broadcasts/1abc');
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
