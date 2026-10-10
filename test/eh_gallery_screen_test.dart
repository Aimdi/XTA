import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/ehviewer/eh_client.dart';
import 'package:xta/plugins/ehviewer/eh_gallery_comments.dart';
import 'package:xta/plugins/ehviewer/eh_gallery_screen.dart';
import 'package:xta/plugins/ehviewer/eh_reader_screen.dart';
import 'package:xta/plugins/ehviewer/eh_search_screen.dart';
import 'package:xta/plugins/ehviewer/eh_store.dart';
import 'package:xta/plugins/plugin_tag_chip.dart';

import 'support/eh_gallery_fixture.dart';

class _PushObserver extends NavigatorObserver {
  final pushed = <Route<dynamic>>[];

  @override
  void didPush(Route<dynamic> route, Route<dynamic>? previousRoute) => pushed.add(route);
}

class _Harness {
  final EhFixtureServer server;
  final _PushObserver observer;

  _Harness(this.server, this.observer);

  /// The screen the last push opened, built from its route without showing it.
  Widget opened(WidgetTester tester) {
    final route = observer.pushed.last as MaterialPageRoute<dynamic>;
    return route.builder(tester.element(find.byType(EhGalleryScreen)));
  }
}

Future<_Harness> _open(
  WidgetTester tester, {
  EhFixtureServer? server,
  int? lastPage,
  Size size = const Size(800, 3000),
  double textScale = 1,
  bool settle = true,
}) async {
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  final fixture = server ?? EhFixtureServer();
  final client = fixture.client();
  final history = EhHistoryStore();
  if (lastPage != null) {
    history.update([EhHistoryEntry(gallery: ehFixtureGallery, lastPage: lastPage, viewedAt: DateTime(2026))]);
  }
  final observer = _PushObserver();
  await tester.pumpWidget(
    PrefService(
      service: client.prefs,
      child: MultiProvider(
        providers: [
          Provider<EhClient>.value(value: client),
          Provider<EhFavoritesStore>.value(value: EhFavoritesStore()),
          Provider<EhHistoryStore>.value(value: history),
        ],
        child: MaterialApp(
          localizationsDelegates: const [
            L10n.delegate,
            GlobalMaterialLocalizations.delegate,
            GlobalWidgetsLocalizations.delegate,
          ],
          navigatorObservers: [observer],
          builder: (context, child) => MediaQuery(
            data: MediaQuery.of(context).copyWith(textScaler: TextScaler.linear(textScale)),
            child: child!,
          ),
          home: const EhGalleryScreen(gallery: ehFixtureGallery),
        ),
      ),
    ),
  );
  await tester.pump(const Duration(milliseconds: 100));
  if (settle) await tester.pumpAndSettle();
  return _Harness(fixture, observer);
}

double _top(WidgetTester tester, Finder finder) => tester.getTopLeft(finder).dy;

