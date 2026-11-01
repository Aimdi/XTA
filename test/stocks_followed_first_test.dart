import 'dart:async';

import 'package:dart_twitter_api/twitter_api.dart';
import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/client/client.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/group/feed_search_query.dart';
import 'package:xta/plugins/stocks/stocks_followed_posts.dart';
import 'package:xta/plugins/stocks/stocks_followed_section.dart';
import 'package:xta/plugins/stocks/stocks_followed_source.dart';
import 'package:xta/tweet/paginated_tweet_list.dart';

final _now = DateTime.utc(2026, 11, 1, 12);
const _weth = '0xc02aaa39b223fe8d0a0e5c4f27ead9083c756cc2';

UserSubscription _follow(String id, {String? screenName}) => UserSubscription(
  id: id,
  screenName: screenName ?? 'user$id',
  name: 'User $id',
  profileImageUrlHttps: null,
  verified: false,
  createdAt: DateTime.utc(2025),
  inFeed: true,
);

/// A post by [author] [hoursAgo] before [_now] carrying [cashtags].
TweetWithCard _post(
  String id, {
  required String author,
  List<String> cashtags = const ['NVDA'],
  String? text,
  int hoursAgo = 1,
  bool repost = false,
}) {
  final tweet = TweetWithCard()
    ..idStr = id
    ..fullText = text ?? cashtags.map((tag) => '\$$tag').join(' ')
    ..createdAt = _now.subtract(Duration(hours: hoursAgo))
    ..user = (User()
      ..idStr = author
      ..screenName = 'user$author')
    ..entities = Entities.fromJson({
      'symbols': [
        for (final tag in cashtags)
          {
            'text': tag,
            'indices': [0, tag.length + 1],
          },
      ],
    });
  if (repost) tweet.retweetedStatusWithCard = (TweetWithCard()..idStr = 'original-$id');
  return tweet;
}

TweetChain _chain(String id, List<TweetWithCard> tweets) => TweetChain(id: id, tweets: tweets, isPinned: false);

TweetChain _single(String id, {required String author, int hoursAgo = 1, List<String> cashtags = const ['NVDA']}) =>
    _chain(id, [_post(id, author: author, hoursAgo: hoursAgo, cashtags: cashtags)]);

final _nvda = TickerPostTerms.of(r'$NVDA');

