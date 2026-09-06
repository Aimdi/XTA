import 'dart:io';
import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_screen.dart';
import 'package:xta/plugins/plugin_home_chrome.dart';
import 'package:xta/plugins/reddit/reddit_home_source.dart';
import 'package:xta/plugins/reddit/reddit_screen.dart';
import 'package:xta/plugins/reddit/reddit_store.dart';
import 'package:xta/plugins/rss/rss_client.dart';
import 'package:xta/plugins/rss/rss_models.dart';
import 'package:xta/plugins/rss/rss_screen.dart';
import 'package:xta/plugins/rss/rss_store.dart';
import 'package:xta/ui/x_look_theme.dart';

class _Feeds extends RssFeedsStore {
  _Feeds(super.prefs) {
    update(const [RssFeed(id: 'design', feedUrl: 'https://example.test/feed', name: 'The reading room')]);
  }
  @override
  Future<void> load() async {}
}

class _Read extends RssReadStore {
  _Read(super.prefs);
  @override
  Future<void> load() async {}
}

class _Tags extends RssTagsStore {
  _Tags(super.prefs);
  @override
  Future<void> load() async {}
}

class _Client extends RssClient {
  @override
  Future<List<RssItem>> fetchItems(RssFeed feed) async => const [
    RssItem(id: 'one', title: 'Designing a calmer place to read',
      feedId: 'design', feedTitle: 'The reading room', author: 'Alex Morgan',
      excerpt: 'Good reading tools put the source and the story first. The interface should make the next page easy to find.'),
    RssItem(id: 'two', title: 'Small details, better everyday journeys',
      feedId: 'design', feedTitle: 'Field notes',
      excerpt: 'A look at the labels, controls and reading choices that make an app feel familiar.'),
    RssItem(id: 'three', title: 'A library shaped around your interests',
      feedId: 'design', feedTitle: 'The reading room',
      excerpt: 'Keep subscriptions and tags close to the articles you came to read.'),
  ];
}

class _Communities extends RedditSubredditsStore {
  _Communities(super.prefs) { update(const ['flutter', 'technology', 'android']); }
  @override
  Future<void> load({bool force = false}) async {}
}

void main() {
  setUpAll(() async {
    final font = FontLoader('Inter')..addFont(rootBundle.load('assets/fonts/Inter-Regular.ttf'));
    await font.load();
  });

  for (final dark in [false, true]) {
    testWidgets('RSS production screen render ${dark ? 'dark' : 'light'}', (tester) async {
      final prefs = PrefServiceCache();
      final feeds = _Feeds(prefs);
      final read = _Read(prefs);
      final tags = _Tags(prefs);
      final timeline = RssTimelineStore(_Client(), feeds);
      final scroll = ScrollController();
      addTearDown(() { feeds.destroy(); read.destroy(); tags.destroy(); timeline.destroy(); scroll.dispose(); });
      await _render(tester, 'rss-${dark ? 'dark' : 'light'}', dark: dark,
        child: PrefService(service: prefs, child: MultiProvider(providers: [
          Provider<RssFeedsStore>.value(value: feeds),
          Provider<RssReadStore>.value(value: read),
          Provider<RssTagsStore>.value(value: tags),
          Provider<RssTimelineStore>.value(value: timeline),
        ], child: RssScreen(scrollController: scroll))),
      );
    });
  }

  testWidgets('pilot production section controls render', (tester) async {
    final prefs = PrefServiceCache();
    final communities = _Communities(prefs);
    final home = RedditHomeStore(prefs);
    addTearDown(() { communities.destroy(); home.destroy(); });
    await _render(tester, 'pilot-section-controls', child: PrefService(service: prefs,
      child: Provider<RedditSubredditsStore>.value(value: communities,
        child: ListView(children: [
          RedditHomeChrome(source: home.state, onMode: home.selectMode),
          RedditSubredditChips(home: home),
          const Divider(height: 32),
          PixivHomeChrome(index: 1, onSelect: (_) {}),
          const Divider(height: 32),
          PluginEmbedded(child: PixivHomeChrome(index: 0, onSelect: (_) {})),
        ]))),
    );
  });
}

Future<void> _render(WidgetTester tester, String name, {required Widget child, bool dark = false}) async {
  tester.view.physicalSize = const Size(390, 844);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.resetPhysicalSize);
  addTearDown(tester.view.resetDevicePixelRatio);
  final key = GlobalKey();
  await tester.pumpWidget(MaterialApp(
    theme: dark ? xLookThemeData(XLookTokens.lightsOut, null) : xLookLightTheme(null),
    localizationsDelegates: const [L10n.delegate, GlobalMaterialLocalizations.delegate,
      GlobalWidgetsLocalizations.delegate, GlobalCupertinoLocalizations.delegate],
    supportedLocales: L10n.delegate.supportedLocales,
    home: RepaintBoundary(key: key, child: Scaffold(body: child)),
  ));
  await tester.pumpAndSettle();
  expect(tester.takeException(), isNull);
  final boundary = key.currentContext!.findRenderObject()! as RenderRepaintBoundary;
  final image = await boundary.toImage();
  final bytes = await image.toByteData(format: ui.ImageByteFormat.png);
  await tester.runAsync(() async {
    final file = File('review-artifacts/renders/$name.png');
    await file.parent.create(recursive: true);
    await file.writeAsBytes(bytes!.buffer.asUint8List());
  });
  image.dispose();
}
