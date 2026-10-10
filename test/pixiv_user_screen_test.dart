import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/pixiv/pixiv_grid.dart';
import 'package:xta/plugins/pixiv/pixiv_mute_store.dart';
import 'package:xta/plugins/pixiv/pixiv_user_header.dart';
import 'package:xta/plugins/pixiv/pixiv_user_list_screen.dart';
import 'package:xta/plugins/pixiv/pixiv_user_profile.dart';
import 'package:xta/plugins/pixiv/pixiv_user_screen.dart';

import 'support/pixiv_reader_harness.dart';
import 'support/pixiv_social_fakes.dart';

FakePixivSocialApi _api({int illusts = 3, int manga = 5}) => FakePixivSocialApi(
  profile: pixivProfileOf(illusts: illusts, manga: manga, comment: 'Draws cats.'),
  works: {
    PixivWorkType.illust: [pixivWork(id: 301, pages: 1)],
    PixivWorkType.manga: [pixivWork(id: 302, pages: 3)],
  },
  bookmarks: [pixivWork(id: 401, pages: 1)],
  following: {
    'public': [pixivPreviewOf(21, name: 'Followed one')],
  },
  followers: [pixivPreviewOf(31, name: 'A follower')],
);

Future<PixivHarness> _pump(WidgetTester tester, FakePixivSocialApi api, {int? ownId, Size? size, double? text}) =>
    pumpPixiv(
      tester,
      const PixivUserScreen(userId: 9),
      extraProviders: [api.provider],
      size: size ?? const Size(390, 844),
      textScale: text ?? 1,
      client: (prefs) {
        if (ownId != null) prefs.set(optionPluginPixivUserId, ownId);
        return FakePixivClient(prefs);
      },
    );

Finder _tile(int id) => find.byWidgetPredicate((w) => w is PixivIllustTile && w.illust.id == id);

PixivMuteStore _mute(WidgetTester tester) =>
    Provider.of<PixivMuteStore>(tester.element(find.byType(PixivUserScreen)), listen: false);

/// Scrolls the header away when the tab bar sits below the fold, then brings
/// the tab into view along the scrolling tab bar and taps it.
Future<void> _tab(WidgetTester tester, String id) async {
  final tab = find.byKey(ValueKey('pixiv-profile-tab-$id'));
  if (tab.evaluate().isEmpty) {
    await tester.drag(find.byType(PixivUserHeader), const Offset(0, -600));
    await settlePixiv(tester);
  }
  await tester.ensureVisible(tab);
  await tester.pumpAndSettle();
  await tester.tap(tab);
  await settlePixiv(tester);
}

Future<void> _menu(WidgetTester tester, String id) async {
  await tester.tap(find.byKey(const ValueKey('pixiv-profile-menu')));
  await settlePixiv(tester);
  await tester.tap(find.byKey(ValueKey('pixiv-profile-menu-$id')));
  await settlePixiv(tester);
}

String? _mockClipboard(WidgetTester tester, void Function(String text) onCopy) {
  tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(SystemChannels.platform, (call) async {
    if (call.method == 'Clipboard.setData') onCopy((call.arguments as Map)['text'] as String);
    return null;
  });
  addTearDown(() => tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(SystemChannels.platform, null));
  return null;
}

