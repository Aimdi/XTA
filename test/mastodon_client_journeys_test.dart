import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/plugins/mastodon/mastodon_models.dart';
import 'package:xta/plugins/mastodon/mastodon_people.dart';
import 'package:xta/plugins/mastodon/mastodon_profile_screen.dart';
import 'package:xta/plugins/mastodon/mastodon_search_sheet.dart';
import 'package:xta/plugins/mastodon/mastodon_search_store.dart';
import 'support/mastodon_harness.dart';

class _DelayedSearch extends MastodonFixtureClient {
  final replies = <String, Completer<MastodonSearchPage>>{};
  @override
  Future<MastodonSearchPage> searchAnywhere(List<String> instances, String q, {int limit = 20}) =>
      (replies[q] = Completer<MastodonSearchPage>()).future;
}

class _RetrySearch extends MastodonFixtureClient {
  bool fail = true;
  @override
  Future<MastodonSearchPage> searchAnywhere(List<String> instances, String q, {int limit = 20}) async {
    if (fail) throw Exception('Temporary fixture failure');
    return super.searchAnywhere(instances, q, limit: limit);
  }
}

Future<void> _open(
  WidgetTester tester,
  MastodonHarness h, {
  bool embedded = false,
  Widget? child,
  bool reducedMotion = true,
}) async {
  tester.view.physicalSize = const Size(390, 844);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.resetPhysicalSize);
  addTearDown(tester.view.resetDevicePixelRatio);
  addTearDown(() => h.close(tester));
  await tester.pumpWidget(h.app(embedded: embedded, child: child, reducedMotion: reducedMotion));
  await tester.pumpAndSettle();
}

