import 'package:flutter/material.dart';
import 'package:flutter/semantics.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/pixiv/pixiv_illust_screen.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_mute_store.dart';
import 'package:xta/plugins/pixiv/pixiv_user_card.dart';
import 'package:xta/plugins/pixiv/pixiv_user_screen.dart';

import 'support/pixiv_reader_harness.dart';
import 'support/pixiv_social_fakes.dart';

PixivIllust _work(int id, {bool r18 = false, bool ai = false}) => PixivIllust(
  id: id,
  title: 'Work $id',
  caption: '',
  type: 'illust',
  thumbnailUrl: 'https://i.pximg.net/c/540x540_70/img-master/$id.jpg',
  pageCount: 1,
  userId: 9,
  userName: 'Painter',
  userAccount: 'painter',
  isR18: r18,
  isAi: ai,
);

PixivUserPreview _preview({bool followed = false}) => PixivUserPreview(
  user: PixivUser(id: 9, name: 'Painter', account: 'painter', comment: '', isFollowed: followed),
  illusts: [_work(1), _work(2, r18: true), _work(3, ai: true), _work(4), _work(5), _work(6)],
);

Widget _host(PixivUserPreview preview) => Scaffold(
  body: ListView(
    padding: const EdgeInsets.all(16),
    children: [PixivUserPreviewCard(preview: preview)],
  ),
);

Iterable<int> _shownWorks(WidgetTester tester) => [
  for (final id in [1, 2, 3, 4, 5, 6])
    if (tester.any(find.byKey(ValueKey('pixiv-user-card-work-$id')))) id,
];

