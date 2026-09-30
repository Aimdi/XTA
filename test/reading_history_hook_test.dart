import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:visibility_detector/visibility_detector.dart';
import 'package:xta/plugins/mastodon/mastodon_models.dart';
import 'package:xta/plugins/mastodon/mastodon_post_card.dart';
import 'package:xta/reading/reading_history_entry.dart';
import 'package:xta/reading/reading_history_hook.dart';
import 'package:xta/reading/reading_history_navigation.dart';
import 'package:xta/reading/reading_history_screen.dart';
import 'package:xta/reading/reading_history_store.dart';
import 'package:xta/utils/read_visibility.dart';

import 'support/reader_tools_harness.dart';

ReadingHistoryEntry _entry(String id, {String text = 'Seen text'}) =>
    ReadingHistoryEntry(source: 'web', kind: ReadingHistoryKind.article, nativeId: id, title: 'Title $id', text: text);

class _Host {
  final prefs = PrefServiceCache();
  final storage = MemoryJsonStore();
  late final store = ReadingHistoryStore(storage, prefs);

  Widget app(Widget child) => readerToolsApp(prefs, ReadingHistoryScope(store: store, child: child));

  Future<void> close(WidgetTester tester) async {
    await tester.pumpWidget(const SizedBox());
    await store.destroy();
  }
}

Widget _card(String id, {double height = 200}) => ReadingHistoryHook(
  entry: () => _entry(id),
  child: SizedBox(height: height, child: Text('card $id')),
);

void main() {
  setUp(() => VisibilityDetectorController.instance.updateInterval = Duration.zero);
  tearDown(() => VisibilityDetectorController.instance.updateInterval = const Duration(milliseconds: 500));

  testWidgets('records a card only after it stays on screen, once', (tester) async {
    final host = _Host();
    await tester.pumpWidget(
      host.app(
        Scaffold(body: ListView(children: [_card('a'), _card('b'), for (var i = 0; i < 20; i++) _card('far$i')])),
      ),
    );
    await tester.pump();
    await tester.pump(readingHistoryCardDwell ~/ 2);
    expect(host.store.state.entries, isEmpty);
    await tester.pump(readingHistoryCardDwell);
    expect(host.store.state.entries.map((entry) => entry.nativeId), containsAll(['a', 'b']));
    expect(
      host.store.state.entries.where((entry) => entry.nativeId.startsWith('far') && entry.nativeId != 'far0'),
      isEmpty,
    );
    await tester.pump(readingHistoryCardDwell * 2);
    expect(host.store.state.entries.where((entry) => entry.nativeId == 'a'), hasLength(1));
    await host.close(tester);
  });

  testWidgets('a card scrolled past quickly is not recorded', (tester) async {
    final host = _Host();
    final controller = ScrollController();
    await tester.pumpWidget(
      host.app(
        Scaffold(
          body: ListView(controller: controller, children: [for (var i = 0; i < 30; i++) _card('$i')]),
        ),
      ),
    );
    await tester.pump();
    controller.jumpTo(3000);
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));
    await tester.pump(readingHistoryCardDwell * 2);
    final ids = host.store.state.entries.map((entry) => entry.nativeId).toSet();
    expect(ids.intersection({'0', '1', '2'}), isEmpty);
    expect(ids, isNotEmpty);
    await host.close(tester);
  });

  testWidgets('a card under another route is not recorded', (tester) async {
    final host = _Host();
    final navigator = GlobalKey<NavigatorState>();
    await tester.pumpWidget(
      readerToolsApp(
        host.prefs,
        ReadingHistoryScope(
          store: host.store,
          child: Navigator(
            key: navigator,
            observers: [readRouteObserver],
            onGenerateRoute: (_) => MaterialPageRoute(builder: (_) => Scaffold(body: _card('under'))),
          ),
        ),
      ),
    );
    await tester.pump();
    navigator.currentState!.push(MaterialPageRoute(builder: (_) => const Scaffold(body: Text('on top'))));
    await tester.pumpAndSettle();
    await tester.pump(readingHistoryCardDwell * 2);
    expect(host.store.state.entries, isEmpty);
    await host.close(tester);
  });

  testWidgets('text behind a Mastodon content warning is recorded only once revealed', (tester) async {
    final host = _Host();
    final post = MastodonPost(
      id: '1',
      acct: 'someone@example.social',
      authorName: 'Someone',
      text: 'Hidden until revealed',
      url: 'https://example.social/@someone/1',
      spoilerText: 'Spoiler',
    );
    await tester.pumpWidget(
      host.app(
        Scaffold(
          body: SingleChildScrollView(child: MastodonPostCard(post: post)),
        ),
      ),
    );
    await tester.pump(readingHistoryCardDwell * 2);
    expect(host.store.state.entries, isEmpty);
    await tester.tap(find.text('Show'));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));
    await tester.pump(readingHistoryCardDwell * 2);
    expect(host.store.state.entries.single.text, 'Hidden until revealed');
    expect(host.store.state.entries.single.extra['handle'], 'someone@example.social');
    await host.close(tester);
  });

  testWidgets('the screen searches, removes, pauses and clears after confirmation', (tester) async {
    final host = _Host();
    await host.store.load();
    host.store.record(_entry('1', text: 'Mountain trails'));
    host.store.record(_entry('2', text: 'City lights'));
    await tester.pumpWidget(host.app(ReadingHistoryScreen(store: host.store)));
    await tester.pump();
    expect(find.text('Title 1'), findsOneWidget);
    await tester.enterText(find.byKey(const ValueKey('history-search')), 'mountain');
    await tester.pump();
    expect(find.text('Title 2'), findsNothing);
    expect(find.text('Title 1'), findsOneWidget);
    await tester.enterText(find.byKey(const ValueKey('history-search')), '');
    await tester.pump();

    await tester.tap(find.byTooltip('Remove from history').first);
    await tester.pumpAndSettle();
    expect(host.store.state.entries.map((entry) => entry.nativeId), ['1']);

    await tester.tap(find.text('Remember what I read'));
    await tester.pumpAndSettle();
    expect(host.store.state.enabled, isFalse);
    expect(find.textContaining('Paused'), findsOneWidget);

    await tester.tap(find.byKey(const ValueKey('history-clear')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Cancel'));
    await tester.pumpAndSettle();
    expect(host.store.state.entries, hasLength(1));
    await tester.tap(find.byKey(const ValueKey('history-clear')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Clear history').last);
    await tester.pumpAndSettle();
    expect(host.store.state.entries, isEmpty);
    expect(find.text('History cleared'), findsOneWidget);
    await host.close(tester);
  });

  testWidgets('an entry that cannot be reopened says so', (tester) async {
    final host = _Host();
    final entry = ReadingHistoryEntry(source: 'retired', kind: ReadingHistoryKind.post, nativeId: '1');
    await tester.pumpWidget(
      host.app(
        Scaffold(
          body: Builder(
            builder: (context) =>
                TextButton(onPressed: () => openReadingHistoryEntry(context, entry), child: const Text('open')),
          ),
        ),
      ),
    );
    await tester.tap(find.text('open'));
    await tester.pump();
    expect(find.text("This can't be opened any more."), findsOneWidget);
    await host.close(tester);
  });
}
