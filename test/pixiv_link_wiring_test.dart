import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:provider/single_child_widget.dart';
import 'package:xta/plugins/pixiv/pixiv_client.dart';
import 'package:xta/plugins/pixiv/pixiv_discovery_api.dart';
import 'package:xta/plugins/pixiv/pixiv_discovery_models.dart';
import 'package:xta/plugins/pixiv/pixiv_history_store.dart';
import 'package:xta/plugins/pixiv/pixiv_link_open.dart';
import 'package:xta/plugins/pixiv/pixiv_links.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_models.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_series_screen.dart';
import 'package:xta/plugins/pixiv/pixiv_screen.dart';
import 'package:xta/plugins/pixiv/pixiv_search_screen.dart';
import 'package:xta/plugins/pixiv/pixiv_series_screen.dart';
import 'package:xta/plugins/pixiv/pixiv_store.dart';
import 'package:xta/plugins/pixiv/pixiv_user_list_screen.dart';
import 'package:xta/plugins/pixiv/pixivision_article_screen.dart';
import 'package:xta/plugins/pixiv/pixivision_parser.dart';

import 'support/memory_json_store.dart';
import 'support/pixiv_discovery_fakes.dart';
import 'support/pixiv_novel_fakes.dart';
import 'support/pixiv_reader_harness.dart';
import 'support/pixiv_search_fakes.dart';
import 'support/pixiv_social_fakes.dart';

final _series = PixivIllustSeries(
  id: 266067,
  title: 'Autumn Diary',
  user: const PixivUser(id: 42, name: 'Mika', account: 'mika', comment: ''),
  workCount: 1,
);

const _article = PixivisionArticle(
  title: 'Cats in Autumn',
  intro: 'Leaves, cats and warm light.',
  works: [PixivisionWork(artworkId: 120, userId: 42, title: 'Sleepy cat', userName: 'Mika')],
);

FakePixivDiscoveryApi _discovery() => FakePixivDiscoveryApi(
  PixivClient(PrefServiceCache()),
  series: _series,
  seriesWorks: [pixivWork(id: 121, pages: 1, title: 'Part one')],
  article: _article,
);

/// A series Pixiv will not hand over, such as a deleted one.
class _UnreadableSeries extends FakePixivDiscoveryApi {
  _UnreadableSeries() : super(PixivClient(PrefServiceCache()));

  @override
  Future<PixivSeriesPage> illustSeries(int seriesId, {String? nextUrl}) async {
    calls.add('series:$seriesId');
    throw PixivException(PixivErrorKind.notFound, 'gone');
  }
}

SingleChildWidget _provide(FakePixivDiscoveryApi api) => Provider<PixivDiscoveryApi>.value(value: api);

/// A button that opens [ref] the way a caption, comment or shared link does.
Widget _linkButton(PixivLinkRef ref, List<bool> opened) => Scaffold(
  body: Builder(
    builder: (context) =>
        TextButton(onPressed: () async => opened.add(await openPixivLinkRef(context, ref)), child: const Text('open')),
  ),
);

/// Records what the browser is handed, as `open_links_test.dart` does.
List<String> _recordLaunches() {
  const launcher = MethodChannel('plugins.flutter.io/url_launcher');
  const resolver = MethodChannel('browser_resolver');
  final launched = <String>[];
  final messenger = TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
  messenger.setMockMethodCallHandler(launcher, (call) async {
    launched.add((call.arguments as Map)['url'] as String);
    return true;
  });
  messenger.setMockMethodCallHandler(resolver, (_) async => null);
  addTearDown(() {
    messenger.setMockMethodCallHandler(launcher, null);
    messenger.setMockMethodCallHandler(resolver, null);
  });
  return launched;
}

