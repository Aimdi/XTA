import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/booru/booru_display.dart';
import 'package:xta/plugins/booru/booru_engines.dart';
import 'package:xta/plugins/booru/booru_grid.dart';
import 'package:xta/plugins/booru/booru_image.dart';
import 'package:xta/plugins/booru/booru_models.dart';
import 'package:xta/plugins/booru/booru_parse.dart';

const _cdn = 'https://cdn.donmai.us';

/// A Danbooru post with its `media_asset.variants`, in the shape the task
/// names and Boorusama reads; not recorded from the live host.
BooruPost _post({double aspect = 0.75, String fileExt = 'png'}) => parseBooruPosts(
  [
    {
      'id': 1,
      'rating': 'g',
      'image_width': (1000 * aspect).round(),
      'image_height': 1000,
      'file_ext': fileExt,
      'file_url': '$_cdn/original/a.$fileExt',
      'large_file_url': '$_cdn/sample/sample-a.jpg',
      'preview_file_url': '$_cdn/180x180/a.jpg',
      'media_asset': {
        'variants': [
          {'type': '180x180', 'url': '$_cdn/180x180/a.jpg', 'width': 135, 'height': 180, 'file_ext': 'jpg'},
          {'type': '360x360', 'url': '$_cdn/360x360/a.jpg', 'width': 270, 'height': 360, 'file_ext': 'jpg'},
          {'type': '720x720', 'url': '$_cdn/720x720/a.webp', 'width': 540, 'height': 720, 'file_ext': 'webp'},
          {'type': 'sample', 'url': '$_cdn/sample/sample-a.jpg', 'width': 850, 'height': 1133, 'file_ext': 'jpg'},
          {'type': 'original', 'url': '$_cdn/original/a.$fileExt', 'width': 3000, 'height': 4000, 'file_ext': fileExt},
        ],
      },
    },
  ],
  engine: BooruEngine.danbooru,
  host: 'https://danbooru.donmai.us',
).single;

void main() {
  test('a tile gets the smallest still variant that fills it, never the original', () {
    final post = _post();

    expect(post.tileUrlFor(120), '$_cdn/180x180/a.jpg');
    expect(post.tileUrlFor(200), '$_cdn/360x360/a.jpg');
    expect(post.tileUrlFor(400), '$_cdn/720x720/a.webp');
    expect(post.tileUrlFor(800), '$_cdn/sample/sample-a.jpg');
    expect(post.tileUrlFor(2000), post.catalogUrl, reason: 'wider than every variant: the picture tiles use today');
  });

  test("a video's tile gets a still variant, not its video sample", () {
    final post = parseBooruPosts(
      [
        {
          'id': 2,
          'file_ext': 'mp4',
          'file_url': '$_cdn/original/b.mp4',
          'preview_file_url': '$_cdn/180x180/b.jpg',
          'media_asset': {
            'variants': [
              {'type': '360x360', 'url': '$_cdn/360x360/b.jpg', 'width': 360, 'height': 203, 'file_ext': 'jpg'},
              {'type': 'sample', 'url': '$_cdn/sample/b.webm', 'width': 850, 'height': 478, 'file_ext': 'webm'},
            ],
          },
        },
      ],
      engine: BooruEngine.danbooru,
      host: 'https://danbooru.donmai.us',
    ).single;

    expect(post.tileUrlFor(300), '$_cdn/360x360/b.jpg');
    expect(post.tileUrlFor(600), post.catalogUrl);
  });

  test('hosts without variants keep the picture tiles use today', () {
    final post = parseBooruPosts(
      [
        {'id': 3, 'file_url': '$_cdn/original/c.png', 'large_file_url': '$_cdn/sample/c.jpg'},
      ],
      engine: BooruEngine.danbooru,
      host: 'https://danbooru.donmai.us',
    ).single;

    expect(post.tileUrlFor(200), post.catalogUrl);
  });

  testWidgets('a grid tile asks for the variant its width needs, and decodes at that width', (tester) async {
    tester.view.physicalSize = const Size(400, 800);
    tester.view.devicePixelRatio = 2;
    addTearDown(tester.view.reset);
    final post = _post();

    await tester.pumpWidget(
      MaterialApp(
        localizationsDelegates: const [
          L10n.delegate,
          GlobalMaterialLocalizations.delegate,
          GlobalWidgetsLocalizations.delegate,
        ],
        home: Align(
          alignment: Alignment.topLeft,
          child: SizedBox(
            width: 150,
            child: BooruPostTile(post: post, onOpen: () {}),
          ),
        ),
      ),
    );

    final image = tester.widget<BooruNetworkImage>(find.byType(BooruNetworkImage));
    expect(image.url, '$_cdn/720x720/a.webp', reason: '150 dp at 2x needs 300 px; the 360 variant is 270 px wide');
    expect(image.cacheWidth, 300);
  });

  test('small thumbnails still mean the preview', () {
    final post = _post();
    const display = BooruDisplay(smallThumbnails: true);
    expect(display.tileUrl(post, pixelWidth: 500), post.thumbnailUrl);
  });
}
