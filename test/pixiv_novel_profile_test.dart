import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/pixiv/pixiv_client.dart';
import 'package:xta/plugins/pixiv/pixiv_comment_models.dart';
import 'package:xta/plugins/pixiv/pixiv_comments_api.dart';
import 'package:xta/plugins/pixiv/pixiv_comments_screen.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_mute_store.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_card.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_models.dart';
import 'package:xta/plugins/pixiv/pixiv_user_card.dart';
import 'package:xta/plugins/pixiv/pixiv_user_header.dart';
import 'package:xta/plugins/pixiv/pixiv_user_screen.dart';
import 'package:xta/plugins/pixiv/pixiv_view_state.dart';
import 'package:xta/utils/json.dart';

import 'support/pixiv_comments_fake.dart';
import 'support/pixiv_novel_fakes.dart';
import 'support/pixiv_reader_harness.dart';
import 'support/pixiv_social_fakes.dart';

FakePixivNovelApi _novelApi() => FakePixivNovelApi(
  PixivClient(PrefServiceCache()),
  userNovelList: [pixivNovel(id: 1, title: 'Their tale', userId: 9)],
  bookmarkNovels: [pixivNovel(id: 2, title: 'Their pick')],
);

FakePixivSocialApi _social({int novels = 0}) => FakePixivSocialApi(profile: pixivProfileOf(novels: novels));

Future<FakePixivNovelApi> _pumpProfile(WidgetTester tester, {int novels = 0, int? ownId, double textScale = 1}) async {
  final api = _novelApi();
  await pumpPixiv(
    tester,
    const PixivUserScreen(userId: 9),
    textScale: textScale,
    size: textScale > 1 ? const Size(320, 640) : const Size(390, 844),
    client: (prefs) {
      if (ownId != null) prefs.set(optionPluginPixivUserId, ownId);
      return FakePixivClient(prefs);
    },
    extraProviders: [
      ...api.providers,
      _social(novels: novels).provider,
    ],
  );
  return api;
}

Finder _profileTab(String id) => find.byKey(ValueKey('pixiv-profile-tab-$id'));

Future<void> _openTab(WidgetTester tester, String id) async {
  await tester.drag(find.byType(PixivUserHeader), const Offset(0, -600));
  await settlePixiv(tester);
  await tester.ensureVisible(_profileTab(id));
  await tester.pumpAndSettle();
  await tester.tap(_profileTab(id));
  await settlePixiv(tester);
}

