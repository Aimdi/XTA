import 'package:flutter/material.dart';
import 'package:flutter_staggered_grid_view/flutter_staggered_grid_view.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/pixiv/pixiv_avatar.dart';
import 'package:xta/plugins/pixiv/pixiv_detail_viewer.dart';
import 'package:xta/plugins/pixiv/pixiv_grid.dart';
import 'package:xta/plugins/pixiv/pixiv_grid_columns.dart';
import 'package:xta/plugins/pixiv/pixiv_illust_screen.dart';
import 'package:xta/plugins/pixiv/pixiv_image.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_quality.dart';
import 'package:xta/plugins/pixiv/pixiv_reader_bar.dart';
import 'package:xta/plugins/plugin_gallery_layout.dart';
import 'package:xta/subscriptions/widgets/fallback_avatar.dart';

import 'support/pixiv_reader_harness.dart';

/// [pixivWork] as a list sends it, with its large image beside the medium preview.
PixivIllust _listed({int id = 120, int pages = 3, bool ai = false}) {
  final work = pixivWork(id: id, pages: pages, ai: ai);
  return PixivIllust(
    id: work.id,
    title: work.title,
    caption: '',
    type: work.type,
    thumbnailUrl: work.thumbnailUrl,
    largeUrl: work.pageUrls.first,
    pageUrls: work.pageUrls,
    originalUrls: work.originalUrls,
    pageThumbUrls: work.pageThumbUrls,
    pageCount: pages,
    width: work.width,
    height: work.height,
    userId: work.userId,
    userName: work.userName,
    userAccount: work.userAccount,
    isAi: ai,
  );
}

Set<String> _shownUrls(WidgetTester tester, [Finder? within]) => {
  for (final image in tester.widgetList<PixivNetworkImage>(
    within == null
        ? find.byType(PixivNetworkImage)
        : find.descendant(of: within, matching: find.byType(PixivNetworkImage)),
  ))
    image.url,
};

Widget _grid(List<PixivIllust> works) => Scaffold(body: PixivIllustGrid(illusts: works));

int _columns(WidgetTester tester) =>
    (tester.widget<SliverMasonryGrid>(find.byType(SliverMasonryGrid)).gridDelegate
            as SliverSimpleGridDelegateWithFixedCrossAxisCount)
        .crossAxisCount;

