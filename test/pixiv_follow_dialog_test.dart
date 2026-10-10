import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/pixiv/pixiv_client.dart';
import 'package:xta/plugins/pixiv/pixiv_detail_author.dart';
import 'package:xta/plugins/pixiv/pixiv_follow_dialog.dart';
import 'package:xta/plugins/pixiv/pixiv_user_card.dart';
import 'package:xta/plugins/pixiv/pixiv_user_profile.dart';

import 'support/pixiv_reader_harness.dart';
import 'support/pixiv_social_fakes.dart';

Widget _card({bool followed = false}) => Scaffold(
  body: ListView(
    padding: const EdgeInsets.all(16),
    children: [
      PixivUserPreviewCard(
        preview: pixivPreviewOf(9, name: 'Painter', followed: followed),
      ),
    ],
  ),
);

Future<void> _longPressFollow(WidgetTester tester, int id) async {
  await tester.longPress(find.byKey(ValueKey('pixiv-follow-$id')));
  await settlePixiv(tester);
}

bool _privateSwitch(WidgetTester tester) =>
    tester.widget<SwitchListTile>(find.byKey(const ValueKey('pixiv-follow-dialog-private'))).value;

class _FailingDetailApi extends FakePixivSocialApi {
  bool failing = true;

  @override
  Future<PixivFollowDetail> followDetail(int userId) async {
    calls.add('followDetail:$userId');
    if (failing) throw PixivException(PixivErrorKind.network, 'offline');
    return const PixivFollowDetail(isFollowed: false);
  }
}

