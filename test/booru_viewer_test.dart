import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:visibility_detector/visibility_detector.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/booru/booru_client.dart';
import 'package:xta/plugins/booru/booru_endpoints.dart';
import 'package:xta/plugins/booru/booru_display.dart';
import 'package:xta/plugins/booru/booru_grid.dart';
import 'package:xta/plugins/booru/booru_load_store.dart';
import 'package:xta/plugins/booru/booru_models.dart';
import 'package:xta/plugins/booru/booru_pager_store.dart';
import 'package:xta/plugins/booru/booru_popular.dart';
import 'package:xta/plugins/booru/booru_popular_tab.dart';
import 'package:xta/plugins/booru/booru_engines.dart';
import 'package:xta/plugins/booru/booru_post_actions.dart';
import 'package:xta/plugins/booru/booru_post_facts.dart';
import 'package:xta/plugins/booru/booru_post_media.dart';
import 'package:xta/plugins/booru/booru_post_screen.dart';
import 'package:xta/plugins/booru/booru_related.dart';
import 'package:xta/plugins/booru/booru_screen.dart';
import 'package:xta/plugins/booru/booru_settings.dart';
import 'package:xta/plugins/booru/booru_store.dart';
import 'package:xta/plugins/booru/booru_tag_list.dart';
import 'package:xta/plugins/booru/booru_tag_style.dart';
import 'package:xta/plugins/plugin_feed_skeleton.dart';
import 'package:xta/plugins/plugin_tag_chip.dart';
import 'package:xta/tweet/_video.dart';

BooruPost _post(
  String id, {
  String host = 'https://danbooru.donmai.us',
  String engine = 'danbooru',
  List<String> tags = const ['kantoku', 'solo', 'highres'],
  Map<String, BooruTagCategory> categories = const {},
  BooruRating rating = BooruRating.general,
  String? parentId,
}) => BooruPost(
  id: id,
  host: host,
  engine: engine,
  tags: tags,
  rating: rating,
  score: 12,
  width: 800,
  height: 600,
  previewUrl: null,
  sampleUrl: null,
  fileUrl: null,
  fileExt: 'png',
  source: null,
  createdAt: DateTime.utc(2024, 1, 2, 3, 4),
  tagCategories: categories,
  fileSize: 2048,
  favCount: 5,
  upScore: 14,
  downScore: 2,
  parentId: parentId,
  uploader: 'someone',
);

class _Client extends BooruClient {
  _Client(super.prefs);

  /// Latest's next page, answered when the test completes it.
  final latestPage = Completer<BooruPostPage>();
  var latestCalls = 0;
  final searches = <String>[];
  final searchSites = <String?>[];
  final popularQueries = <BooruPopularQuery>[];
  var commentCalls = 0;
  var wikiCalls = 0;
  final wikiSites = <String?>[];

  @override
  Future<BooruPostPage> search(
    String query, {
    int page = 1,
    int limit = BooruClient.defaultPageSize,
    BooruSite? site,
  }) async {
    searches.add(query);
    searchSites.add(site?.host);
    return BooruPostPage(posts: [_post('1'), _post('10'), _post('11')], page: page, hasMore: false);
  }

  @override
  Future<BooruPostPage> latest({int page = 1, int limit = BooruClient.defaultPageSize}) {
    latestCalls++;
    return latestPage.future;
  }

  @override
  Future<List<BooruComment>> comments(BooruPost post) async {
    commentCalls++;
    return [
      BooruComment(id: '1', author: 'reader', body: '[b]Nice[/b] [[kantoku]]', createdAt: DateTime.utc(2024), score: 1),
    ];
  }

  static const counts = {'kantoku': 50, 'solo': 4330000, 'highres': 9000000, 'blue_eyes': 573000};

  @override
  Future<Map<String, BooruTagInfo>> tagInfo(BooruPost post) async => {
    for (final tag in post.tags) tag: BooruTagInfo(category: post.tagCategories[tag], postCount: counts[tag]),
  };

  @override
  Future<String?> wiki(String tag, {BooruSite? site}) async {
    wikiCalls++;
    wikiSites.add(site?.host);
    return tag == 'kantoku' ? 'h4. Kantoku\nAn illustrator.' : null;
  }
}

/// Waits out the 50 ms `execute` holds a load back before starting it.
Future<void> _pastDebounce() => Future<void>.delayed(const Duration(milliseconds: 80));

/// Preferences whose writes wait until [open] completes, so a test can leave
/// a screen while one is in flight.
class _GatedPrefs extends PrefServiceCache {
  _GatedPrefs(Map<String, Object> cache) : super(cache: cache);

  final open = Completer<void>();

