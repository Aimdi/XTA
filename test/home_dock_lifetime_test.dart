import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/plugin_home_chrome.dart';
import 'package:xta/plugins/plugin_home_dock.dart';
import 'package:xta/plugins/substack/substack_archive_screen.dart';
import 'package:xta/plugins/substack/substack_client.dart';
import 'package:xta/plugins/substack/substack_models.dart';
import 'package:xta/plugins/substack/substack_post_card.dart';
import 'package:xta/plugins/substack/substack_screen.dart';
import 'package:xta/plugins/substack/substack_store.dart';

const _publication = SubstackPublication(
  subdomain: 'fieldnotes',
  baseUrl: 'https://fieldnotes.substack.com',
  name: 'Field Notes',
);

class _Client extends SubstackClient {
  _Client() : super(httpClient: MockClient((_) async => http.Response('[]', 200)));
  @override
  Future<List<SubstackPost>> fetchPosts(SubstackPublication publication, {int limit = 12, int offset = 0}) async => [
    for (var i = offset; i < offset + limit; i++)
      SubstackPost(
        id: '$i',
        title: 'Morning reading $i',
        slug: 'morning-$i',
        publicationBaseUrl: publication.baseUrl,
        publicationName: publication.name,
        authorName: 'Reader',
        subtitle: 'An article to read.',
        postDate: DateTime.utc(2026, 9, 25).subtract(Duration(days: i)).toIso8601String(),
      ),
  ];
}

class _Publications extends SubstackPublicationsStore {
  _Publications(super.prefs) {
    update([_publication]);
  }
  @override
  Future<void> load() async {}
}

class _Fixture {
  final prefs = PrefServiceCache(defaults: {});
  final client = _Client();
  final scroll = ScrollController();
  final dock = PluginHomeDockStore();
  late final pubs = _Publications(prefs);
  late final read = SubstackReadStore(prefs);
  late final likes = SubstackLikesStore(prefs);
  late final saved = SubstackSavedStore(prefs);
  late final feed = SubstackFeedStore(client, pubs);
  late final notes = SubstackNotesStore(client, pubs);
  Widget app() => PrefService(
    service: prefs,
    child: MultiProvider(
      providers: [
        Provider<SubstackClient>.value(value: client),
        Provider<SubstackPublicationsStore>.value(value: pubs),
        Provider<SubstackReadStore>.value(value: read),
        Provider<SubstackLikesStore>.value(value: likes),
        Provider<SubstackSavedStore>.value(value: saved),
        Provider<SubstackFeedStore>.value(value: feed),
        Provider<SubstackNotesStore>.value(value: notes),
      ],
      child: MaterialApp(
        localizationsDelegates: const [
          L10n.delegate,
          GlobalMaterialLocalizations.delegate,
          GlobalWidgetsLocalizations.delegate,
          GlobalCupertinoLocalizations.delegate,
        ],
        supportedLocales: L10n.delegate.supportedLocales,
        home: PluginHomeDockScope(
          store: dock,
          source: 'substack',
          enabled: true,
          openClientLabel: 'Open Substack',
          onOpenClient: () {},
          child: Scaffold(
            appBar: AppBar(
              title: const Text('Substack'),
              actions: [PluginDockActions(store: dock, source: 'substack')],
            ),
            body: Column(
              children: [
                PluginDockRow(store: dock, source: 'substack'),
                Expanded(
                  child: PrimaryScrollController(
                    controller: scroll,
                    child: PluginEmbedded(child: SubstackScreen(scrollController: scroll)),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    ),
  );
  Future<void> close(WidgetTester tester) async {
    await tester.pumpWidget(const SizedBox());
    await tester.pump();
    await feed.destroy();
    await notes.destroy();
    await read.destroy();
    await likes.destroy();
    await saved.destroy();
    await pubs.destroy();
    await dock.destroy();
    scroll.dispose();
    client.httpClient.close();
  }
}

void main() {
  testWidgets('Substack reading controls survive scrolling past their lazy owner', (tester) async {
    final h = _Fixture();
    try {
      await tester.pumpWidget(h.app());
      await tester.pumpAndSettle();
      expect(find.byType(SubstackPostCard), findsWidgets);
      expect(find.byTooltip(L10n.current.filters), findsOneWidget);
      h.scroll.jumpTo(1400);
      await tester.pumpAndSettle();
      expect(h.dock.content('substack', 'reading'), isNotNull);
      expect(find.byTooltip(L10n.current.filters), findsOneWidget);
      await tester.tap(find.byTooltip(L10n.current.filters));
      await tester.pumpAndSettle();
      expect(find.byType(TextField), findsOneWidget);
      expect(tester.takeException(), isNull);
    } finally {
      await h.close(tester);
    }
  });

  testWidgets('hosted Substack publications retain archive navigation', (tester) async {
    final h = _Fixture();
    try {
      await tester.pumpWidget(h.app());
      await tester.pumpAndSettle();
      await tester.tap(find.widgetWithText(TextButton, L10n.current.plugin_substack_library_following));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Field Notes').last);
      await tester.pumpAndSettle();
      expect(find.byType(SubstackArchiveScreen), findsOneWidget);
      expect(tester.takeException(), isNull);
    } finally {
      await h.close(tester);
    }
  });

  testWidgets('a menu opened for a departed source cannot execute its old action', (tester) async {
    final dock = PluginHomeDockStore();
    var calls = 0;
    Widget app(String source) => MaterialApp(
      home: PluginHomeDockScope(
        store: dock,
        source: source,
        enabled: true,
        openClientLabel: 'Client',
        onOpenClient: () {},
        child: Scaffold(
          body: PluginDockActions(store: dock, source: source),
        ),
      ),
    );
    dock.publish(
      'blue',
      'navigation',
      Object(),
      PluginDockContent(
        actions: [
          PluginHomeMenu(
            onSelected: (_) => calls++,
            itemBuilder: (_) => [const PopupMenuItem(value: 'old', child: Text('Old action'))],
          ),
        ],
      ),
    );
    await tester.pumpWidget(app('blue'));
    await tester.pumpAndSettle();
    await tester.tap(find.byType(PopupMenuButton<String>));
    await tester.pumpAndSettle();
    await tester.pumpWidget(app('masto'));
    await tester.pumpAndSettle();
    if (find.text('Old action').evaluate().isNotEmpty) {
      await tester.tap(find.text('Old action'));
      await tester.pumpAndSettle();
    }
    expect(calls, 0);
    expect(tester.takeException(), isNull);
    await tester.pumpWidget(const SizedBox());
    await dock.destroy();
  });
}