void main() {
  testWidgets('full client loads only chosen feeds and restores their positions', (tester) async {
    final h = MastodonHarness();
    await _open(tester, h);
    expect(h.client.publicReads, 0);
    expect(h.client.followingReads, 0);
    h.scroll.jumpTo(650);
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('mastodon-destination-1')));
    await tester.pumpAndSettle();
    expect(h.client.publicReads, 1);
    expect(h.scroll.offset, 0);
    h.scroll.jumpTo(420);
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('mastodon-destination-0')));
    await tester.pumpAndSettle();
    expect(h.scroll.offset, closeTo(650, 1));
    await tester.tap(find.byKey(const ValueKey('mastodon-destination-1')));
    await tester.pumpAndSettle();
    expect(h.scroll.offset, closeTo(420, 1));
    expect(h.client.publicReads, 1);
    expect(tester.takeException(), isNull);
  });

  for (final reducedMotion in [true, false]) {
    testWidgets('Home controls stay hidden until the top, reduced motion: $reducedMotion', (tester) async {
      final h = MastodonHarness();
      await _open(tester, h, embedded: true, reducedMotion: reducedMotion);
      final chrome = find.byKey(const ValueKey('mastodon-compact-controls'));
      expect(chrome, findsOneWidget);
      expect(find.byType(NavigationBar), findsNothing);
      h.scroll.jumpTo(700);
      await tester.pumpAndSettle();
      expect(chrome, findsNothing);
      h.scroll.jumpTo(250);
      await tester.pumpAndSettle();
      expect(chrome, findsNothing);
      h.scroll.jumpTo(0);
      await tester.pumpAndSettle();
      expect(chrome, findsOneWidget);
      expect(tester.takeException(), isNull);
    });
  }

  testWidgets('Following exposes people, profiles, and a working add action', (tester) async {
    final h = MastodonHarness();
    await _open(tester, h);
    await tester.tap(find.byKey(const ValueKey('mastodon-destination-3')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Accounts'));
    await tester.pumpAndSettle();
    expect(find.byType(MastodonPeoplePane), findsOneWidget);
    await tester.tap(find.text('Maya Chen'));
    await tester.pumpAndSettle();
    expect(find.byType(MastodonProfileScreen), findsOneWidget);
    expect(find.text('Unfollow'), findsOneWidget);
    await tester.pageBack();
    await tester.pumpAndSettle();
    expect(find.byType(MastodonPeoplePane), findsOneWidget);
    await tester.tap(find.byKey(const ValueKey('mastodon-add-account')));
    await tester.pumpAndSettle();
    await tester.enterText(find.byType(TextFormField), 'new@studio.example');
    await tester.tap(find.text('OK'));
    await tester.pumpAndSettle();
    expect(find.byType(AlertDialog), findsNothing);
    expect(h.accounts.state.any((account) => account.acct == 'new@studio.example'), isTrue);
    expect(tester.takeException(), isNull);
  });

  testWidgets('search keeps query, result type and position after profile and back', (tester) async {
    final h = MastodonHarness();
    await _open(tester, h);
    await tester.tap(find.byKey(const ValueKey('mastodon-search')));
    await tester.pumpAndSettle();
    expect(find.byType(MastodonSearchScreen), findsOneWidget);
    final field = find.byKey(const ValueKey('mastodon-search-field'));
    await tester.enterText(field, 'design');
    await tester.testTextInput.receiveAction(TextInputAction.search);
    await tester.pumpAndSettle();
    await tester.tap(find.text('Maya Chen'));
    await tester.pumpAndSettle();
    expect(find.byType(MastodonProfileScreen), findsOneWidget);
    await tester.pageBack();
    await tester.pumpAndSettle();
    expect(tester.widget<TextField>(field).controller!.text, 'design');
    expect(h.client.searches, 1);
    await tester.tap(find.text('Posts'));
    await tester.pumpAndSettle();
    final list = find.byKey(const PageStorageKey('mastodon-search-posts'));
    await tester.drag(list, const Offset(0, -500));
    await tester.pumpAndSettle();
    final scrollable = find.descendant(of: list, matching: find.byType(Scrollable));
    final offset = tester.state<ScrollableState>(scrollable).position.pixels;
    await tester.tap(find.text('Accounts'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Posts'));
    await tester.pumpAndSettle();
    expect(tester.state<ScrollableState>(scrollable).position.pixels, closeTo(offset, 1));
    expect(tester.takeException(), isNull);
  });

  testWidgets('failed search can be retried without losing the query', (tester) async {
    final client = _RetrySearch();
    final h = MastodonHarness(client: client);
    await _open(tester, h, child: const MastodonSearchScreen(initialQuery: 'design'));
    expect(find.text('Retry'), findsOneWidget);
    client.fail = false;
    await tester.tap(find.text('Retry'));
    await tester.pumpAndSettle();
    expect(find.text('Maya Chen'), findsOneWidget);
    expect(tester.widget<TextField>(find.byKey(const ValueKey('mastodon-search-field'))).controller!.text, 'design');
    expect(tester.takeException(), isNull);
  });

  test('new search wins over a slower old response, including old errors', () async {
    final client = _DelayedSearch();
    final store = MastodonSearchStore(client, ['https://studio.example']);
    addTearDown(store.destroy);
    addTearDown(client.httpClient.close);
    final old = store.search('old');
    final current = store.search('current');
    client.replies['current']!.complete(MastodonSearchPage(posts: samplePosts));
    await current;
    client.replies['old']!.completeError(Exception('late error'));
    await old;
    expect(store.state.query, 'current');
    expect(store.state.error, isNull);
    expect(store.state.results.posts, samplePosts);
    expect(store.state.tab, 1);
  });

  test('a search result arriving after disposal is ignored', () async {
    final client = _DelayedSearch();
    final store = MastodonSearchStore(client, ['https://studio.example']);
    addTearDown(client.httpClient.close);
    final pending = store.search('pending');
    await store.destroy();
    client.replies['pending']!.complete(const MastodonSearchPage());
    await pending;
    expect(store.state.loading, isTrue);
  });
}