  @override
  Future<bool> put<T>(String key, T val) async {
    await open.future;
    return super.put(key, val);
  }
}

/// Followed tags in memory, so the Booru home needs no database.
class _Tags extends BooruTagsStore {
  @override
  Future<void> load() async {}
}

Future<_Client> _pump(
  WidgetTester tester,
  Widget child, {
  Map<String, Object> prefs = const {},
  PrefServiceCache? service,
  BooruTagsStore? tags,
  bool settle = true,
}) async {
  // Tall enough for a post's picture, facts and tags without scrolling.
  tester.view.physicalSize = const Size(800, 2400);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  service ??= PrefServiceCache(cache: {optionPluginBooruEngine: 'danbooru', ...prefs});
  final client = _Client(service);
  await tester.pumpWidget(
    PrefService(
      service: service,
      child: MultiProvider(
        providers: [
          Provider<BooruClient>.value(value: client),
          Provider<BooruTagsStore>.value(value: tags ?? BooruTagsStore()),
          Provider<BooruMuteStore>.value(value: BooruMuteStore(service)),
        ],
        child: MaterialApp(
          localizationsDelegates: const [
            L10n.delegate,
            GlobalMaterialLocalizations.delegate,
            GlobalWidgetsLocalizations.delegate,
          ],
          home: child,
        ),
      ),
    ),
  );
  if (settle) {
    await tester.pump(const Duration(milliseconds: 300));
    await tester.pumpAndSettle();
  }
  return client;
}

