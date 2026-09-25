import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:provider/provider.dart';
import 'package:xta/plugins/bluesky/bluesky_post_card.dart';
import 'package:xta/plugins/bluesky/bluesky_reader_store.dart';
import 'package:xta/plugins/bluesky/bluesky_screen.dart';
import 'package:xta/plugins/bluesky/bluesky_store.dart';
import 'support/bluesky_reading_harness.dart';

class _HomeHarness {
  final blue = BlueReadingHarness();
  final scroll = ScrollController();
  late final feed = BlueskyFeedStore(blue.client, blue.accounts);

  Widget app({double scale = 1, bool rtl = false}) => blue.app(
    Provider<BlueskyFeedStore>.value(
      value: feed,
      child: BlueskyScreen(scrollController: scroll),
    ),
    scale: scale,
    rtl: rtl,
  );

  Future<void> close(WidgetTester tester) async {
    await blue.close(tester);
    await feed.destroy();
    scroll.dispose();
  }
}

void _viewport(WidgetTester tester, {double width = 390}) {
  tester.view.physicalSize = Size(width, 844);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.resetPhysicalSize);
  addTearDown(tester.view.resetDevicePixelRatio);
}

void main() {
  setUpAll(() async {
    autoUpdateGoldenFiles = true;
    await (FontLoader('Inter')..addFont(rootBundle.load('assets/fonts/Inter-Regular.ttf'))).load();
    await (FontLoader('MaterialIcons')..addFont(rootBundle.load('fonts/MaterialIcons-Regular.otf'))).load();
  });

  testWidgets('fresh Bluesky session restores the visible Following anchor without an author read', (tester) async {
    _viewport(tester);
    final first = _HomeHarness();
    await tester.pumpWidget(first.app());
    await tester.pumpAndSettle();
    expect(first.blue.client.calls, hasLength(1));
    first.scroll.jumpTo(900);
    await tester.pumpAndSettle();
    await tester.pump(const Duration(milliseconds: 400));
    final raw = first.blue.prefs.get<String>(blueskyReaderPreference)!;
    final anchor = jsonDecode(raw)['anchor'] as String;
    final card = find.byWidgetPredicate((widget) => widget is BlueskyPostCard && widget.post.uri == anchor);
    final previousTop = tester.getTopLeft(card).dy;
    expect(anchor, isNot(first.feed.state.first.uri));
    await first.close(tester);

    final second = _HomeHarness();
    await second.blue.prefs.set(blueskyReaderPreference, raw);
    await tester.pumpWidget(second.app());
    await tester.pumpAndSettle();
    expect(card, findsOneWidget);
    expect(tester.getTopLeft(card).dy, closeTo(previousTop, 1));
    expect(second.blue.client.calls, isEmpty);
    expect(tester.takeException(), isNull);
    await second.close(tester);
  });

  testWidgets('Liked tab selection survives a fresh mounted session', (tester) async {
    _viewport(tester);
    final first = _HomeHarness();
    await tester.pumpWidget(first.app());
    await tester.pumpAndSettle();
    await tester.ensureVisible(find.byTooltip('Liked'));
    await tester.tap(find.byTooltip('Liked'));
    await tester.pumpAndSettle();
    await tester.pump(const Duration(milliseconds: 400));
    expect(find.text(bluePost('root').text), findsOneWidget);
    final raw = first.blue.prefs.get<String>(blueskyReaderPreference)!;
    expect(jsonDecode(raw)['tab'], 3);
    await first.close(tester);

    final second = _HomeHarness();
    await second.blue.prefs.set(blueskyReaderPreference, raw);
    await tester.pumpWidget(second.app());
    await tester.pumpAndSettle();
    expect(find.text(bluePost('root').text), findsOneWidget);
    expect(find.text(bluePost('post-0').text), findsNothing);
    expect(second.blue.client.calls, isEmpty);
    expect(tester.takeException(), isNull);
    await second.close(tester);
  });

  testWidgets('Following exposes local filter controls and reset without another network read', (tester) async {
    _viewport(tester);
    final h = _HomeHarness();
    await tester.pumpWidget(h.app());
    await tester.pumpAndSettle();
    await expectLater(
      find.byKey(const ValueKey('bluesky-window')),
      matchesGoldenFile('../review-artifacts/renders/bluesky-following-controls.png'),
    );
    await tester.tap(find.text('Filters'));
    await tester.pumpAndSettle();
    expect(find.text('Search loaded posts'), findsOneWidget);
    expect(find.text('Hide reposts'), findsOneWidget);
    expect(find.text('Hide replies'), findsOneWidget);
    await tester.enterText(find.byType(TextField), 'post-12');
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('Close'));
    await tester.pumpAndSettle();
    expect(find.byType(BlueskyPostCard), findsOneWidget);
    expect(find.text(bluePost('post-12').text), findsOneWidget);
    expect(h.blue.client.calls, hasLength(1));
    await tester.tap(find.text('Filters'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Reset filters'));
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('Close'));
    await tester.pumpAndSettle();
    expect(find.byType(BlueskyPostCard).evaluate().length, greaterThan(1));
    expect(h.blue.client.calls, hasLength(1));
    expect(tester.takeException(), isNull);
    await h.close(tester);
  });

  testWidgets('Following filter sheet fits a narrow RTL screen at double text size', (tester) async {
    _viewport(tester, width: 320);
    final h = _HomeHarness();
    await tester.pumpWidget(h.app(scale: 2, rtl: true));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Filters'));
    await tester.pumpAndSettle();
    expect(find.byTooltip('Close'), findsOneWidget);
    expect(find.text('Hide reposts'), findsOneWidget);
    expect(tester.takeException(), isNull);
    await expectLater(
      find.byKey(const ValueKey('bluesky-window')),
      matchesGoldenFile('../review-artifacts/renders/bluesky-following-filters-large-rtl.png'),
    );
    await h.close(tester);
  });
}