void main() {
  group('PixivFollowDraftStore', () {
    test('starts on Private for a creator not followed yet, and on the follow for one followed', () async {
      final fresh = PixivFollowDraftStore(() async => const PixivFollowDetail(isFollowed: false));
      await fresh.load();
      expect(fresh.state!.private, isTrue);

      final public = PixivFollowDraftStore(() async => const PixivFollowDetail(isFollowed: true, restrict: 'public'));
      await public.load();
      expect(public.state!.private, isFalse);
      public.setPrivate(true);
      expect(public.state!.private, isTrue);
      expect(public.state!.detail.isFollowed, isTrue);
      fresh.destroy();
      public.destroy();
    });
  });

  group('long-pressing a follow button', () {
    testWidgets('loads the follow detail and switches a public follow to private', (tester) async {
      final api = FakePixivSocialApi(followDetailResult: const PixivFollowDetail(isFollowed: true, restrict: 'public'));
      final harness = await pumpPixiv(tester, _card(followed: true), extraProviders: [api.provider]);
      await _longPressFollow(tester, 9);
      expect(api.calls, ['followDetail:9']);
      expect(find.byKey(const ValueKey('pixiv-follow-dialog')), findsOneWidget);
      expect(_privateSwitch(tester), isFalse);
      expect(find.byKey(const ValueKey('pixiv-follow-dialog-unfollow')), findsOneWidget);
      expect(find.widgetWithText(FilledButton, 'Save'), findsOneWidget);

      await tester.tap(find.byKey(const ValueKey('pixiv-follow-dialog-private')));
      await tester.pump();
      expect(_privateSwitch(tester), isTrue);
      await tester.tap(find.byKey(const ValueKey('pixiv-follow-dialog-confirm')));
      await settlePixiv(tester);
      expect(harness.client.calls, ['follow:9:private']);
      expect(find.byKey(const ValueKey('pixiv-follow-dialog')), findsNothing);
      expect(find.text('Unfollow'), findsOneWidget);
      await disposePixiv(tester);
    });

    testWidgets('follows a new creator privately unless the switch is turned off', (tester) async {
      final api = FakePixivSocialApi();
      final harness = await pumpPixiv(tester, _card(), extraProviders: [api.provider]);
      await _longPressFollow(tester, 9);
      expect(_privateSwitch(tester), isTrue);
      expect(find.byKey(const ValueKey('pixiv-follow-dialog-unfollow')), findsNothing);
      expect(
        find.descendant(of: find.byKey(const ValueKey('pixiv-follow-dialog-confirm')), matching: find.text('Follow')),
        findsOneWidget,
      );
      await tester.tap(find.byKey(const ValueKey('pixiv-follow-dialog-confirm')));
      await settlePixiv(tester);
      expect(harness.client.calls, ['follow:9:private']);

      await tester.tap(find.byKey(const ValueKey('pixiv-follow-9')));
      await settlePixiv(tester);
      await _longPressFollow(tester, 9);
      await tester.tap(find.byKey(const ValueKey('pixiv-follow-dialog-private')));
      await tester.pump();
      await tester.tap(find.byKey(const ValueKey('pixiv-follow-dialog-confirm')));
      await settlePixiv(tester);
      expect(harness.client.calls, ['follow:9:private', 'unfollow:9', 'follow:9:public']);
      await disposePixiv(tester);
    });

    testWidgets('unfollows from the dialog, and Cancel changes nothing', (tester) async {
      final api = FakePixivSocialApi(
        followDetailResult: const PixivFollowDetail(isFollowed: true, restrict: 'private'),
      );
      final harness = await pumpPixiv(tester, _card(followed: true), extraProviders: [api.provider]);
      await _longPressFollow(tester, 9);
      await tester.tap(find.text('Cancel'));
      await settlePixiv(tester);
      expect(harness.client.calls, isEmpty);

      await _longPressFollow(tester, 9);
      expect(_privateSwitch(tester), isTrue);
      await tester.tap(find.byKey(const ValueKey('pixiv-follow-dialog-unfollow')));
      await settlePixiv(tester);
      expect(harness.client.calls, ['unfollow:9']);
      expect(find.text('Follow'), findsOneWidget);
      await disposePixiv(tester);
    });

    testWidgets('a detail that will not load offers a retry and no confirm', (tester) async {
      final api = _FailingDetailApi();
      await pumpPixiv(tester, _card(), extraProviders: [api.provider]);
      await _longPressFollow(tester, 9);
      final confirm = tester.widget<FilledButton>(find.byKey(const ValueKey('pixiv-follow-dialog-confirm')));
      expect(confirm.onPressed, isNull);
      expect(find.text('Retry'), findsOneWidget);

      api.failing = false;
      await tester.tap(find.text('Retry'));
      await settlePixiv(tester);
      expect(api.calls, ['followDetail:9', 'followDetail:9']);
      expect(_privateSwitch(tester), isTrue);
      await disposePixiv(tester);
    });
  });

  group('the work detail author row', () {
    Widget author() => Scaffold(body: PixivDetailAuthor(illust: pixivWork()));

    testWidgets('has a follow button that follows the author', (tester) async {
      final harness = await pumpPixiv(tester, author());
      final button = find.byKey(const ValueKey('pixiv-follow-42'));
      expect(tester.getSize(button).height, greaterThanOrEqualTo(48));
      await tester.tap(button);
      await settlePixiv(tester);
      expect(harness.client.calls, ['follow:42:public']);
      expect(find.text('Unfollow'), findsOneWidget);
      await disposePixiv(tester);
    });

    testWidgets('offers no follow on the reader\'s own work', (tester) async {
      await pumpPixiv(
        tester,
        author(),
        client: (prefs) {
          prefs.set(optionPluginPixivUserId, 42);
          return FakePixivClient(prefs);
        },
      );
      expect(find.byKey(const ValueKey('pixiv-follow-42')), findsNothing);
      expect(find.text('Mika'), findsOneWidget);
      await disposePixiv(tester);
    });

    testWidgets('large text on a narrow phone puts the button under the name', (tester) async {
      await pumpPixiv(tester, author(), size: const Size(320, 640), textScale: 2);
      expect(tester.takeException(), isNull);
      final name = tester.getRect(find.text('Mika'));
      final button = tester.getRect(find.byKey(const ValueKey('pixiv-follow-42')));
      expect(button.top, greaterThan(name.bottom));
      await disposePixiv(tester);
    });
  });
}