void main() {
  group('pager store', () {
    test('grows with its feed and asks for more near the end', () async {
      var loads = 0;
      final first = [for (var i = 0; i < 6; i++) _post('$i')];
      final feed = BooruFeedStore(_Client(PrefServiceCache()), ({required page}) async {
        loads++;
        return BooruPostPage(posts: [_post('${page * 100}')], page: page, hasMore: true);
      })..update(first);
      final pager = BooruPagerStore(posts: first, index: 0, feed: feed);

      pager.show(1);
      expect(loads, 0);
      pager.show(3);
      await Future<void>.delayed(Duration.zero);
      expect(loads, 1);
      expect(pager.state.posts, hasLength(7));
      expect(pager.state.index, 3);

      feed.update([_post('x')]);
      expect(pager.state.posts, hasLength(7));
      await pager.destroy();
    });

    test('after a site change a feed starts over instead of growing', () async {
      final prefs = PrefServiceCache(
        cache: {optionPluginBooruEngine: 'danbooru', optionPluginBooruHost: 'https://danbooru.donmai.us'},
      );
      final client = _Client(prefs);
      final asked = <String>[];
      final feed = BooruFeedStore(client, ({required page}) async {
        asked.add('${client.host} $page');
        return BooruPostPage(posts: [_post('${asked.length}')], page: page, hasMore: true);
      });
      await feed.refresh();
      expect(feed.isStale, isFalse);

      await prefs.set(optionPluginBooruEngine, 'e621');
      await prefs.set(optionPluginBooruHost, 'https://e621.net');
      expect(feed.isStale, isTrue);
      await feed.loadMore();
      expect(asked, ['https://danbooru.donmai.us 1', 'https://e621.net 1'], reason: 'not page 2 of the old list');
      expect(feed.state.map((p) => p.id), ['2']);
      expect(feed.isStale, isFalse);
      await feed.destroy();
    });

    test('a new rating cap or blacklist entry makes a feed stale', () async {
      final prefs = PrefServiceCache(cache: {optionPluginBooruEngine: 'danbooru', optionPluginBooruMaxRating: 'g'});
      final feed = BooruFeedStore(
        _Client(prefs),
        ({required page}) async => BooruPostPage(posts: [_post('$page')], page: page, hasMore: true),
      );
      await feed.ensureFresh();
      expect(feed.state, isNotEmpty);
      await prefs.set(optionPluginBooruMaxRating, 'e');
      expect(feed.isStale, isTrue);
      await feed.ensureFresh();
      expect(feed.isStale, isFalse);
      await prefs.set(optionPluginBooruMutedTags, '["solo"]');
      expect(feed.isStale, isTrue);
      await feed.destroy();
    });

    test('a failed page is asked for once, until the reader retries', () async {
      var calls = 0;
      final feed = BooruFeedStore(_Client(PrefServiceCache()), ({required page}) async {
        if (page == 1) return BooruPostPage(posts: [_post('1')], page: 1, hasMore: true);
        calls++;
        throw BooruException(BooruErrorKind.rateLimited, '429');
      });
      await feed.refresh();
      for (var notification = 0; notification < 5; notification++) {
        await feed.loadMore();
      }
      expect(calls, 1, reason: 'scrolling near the end does not ask again');
      expect(feed.paging.state, BooruPaging.failed);

      await feed.retryMore();
      expect(calls, 2);
      await feed.destroy();
    });

    test('a page asked for before a refresh never lands after it', () async {
      var date = 'monday';
      final pending = <String, Completer<BooruPostPage>>{};
      final feed = BooruFeedStore(
        _Client(PrefServiceCache()),
        ({required page}) => (pending['$date $page'] = Completer<BooruPostPage>()).future,
      );
      BooruPostPage answer(String id, int page) => BooruPostPage(posts: [_post(id)], page: page, hasMore: true);
      List<String> shown() => [for (final post in feed.state) post.id];

      final opening = feed.refresh();
      await _pastDebounce();
      pending['monday 1']!.complete(answer('m1', 1));
      await opening;

      final more = feed.loadMore();
      date = 'tuesday';
      final change = feed.refresh();
      await _pastDebounce();
      pending['tuesday 1']!.complete(answer('t1', 1));
      await change;
      pending['monday 2']!.complete(answer('m2', 2));
      await more;
      expect(shown(), ['t1'], reason: "Monday's second page is not added to Tuesday's");

      final next = feed.loadMore();
      await Future<void>.delayed(Duration.zero);
      expect(pending.keys, contains('tuesday 2'), reason: 'Tuesday goes on from its own second page');
      pending['tuesday 2']!.complete(answer('t2', 2));
      await next;
      expect(shown(), ['t1', 't2']);
      await feed.destroy();
    });

    test('of two overlapping refreshes, the newer one decides what the feed holds', () async {
      var date = 'monday';
      final pending = <String, Completer<BooruPostPage>>{};
      final feed = BooruFeedStore(
        _Client(PrefServiceCache()),
        ({required page}) => (pending['$date $page'] = Completer<BooruPostPage>()).future,
      );

      final monday = feed.refresh();
      await _pastDebounce();
      date = 'tuesday';
      final tuesday = feed.refresh();
      await _pastDebounce();
      pending['tuesday 1']!.complete(BooruPostPage(posts: [_post('t1')], page: 1, hasMore: true));
      await tuesday;
      pending['monday 1']!.complete(BooruPostPage(posts: [_post('m1')], page: 1, hasMore: false, failedQueries: 2));
      await monday;
      await Future<void>.delayed(Duration.zero);

      expect(feed.state.map((post) => post.id), ['t1']);
      expect(feed.hasMore, isTrue, reason: "Monday's last page does not end Tuesday's feed");
      expect(feed.paging.state, BooruPaging.idle);
      expect(feed.failedQueries.state, 0, reason: "Monday's failures are not Tuesday's");
      await feed.destroy();
    });

    test('no next page is asked for while a refresh is on its way', () async {
      final asked = <int>[];
      final first = Completer<BooruPostPage>();
      final feed = BooruFeedStore(_Client(PrefServiceCache()), ({required page}) {
        asked.add(page);
        return page == 1 && asked.length > 1
            ? first.future
            : Future.value(BooruPostPage(posts: [_post('${asked.length}')], page: page, hasMore: true));
      });
      await feed.refresh();
      final refreshing = feed.refresh();
      await _pastDebounce();
      await feed.loadMore();
      expect(asked, [1, 1], reason: 'the second page of a list about to be replaced is not worth asking for');
      first.complete(BooruPostPage(posts: [_post('fresh')], page: 1, hasMore: true));
      await refreshing;
      await feed.loadMore();
      expect(asked, [1, 1, 2]);
      await feed.destroy();
    });

    test('a closed feed asks for no more pages, and what was on its way is dropped', () async {
      final asked = <int>[];
      final first = Completer<BooruPostPage>();
      final feed = BooruFeedStore(_Client(PrefServiceCache()), ({required page}) {
        asked.add(page);
        return page == 1
            ? first.future
            : Future.value(BooruPostPage(posts: [_post('$page')], page: page, hasMore: true));
      });
      final opening = feed.refresh();
      await _pastDebounce();
      await feed.destroy();
      first.complete(const BooruPostPage(posts: [], page: 1, hasMore: true));
      await expectLater(opening, completes, reason: 'nothing lands on the closed feed');
      expect(asked, [1], reason: 'an emptied first page would otherwise send it on to page 2');
    });

    test('a closed load ignores its late answer', () async {
      final answer = Completer<String>();
      final load = BooruLoadStore<String>(() => answer.future);
      final loading = load.ensure();
      await _pastDebounce();
      await load.destroy();
      answer.complete('late');
      await expectLater(loading, completes);
    });

    test('the feed skips posts it already shows', () async {
      final pages = {
        1: [_post('1'), _post('2')],
        2: [_post('2'), _post('3')],
      };
      final feed = BooruFeedStore(
        _Client(PrefServiceCache()),
        ({required page}) async => BooruPostPage(posts: pages[page] ?? const [], page: page, hasMore: page < 2),
      );
      await feed.refresh();
      await feed.loadMore();
      expect(feed.state.map((p) => p.id), ['1', '2', '3']);
      await feed.destroy();
    });

    test('a host that ignores the page number ends the feed', () async {
      var calls = 0;
      final feed = BooruFeedStore(_Client(PrefServiceCache()), ({required page}) async {
        calls++;
        return BooruPostPage(posts: [_post('1'), _post('2')], page: page, hasMore: true);
      });
      await feed.refresh();
      await feed.loadMore();
      expect(feed.hasMore, isFalse);
      await feed.loadMore();
      expect(calls, 4);
      expect(feed.state, hasLength(2));
      await feed.destroy();
    });

    test('the next and the previous picture are loaded ahead, never a video', () {
      BooruPost post(String id, String ext) => BooruPost(
        id: id,
        host: 'https://danbooru.donmai.us',
        engine: 'danbooru',
        tags: const [],
        rating: BooruRating.general,
        score: 0,
        width: 1,
        height: 1,
        previewUrl: null,
        sampleUrl: null,
        fileUrl: 'https://cdn.example/$id.$ext',
        fileExt: ext,
        source: null,
        createdAt: null,
      );
      final posts = [post('1', 'png'), post('2', 'mp4'), post('3', 'jpg'), post('4', 'png')];

      expect(booruPreloadUrls(posts, 0), isEmpty, reason: 'the next is a video and there is no previous');
      expect(booruPreloadUrls(posts, 1), ['https://cdn.example/3.jpg', 'https://cdn.example/1.png']);
      expect(booruPreloadUrls(posts, 2), ['https://cdn.example/4.png']);
      expect(booruPreloadUrls(posts, 3), ['https://cdn.example/3.jpg']);
    });

    test('a list only counts as extended when it starts the same', () {
      final a = [_post('1'), _post('2')];
      expect(booruPostsExtend(a, [...a, _post('3')]), isTrue);
      expect(booruPostsExtend(a, [_post('9'), _post('2'), _post('3')]), isFalse);
      expect(booruPostsExtend(a, a), isFalse);
    });
  });

  group('tag groups', () {
    test('kinds order the groups; unknown tags count as general; names sort A–Z', () {
      final groups = booruTagGroups(
        ['solo', 'kantoku', 'highres', 'mystery'],
        const {
          'kantoku': BooruTagInfo(category: BooruTagCategory.artist),
          'solo': BooruTagInfo(category: BooruTagCategory.general),
          'highres': BooruTagInfo(category: BooruTagCategory.meta),
        },
      );
      expect(groups.map((g) => g.category), [BooruTagCategory.artist, BooruTagCategory.general, BooruTagCategory.meta]);
      expect(groups[1].tags, ['mystery', 'solo']);
      final unknown = booruTagGroups(['b', 'a'], const {}).single;
      expect(unknown.category, isNull);
      expect(unknown.tags, ['a', 'b']);
      expect(booruTagGroups(const [], const {}), isEmpty);
    });

    test('by use, the most used come first and uncounted tags last', () {
      const info = {'a': BooruTagInfo(postCount: 5), 'b': BooruTagInfo(postCount: 900), 'c': BooruTagInfo()};
      expect(booruTagGroups(['c', 'a', 'b'], info, sort: BooruTagSort.count).single.tags, ['b', 'a', 'c']);
      expect(BooruTagSort.parse('count'), BooruTagSort.count);
      expect(BooruTagSort.parse(null), BooruTagSort.name);
    });

    test('chips read like the host: spaces, short counts, kind colours', () {
      expect(booruTagDisplayName('blue_eyes'), 'blue eyes');
      expect(pluginCompactCount(5450000, 'en'), '5.45M');
      expect(pluginCompactCount(2410, 'en'), '2.41K');
      expect(pluginCompactCount(50, 'en'), '50');
      for (final brightness in Brightness.values) {
        final scheme = ColorScheme.fromSeed(seedColor: Colors.teal, brightness: brightness);
        final general = pluginTagPalette(booruTagKind(BooruTagCategory.general), scheme);
        expect(general.text, isNot(scheme.onSurface));
        expect(general.fill, isNot(general.text));
        expect(pluginTagPalette(null, scheme).text, scheme.onSurface);
        final kinds = booruTagCategoryOrder.map((c) => booruTagColor(c, scheme)).toSet();
        expect(kinds, hasLength(booruTagCategoryOrder.length));
      }
    });

    test('the artist for "More from" skips placeholder artist tags', () {
      const info = {
        'unknown_artist': BooruTagInfo(category: BooruTagCategory.artist),
        'kantoku': BooruTagInfo(category: BooruTagCategory.artist),
      };
      expect(booruArtistTag(['unknown_artist', 'kantoku'], info), 'kantoku');
      expect(booruArtistTag(['unknown_artist'], info), isNull);
    });

    test('family query names the parent', () {
      expect(booruFamilyQuery(_post('5', parentId: '4')), 'parent:4');
      expect(booruFamilyQuery(_post('5')), 'parent:5');
    });
  });

  group('details', () {
    testWidgets('facts, grouped tags and the artist strip', (tester) async {
      final post = _post(
        '1',
        categories: {
          'kantoku': BooruTagCategory.artist,
          'solo': BooruTagCategory.general,
          'highres': BooruTagCategory.meta,
        },
      );
      final client = await _pump(tester, BooruPostScreen(post: post));

      expect(find.text('PNG · 2.0 kB'), findsOneWidget);
      expect(find.text('800 × 600'), findsOneWidget);
      expect(find.text('+14 −2'), findsOneWidget);
      expect(find.text('someone'), findsOneWidget);
      expect(find.text('Artist'), findsOneWidget);
      expect(find.text('Meta'), findsOneWidget);
      expect(find.text('kantoku  50'), findsOneWidget);
      expect(find.text('highres  9M'), findsOneWidget);
      expect(client.searches, contains('kantoku'));
      expect(find.text('More from kantoku'), findsOneWidget);
    });

    testWidgets('tags sort by name or by use, and the choice is kept', (tester) async {
      final post = _post(
        '1',
        tags: const ['solo', 'blue_eyes'],
        categories: {'solo': BooruTagCategory.general, 'blue_eyes': BooruTagCategory.general},
      );
      await _pump(tester, BooruPostScreen(post: post));
      double x(String tag) => tester.getTopLeft(find.byKey(ValueKey('booru-post-tag-$tag'))).dx;

      expect(find.text('blue eyes  573K'), findsOneWidget);
      expect(x('blue_eyes'), lessThan(x('solo')));
      await tester.tap(find.byKey(const ValueKey('booru-tag-sort')));
      await tester.pumpAndSettle();
      expect(x('solo'), lessThan(x('blue_eyes')));
      expect(find.text('Most used'), findsOneWidget);
      final prefs = PrefService.of(tester.element(find.byType(BooruPostScreen)), listen: false);
      expect(prefs.get<String>(optionPluginBooruTagSort), 'count');
    });

    testWidgets('comments load when opened', (tester) async {
      final client = await _pump(tester, BooruPostScreen(post: _post('1')));
      final comments = find.byKey(const ValueKey('booru-comments'));
      await tester.scrollUntilVisible(comments, 300, scrollable: find.byType(Scrollable).at(1));
      expect(client.commentCalls, 0);
      await tester.tap(comments);
      await tester.pump(const Duration(milliseconds: 300));
      await tester.pumpAndSettle();
      expect(client.commentCalls, 1);
      expect(find.text('Nice kantoku'), findsOneWidget);
    });

    testWidgets('a post from another site is looked up on that site', (tester) async {
      final client = await _pump(
        tester,
        BooruPostScreen(
          post: _post('1', host: 'https://e621.net', engine: 'e621', categories: {'kantoku': BooruTagCategory.artist}),
        ),
      );
      expect(client.searchSites, contains('https://e621.net'), reason: '"More from" searches the post\'s site');
      await tester.tap(find.byKey(const ValueKey('booru-post-tag-kantoku')));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('booru-tag-action-wiki')));
      await tester.pumpAndSettle();
      expect(client.wikiSites, ['https://e621.net']);
    });

    testWidgets('the wiki opens from a tag', (tester) async {
      final client = await _pump(
        tester,
        BooruPostScreen(post: _post('1', categories: {'kantoku': BooruTagCategory.artist})),
      );
      await tester.tap(find.byKey(const ValueKey('booru-post-tag-kantoku')));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('booru-tag-action-wiki')));
      await tester.pump(const Duration(milliseconds: 300));
      await tester.pumpAndSettle();
      expect(client.wikiCalls, 1);
      expect(find.text('Kantoku\nAn illustrator.'), findsOneWidget);
    });

    test('facts leave out what the host did not send', () async {
      final l10n = await L10n.load(const Locale('en'));
      final bare = BooruPost(
        id: '1',
        host: 'h',
        engine: 'danbooru',
        tags: const [],
        rating: null,
        score: null,
        width: 0,
        height: 0,
        previewUrl: null,
        sampleUrl: null,
        fileUrl: null,
        fileExt: null,
        source: null,
        createdAt: null,
      );
      expect(booruPostFacts(bare, l10n, 'en'), isEmpty);
    });

    test('a time the post last changed is shown as updated, not posted', () async {
      final l10n = await L10n.load(const Locale('en'));
      final changed = BooruPost(
        id: '1',
        host: 'h',
        engine: 'gelbooru',
        tags: const [],
        rating: null,
        score: null,
        width: 0,
        height: 0,
        previewUrl: null,
        sampleUrl: null,
        fileUrl: null,
        fileExt: null,
        source: null,
        createdAt: null,
        updatedAt: DateTime.utc(2024, 1, 2, 3, 4),
      );
      final fact = booruPostFacts(changed, l10n, 'en').single;
      expect(fact.text, startsWith('Updated '));
      expect(fact.text, contains('2024'));
    });
  });

  group('popular tab', () {
    testWidgets('scales and dates change the query', (tester) async {
      final changes = <BooruPopularQuery>[];
      final query = BooruPopularQuery(scale: BooruPopularScale.day, date: DateTime(2024, 5, 10));
      await _pump(
        tester,
        Scaffold(
          body: BooruPopularTab(
            engine: BooruEngine.danbooru,
            query: query,
            onChanged: changes.add,
            child: const SizedBox(),
          ),
        ),
      );
      expect(find.text('May 10, 2024'), findsOneWidget);
      await tester.tap(find.byKey(const ValueKey('booru-popular-week')));
      await tester.tap(find.byTooltip('Earlier'));
      await tester.tap(find.byTooltip('Later'));
      expect(changes.map((q) => (q.scale, q.date)), [
        (BooruPopularScale.week, DateTime(2024, 5, 10)),
        (BooruPopularScale.day, DateTime(2024, 5, 9)),
        (BooruPopularScale.day, DateTime(2024, 5, 11)),
      ]);
    });

    testWidgets('a host without a popular list says what it shows instead', (tester) async {
      await _pump(
        tester,
        Scaffold(
          body: BooruPopularTab(
            engine: BooruEngine.gelbooruV2,
            query: BooruPopularQuery.today(),
            onChanged: (_) {},
            child: const SizedBox(),
          ),
        ),
      );
      expect(find.byKey(const ValueKey('booru-popular-day')), findsNothing);
      expect(find.textContaining('highest-scored'), findsOneWidget);
    });
  });

  group('display', () {
    testWidgets('columns, details and blur follow the settings', (tester) async {
      final posts = [_post('1', rating: BooruRating.explicit), _post('2')];
      await _pump(
        tester,
        Scaffold(body: BooruPostGrid(posts: posts)),
        prefs: {
          optionPluginBooruGridColumns: 2,
          optionPluginBooruTileDetails: false,
          optionPluginBooruBlurExplicit: true,
        },
      );
      expect(find.byType(BooruPostTile), findsNWidgets(2));
      expect(find.text('https://danbooru.donmai.us'), findsNothing);
      expect(find.byType(ImageFiltered), findsOneWidget);
      expect(find.byIcon(Icons.visibility_off_outlined), findsOneWidget);
    });

    testWidgets('settings take a multi-tag blacklist entry and a column count', (tester) async {
      await _pump(tester, const BooruSettingsScreen());
      final field = find.widgetWithText(TextField, 'Mute a tag');
      await tester.scrollUntilVisible(field, 300, scrollable: find.byType(Scrollable).first);
      await tester.enterText(field, 'Solo  smile');
      await tester.testTextInput.receiveAction(TextInputAction.done);
      await tester.pumpAndSettle();
      final prefs = PrefService.of(tester.element(find.byType(BooruSettingsScreen)), listen: false);
      expect(prefs.get<String>(optionPluginBooruMutedTags), '["solo smile"]');

      await tester.scrollUntilVisible(find.text('Grid columns'), -300, scrollable: find.byType(Scrollable).first);
      await tester.tap(find.text('Grid columns'));
      await tester.pumpAndSettle();
      await tester.tap(find.widgetWithText(RadioListTile<int>, '3'));
      await tester.pumpAndSettle();
      expect(prefs.get<int>(optionPluginBooruGridColumns), 3);
      expect(find.text('3'), findsOneWidget);
    });

    testWidgets('the grid footer offers a retry after a failed page, then marks the end', (tester) async {
      var failNext = true;
      final feed = BooruFeedStore(_Client(PrefServiceCache()), ({required page}) async {
        if (failNext) {
          failNext = false;
          throw BooruException(BooruErrorKind.network, 'offline');
        }
        return BooruPostPage(posts: [_post('b')], page: page, hasMore: false);
      })..update([_post('a')]);
      addTearDown(feed.destroy);
      await _pump(
        tester,
        Scaffold(
          body: BooruPostGrid(posts: feed.state, feed: feed),
        ),
      );

      unawaited(feed.loadMore());
      await tester.pumpAndSettle();
      expect(find.text('Could not load more posts'), findsOneWidget);

      await tester.tap(find.text('Retry'));
      await tester.pumpAndSettle();
      expect(find.text('Could not load more posts'), findsNothing);
      expect(find.text('No more posts'), findsOneWidget);
      expect(feed.state.map((p) => p.id), ['a', 'b']);
    });

    testWidgets('Following says when some followed tags did not load, and offers a retry', (tester) async {
      const notice = 'Some followed tags could not be loaded';
      final failed = BooruFailedQueriesStore();
      addTearDown(failed.destroy);
      var retries = 0;
      await _pump(
        tester,
        Scaffold(
          body: BooruFollowingNotice(failed: failed, onRetry: () => retries++),
        ),
      );
      expect(find.text(notice), findsNothing);

      failed.update(2);
      await tester.pumpAndSettle();
      expect(find.text(notice), findsOneWidget);
      await tester.tap(find.byTooltip('Retry'));
      expect(retries, 1);

      failed.update(0);
      await tester.pumpAndSettle();
      expect(find.text(notice), findsNothing);
    });

    testWidgets('a video waits for a tap when media loads only on tap', (tester) async {
      final interval = VisibilityDetectorController.instance.updateInterval;
      VisibilityDetectorController.instance.updateInterval = Duration.zero;
      addTearDown(() => VisibilityDetectorController.instance.updateInterval = interval);
      final clip = BooruPost(
        id: '7',
        host: 'https://danbooru.donmai.us',
        engine: 'danbooru',
        tags: const [],
        rating: BooruRating.general,
        score: null,
        width: 16,
        height: 9,
        previewUrl: null,
        sampleUrl: null,
        fileUrl: 'https://cdn.donmai.us/original/clip.mp4',
        fileExt: 'mp4',
        source: null,
        createdAt: null,
      );
      await _pump(
        tester,
        Scaffold(
          body: SingleChildScrollView(child: BooruPostMedia(post: clip)),
        ),
        prefs: {
          optionMediaDisableAutoload: true,
          optionMediaDefaultMute: true,
          optionMediaDefaultLoop: false,
          optionMediaDefaultAutoPlay: false,
          optionMediaBackgroundPlayback: true,
          optionMediaAllowBackgroundPlayOtherApps: false,
        },
      );
      addTearDown(() => tester.pumpWidget(const SizedBox()));
      expect(find.byType(TweetVideo), findsNothing, reason: 'no player, and no download, before the tap');

      await tester.tap(find.text('Tap to show Media'));
      await tester.pump();
      final player = tester.widget<TweetVideo>(find.byType(TweetVideo));
      expect(player.loop, isTrue, reason: "Booru's own player settings are kept");
      expect(player.tweetId, 'booru-https://danbooru.donmai.us-7');
    });

    testWidgets('Latest shows the skeleton, not "no posts", until its first page arrives', (tester) async {
      const empty = 'No posts yet — check the host in settings';
      final scroll = ScrollController();
      addTearDown(scroll.dispose);
      final client = await _pump(tester, BooruScreen(scrollController: scroll), tags: _Tags(), settle: false);
      expect(find.byType(PluginGridSkeleton), findsOneWidget);
      expect(find.text(empty), findsNothing);

      await tester.pump(const Duration(milliseconds: 300));
      expect(find.byType(PluginGridSkeleton), findsOneWidget, reason: 'the first page is still on its way');
      expect(find.text(empty), findsNothing);

      client.latestPage.complete(BooruPostPage(posts: [_post('1'), _post('2')], page: 1, hasMore: false));
      await tester.pumpAndSettle();
      expect(find.byType(PluginGridSkeleton), findsNothing);
      expect(find.byType(BooruPostGrid), findsOneWidget);
    });

    testWidgets('Latest asks for its first page in its first frame', (tester) async {
      final scroll = ScrollController();
      addTearDown(scroll.dispose);
      final client = await _pump(tester, BooruScreen(scrollController: scroll), tags: _Tags(), settle: false);

      await tester.pump(Duration.zero);

      expect(client.latestCalls, 1, reason: 'neither the stores reloading nor a 50 ms debounce stands in front of it');
      client.latestPage.complete(const BooruPostPage(posts: [], page: 1, hasMore: false));
      await tester.pumpAndSettle();
    });

    testWidgets('leaving settings while a site or a mute is being saved throws nothing', (tester) async {
      Future<_GatedPrefs> open() async {
        final prefs = _GatedPrefs({optionPluginBooruEngine: 'danbooru'});
        await _pump(tester, const BooruSettingsScreen(), service: prefs);
        return prefs;
      }

      var prefs = await open();
      await tester.tap(find.widgetWithText(ChoiceChip, 'e621'));
      await tester.pump();
      await tester.pumpWidget(const SizedBox());
      prefs.open.complete();
      await tester.pumpAndSettle();

      prefs = await open();
      final field = find.widgetWithText(TextField, 'Mute a tag');
      await tester.scrollUntilVisible(field, 300, scrollable: find.byType(Scrollable).first);
      await tester.enterText(field, 'solo');
      await tester.testTextInput.receiveAction(TextInputAction.done);
      await tester.pump();
      await tester.pumpWidget(const SizedBox());
      prefs.open.complete();
      await tester.pumpAndSettle();
      expect(prefs.get<String>(optionPluginBooruMutedTags), '["solo"]', reason: 'the write itself still lands');
    });

    testWidgets('settings keep the login and key with the site they belong to', (tester) async {
      await _pump(
        tester,
        const BooruSettingsScreen(),
        prefs: {
          optionPluginBooruHost: 'https://danbooru.donmai.us',
          optionPluginBooruLogin: 'reader',
          optionPluginBooruApiKey: 'secret-key',
          optionPluginBooruCredentialsHost: 'https://danbooru.donmai.us',
        },
      );
      final prefs = PrefService.of(tester.element(find.byType(BooruSettingsScreen)), listen: false);
      final login = find.widgetWithText(TextField, 'Login / user id');
      String shown() => tester.widget<TextField>(login).controller!.text;
      expect(shown(), 'reader');

      await tester.tap(find.widgetWithText(ChoiceChip, 'e621'));
      await tester.pumpAndSettle();
      expect(shown(), isEmpty, reason: "another site's login is not shown as this one's");
      expect(find.textContaining('danbooru.donmai.us'), findsOneWidget);

      await tester.tap(find.widgetWithText(ChoiceChip, 'Danbooru'));
      await tester.pumpAndSettle();
      expect(shown(), 'reader');

      await tester.tap(find.widgetWithText(ChoiceChip, 'e621'));
      await tester.pumpAndSettle();
      await tester.enterText(login, 'fox');
      await tester.pumpAndSettle();
      expect(prefs.get<String>(optionPluginBooruCredentialsHost), 'https://e621.net');
      expect(prefs.get<String>(optionPluginBooruApiKey), isEmpty);
      expect(find.textContaining('danbooru.donmai.us'), findsNothing);
    });

    test('thumbnails and full screen use the chosen file size', () {
      final post = BooruPost(
        id: '1',
        host: 'h',
        engine: 'danbooru',
        tags: const [],
        rating: null,
        score: null,
        width: 1,
        height: 1,
        previewUrl: 'p',
        sampleUrl: 's',
        fileUrl: 'f',
        fileExt: 'jpg',
        source: null,
        createdAt: null,
      );
      expect(const BooruDisplay().tileUrl(post), 's');
      expect(const BooruDisplay(smallThumbnails: true).tileUrl(post), 'p');
      expect(const BooruDisplay().viewerUrl(post), 's');
      expect(const BooruDisplay(originalInViewer: true).viewerUrl(post), 'f');
      expect(const BooruDisplay(columns: 3).columnsFor(2000, TextScaler.noScaling), 3);
    });
  });

  test('animations sampled as webm play as video', () {
    final ugoira = BooruPost(
      id: '1',
      host: 'h',
      engine: 'danbooru',
      tags: const [],
      rating: null,
      score: null,
      width: 1,
      height: 1,
      previewUrl: 'https://cdn/p.jpg',
      sampleUrl: 'https://cdn/s.webm',
      fileUrl: 'https://cdn/f.zip',
      fileExt: 'zip',
      source: null,
      createdAt: null,
    );
    expect(ugoira.isVideo, isTrue);
    expect(ugoira.videoUrl, 'https://cdn/s.webm');
    expect(ugoira.catalogUrl, 'https://cdn/p.jpg');
    expect(
      booruDownloadItem(ugoira).resolvedDownloadUrl,
      'https://cdn/s.webm',
      reason: 'Download saves the animation that plays, not the zip of its frames',
    );
    expect(booruDownloadItem(_post('2')).resolvedDownloadUrl, _post('2').originalUrl);
  });
}