void main() {
  testWidgets('the header and facts show what the site sent, with spoken labels', (tester) async {
    final semantics = tester.ensureSemantics();
    await _open(tester);

    expect(find.text('夏の本'), findsOneWidget);
    expect(find.text('Sommer Book'), findsOneWidget);
    expect(find.text('Doujinshi'), findsOneWidget);
    expect(find.text('4.62 (321)'), findsOneWidget);
    expect(find.text('24 pages'), findsOneWidget);
    expect(find.text('English'), findsOneWidget);
    expect(find.text('96.6 MB'), findsOneWidget);
    expect(find.text('1.23K'), findsOneWidget);
    expect(find.text('Aug 11, 2024'), findsOneWidget);
    expect(find.bySemanticsLabel('Uploader Alice'), findsOneWidget);
    expect(find.bySemanticsLabel('Rated 4.62 out of 5 from 321 ratings'), findsOneWidget);
    expect(find.bySemanticsLabel('Language: English, translated'), findsOneWidget);
    expect(find.bySemanticsLabel('File size 96.6 MB'), findsOneWidget);
    expect(find.bySemanticsLabel('Favorited 1234 times'), findsOneWidget);
    expect(find.bySemanticsLabel('Posted Aug 11, 2024'), findsOneWidget);
    expect(find.byKey(const ValueKey('eh-gallery-continue')), findsNothing);
    semantics.dispose();
  });

  testWidgets('the uploader opens a search for their galleries', (tester) async {
    final harness = await _open(tester);
    await tester.tap(find.byKey(const ValueKey('eh-gallery-uploader')));
    final search = harness.opened(tester) as EhSearchScreen;
    expect(search.initialQuery, 'uploader:"Alice"');
  });

  testWidgets('on a phone each namespace heads its own tags', (tester) async {
    final semantics = tester.ensureSemantics();
    await _open(tester, size: const Size(400, 3000));

    final order = ['Language', 'english', 'Parody', 'original', 'Female', 'big breasts'];
    final tops = [for (final text in order) _top(tester, find.text(text))];
    for (var i = 1; i < tops.length; i++) {
      expect(tops[i], greaterThan(tops[i - 1]), reason: '${order[i]} below ${order[i - 1]}');
    }
    expect(_top(tester, find.text('twin tails')), lessThan(_top(tester, find.text('Comments'))));

    PluginTagChip chip(String raw) => tester.widget(find.byKey(ValueKey('eh-tag-$raw')));
    expect(chip('female:twin tails').weak, isTrue);
    expect(chip('female:big breasts').weak, isFalse);
    expect(chip('female:big breasts').kind, PluginTagKind.general);
    expect(chip('parody:original').kind, PluginTagKind.copyright);
    expect(find.bySemanticsLabel('twin tails, not yet confirmed'), findsOneWidget);
    semantics.dispose();
  });

  testWidgets('on a wide screen the namespaces form a label column', (tester) async {
    await _open(tester);
    final label = tester.getRect(find.text('Female'));
    final chip = tester.getRect(find.text('big breasts'));
    expect(label.right, lessThan(chip.left));
    expect((label.center.dy - chip.center.dy).abs(), lessThan(24));
  });

  testWidgets('a tag searches for exactly itself and a long press copies it', (tester) async {
    String? copied;
    tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(SystemChannels.platform, (call) async {
      if (call.method == 'Clipboard.setData') copied = (call.arguments as Map)['text'] as String?;
      return null;
    });
    addTearDown(() => tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(SystemChannels.platform, null));
    final harness = await _open(tester);

    await tester.longPress(find.text('big breasts'));
    await tester.pump();
    expect(copied, 'female:big breasts');
    expect(find.text('Copied female:big breasts'), findsOneWidget);
    expect(harness.observer.pushed, hasLength(1));

    await tester.tap(find.text('big breasts'));
    final search = harness.opened(tester) as EhSearchScreen;
    expect(search.initialQuery, r'female:"big breasts$"');
  });

  for (final (name, key, page) in [
    ('Read', 'eh-gallery-read', 1),
    ('Continue', 'eh-gallery-continue', 7),
    ('a preview', 'eh-preview-3', 3),
  ]) {
    testWidgets('$name opens the reader at page $page', (tester) async {
      final harness = await _open(tester, lastPage: 7);
      expect(find.text('Continue from page 7'), findsOneWidget);

      await tester.tap(find.byKey(ValueKey(key)));
      final reader = harness.opened(tester) as EhReaderScreen;
      expect(reader.initialPage, page);
      expect(reader.gallery.gid, 9);
      expect(reader.previews.map((p) => p.page), [1, 2, 3, 4]);
    });
  }

  testWidgets('three comments show, then all of them in a sheet', (tester) async {
    await _open(tester);
    expect(find.byType(EhCommentTile), findsNWidgets(3));
    expect(find.byKey(const ValueKey('eh-comment-uploader')), findsOneWidget);
    expect(find.text('Comment number 3'), findsNothing);

    await tester.tap(find.text('Show all 4 comments'));
    await tester.pumpAndSettle();
    final sheet = find.byKey(const ValueKey('eh-gallery-comments-sheet'));
    expect(find.descendant(of: sheet, matching: find.byType(EhCommentTile)), findsNWidgets(4));
    expect(find.descendant(of: sheet, matching: find.text('Comment number 3')), findsOneWidget);
  });

  testWidgets('bones stand in below the header until the detail arrives', (tester) async {
    final server = EhFixtureServer()..gate = Completer<void>();
    await _open(tester, server: server, settle: false);
    expect(find.byKey(const ValueKey('eh-gallery-skeleton')), findsOneWidget);
    expect(find.text('夏の本'), findsOneWidget);

    server.gate!.complete();
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('eh-gallery-skeleton')), findsNothing);
    expect(find.text('24 pages'), findsOneWidget);
  });

  testWidgets('a failed load shows the error with a retry that works', (tester) async {
    await _open(tester, server: EhFixtureServer()..failures = 1);
    expect(find.text('The site returned something unexpected'), findsOneWidget);

    await tester.tap(find.text('Retry'));
    await tester.pumpAndSettle();
    expect(find.text('24 pages'), findsOneWidget);
  });

  testWidgets('pulling down reloads; a failed reload keeps the page and says why', (tester) async {
    final harness = await _open(tester, server: EhFixtureServer(sheetCount: 1), size: const Size(400, 800));
    final scroll = find.byKey(const ValueKey('eh-gallery-scroll'));

    await tester.fling(scroll, const Offset(0, 400), 1000);
    await tester.pumpAndSettle();
    expect(harness.server.sheetsRequested, [0, 0]);

    harness.server.failures = 1;
    await tester.fling(scroll, const Offset(0, 400), 1000);
    await tester.pumpAndSettle();
    expect(find.text('The site returned something unexpected'), findsOneWidget);
    expect(find.text('24 pages'), findsOneWidget);
  });

  testWidgets('a narrow phone at large text scrolls through without overflow, loading previews', (tester) async {
    final harness = await _open(tester, lastPage: 12, size: const Size(360, 800), textScale: 1.3);
    expect(tester.takeException(), isNull);
    expect(find.text('Continue from page 12'), findsOneWidget);

    for (var step = 0; step < 14; step++) {
      await tester.drag(find.byKey(const ValueKey('eh-gallery-scroll')), const Offset(0, -400));
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
    }
    expect(harness.server.sheetsRequested, containsAllInOrder([0, 1]));
    expect(find.byKey(const ValueKey('eh-preview-12')), findsOneWidget);
  });
}
