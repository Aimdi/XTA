import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/plugin_home_chrome.dart';
import 'package:xta/plugins/substack/substack_archive_screen.dart';
import 'package:xta/plugins/substack/substack_client.dart';
import 'package:xta/plugins/substack/substack_home_controls.dart';
import 'package:xta/plugins/substack/substack_models.dart';
import 'package:xta/plugins/substack/substack_post_card.dart';
import 'package:xta/plugins/substack/substack_reading_toolbar.dart';
import 'package:xta/plugins/substack/substack_screen.dart';
import 'package:xta/plugins/substack/substack_store.dart';
import 'package:xta/ui/x_look_theme.dart';

const _pub = SubstackPublication(
  subdomain: 'fieldnotes',
  baseUrl: 'https://fieldnotes.substack.com',
  name: 'Field Notes',
);
SubstackPost _post(int id, {int? likes, String? date}) => SubstackPost(
  id: '$id',
  title: 'A quiet morning $id',
  slug: 'morning-$id',
  publicationBaseUrl: _pub.baseUrl,
  publicationName: _pub.name,
  authorName: 'Maya Chen',
  subtitle: 'Observations from the coast',
  postDate: date ?? DateTime.utc(2026, 9, 25).subtract(Duration(days: id)).toIso8601String(),
  reactionCount: likes,
);

class _Client extends SubstackClient {
  final offsets = <int>[];
  var notesRequests = 0;
  var noteCount = 20;
  var failOtherPublication = false;
  _Client() : super(httpClient: MockClient((_) async => http.Response('[]', 200)));
  @override
  Future<List<SubstackPost>> fetchPosts(SubstackPublication publication, {int limit = 12, int offset = 0}) async {
    offsets.add(offset);
    if (publication.id != _pub.id) {
      if (failOtherPublication) throw StateError('Publication unavailable');
      return [];
    }
    return offset == 0 ? [for (var i = 0; i < limit; i++) _post(i)] : [_post(offset)];
  }

  @override
  Future<SubstackNotesPage> fetchReaderNotes({String? host, String? cursor, int limit = 20}) async {
    notesRequests++;
    return SubstackNotesPage(
      notes: [
        for (var i = 0; i < noteCount; i++)
          SubstackNote(id: 'note-$i', body: 'Field notes from the coast, observation $i'),
      ],
    );
  }
}

class _Publications extends SubstackPublicationsStore {
  _Publications(super.prefs) {
    update([_pub]);
  }
  @override
  Future<void> load() async {}
}

