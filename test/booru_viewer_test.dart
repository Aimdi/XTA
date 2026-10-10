import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/booru/booru_client.dart';
import 'package:xta/plugins/booru/booru_display.dart';
import 'package:xta/plugins/booru/booru_grid.dart';
import 'package:xta/plugins/booru/booru_models.dart';
import 'package:xta/plugins/booru/booru_pager_store.dart';
import 'package:xta/plugins/booru/booru_popular.dart';
import 'package:xta/plugins/booru/booru_popular_tab.dart';
import 'package:xta/plugins/booru/booru_engines.dart';
import 'package:xta/plugins/booru/booru_post_facts.dart';
import 'package:xta/plugins/booru/booru_post_screen.dart';
import 'package:xta/plugins/booru/booru_related.dart';
import 'package:xta/plugins/booru/booru_settings.dart';
import 'package:xta/plugins/booru/booru_store.dart';
import 'package:xta/plugins/booru/booru_tag_list.dart';
import 'package:xta/plugins/booru/booru_tag_style.dart';
import 'package:xta/plugins/plugin_tag_chip.dart';

BooruPost _post(
  String id, {
  List<String> tags = const ['kantoku', 'solo', 'highres'],
  Map<String, BooruTagCategory> categories = const {},
  BooruRating rating = BooruRating.general,
  String? parentId,
}) => BooruPost(
  id: id,
  host: 'https://danbooru.donmai.us',
  engine: 'danbooru',
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

  final searches = <String>[];
  final popularQueries = <BooruPopularQuery>[];
  var commentCalls = 0;
  var wikiCalls = 0;

  @override
  Future<BooruPostPage> search(String query, {int page = 1, int limit = BooruClient.defaultPageSize}) async {
    searches.add(query);
    return BooruPostPage(posts: [_post('1'), _post('10'), _post('11')], page: page, hasMore: false);
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
  Future<String?> wiki(String tag) async {
    wikiCalls++;
    return tag == 'kantoku' ? 'h4. Kantoku\nAn illustrator.' : null;
  }
}

Future<_Client> _pump(WidgetTester tester, Widget child, {Map<String, Object> prefs = const {}}) async {
  // Tall enough for a post's picture, facts and tags without scrolling.
  tester.view.physicalSize = const Size(800, 2400);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  final service = PrefServiceCache(cache: {optionPluginBooruEngine: 'danbooru', ...prefs});
  final client = _Client(service);
  await tester.pumpWidget(
    PrefService(
      service: service,
      child: MultiProvider(
        providers: [
          Provider<BooruClient>.value(value: client),
          Provider<BooruTagsStore>.value(value: BooruTagsStore()),
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
  await tester.pump(const Duration(milliseconds: 300));
  await tester.pumpAndSettle();
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
  });
}
