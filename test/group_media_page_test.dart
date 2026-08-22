import 'package:dart_twitter_api/twitter_api.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/client/client.dart';
import 'package:xta/group/group_media_page.dart';
import 'package:xta/profile/media_grid/media_grid_items/media_grid_item.dart';

TweetWithCard _tweetWith(String id, List<String> types) {
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
            'large': {'w': 4, 'h': 3},
          },
          'video_info': {
            'aspect_ratio': [16, 9],
          },
        },
    ],
  });
  return tweet;
}

TweetChain _chain(TweetWithCard tweet) =>
    TweetChain(id: tweet.idStr!, tweets: [tweet], isPinned: false);

void main() {
  final photoChain = _chain(_tweetWith('1', ['photo']));
  final textChain = _chain(_tweetWith('2', const []));

  group('seedGroupMediaPage', () {
    test('reuses loaded tweets and does not ask for a fetch', () {
      final page = seedGroupMediaPage(
        loadedChains: [photoChain],
        previewChains: [textChain],
        feedNextCursor: 'next',
        itemsOf: mediaItemsFromChains,
      );

      expect(page, isNotNull);
      expect(page!.items, hasLength(1));
      expect(page.nextCursor, 'next');
    });

    test('an empty loaded feed is the end, not a reason to Search again', () {
      final page = seedGroupMediaPage(
        loadedChains: const [],
        previewChains: [photoChain],
        feedNextCursor: null,
        itemsOf: mediaItemsFromChains,
      );

      expect(page, isNotNull);
      expect(page!.items, isEmpty);
      expect(page.nextCursor, isNull);
    });

    test('preview media is shown without a first-page Search', () {
      final page = seedGroupMediaPage(
        loadedChains: null,
        previewChains: [photoChain],
        feedNextCursor: null,
        itemsOf: mediaItemsFromChains,
      );

      expect(page, isNotNull);
      expect(page!.items, hasLength(1));
      expect(page.nextCursor, groupMediaPreviewContinueCursor);
    });

    test('nothing in memory leaves the caller to fetch', () {
      expect(
        seedGroupMediaPage(
          loadedChains: null,
          previewChains: null,
          feedNextCursor: null,
          itemsOf: mediaItemsFromChains,
        ),
        isNull,
      );
      expect(
        seedGroupMediaPage(
          loadedChains: null,
          previewChains: [textChain],
          feedNextCursor: null,
          itemsOf: mediaItemsFromChains,
        ),
        isNull,
        reason: 'text-only cache still needs a real page',
      );
    });
  });

  group('groupMediaPage', () {
    test('first open with loaded tweets does not hit Search', () async {
      var fetches = 0;
      final page = await groupMediaPage(
        cursor: null,
        loadedChains: [photoChain],
        previewChains: null,
        feedNextCursor: 'next',
        fetch: (cursor) async {
          fetches++;
          return (chains: const <TweetChain>[], nextCursor: null);
        },
        itemsOf: mediaItemsFromChains,
      );

      expect(fetches, 0);
      expect(page.items, hasLength(1));
      expect(page.nextCursor, 'next');
    });

    test('switching the image tab back still does not refetch', () async {
      var fetches = 0;
      Future<ChainPage> fetch(String? cursor) async {
        fetches++;
        return (chains: const <TweetChain>[], nextCursor: null);
      }

      await groupMediaPage(
        cursor: null,
        loadedChains: [photoChain],
        previewChains: null,
        feedNextCursor: 'next',
        fetch: fetch,
        itemsOf: mediaItemsFromChains,
      );
      final again = await groupMediaPage(
        cursor: null,
        loadedChains: [photoChain],
        previewChains: null,
        feedNextCursor: 'next',
        fetch: fetch,
        itemsOf: mediaItemsFromChains,
      );

      expect(fetches, 0);
      expect(again.items, hasLength(1));
    });

    test(
      'after a preview seed, a loaded list is reused instead of Search',
      () async {
        var fetches = 0;
        final page = await groupMediaPage(
          cursor: groupMediaPreviewContinueCursor,
          loadedChains: [
            photoChain,
            _chain(_tweetWith('3', ['photo'])),
          ],
          previewChains: [photoChain],
          feedNextCursor: 'next',
          fetch: (cursor) async {
            fetches++;
            return (chains: const <TweetChain>[], nextCursor: null);
          },
          itemsOf: mediaItemsFromChains,
        );

        expect(fetches, 0);
        expect(page.items, hasLength(2));
        expect(page.nextCursor, 'next');
      },
    );

    test(
      'a cold open with no cache fetches one page, not a fan-out per sub',
      () async {
        final asked = <String?>[];
        final page = await groupMediaPage(
          cursor: null,
          loadedChains: null,
          previewChains: null,
          feedNextCursor: null,
          fetch: (cursor) async {
            asked.add(cursor);
            return (chains: [photoChain], nextCursor: 'more');
          },
          itemsOf: mediaItemsFromChains,
        );

        expect(asked, [null]);
        expect(page.items, hasLength(1));
        expect(page.nextCursor, 'more');
      },
    );

    test(
      'loaded text-only tweets page from the feed cursor, not page 0',
      () async {
        final asked = <String?>[];
        await groupMediaPage(
          cursor: null,
          loadedChains: [textChain],
          previewChains: null,
          feedNextCursor: 'page-2',
          fetch: (cursor) async {
            asked.add(cursor);
            return (chains: [photoChain], nextCursor: 'page-3');
          },
          itemsOf: mediaItemsFromChains,
        );

        expect(asked, ['page-2']);
      },
    );
  });

  group('SharedAsyncLoad', () {
    test('concurrent first-page loads share one fetch', () async {
      var calls = 0;
      final shared = SharedAsyncLoad<int>();
      final started = <int>[];

      Future<int> fetch() async {
        calls++;
        started.add(calls);
        await Future<void>.delayed(Duration.zero);
        return calls;
      }

      final results = await Future.wait([
        shared.load(fetch),
        shared.load(fetch),
        shared.load(fetch),
      ]);

      expect(calls, 1);
      expect(results, [1, 1, 1]);
    });

    test('a later load after the first finishes is a new fetch', () async {
      var calls = 0;
      final shared = SharedAsyncLoad<int>();

      expect(await shared.load(() async => ++calls), 1);
      expect(await shared.load(() async => ++calls), 2);
      expect(calls, 2);
    });
  });
}