void main() {
  group('what names a ticker', () {
    test("reads the search's cashtags and quoted addresses", () {
      final terms = TickerPostTerms.of('("$_weth" OR \$weth)');
      expect(terms.cashtags, {'WETH'});
      expect(terms.phrases, [_weth]);
      expect(terms.likePatterns, [r'%$WETH%', '%$_weth%']);
      expect(TickerPostTerms.of('').isEmpty, isTrue);
    });

    test('a cashtag by its entity, not its letters', () {
      expect(_nvda.names(_post('1', author: 'a')), isTrue);
      expect(_nvda.names(_post('2', author: 'a', cashtags: ['NVDAX'], text: r'$NVDAX')), isFalse);
      expect(_nvda.names(_post('3', author: 'a', cashtags: const [], text: 'NVDA is up')), isFalse);
    });

    test('an EVM address in either case, any other exactly', () {
      final evm = TickerPostTerms.of('"$_weth"');
      expect(evm.names(_post('1', author: 'a', cashtags: const [], text: 'buy ${_weth.toUpperCase()}')), isTrue);
      const solana = 'DezXAZ8z7PnrnRJjz3wXBoRgixCa6xjnB7YaB1pPB263';
      final exact = TickerPostTerms.of('"$solana"');
      expect(exact.names(_post('2', author: 'a', cashtags: const [], text: 'see $solana')), isTrue);
      expect(exact.names(_post('3', author: 'a', cashtags: const [], text: 'see ${solana.toLowerCase()}')), isFalse);
    });

    test('a followed account must have written it: a repost is left out', () {
      final followed = {'a'};
      expect(isFollowedPost(_post('1', author: 'a'), followed, _nvda), isTrue);
      expect(isFollowedPost(_post('2', author: 'b'), followed, _nvda), isFalse);
      expect(isFollowedPost(_post('3', author: 'a', repost: true), followed, _nvda), isFalse);
    });
  });

  group('the top part', () {
    test('newest first, each post once, only followed posts naming the ticker', () {
      final top = followedSection(
        [
          _single('old', author: 'a', hoursAgo: 5),
          _single('stranger', author: 'z'),
          _single('new', author: 'b', hoursAgo: 1),
          _single('off-topic', author: 'a', cashtags: ['AAPL']),
          _single('new', author: 'b', hoursAgo: 1),
          // The same post again, in another chain: a reply under it.
          _chain('thread', [_post('old', author: 'a', hoursAgo: 5), _post('reply', author: 'z')]),
        ],
        {'a', 'b'},
        _nvda,
        now: _now,
      );
      expect([for (final chain in top) chain.id], ['new', 'old']);
    });

    test('at most ten, from the last seven days', () {
      final top = followedSection(
        [
          for (var i = 0; i < 12; i++) _single('p$i', author: 'a', hoursAgo: i + 1),
          _single('stale', author: 'a', hoursAgo: 24 * 8),
        ],
        {'a'},
        _nvda,
        now: _now,
      );
      expect(top, hasLength(kFollowedPostsShown));
      expect(top.map((chain) => chain.id), isNot(contains('stale')));
      expect(top.first.id, 'p0');
    });

    test('a later chain repeating a post of the top part is left out', () {
      final top = [_single('mine', author: 'a')];
      final page = [
        _single('mine', author: 'a'),
        _chain('context', [_post('parent', author: 'z'), _post('mine', author: 'a')]),
        _single('theirs', author: 'z'),
      ];
      expect([for (final chain in withoutSection(page, top)) chain.id], ['theirs']);
    });
  });

  group('the searches', () {
    test('narrow the ticker search to followed accounts, packed within 512 characters', () {
      final follows = [for (var i = 0; i < 80; i++) _follow('$i', screenName: 'account_number_$i')];
      final searches = followedTickerSearches(r'$NVDA', follows);
      expect(searches, hasLength(kFollowedSearchesPerTicker));
      expect(searches.every((query) => query.length <= feedSearchQueryLimit), isTrue);
      expect(searches.first, startsWith(r'$NVDA (from:account_number_0 OR from:account_number_1 OR '));
      expect(searches.first, endsWith(') -filter:retweets'));
    });

    test('accounts seen posting the ticker go first', () {
      final follows = [_follow('1'), _follow('2'), _follow('3')];
      expect(followedTickerSearches(r'$NVDA', follows, knownPosters: {'3'}), [
        r'$NVDA (from:user3 OR from:user1 OR from:user2) -filter:retweets',
      ]);
      expect(followedTickerSearches('', follows), isEmpty);
    });
  });

  group('the source', () {
    test('keeps a ticker\'s searches for five minutes, and a failed one adds nothing', () async {
      var now = _now;
      final asked = <String>[];
      final source = FollowedTickerSource(
        now: () => now,
        cached: (_) async => const [],
        search: (query) async {
          asked.add(query);
          if (asked.length == 2) throw Exception('429');
          return [_single('found-${asked.length}', author: '1')];
        },
      );
      final follows = [_follow('1')];
      expect((await source.fromX(r'$NVDA', follows)).single.id, 'found-1');
      expect((await source.fromX(r'$NVDA', follows)).single.id, 'found-1');
      expect(asked, hasLength(1));

      now = now.add(FollowedTickerSource.keptFor);
      expect(await source.fromX(r'$NVDA', follows), isEmpty, reason: 'the search failed');
      expect(asked, hasLength(2));
    });

    test('the device answers nothing when its cache cannot be read', () async {
      final source = FollowedTickerSource(cached: (_) async => throw Exception('locked'));
      expect(await source.onDevice(_nvda), isEmpty);
    });
  });

  group('the pages', () {
    late FollowedSectionStore section;

    setUp(() => section = FollowedSectionStore());
    tearDown(() => section.destroy());

    Future<TweetPageResult> general(String? cursor) async => cursor == null
        ? (
            chains: [
              _single('mine', author: '1'),
              _single('theirs', author: 'z'),
            ],
            nextCursor: 'next',
          )
        : (
            chains: [
              _single('mine', author: '1'),
              _single('later', author: 'z'),
            ],
            nextCursor: null,
          );

    FollowedTickerSource source({List<TweetChain> cached = const [], List<TweetChain> searched = const []}) =>
        FollowedTickerSource(now: () => _now, cached: (_) async => cached, search: (_) async => searched);

    test('the first page puts the followed posts on top, a later page leaves them out', () async {
      final first = await followedFirstPage(
        null,
        query: r'$NVDA',
        follows: [_follow('1')],
        section: section,
        page: general,
        source: source(
          cached: [_single('device', author: '1', hoursAgo: 2)],
          searched: [_single('mine', author: '1')],
        ),
      );
      expect([for (final chain in first.chains) chain.id], ['mine', 'device', 'theirs']);
      expect(section.state.partStartingAt('mine'), FollowedPart.followed);
      expect(section.state.partStartingAt('theirs'), FollowedPart.others);
      expect(section.state.partStartingAt('device'), isNull);

      final next = await followedFirstPage(
        'next',
        query: r'$NVDA',
        follows: [_follow('1')],
        section: section,
        page: general,
      );
      expect([for (final chain in next.chains) chain.id], ['later']);
    });

    test('without followed posts the page is as before, with no labels', () async {
      final first = await followedFirstPage(
        null,
        query: r'$NVDA',
        follows: const [],
        section: section,
        page: general,
        source: source(searched: [_single('mine', author: '1')]),
      );
      expect([for (final chain in first.chains) chain.id], ['mine', 'theirs']);
      expect(section.state.partStartingAt('mine'), isNull);
    });

    testWidgets('the first page does not wait past its time for X', (tester) async {
      final never = Completer<List<TweetChain>>();
      final slow = FollowedTickerSource(
        now: () => _now,
        cached: (_) async => [_single('device', author: '1')],
        search: (_) => never.future,
      );
      TweetPageResult? result;
      unawaited(
        followedFirstPage(
          null,
          query: r'$NVDA',
          follows: [_follow('1')],
          section: section,
          page: general,
          source: slow,
        ).then((page) => result = page),
      );
      await tester.pump(kFollowedSearchWait - const Duration(milliseconds: 100));
      expect(result, isNull);
      await tester.pump(const Duration(milliseconds: 200));
      expect([for (final chain in result!.chains) chain.id], ['device', 'mine', 'theirs']);
    });
  });

  group('the labels', () {
    testWidgets('sit above where each part starts, and read as headings', (tester) async {
      final section = FollowedSectionStore();
      addTearDown(section.destroy);
      final chains = section.first(
        [_single('a', author: '1'), _single('b', author: '1')],
        [_single('c', author: 'z'), _single('d', author: 'z')],
      );
      await tester.pumpWidget(
        MaterialApp(
          localizationsDelegates: const [L10n.delegate, ...GlobalMaterialLocalizations.delegates],
          supportedLocales: L10n.delegate.supportedLocales,
          home: Scaffold(
            body: ListView(
              children: [for (final chain in chains) followedSectionChain(section, chain, Text('post ${chain.id}'))],
            ),
          ),
        ),
      );
      final l10n = L10n.current;
      final order = [
        l10n.plugin_stocks_posts_followed,
        'post a',
        'post b',
        l10n.plugin_stocks_posts_others,
        'post c',
        'post d',
      ];
      final tops = [for (final text in order) tester.getTopLeft(find.text(text)).dy];
      expect(tops, orderedEquals([...tops]..sort()));
      expect(find.text(l10n.plugin_stocks_posts_followed), findsOneWidget);
      expect(
        tester.getSemantics(find.text(l10n.plugin_stocks_posts_followed)),
        isSemantics(isHeader: true, label: l10n.plugin_stocks_posts_followed),
      );

      section.first(const [], chains);
      await tester.pump();
      expect(find.text(l10n.plugin_stocks_posts_followed), findsNothing);
      expect(find.text(l10n.plugin_stocks_posts_others), findsNothing);
    });
  });
}
