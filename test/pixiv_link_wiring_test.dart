import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:provider/single_child_widget.dart';
import 'package:xta/plugins/pixiv/pixiv_client.dart';
import 'package:xta/plugins/pixiv/pixiv_discovery_api.dart';
import 'package:xta/plugins/pixiv/pixiv_discovery_models.dart';
import 'package:xta/plugins/pixiv/pixiv_link_open.dart';
import 'package:xta/plugins/pixiv/pixiv_links.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_screen.dart';
import 'package:xta/plugins/pixiv/pixiv_search_screen.dart';
import 'package:xta/plugins/pixiv/pixiv_series_screen.dart';
import 'package:xta/plugins/pixiv/pixiv_store.dart';
import 'package:xta/plugins/pixiv/pixiv_user_list_screen.dart';
import 'package:xta/plugins/pixiv/pixivision_article_screen.dart';
import 'package:xta/plugins/pixiv/pixivision_parser.dart';

import 'support/pixiv_discovery_fakes.dart';
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

      await tester.pageBack();
      await settlePixiv(tester);
      expect(opened, [true]);
      expect(launched, isEmpty);
      await disposePixiv(tester);
    });

    testWidgets('novels and novel series still open in the browser', (tester) async {
      final api = _discovery();
      final launched = _recordLaunches();
      final opened = <bool>[];
      await pumpPixiv(
        tester,
        Column(
          children: [
            Expanded(child: _linkButton(const PixivNovelLinkRef(789), opened)),
            Expanded(child: _linkButton(const PixivNovelSeriesLinkRef(55), opened)),
          ],
        ),
        extraProviders: [_provide(api)],
      );

      await tester.tap(find.text('open').first);
      await settlePixiv(tester);
      await tester.tap(find.text('open').last);
      await settlePixiv(tester);
      expect(opened, [true, true]);
      expect(launched, ['https://www.pixiv.net/novel/show.php?id=789', 'https://www.pixiv.net/novel/series/55']);
      expect(find.byType(PixivSeriesScreen), findsNothing);
      expect(api.calls, isEmpty);
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