void main() {
  group('Pixiv links', () {
    testWidgets('a series link opens the series screen instead of the browser', (tester) async {
      final api = _discovery();
      final launched = _recordLaunches();
      final opened = <bool>[];
      await pumpPixiv(
        tester,
        _linkButton(const PixivSeriesLinkRef(266067, userId: 42), opened),
        extraProviders: [_provide(api)],
      );

      await tester.tap(find.text('open'));
      await settlePixiv(tester);
      expect(find.byWidgetPredicate((w) => w is PixivSeriesScreen && w.seriesId == 266067), findsOneWidget);
      expect(api.calls, contains('series:266067'));
      expect(find.text('Part one'), findsOneWidget);

      await tester.pageBack();
      await settlePixiv(tester);
      expect(opened, [true]);
      expect(launched, isEmpty);
      await disposePixiv(tester);
    });

    testWidgets('a pixivision link opens the article screen instead of the browser', (tester) async {
      final api = _discovery();
      final launched = _recordLaunches();
      final opened = <bool>[];
      await pumpPixiv(
        tester,
        _linkButton(parsePixivLink('https://www.pixivision.net/ja/a/9876')!, opened),
        extraProviders: [_provide(api)],
      );

      await tester.tap(find.text('open'));
      await settlePixiv(tester);
      expect(find.byWidgetPredicate((w) => w is PixivisionArticleScreen && w.articleId == 9876), findsOneWidget);
      expect(api.calls, ['pixivision:9876']);
      expect(find.text('Sleepy cat'), findsOneWidget);
      expect(launched, isEmpty);

      await tester.tap(find.byTooltip('Open in browser'));
      await settlePixiv(tester);
      expect(launched, ['https://www.pixivision.net/ja/a/9876'], reason: 'the page the link named, not a translation');

      await tester.pageBack();
      await settlePixiv(tester);
      expect(opened, [true]);
      await disposePixiv(tester);
    });

    testWidgets('a series link that cannot be read still offers its page on pixiv.net', (tester) async {
      final api = _UnreadableSeries();
      final launched = _recordLaunches();
      await pumpPixiv(
        tester,
        _linkButton(parsePixivLink('https://www.pixiv.net/user/42/series/266067')!, []),
        extraProviders: [_provide(api)],
      );

      await tester.tap(find.text('open'));
      await settlePixiv(tester);
      expect(find.byType(PixivSeriesScreen), findsOneWidget);
      expect(api.calls, contains('series:266067'));
      expect(find.byTooltip('Share link'), findsOneWidget);

      await tester.tap(find.byTooltip('Open on Pixiv'));
      await settlePixiv(tester);
      expect(launched, ['https://www.pixiv.net/user/42/series/266067']);
      await disposePixiv(tester);
    });

    testWidgets('a novel series link that cannot be read still offers its page on pixiv.net', (tester) async {
      final novels = FakePixivNovelApi(PixivClient(PrefServiceCache()));
      final launched = _recordLaunches();
      final opened = <bool>[];
      await pumpPixiv(
        tester,
        _linkButton(parsePixivLink('https://www.pixiv.net/novel/series/55')!, opened),
        extraProviders: novels.providers,
      );

      await tester.tap(find.text('open'));
      await settlePixiv(tester);
      expect(find.byType(PixivNovelSeriesScreen), findsOneWidget);
      expect(novels.calls, ['series:55:null']);
      expect(find.text('Retry'), findsOneWidget);
      expect(find.byTooltip('Share link'), findsOneWidget);

      await tester.tap(find.byTooltip('Open on Pixiv'));
      await settlePixiv(tester);
      expect(launched, ['https://www.pixiv.net/novel/series/55']);
      await disposePixiv(tester);
    });

    testWidgets('a novel link opens the novel and joins the history; a novel series link opens its screen', (
      tester,
    ) async {
      final novels = FakePixivNovelApi(
        PixivClient(PrefServiceCache()),
        details: {789: pixivNovel(id: 789, title: 'Letters')},
        seriesPages: {
          null: PixivNovelSeriesPage(
            series: PixivNovelSeries(id: 55, title: 'Seasons', user: pixivNovel().user),
            chapters: [PixivNovelChapter(order: 1, novel: pixivNovel(id: 10, title: 'Spring'))],
            listed: 1,
          ),
        },
      );
      final history = PixivNovelHistoryStore(storage: MemoryJsonStore());
      addTearDown(history.destroy);
      final launched = _recordLaunches();
      final opened = <bool>[];
      await pumpPixiv(
        tester,
        Column(
          children: [
            Expanded(child: _linkButton(parsePixivLink('https://www.pixiv.net/novel/show.php?id=789')!, opened)),
            Expanded(child: _linkButton(parsePixivLink('pixiv://novels/790')!, opened)),
            Expanded(child: _linkButton(parsePixivLink('https://www.pixiv.net/novel/series/55')!, opened)),
          ],
        ),
        extraProviders: [
          ...novels.providers,
          Provider<PixivNovelHistoryStore>.value(value: history),
        ],
      );

      await tester.tap(find.text('open').at(0));
      await settlePixiv(tester);
      expect(novels.calls, ['detail:789']);
      expect(launched, ['https://www.pixiv.net/novel/show.php?id=789']);
      expect([for (final entry in history.state) (entry.id, entry.title)], [(789, 'Letters')]);

      await tester.tap(find.text('open').at(1));
      await settlePixiv(tester);
      expect(opened, [true, false], reason: 'a novel Pixiv does not have goes back to the caller');
      expect(launched, hasLength(1));

      await tester.tap(find.text('open').at(2));
      await settlePixiv(tester);
      expect(find.byWidgetPredicate((w) => w is PixivNovelSeriesScreen && w.seriesId == 55), findsOneWidget);
      expect(novels.calls.last, 'series:55:null');
      expect(find.text('Spring'), findsOneWidget);
      await tester.pageBack();
      await settlePixiv(tester);
      expect(opened, [true, false, true]);
      expect(launched, hasLength(1));
      await disposePixiv(tester);
    });
  });

  group('search shortcuts', () {
    testWidgets('a number also offers its Pixivision article and opens it', (tester) async {
      final api = _discovery();
      await pumpPixiv(
        tester,
        const PixivSearchScreen(initialQuery: '9876'),
        extraProviders: [FakePixivSearchApi().provider, _provide(api)],
      );
      final tile = find.byKey(const ValueKey('pixiv-open-pixivision-9876'));
      expect(tile, findsOneWidget);
      expect(find.text('Open Pixivision article #9876'), findsOneWidget);
      expect(tester.getSize(tile).height, greaterThanOrEqualTo(48));

      await tester.tap(tile);
      await settlePixiv(tester);
      expect(find.byWidgetPredicate((w) => w is PixivisionArticleScreen && w.articleId == 9876), findsOneWidget);
      expect(api.calls, ['pixivision:9876']);
      await disposePixiv(tester);
    });

    testWidgets('the shortcut tiles fit a narrow phone at large text', (tester) async {
      await pumpPixiv(
        tester,
        const PixivSearchScreen(initialQuery: '1234567890'),
        size: const Size(320, 640),
        textScale: 2,
        extraProviders: [FakePixivSearchApi().provider, _provide(_discovery())],
      );
      expect(find.byKey(const ValueKey('pixiv-open-pixivision-1234567890')), findsOneWidget);
      expect(tester.takeException(), isNull);
      await disposePixiv(tester);
    });
  });

  testWidgets('Home\'s people icon opens the signed-in reader\'s following list', (tester) async {
    final scroll = ScrollController();
    addTearDown(scroll.dispose);
    late PixivFeedStore feed;
    final social = FakePixivSocialApi(
      following: {
        'public': [pixivPreviewOf(21, name: 'Public one', followed: true)],
      },
    );
    await pumpPixiv(
      tester,
      PixivScreen(scrollController: scroll),
      client: (prefs) {
        final client = FakePixivScreenClient(prefs);
        feed = PixivFeedStore(client);
        return client;
      },
      extraProviders: [
        _provide(_discovery()),
        social.provider,
        Provider<PixivFeedStore>(create: (_) => feed, dispose: (_, store) => store.destroy()),
      ],
    );

    await tester.tap(find.byKey(const ValueKey('pixiv-home-following-list')));
    await settlePixiv(tester);
    expect(
      find.byWidgetPredicate(
        (w) => w is PixivUserListScreen && w.kind == PixivUserListKind.following && w.userId == null,
      ),
      findsOneWidget,
    );
    expect(social.calls, ['following:7:public']);
    expect(find.text('Public one'), findsOneWidget);
    expect(find.byKey(const ValueKey('pixiv-user-list-restrict')), findsOneWidget);
    await disposePixiv(tester);
  });
}