void main() {
  testWidgets('shows three works, leaving out R-18, AI-hidden and muted ones', (tester) async {
    final harness = await pumpPixiv(tester, _host(_preview()));
    expect(find.text('Painter'), findsOneWidget);
    expect(find.text('@painter'), findsOneWidget);
    expect(_shownWorks(tester), [1, 3, 4]);

    final mute = Provider.of<PixivMuteStore>(tester.element(find.byType(PixivUserPreviewCard)), listen: false);
    await harness.prefs.set(optionPluginPixivHideAi, true);
    await mute.muteIllust(4);
    await tester.pump();
    expect(_shownWorks(tester), [1, 5, 6]);
    await disposePixiv(tester);
  });

  testWidgets('Show R-18 lets R-18 previews through', (tester) async {
    await pumpPixiv(
      tester,
      _host(_preview()),
      client: (prefs) {
        prefs.set(optionPluginPixivShowR18, true);
        return FakePixivClient(prefs);
      },
    );
    expect(_shownWorks(tester), [1, 2, 3]);
    await disposePixiv(tester);
  });

  testWidgets('the follow button is a full touch target and follows, then unfollows', (tester) async {
    final harness = await pumpPixiv(tester, _host(_preview()));
    final button = find.byKey(const ValueKey('pixiv-follow-9'));
    final size = tester.getSize(button);
    expect(size.height, greaterThanOrEqualTo(48));
    expect(size.width, greaterThanOrEqualTo(48));

    await tester.tap(button);
    await settlePixiv(tester);
    expect(find.text('Unfollow'), findsOneWidget);
    await tester.tap(button);
    await settlePixiv(tester);
    expect(find.text('Follow'), findsOneWidget);
    expect(harness.client.calls, ['follow:9:public', 'unfollow:9']);
    await disposePixiv(tester);
  });

  testWidgets('tapping the card opens the profile and a work opens that work', (tester) async {
    await pumpPixiv(tester, _host(_preview(followed: true)));
    expect(find.text('Unfollow'), findsOneWidget);

    await tester.tap(find.byKey(const ValueKey('pixiv-user-card-work-3')));
    await settlePixiv(tester);
    expect(find.byWidgetPredicate((w) => w is PixivIllustScreen && w.illust.id == 3), findsOneWidget);

    await tester.pageBack();
    await settlePixiv(tester);
    await tester.tap(find.text('@painter'));
    await settlePixiv(tester);
    expect(find.byWidgetPredicate((w) => w is PixivUserScreen && w.userId == 9), findsOneWidget);
    await disposePixiv(tester);
  });

  testWidgets('large text on a narrow phone does not overflow', (tester) async {
    await pumpPixiv(tester, _host(_preview()), size: const Size(320, 640), textScale: 2);
    expect(tester.takeException(), isNull);
    await disposePixiv(tester);
  });

  group('follow state', () {
    testWidgets('survives the list rebuilding its rows with the follow it loaded', (tester) async {
      late StateSetter rebuild;
      var tick = 0;
      final heard = <bool>[];
      await pumpPixiv(
        tester,
        Scaffold(
          body: StatefulBuilder(
            builder: (context, setState) {
              rebuild = setState;
              return ListView(
                children: [
                  Text('page $tick'),
                  PixivUserPreviewCard(preview: _preview(), onFollowChanged: heard.add),
                ],
              );
            },
          ),
        ),
      );
      await tester.tap(find.byKey(const ValueKey('pixiv-follow-9')));
      await settlePixiv(tester);
      expect(heard, [true]);

      rebuild(() => tick++);
      await tester.pump();
      expect(find.text('Unfollow'), findsOneWidget);
      await disposePixiv(tester);
    });

    testWidgets('survives the row being recycled and is shared with every card of the creator', (tester) async {
      late StateSetter rebuild;
      var shown = true;
      final harness = await pumpPixiv(
        tester,
        Scaffold(
          body: StatefulBuilder(
            builder: (context, setState) {
              rebuild = setState;
              return ListView(
                children: [
                  if (shown) PixivUserPreviewCard(key: const ValueKey('first'), preview: _preview()),
                  PixivUserPreviewCard(key: const ValueKey('second'), preview: _preview()),
                ],
              );
            },
          ),
        ),
      );
      await tester.tap(find.byKey(const ValueKey('pixiv-follow-9')).first);
      await settlePixiv(tester);
      expect(find.text('Unfollow'), findsNWidgets(2));

      rebuild(() => shown = false);
      await tester.pump();
      rebuild(() => shown = true);
      await tester.pump();
      expect(find.text('Unfollow'), findsNWidgets(2));
      expect(harness.client.calls, ['follow:9:public']);
      await disposePixiv(tester);
    });
  });

  testWidgets('a screen reader can open each preview work on its own', (tester) async {
    final semantics = tester.ensureSemantics();
    await pumpPixiv(tester, _host(_preview()));
    final work = find.semantics.byLabel('Work 3');
    expect(work, findsOne);
    expect(work.evaluate().single.getSemanticsData().hasAction(SemanticsAction.tap), isTrue);

    tester.semantics.tap(work);
    await settlePixiv(tester);
    expect(find.byWidgetPredicate((w) => w is PixivIllustScreen && w.illust.id == 3), findsOneWidget);
    semantics.dispose();
    await disposePixiv(tester);
  });

  testWidgets('the profile counts read as whole phrases, singular where the count is one', (tester) async {
    final api = FakePixivSocialApi(profile: pixivProfileOf(illusts: 1));
    await pumpPixiv(tester, const PixivUserScreen(userId: 9), extraProviders: [api.provider]);
    expect(find.text('1 work'), findsOneWidget);
    expect(find.text('1.5K following'), findsOneWidget);
    expect(find.text('2 My pixiv friends'), findsOneWidget);
    await disposePixiv(tester);
  });

  group('header layout', () {
    Rect nameRect(WidgetTester tester) => tester.getRect(find.text('Painter'));
    Rect buttonRect(WidgetTester tester) => tester.getRect(find.byKey(const ValueKey('pixiv-follow-9')));

    testWidgets('puts the follow button under the name when beside it would squeeze the name', (tester) async {
      await pumpPixiv(tester, _host(_preview()), size: const Size(320, 640), textScale: 2);
      expect(nameRect(tester).width, greaterThanOrEqualTo(7 * 14 * 2));
      expect(buttonRect(tester).top, greaterThan(nameRect(tester).bottom));
      await disposePixiv(tester);
    });

    testWidgets('keeps the follow button beside the name when there is room', (tester) async {
      await pumpPixiv(tester, _host(_preview()), size: const Size(700, 800));
      expect(buttonRect(tester).left, greaterThan(nameRect(tester).right));
      expect(buttonRect(tester).top, lessThan(nameRect(tester).bottom));
      await disposePixiv(tester);
    });
  });
}
