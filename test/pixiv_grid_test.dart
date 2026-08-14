import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_bookmark_store.dart';
import 'package:xta/plugins/pixiv/pixiv_grid.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';

const _wide = PixivIllust(
  id: 1,
  title: 'GLADIIA',
  caption: '',
  type: 'illust',
  thumbnailUrl: 'https://example.test/wide.jpg',
  pageCount: 1,
  userId: 2,
  userName: 'Candy',
  userAccount: 'candy',
  width: 1600,
  height: 900,
);

Widget _tileApp() {
  return Provider<PixivBookmarkStore>.value(
    value: PixivBookmarkStore(),
    child: MaterialApp(
      localizationsDelegates: const [
        L10n.delegate,
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
      ],
      supportedLocales: L10n.delegate.supportedLocales,
      home: const Scaffold(
        body: Align(
          alignment: Alignment.topLeft,
          child: SizedBox(width: 180, child: PixivIllustTile(illust: _wide)),
        ),
      ),
    ),
  );
}

void main() {
  testWidgets('a landscape tile is only as tall as the image and caption', (
    tester,
  ) async {
    await tester.pumpWidget(_tileApp());

    final size = tester.getSize(find.byType(PixivIllustTile));
    // Width 180, clamped ratio 1.6 → image 112.5, plus a short caption.
    // The old related-works grid used childAspectRatio 0.72 (cell 250 tall)
    // and left a blank slab under wide art.
    expect(size.width, 180);
    expect(size.height, lessThan(200));
    expect(size.height, greaterThan(120));
  });
}
