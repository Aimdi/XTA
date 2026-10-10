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