void main() {
  testWidgets('the header shows the bio and counts, and the tabs are Works, Bookmarks, Following and Info', (
    tester,
  ) async {
    final api = _api();
    await _pump(tester, api);
    expect(find.text('Draws cats.'), findsOneWidget);
    expect(find.text('8 works'), findsOneWidget);
    for (final label in ['Works', 'Bookmarks', 'Following', 'Info']) {
      expect(find.descendant(of: find.byType(TabBar), matching: find.text(label)), findsOneWidget);
    }
    await disposePixiv(tester);
  });

  testWidgets('Works starts on manga when there is more of it and switches to illustrations', (tester) async {
    final api = _api();
    await _pump(tester, api);
    expect(api.calls, contains('works:9:manga'));
    expect(_tile(302), findsOneWidget);

    await tester.tap(find.text('Illustrations'));
    await settlePixiv(tester);
    expect(api.calls.last, 'works:9:illust');
    expect(_tile(301), findsOneWidget);
    await disposePixiv(tester);
  });

  testWidgets('a creator with illustrations only gets no switch', (tester) async {
    final api = _api(manga: 0);
    await _pump(tester, api);
    expect(find.byKey(const ValueKey('pixiv-profile-work-type')), findsNothing);
    expect(api.calls, contains('works:9:illust'));
    await disposePixiv(tester);
  });

  testWidgets('Bookmarks lists the public bookmarks and Following lists whom they follow', (tester) async {
    final api = _api();
    await _pump(tester, api);
    await _tab(tester, 'bookmarks');
    expect(api.calls, contains('bookmarks:9:public'));
    expect(_tile(401), findsOneWidget);

    await _tab(tester, 'following');
    expect(api.calls, contains('following:9:public'));
    expect(find.text('Followed one'), findsOneWidget);
    expect(find.byKey(const ValueKey('pixiv-user-list-restrict')), findsNothing);
    await disposePixiv(tester);
  });

  testWidgets('Info copies the user ID and opens the followers list', (tester) async {
    String? copied;
    _mockClipboard(tester, (text) => copied = text);
    final api = _api();
    await _pump(tester, api);
    await _tab(tester, 'info');
    expect(find.text('User ID'), findsOneWidget);
    expect(find.text('example.com/mika'), findsNothing);
    expect(find.text('https://example.com/mika'), findsOneWidget);
    expect(find.text('@mika_draws'), findsOneWidget);

    await tester.tap(find.byKey(const ValueKey('pixiv-profile-info-id')));
    await settlePixiv(tester);
    expect(copied, '9');
    expect(find.text('User ID copied'), findsOneWidget);

    await tester.tap(find.byKey(const ValueKey('pixiv-profile-info-followers')));
    await settlePixiv(tester);
    expect(find.byWidgetPredicate((w) => w is PixivUserListScreen && w.kind == PixivUserListKind.followers), findsOne);
    expect(api.calls, contains('followers:9'));
    expect(find.text('A follower'), findsOneWidget);
    await disposePixiv(tester);
  });

  testWidgets('the following count opens the following list', (tester) async {
    final api = _api();
    await _pump(tester, api);
    await tester.tap(find.byKey(const ValueKey('pixiv-profile-following-count')));
    await settlePixiv(tester);
    expect(
      find.byWidgetPredicate((w) => w is PixivUserListScreen && w.kind == PixivUserListKind.following && w.userId == 9),
      findsOne,
    );
    await disposePixiv(tester);
  });

  group('muting', () {
    testWidgets('a muted creator shows a placeholder that can show anyway', (tester) async {
      await _pump(tester, _api());
      await _mute(tester).muteAuthor(9);
      await settlePixiv(tester);
      expect(find.byKey(const ValueKey('pixiv-profile-muted')), findsOneWidget);
      expect(find.byType(TabBar), findsNothing);

      await tester.tap(find.text('Show anyway'));
      await settlePixiv(tester);
      expect(find.byType(TabBar), findsOneWidget);
      await disposePixiv(tester);
    });

    testWidgets('Unmute on the placeholder brings the profile back for good', (tester) async {
      await _pump(tester, _api());
      final mute = _mute(tester);
      await mute.muteAuthor(9);
      await settlePixiv(tester);
      await tester.tap(find.widgetWithText(OutlinedButton, 'Unmute'));
      await settlePixiv(tester);
      expect(mute.state.authorIds, isEmpty);
      expect(find.byType(TabBar), findsOneWidget);
      await disposePixiv(tester);
    });

    testWidgets('Mute author in the menu asks first, then shows the placeholder', (tester) async {
      await _pump(tester, _api());
      await _menu(tester, 'mute');
      await tester.tap(find.widgetWithText(FilledButton, 'Mute author'));
      await settlePixiv(tester);
      expect(_mute(tester).state.authorIds, {9});
      expect(find.byKey(const ValueKey('pixiv-profile-muted')), findsOneWidget);
      await disposePixiv(tester);
    });
  });

  testWidgets('Copy info puts the name, account and link on the clipboard', (tester) async {
    String? copied;
    _mockClipboard(tester, (text) => copied = text);
    await _pump(tester, _api());
    await _menu(tester, 'copyInfo');
    expect(copied, 'Mika\n@mika\nhttps://www.pixiv.net/users/9');
    expect(find.text('Profile info copied'), findsOneWidget);
    await disposePixiv(tester);
  });

  testWidgets('Follow privately in the menu follows with restrict=private', (tester) async {
    final api = _api();
    final harness = await _pump(tester, api);
    await _menu(tester, 'followPrivately');
    expect(api.calls, contains('followDetail:9'));
    expect(tester.widget<SwitchListTile>(find.byType(SwitchListTile)).value, isTrue);
    await tester.tap(find.byKey(const ValueKey('pixiv-follow-dialog-confirm')));
    await settlePixiv(tester);
    expect(harness.client.calls, ['follow:9:private']);
    expect(find.text('Unfollow'), findsOneWidget);
    await disposePixiv(tester);
  });

  testWidgets('your own profile offers no follow button and no private follow', (tester) async {
    await _pump(tester, _api(), ownId: 9);
    expect(find.byKey(const ValueKey('pixiv-follow-9')), findsNothing);
    await tester.tap(find.byKey(const ValueKey('pixiv-profile-menu')));
    await settlePixiv(tester);
    expect(find.byKey(const ValueKey('pixiv-profile-menu-followPrivately')), findsNothing);
    expect(find.byKey(const ValueKey('pixiv-profile-menu-mute')), findsNothing);
    expect(find.byKey(const ValueKey('pixiv-profile-menu-copyInfo')), findsOneWidget);
    await disposePixiv(tester);
  });

  testWidgets('the header image and avatar say how to save them', (tester) async {
    final semantics = tester.ensureSemantics();
    final api = FakePixivSocialApi(profile: pixivProfileOf(background: 'https://i.pximg.net/background/9.jpg'));
    await _pump(tester, api);
    expect(find.byKey(const ValueKey('pixiv-profile-banner')), findsOneWidget);
    expect(find.bySemanticsLabel('Save profile picture'), findsOneWidget);
    final avatar = tester.getSize(find.byKey(const ValueKey('pixiv-profile-avatar')));
    expect(avatar.shortestSide, greaterThanOrEqualTo(48));
    semantics.dispose();
    await disposePixiv(tester);
  });

  testWidgets('large text on a narrow phone does not overflow', (tester) async {
    final api = FakePixivSocialApi(
      profile: pixivProfileOf(manga: 4, premium: true, background: 'https://i.pximg.net/background/9.jpg'),
    );
    await _pump(tester, api, size: const Size(320, 640), text: 2);
    expect(tester.takeException(), isNull);
    await _tab(tester, 'info');
    expect(tester.takeException(), isNull);
    await disposePixiv(tester);
  });
}
