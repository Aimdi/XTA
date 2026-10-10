import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/pixiv/pixiv_illust_screen.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_settings.dart';

import 'support/pixiv_bookmark_fakes.dart';
import 'support/pixiv_reader_harness.dart';

Future<PixivHarness> _pumpWork(
  WidgetTester tester,
  FakePixivBookmarkApi api, {
  bool bookmarkAfterSave = true,
  PixivIllust? work,
}) {
  final illust = work ?? pixivWork();
  return pumpPixiv(
    tester,
    PixivIllustScreen(illust: illust),
    client: (prefs) {
      prefs.set(optionPluginPixivBookmarkAfterDownload, bookmarkAfterSave);
      return FakePixivClient(prefs, detail: illust);
    },
    extraProviders: [api.provider],
  );
}

Future<void> _saveAll(WidgetTester tester) async {
  await tester.tap(find.byKey(const ValueKey('pixiv-illust-menu')));
  await settlePixiv(tester);
  await tester.tap(find.byKey(const ValueKey('pixiv-illust-menu-downloadAll')));
  await settlePixiv(tester);
}

void main() {
  group('bookmark after saving', () {
    testWidgets('saving a page from the work bookmarks it with the defaults', (tester) async {
      final api = FakePixivBookmarkApi();
      final harness = await _pumpWork(tester, api);
      await tester.tap(find.byKey(const ValueKey('pixiv-illust-download')));
      await settlePixiv(tester);
      expect(harness.downloader.pages, [0]);
      expect(api.writes, ['add:120:public:']);
      await disposePixiv(tester);
    });

    testWidgets('saving every page bookmarks once', (tester) async {
      final api = FakePixivBookmarkApi();
      final harness = await _pumpWork(tester, api);
      await _saveAll(tester);
      expect(harness.downloader.requests, hasLength(8));
      expect(api.writes, ['add:120:public:']);
      await disposePixiv(tester);
    });

    testWidgets('a save that never started or the setting off bookmarks nothing', (tester) async {
      final api = FakePixivBookmarkApi();
      final harness = await _pumpWork(tester, api);
      harness.downloader.folder = null;
      await _saveAll(tester);
      await disposePixiv(tester);

      final off = FakePixivBookmarkApi();
      await _pumpWork(tester, off, bookmarkAfterSave: false);
      await tester.tap(find.byKey(const ValueKey('pixiv-illust-download')));
      await settlePixiv(tester);
      expect([...api.writes, ...off.writes], isEmpty);
      await disposePixiv(tester);
    });

    testWidgets('an already bookmarked work is left as it is', (tester) async {
      final api = FakePixivBookmarkApi();
      final work = pixivWork();
      await _pumpWork(tester, api, work: work.copyWith(isBookmarked: true));
      await tester.tap(find.byKey(const ValueKey('pixiv-illust-download')));
      await settlePixiv(tester);
      expect(api.writes, isEmpty);
      await disposePixiv(tester);
    });
  });

  testWidgets('the Bookmarking settings switch their preferences', (tester) async {
    final harness = await pumpPixiv(tester, const PixivSettingsScreen(), size: const Size(390, 4800));
    for (final (key, pref) in [
      ('pixiv-default-private-bookmark', optionPluginPixivDefaultPrivateBookmark),
      ('pixiv-auto-tag-bookmarks', optionPluginPixivAutoTagBookmarks),
      ('pixiv-follow-after-bookmark', optionPluginPixivFollowAfterBookmark),
      ('pixiv-download-after-bookmark', optionPluginPixivDownloadAfterBookmark),
      ('pixiv-bookmark-after-download', optionPluginPixivBookmarkAfterDownload),
      ('pixiv-haptics', optionPluginPixivHaptics),
    ]) {
      await tester.ensureVisible(find.byKey(ValueKey(key)));
      await tester.tap(find.byKey(ValueKey(key)));
      await tester.pump();
      expect(harness.prefs.get<bool>(pref), isTrue, reason: key);
    }
    expect(find.text('Bookmarking'), findsOneWidget);
    await disposePixiv(tester);
  });
}