void main() {
  group('image sizes', () {
    test('each place starts at its default and ignores a size it does not offer', () {
      expect(pixivQuality(null, PixivQualitySlot.feed), PixivImageQuality.medium);
      expect(pixivQuality(null, PixivQualitySlot.detail), PixivImageQuality.large);
      expect(pixivQuality(null, PixivQualitySlot.reader), PixivImageQuality.large);
      final prefs = PrefServiceCache(
        cache: {optionPluginPixivQualityReader: 'medium', optionPluginPixivQualityDetail: 'original'},
      );
      expect(pixivQuality(prefs, PixivQualitySlot.reader), PixivImageQuality.large);
      expect(pixivQuality(prefs, PixivQualitySlot.detail), PixivImageQuality.original);
    });

    test('a tile shows the medium preview, or the large image when Pixiv sent one', () {
      final work = _listed();
      expect(pixivTileUrl(work, PixivImageQuality.medium), work.thumbnailUrl);
      expect(pixivTileUrl(work, PixivImageQuality.large), work.largeUrl);
      expect(pixivTileUrl(pixivWork(), PixivImageQuality.large), pixivWork().thumbnailUrl);
    });

    test('a page comes in each size, falling back to what Pixiv did send', () {
      final work = _listed();
      expect(pixivPageUrl(work, 1, PixivImageQuality.medium), work.pageThumbUrls[1]);
      expect(pixivPageUrl(work, 1, PixivImageQuality.large), work.pageUrls[1]);
      expect(pixivPageUrl(work, 1, PixivImageQuality.original), work.originalUrls[1]);
      const bare = PixivIllust(
        id: 1,
        title: '',
        caption: '',
        type: 'illust',
        thumbnailUrl: 'https://i.pximg.net/t.jpg',
        pageCount: 1,
        userId: 2,
        userName: '',
        userAccount: '',
      );
      for (final quality in PixivImageQuality.values) {
        expect(pixivPageUrl(bare, 0, quality), bare.thumbnailUrl, reason: quality.name);
      }
    });

    testWidgets('the grid size setting changes which file tiles load', (tester) async {
      final work = _listed();
      await pumpPixiv(
        tester,
        _grid([work]),
        client: (prefs) {
          prefs.set(optionPluginPixivQualityFeed, PixivImageQuality.large.name);
          return FakePixivClient(prefs);
        },
      );
      expect(_shownUrls(tester), {work.largeUrl});
      await disposePixiv(tester);
    });

    testWidgets('the work page size setting changes which file the detail loads', (tester) async {
      final work = _listed();
      await pumpPixiv(
        tester,
        PixivIllustScreen(illust: work),
        client: (prefs) {
          prefs.set(optionPluginPixivQualityDetail, PixivImageQuality.original.name);
          return FakePixivClient(prefs, detail: work);
        },
      );
      final viewer = find.byType(PixivDetailViewer);
      expect(_shownUrls(tester, viewer), containsAll([work.thumbnailUrl, work.originalUrls.first]));
      expect(_shownUrls(tester, viewer), isNot(contains(work.pageUrls.first)));
      await disposePixiv(tester);
    });
  });

  group('grid columns', () {
    test('automatic follows the gallery layout; a picked count applies to its orientation only', () {
      const scaler = TextScaler.noScaling;
      final prefs = PrefServiceCache(cache: {optionPluginPixivGridColumnsLandscape: 4});
      expect(pixivGridColumns(390, Orientation.portrait, scaler, prefs), pluginGalleryColumns(390, scaler));
      expect(pixivGridColumns(900, Orientation.landscape, scaler, prefs), 4);
      expect(pixivGridColumns(900, Orientation.landscape, scaler, null), pluginGalleryColumns(900, scaler));
    });

    test('a picked count never squeezes tiles below 96dp, and an unknown count means automatic', () {
      const scaler = TextScaler.noScaling;
      final prefs = PrefServiceCache(cache: {optionPluginPixivGridColumnsPortrait: 4});
      expect(pixivGridColumns(300, Orientation.portrait, scaler, prefs), 3);
      expect(pixivGridColumns(60, Orientation.portrait, scaler, prefs), 1);
      prefs.set(optionPluginPixivGridColumnsPortrait, 7);
      expect(pixivGridColumns(390, Orientation.portrait, scaler, prefs), pluginGalleryColumns(390, scaler));
    });

    testWidgets('a grid takes the picked count and follows when it changes', (tester) async {
      final harness = await pumpPixiv(
        tester,
        _grid([for (var id = 1; id <= 6; id++) _listed(id: id)]),
        client: (prefs) {
          prefs.set(optionPluginPixivGridColumnsPortrait, 3);
          return FakePixivClient(prefs);
        },
      );
      expect(_columns(tester), 3);
      await harness.prefs.set(optionPluginPixivGridColumnsPortrait, 4);
      await tester.pump();
      expect(_columns(tester), 4);
      await harness.prefs.set(optionPluginPixivGridColumnsPortrait, pixivGridColumnsAuto);
      await tester.pump();
      expect(_columns(tester), 2);
      await disposePixiv(tester);
    });
  });

  testWidgets('the AI badge shows until switched off, live in the grid', (tester) async {
    final harness = await pumpPixiv(tester, _grid([_listed(ai: true)]));
    expect(find.text('AI'), findsOneWidget);
    await harness.prefs.set(optionPluginPixivAiBadge, false);
    await tester.pump();
    expect(find.text('AI'), findsNothing);
    await disposePixiv(tester);
  });

  group('loading states', () {
    testWidgets('a failed tile retries on tap while the rest of it still opens the work', (tester) async {
      await pumpPixiv(tester, _grid([_listed()]));
      final retry = find.byKey(const ValueKey('pixiv-image-retry'));
      expect(retry, findsOneWidget);
      expect(find.byTooltip('Retry'), findsOneWidget);

      await tester.tap(retry);
      await tester.pump();
      expect(find.byType(PixivIllustScreen), findsNothing);
      await settlePixiv(tester);

      await tester.tap(find.text(_listed().title));
      await settlePixiv(tester);
      expect(find.byType(PixivIllustScreen), findsOneWidget);
      await disposePixiv(tester);
    });

    testWidgets('a detail page that failed offers retry over its poster', (tester) async {
      await pumpPixiv(tester, PixivIllustScreen(illust: _listed()));
      expect(
        find.descendant(of: find.byType(PixivDetailViewer), matching: find.byKey(const ValueKey('pixiv-image-retry'))),
        findsOneWidget,
      );
      expect(find.text('Failed to load image'), findsNothing);
      await disposePixiv(tester);
    });

    testWidgets("an image that fails never shows the library's English text; an avatar falls back to initials", (
      tester,
    ) async {
      await pumpPixiv(
        tester,
        const Scaffold(
          body: Column(
            children: [
              SizedBox.square(dimension: 120, child: PixivNetworkImage(url: 'https://i.pximg.net/c/a/1.jpg')),
              PixivAvatar(userId: 5, name: 'Mika', url: 'https://i.pximg.net/user-profile/img/5.jpg'),
            ],
          ),
        ),
      );
      expect(find.text('Failed to load image'), findsNothing);
      expect(find.byIcon(Icons.broken_image_outlined), findsOneWidget);
      expect(find.descendant(of: find.byType(PixivAvatar), matching: find.byType(FallbackAvatar)), findsOneWidget);
      await disposePixiv(tester);
    });

    test('reader progress is the share of bytes that arrived, unknown without a size', () {
      expect(pixivLoadProgress(null), isNull);
      expect(pixivLoadProgress(const ImageChunkEvent(cumulativeBytesLoaded: 10, expectedTotalBytes: null)), isNull);
      expect(pixivLoadProgress(const ImageChunkEvent(cumulativeBytesLoaded: 25, expectedTotalBytes: 100)), 0.25);
      expect(pixivLoadProgress(const ImageChunkEvent(cumulativeBytesLoaded: 120, expectedTotalBytes: 100)), 1);
    });
  });
}
