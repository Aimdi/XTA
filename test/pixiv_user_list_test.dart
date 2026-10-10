import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:provider/provider.dart';
import 'package:xta/plugins/pixiv/pixiv_following_screen.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_mute_store.dart';
import 'package:xta/plugins/pixiv/pixiv_user_list_screen.dart';

import 'support/pixiv_reader_harness.dart';
import 'support/pixiv_social_fakes.dart';

FakePixivSocialApi _api() => FakePixivSocialApi(
  following: {
    'public': [pixivPreviewOf(21, name: 'Public one', followed: true), pixivPreviewOf(22, name: 'Public two')],
    'private': [pixivPreviewOf(23, name: 'Quiet one', followed: true)],
  },
  followers: [pixivPreviewOf(31, name: 'A follower')],
);

PixivMuteStore _mute(WidgetTester tester) =>
    Provider.of<PixivMuteStore>(tester.element(find.byType(PixivUserList)), listen: false);

void main() {
  group('PixivUserListStore', () {
    test('skips a page whose creators were all muted and keeps each creator once', () async {
      final pages = <String?, PixivPage<PixivUserPreview>>{
        null: PixivPage([pixivPreviewOf(1)], nextUrl: 'p2'),
        'p2': PixivPage([pixivPreviewOf(1), pixivPreviewOf(2)], nextUrl: 'p3'),
        'p3': PixivPage([pixivPreviewOf(3)]),
      };
      const mute = PixivMuteState(authorIds: {1, 2});
      final store = PixivUserListStore(
        ({nextUrl}) async => pages[nextUrl]!,
        filter: (previews) => pixivVisibleUsers(previews, mute),
      );
      await store.refresh();
      expect(store.state.map((preview) => preview.user.id), [3]);
      expect(store.hasMore, isFalse);
      store.destroy();
    });

    test('the reader\'s own list asks for their id', () async {
      final api = _api();
      final page = await pixivUserListLoader(api, PixivUserListKind.following, restrict: 'private')();
      expect(api.calls, ['following:7:private']);
      expect(page.items.map((preview) => preview.user.id), [23]);
    });
  });

  testWidgets('Home\'s people icon lists your follows and switches to the private ones', (tester) async {
    final api = _api();
    await pumpPixiv(tester, const PixivFollowingScreen(), extraProviders: [api.provider]);
    expect(find.text('Following'), findsOneWidget);
    expect(api.calls, ['following:7:public']);
    expect(find.text('Public one'), findsOneWidget);
    expect(find.text('Unfollow'), findsOneWidget);

    await tester.tap(find.text('Private'));
    await settlePixiv(tester);
    expect(api.calls, ['following:7:public', 'following:7:private']);
    expect(find.text('Quiet one'), findsOneWidget);
    expect(find.text('Public one'), findsNothing);
    await disposePixiv(tester);
  });

  testWidgets('someone else\'s following list has no public / private switch', (tester) async {
    final api = _api();
    await pumpPixiv(
      tester,
      const PixivUserListScreen(kind: PixivUserListKind.following, userId: 9),
      extraProviders: [api.provider],
    );
    expect(find.byKey(const ValueKey('pixiv-user-list-restrict')), findsNothing);
    expect(api.calls, ['following:9:public']);
    await disposePixiv(tester);
  });

  testWidgets('the followers list shows who follows them', (tester) async {
    final api = _api();
    await pumpPixiv(
      tester,
      const PixivUserListScreen(kind: PixivUserListKind.followers, userId: 9),
      extraProviders: [api.provider],
    );
    expect(find.text('Followers'), findsOneWidget);
    expect(api.calls, ['followers:9']);
    expect(find.text('A follower'), findsOneWidget);
    await disposePixiv(tester);
  });

  testWidgets('each card unfollows in place and offers Add to group', (tester) async {
    final harness = await pumpPixiv(tester, const PixivFollowingScreen(), extraProviders: [_api().provider]);
    final group = find.byKey(const ValueKey('pixiv-user-list-group-21'));
    expect(tester.getSize(group).shortestSide, greaterThanOrEqualTo(48));
    expect(find.byTooltip('Add to group'), findsNWidgets(2));

    await tester.tap(find.byKey(const ValueKey('pixiv-follow-21')));
    await settlePixiv(tester);
    expect(harness.client.calls, ['unfollow:21']);
    expect(find.text('Public one'), findsOneWidget);
    await disposePixiv(tester);
  });

  testWidgets('a creator muted while the list is open disappears from it', (tester) async {
    await pumpPixiv(tester, const PixivFollowingScreen(), extraProviders: [_api().provider]);
    await _mute(tester).muteAuthor(21);
    await tester.pump();
    expect(find.text('Public one'), findsNothing);
    expect(find.text('Public two'), findsOneWidget);
    await disposePixiv(tester);
  });

  testWidgets('an empty list says so', (tester) async {
    await pumpPixiv(tester, const PixivFollowingScreen(), extraProviders: [FakePixivSocialApi().provider]);
    expect(find.text('Not following anyone yet'), findsOneWidget);
    await disposePixiv(tester);
  });

  group('pages', () {
    FakePixivSocialApi manyFollowers() =>
        FakePixivSocialApi(followers: [for (var id = 1; id <= 25; id++) pixivPreviewOf(100 + id)], pageSize: 10);

    Future<void> openFollowers(WidgetTester tester) async {
      final navigator = tester.state<NavigatorState>(find.byType(Navigator));
      unawaited(
        navigator.push(
          MaterialPageRoute<void>(
            builder: (_) => const PixivUserListScreen(kind: PixivUserListKind.followers, userId: 9),
          ),
        ),
      );
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 500));
    }

    Future<void> leave(WidgetTester tester, Completer<void> gate) async {
      tester.state<NavigatorState>(find.byType(Navigator)).pop();
      await tester.pumpAndSettle();
      expect(find.byType(PixivUserList), findsNothing);
      gate.complete();
      await tester.pump(const Duration(seconds: 1));
      expect(tester.takeException(), isNull);
    }

    testWidgets('scrolling to the end brings the next pages', (tester) async {
      final api = manyFollowers();
      await pumpPixiv(
        tester,
        const PixivUserListScreen(kind: PixivUserListKind.followers, userId: 9),
        extraProviders: [api.provider],
      );
      expect(api.calls, ['followers:9']);
      expect(find.text('Painter 125'), findsNothing);

      await tester.scrollUntilVisible(find.text('Painter 125'), 600, scrollable: find.byType(Scrollable).first);
      await settlePixiv(tester);
      expect(api.calls, ['followers:9', 'followers:9@next1', 'followers:9@next2']);
      await disposePixiv(tester);
    });

    testWidgets('leaving before the first page arrives is safe', (tester) async {
      final gate = Completer<void>();
      final api = manyFollowers()..gate = gate.future;
      await pumpPixiv(tester, const Scaffold(body: SizedBox()), extraProviders: [api.provider]);
      await openFollowers(tester);
      expect(api.calls, ['followers:9']);
      await leave(tester, gate);
      await disposePixiv(tester);
    });

    testWidgets('leaving while the next page loads is safe', (tester) async {
      final api = manyFollowers();
      await pumpPixiv(tester, const Scaffold(body: SizedBox()), extraProviders: [api.provider]);
      await openFollowers(tester);
      await settlePixiv(tester);
      final gate = Completer<void>();
      api.gate = gate.future;
      await tester.drag(find.byType(Scrollable).first, const Offset(0, -3000));
      await tester.pump();
      expect(api.calls.last, 'followers:9@next1');
      await leave(tester, gate);
      await disposePixiv(tester);
    });
  });

  testWidgets('large text on a narrow phone does not overflow', (tester) async {
    await pumpPixiv(
      tester,
      const PixivFollowingScreen(),
      extraProviders: [_api().provider],
      size: const Size(320, 640),
      textScale: 2,
    );
    expect(tester.takeException(), isNull);
    await disposePixiv(tester);
  });
}