class _Harness {
  final prefs = PrefServiceCache(
    defaults: {
      optionZenMode: false,
      optionCalmMode: false,
      optionDisableAnimations: true,
      optionThemeTrueBlack: false,
      optionThemeTrueBlackTweetCards: false,
    },
  );
  final client = _Client();
  final scroll = ScrollController();
  late final publications = _Publications(prefs);
  late final read = SubstackReadStore(prefs);
  late final likes = SubstackLikesStore(prefs);
  late final saved = SubstackSavedStore(prefs);
  late final feed = SubstackFeedStore(client, publications);
  late final notes = SubstackNotesStore(client, publications);
  Widget app({double scale = 1, bool rtl = false, bool dark = false, bool embedded = false, Locale? locale}) =>
      PrefService(
        service: prefs,
        child: MultiProvider(
          providers: [
            Provider<SubstackClient>.value(value: client),
            Provider<SubstackPublicationsStore>.value(value: publications),
            Provider<SubstackReadStore>.value(value: read),
            Provider<SubstackLikesStore>.value(value: likes),
            Provider<SubstackSavedStore>.value(value: saved),
            Provider<SubstackFeedStore>.value(value: feed),
            Provider<SubstackNotesStore>.value(value: notes),
          ],
          child: MaterialApp(
            theme: xLookThemeData(
              (dark ? XLookTokens.lightsOut : XLookTokens.light).copyWith(accent: const Color(0xFFFF8A00)),
              null,
            ),
            locale: locale,
            localizationsDelegates: const [
              L10n.delegate,
              GlobalMaterialLocalizations.delegate,
              GlobalWidgetsLocalizations.delegate,
              GlobalCupertinoLocalizations.delegate,
            ],
            supportedLocales: L10n.delegate.supportedLocales,
            builder: (context, child) => RepaintBoundary(
              key: const ValueKey('substack-home-window'),
              child: MediaQuery(
                data: MediaQuery.of(context).copyWith(textScaler: TextScaler.linear(scale), disableAnimations: true),
                child: Directionality(textDirection: rtl ? TextDirection.rtl : TextDirection.ltr, child: child!),
              ),
            ),
            home: embedded
                ? PluginEmbedded(child: SubstackScreen(scrollController: scroll))
                : SubstackScreen(scrollController: scroll),
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
    await publications.destroy();
    scroll.dispose();
    client.httpClient.close();
  }
}

void _viewport(WidgetTester tester, [double width = 390]) {
  tester.view.physicalSize = Size(width, 844);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.resetPhysicalSize);
  addTearDown(tester.view.resetDevicePixelRatio);
}

Future<void> _options(WidgetTester tester, {bool search = false}) async {
  await tester.tap(find.byTooltip(search ? 'Search' : 'Filters'));
  await tester.pumpAndSettle();
}

Future<void> _closeOptions(WidgetTester tester) async {
  await tester.tap(find.byTooltip('Close'));
  await tester.pumpAndSettle();
}

void main() {
  setUpAll(() async {
    autoUpdateGoldenFiles = true;
    await (FontLoader('Inter')..addFont(rootBundle.load('assets/fonts/Inter-Regular.ttf'))).load();
    await (FontLoader('MaterialIcons')..addFont(rootBundle.load('fonts/MaterialIcons-Regular.otf'))).load();
  });
  test('loaded search combines publication, author and article terms without mutating source', () {
    final posts = [_post(1), _post(2)];
    expect(filterSubstackLoaded(posts, const SubstackLoadedOptions(query: 'MAYA coast 2')).map((p) => p.id), ['2']);
    expect(posts.map((p) => p.id), ['1', '2']);
  });
  test('oldest and popular are stable and missing dates follow dated articles', () {
    final posts = [_post(1, likes: 2), _post(2, likes: 10), _post(3, likes: 10), _post(4, date: 'invalid')];
    expect(
      filterSubstackLoaded(posts, const SubstackLoadedOptions(order: SubstackLoadedOrder.oldest)).map((p) => p.id),
      ['3', '2', '1', '4'],
    );
    expect(
      filterSubstackLoaded(posts, const SubstackLoadedOptions(order: SubstackLoadedOrder.popular)).map((p) => p.id),
      ['2', '3', '1', '4'],
    );
  });
  testWidgets('Home search stays local and survives a lazy tab switch', (tester) async {
    _viewport(tester);
    final h = _Harness();
    await tester.pumpWidget(h.app());
    await tester.pumpAndSettle();
    await _options(tester, search: true);
    await tester.enterText(find.byType(TextField), 'morning 3');
    await tester.pumpAndSettle();
    await _closeOptions(tester);
    expect(find.byType(SubstackPostCard), findsOneWidget);
    expect(h.client.offsets, [0]);
    await tester.tap(find.byTooltip('Inbox'));
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('Home'));
    await tester.pumpAndSettle();
    await _options(tester, search: true);
    expect(tester.widget<TextField>(find.byType(TextField)).controller!.text, 'morning 3');
    expect(find.byType(SubstackPostCard), findsOneWidget);
    expect(h.client.offsets, [0]);
    await tester.tap(find.byTooltip('Clear search'));
    await tester.pumpAndSettle();
    await _closeOptions(tester);
    expect(find.byType(SubstackPostCard), findsWidgets);
    expect(tester.takeException(), isNull);
    await h.close(tester);
  });
  testWidgets('embedded German Home starts with articles instead of stacked controls', (tester) async {
    _viewport(tester);
    final h = _Harness();
    await tester.pumpWidget(h.app(dark: true, embedded: true, locale: const Locale('de')));
    await tester.pumpAndSettle();
    expect(find.byType(TextField), findsNothing);
    expect(find.byType(ChoiceChip), findsNothing);
    expect(tester.getTopLeft(find.byType(SubstackPostCard).first).dy, lessThan(130));
    expect(tester.getSize(find.byType(SubstackReadingToolbar)).height, lessThanOrEqualTo(64));
    await expectLater(
      find.byKey(const ValueKey('substack-home-window')),
      matchesGoldenFile('../review-artifacts/renders/substack-home-compact-de.png'),
    );
    expect(tester.takeException(), isNull);
    await h.close(tester);
  });
  testWidgets('sort and reset work locally and show an active options indicator', (tester) async {
    _viewport(tester);
    final h = _Harness();
    await tester.pumpWidget(h.app());
    await tester.pumpAndSettle();
    await _options(tester);
    await tester.tap(find.byTooltip('Sort posts'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Oldest first'));
    await tester.pumpAndSettle();
    await _closeOptions(tester);
    expect(tester.widget<SubstackPostCard>(find.byType(SubstackPostCard).first).post.id, '${substackFeedPageSize - 1}');
    final badge = find.descendant(of: find.byTooltip('Filters'), matching: find.byType(Badge));
    expect(tester.widget<Badge>(badge).isLabelVisible, isTrue);
    await _options(tester);
    await tester.tap(find.text('Reset filters'));
    await tester.pumpAndSettle();
    await _closeOptions(tester);
    expect(tester.widget<SubstackPostCard>(find.byType(SubstackPostCard).first).post.id, '0');
    expect(tester.widget<Badge>(badge).isLabelVisible, isFalse);
    expect(h.client.offsets, [0]);
    expect(tester.takeException(), isNull);
    await h.close(tester);
  });
  testWidgets('Following opens a named publication list and its archive', (tester) async {
    _viewport(tester);
    tester.view.padding = const FakeViewPadding(bottom: 34);
    addTearDown(tester.view.resetPadding);
    final h = _Harness();
    await tester.pumpWidget(h.app());
    await tester.pumpAndSettle();
    await tester.tap(find.widgetWithText(TextButton, 'Following'));
    await tester.pumpAndSettle();
    final publication = find.widgetWithText(ListTile, 'Field Notes');
    expect(publication, findsOneWidget);
    expect(find.descendant(of: publication, matching: find.text('Unread')), findsOneWidget);
    expect(tester.getBottomRight(publication).dy, lessThanOrEqualTo(810));
    await expectLater(
      find.byKey(const ValueKey('substack-home-window')),
      matchesGoldenFile('../review-artifacts/renders/substack-following-sheet.png'),
    );
    await tester.tap(publication);
    await tester.pumpAndSettle();
    expect(find.byType(SubstackArchiveScreen), findsOneWidget);
    await tester.pageBack();
    await tester.pumpAndSettle();
    expect(find.byType(SubstackReadingToolbar), findsOneWidget);
    expect(tester.takeException(), isNull);
    await h.close(tester);
  });
  testWidgets('partial failure stays compact and can be retried from its details', (tester) async {
    _viewport(tester, 320);
    final h = _Harness();
    h.client.failOtherPublication = true;
    h.publications.update([
      _pub,
      const SubstackPublication(subdomain: 'other', baseUrl: 'https://other.substack.com', name: 'Other'),
    ]);
    await tester.pumpWidget(h.app(scale: 2, rtl: true));
    await tester.pumpAndSettle();
    const message = '1 publication failed to load';
    expect(h.feed.state.failedCount, 1);
    expect(find.byTooltip(message), findsOneWidget);
    expect(find.text(message), findsNothing);
    expect(tester.getSize(find.byType(SubstackReadingToolbar)).height, lessThanOrEqualTo(64));
    await tester.tap(find.byTooltip(message));
    await tester.pumpAndSettle();
    expect(find.text(message), findsOneWidget);
    h.client.failOtherPublication = false;
    await tester.tap(find.text('Retry'));
    await tester.pumpAndSettle();
    expect(h.feed.state.failedCount, 0);
    expect(find.byTooltip(message), findsNothing);
    expect(find.byType(SubstackPostCard), findsWidgets);
    expect(tester.takeException(), isNull);
    await h.close(tester);
  });
  testWidgets('pulling Home refreshes a freshly cached feed', (tester) async {
    _viewport(tester);
    final h = _Harness();
    await tester.pumpWidget(h.app());
    await tester.pumpAndSettle();
    expect(h.client.offsets, [0]);
    await tester.drag(find.byType(RefreshIndicator), const Offset(0, 400));
    await tester.pumpAndSettle();
    expect(h.client.offsets, [0, 0]);
    expect(find.byType(SubstackPostCard), findsWidgets);
    expect(tester.takeException(), isNull);
    await h.close(tester);
  });
  testWidgets('pulling Notes refreshes a freshly cached public stream', (tester) async {
    _viewport(tester);
    final h = _Harness();
    h.client.noteCount = 1;
    await tester.pumpWidget(h.app());
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('Notes'));
    await tester.pumpAndSettle();
    expect(h.client.notesRequests, 1);
    await tester.drag(find.byType(RefreshIndicator), const Offset(0, 400));
    await tester.pumpAndSettle();
    expect(h.client.notesRequests, 2);
    expect(tester.takeException(), isNull);
    await h.close(tester);
  });
  testWidgets('individual read toggle is reversible and current unread filter updates', (tester) async {
    _viewport(tester);
    final h = _Harness();
    await tester.pumpWidget(h.app());
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('Mark as read').first);
    await tester.pumpAndSettle();
    expect(h.read.state, contains('0'));
    expect(find.byTooltip('Mark as unread'), findsOneWidget);
    await tester.tap(find.byTooltip('Mark as unread'));
    await tester.pumpAndSettle();
    expect(h.read.state, isEmpty);
    await _options(tester);
    await tester.tap(find.widgetWithText(ChoiceChip, 'Unread'));
    await tester.pumpAndSettle();
    await _closeOptions(tester);
    await tester.tap(find.byTooltip('Mark as read').first);
    await tester.pumpAndSettle();
    expect(find.byWidgetPredicate((widget) => widget is SubstackPostCard && widget.post.id == '0'), findsNothing);
    expect(tester.takeException(), isNull);
    await h.close(tester);
  });
  testWidgets('an empty loaded Inbox can page to older unread articles', (tester) async {
    _viewport(tester);
    final h = _Harness();
    await tester.pumpWidget(h.app());
    await tester.pumpAndSettle();
    await h.read.markAllRead(h.feed.allPosts.map((p) => p.id));
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('Inbox'));
    await tester.pumpAndSettle();
    expect(find.byType(SubstackPostCard), findsNothing);
    expect(find.text('Load more'), findsOneWidget);
    await tester.tap(find.text('Load more'));
    await tester.pumpAndSettle();
    expect(h.client.offsets, [0, substackFeedPageSize]);
    expect(find.byType(SubstackPostCard), findsOneWidget);
    expect(tester.takeException(), isNull);
    await h.close(tester);
  });
  testWidgets('Library choice and query survive returning from Home', (tester) async {
    _viewport(tester);
    final h = _Harness();
    await h.saved.toggle(_post(2));
    await tester.pumpWidget(h.app());
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('Library'));
    await tester.pumpAndSettle();
    await tester.tap(find.widgetWithText(ChoiceChip, 'Saved'));
    await tester.pumpAndSettle();
    await tester.enterText(find.byType(TextField), 'coast');
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('Home'));
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('Library'));
    await tester.pumpAndSettle();
    expect(tester.widget<TextField>(find.byType(TextField)).controller!.text, 'coast');
    expect(tester.widget<ChoiceChip>(find.widgetWithText(ChoiceChip, 'Saved')).selected, isTrue);
    expect(find.byType(SubstackPostCard), findsOneWidget);
    await h.close(tester);
  });
  testWidgets('Home and card actions fit narrow enlarged RTL text', (tester) async {
    _viewport(tester, 320);
    tester.view.padding = const FakeViewPadding(bottom: 34);
    addTearDown(tester.view.resetPadding);
    final h = _Harness();
    await tester.pumpWidget(h.app(scale: 2, rtl: true));
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
    await expectLater(
      find.byKey(const ValueKey('substack-home-window')),
      matchesGoldenFile('../review-artifacts/renders/substack-home-large-rtl.png'),
    );
    await _options(tester);
    expect(find.widgetWithText(ChoiceChip, 'Podcasts'), findsOneWidget);
    expect(tester.getBottomRight(find.text('Reset filters')).dy, lessThanOrEqualTo(810));
    await expectLater(
      find.byKey(const ValueKey('substack-home-window')),
      matchesGoldenFile('../review-artifacts/renders/substack-options-large-rtl.png'),
    );
    tester.view.viewInsets = const FakeViewPadding(bottom: 300);
    addTearDown(tester.view.resetViewInsets);
    await tester.pumpAndSettle();
    await tester.enterText(find.byType(TextField), 'morning');
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
    await _closeOptions(tester);
    tester.view.resetViewInsets();
    await tester.pumpAndSettle();
    await tester.tap(find.byType(PopupMenuButton<String>));
    await tester.pumpAndSettle();
    expect(find.text('Add publication'), findsOneWidget);
    await tester.tap(find.text('Mark all as read'));
    await tester.pumpAndSettle();
    expect(h.read.state, unorderedEquals(h.feed.allPosts.map((post) => post.id)));
    expect(tester.takeException(), isNull);
    await h.close(tester);
  });
}