void main() {
  group('the profile Novels tab', () {
    testWidgets('shows for a creator with novels: their novels, then their novel bookmarks', (tester) async {
      final api = await _pumpProfile(tester, novels: 4);
      expect(_profileTab('novels'), findsOneWidget);

      await _openTab(tester, 'novels');
      expect(api.calls, contains('userNovels:9'));
      expect(find.widgetWithText(PixivNovelCard, 'Their tale'), findsOneWidget);
      expect(tester.getSize(find.byKey(const ValueKey('pixiv-profile-novel-list'))).height, greaterThanOrEqualTo(48));

      await tester.tap(
        find.descendant(of: find.byKey(const ValueKey('pixiv-profile-novel-list')), matching: find.text('Bookmarks')),
      );
      await settlePixiv(tester);
      expect(api.calls.last, 'bookmarks:9:public');
      expect(find.widgetWithText(PixivNovelCard, 'Their pick'), findsOneWidget);
      await disposePixiv(tester);
    });

    testWidgets('is left out for a creator without novels, but shown on the reader\'s own profile', (tester) async {
      await _pumpProfile(tester);
      expect(_profileTab('novels'), findsNothing);
      await disposePixiv(tester);

      final api = await _pumpProfile(tester, ownId: 9);
      expect(_profileTab('novels'), findsOneWidget);
      await _openTab(tester, 'novels');
      expect(find.text('No novels yet'), findsNothing, reason: 'the fake has one');
      expect(api.calls, contains('userNovels:9'));
      await disposePixiv(tester);
    });

    testWidgets('fits a narrow phone at large text', (tester) async {
      await _pumpProfile(tester, novels: 4, textScale: 2);
      await _openTab(tester, 'novels');
      expect(find.byKey(const ValueKey('pixiv-profile-novel-list')), findsOneWidget);
      expect(tester.takeException(), isNull);
      await disposePixiv(tester);
    });

    testWidgets('a creator shown anyway past their mute still shows their own novels', (tester) async {
      final api = await _pumpProfile(tester, novels: 1);
      final mute = Provider.of<PixivMuteStore>(tester.element(find.byType(PixivUserScreen)), listen: false);
      await mute.muteAuthor(9, name: 'Mika');
      await settlePixiv(tester);
      await tester.tap(find.text('Show anyway'));
      await settlePixiv(tester);
      await _openTab(tester, 'novels');
      expect(api.calls, contains('userNovels:9'));
      expect(find.widgetWithText(PixivNovelCard, 'Their tale'), findsOneWidget);
      await disposePixiv(tester);
    });
  });

  group('novel previews in creator cards', () {
    test('parse, filter and keep three, skipping what does not parse', () {
      Json novel(int id, {int xRestrict = 0, int ai = 1, int userId = 5}) => Json({
        'id': id,
        'title': 'Tale $id',
        'x_restrict': xRestrict,
        'novel_ai_type': ai,
        'user': {'id': userId},
      });
      final raw = [novel(1), const Json('junk'), novel(2, xRestrict: 1), novel(3, ai: 2), novel(4), novel(5), novel(6)];
      List<int> ids(List<PixivNovel> novels) => [for (final novel in novels) novel.id];

      expect(ids(pixivVisiblePreviewNovels(raw, mute: PixivMuteState.empty, showR18: false, hideAi: false)), [1, 3, 4]);
      expect(ids(pixivVisiblePreviewNovels(raw, mute: PixivMuteState.empty, showR18: true, hideAi: true)), [1, 2, 4]);
      expect(
        ids(pixivVisiblePreviewNovels(raw, mute: const PixivMuteState(novelIds: {1}), showR18: false, hideAi: true)),
        [4, 5, 6],
      );
    });

    testWidgets('fall back to the works when the creator has no novel to show', (tester) async {
      await pumpPixiv(
        tester,
        Scaffold(
          body: ListView(
            children: [
              PixivUserPreviewCard(
                preview: PixivUserPreview(
                  user: const PixivUser(id: 5, name: 'Writer', account: 'writer', comment: ''),
                  illusts: [pixivWork(id: 50, pages: 1, type: 'illust')],
                  novels: const [
                    Json({'id': 61, 'x_restrict': 1}),
                  ],
                ),
                previews: PixivContentMode.novel,
              ),
            ],
          ),
        ),
      );
      expect(find.byKey(const ValueKey('pixiv-user-card-work-50')), findsOneWidget);
      expect(find.byKey(const ValueKey('pixiv-user-card-novel-61')), findsNothing);
      await disposePixiv(tester);
    });
  });

  testWidgets('a novel\'s long press offers its comments and opens them for the novel', (tester) async {
    final comments = FakePixivCommentsApi({
      FakePixivCommentsApi.commentsKey(const PixivCommentTarget.novel(900)): [
        PixivCommentPage([pixivTestComment(1, text: 'Lovely prose')]),
      ],
    });
    await pumpPixiv(
      tester,
      Scaffold(
        body: ListView(children: [PixivNovelCard(novel: pixivNovel(comments: 7))]),
      ),
      extraProviders: [
        ..._novelApi().providers,
        Provider<PixivCommentsApi>.value(value: comments),
      ],
    );

    await tester.longPress(find.byType(PixivNovelCard));
    await settlePixiv(tester);
    final entry = find.byKey(const ValueKey('plugin-post-action-pixiv-novel-comments'));
    expect(find.descendant(of: entry, matching: find.text('View comments (7)')), findsOneWidget);

    await tester.tap(entry);
    await settlePixiv(tester);
    expect(
      find.byWidgetPredicate((w) => w is PixivCommentsScreen && w.target == const PixivCommentTarget.novel(900)),
      findsOneWidget,
    );
    expect(comments.calls, ['novel:900@first']);
    expect(find.text('Lovely prose'), findsOneWidget);
    await disposePixiv(tester);
  });
}
