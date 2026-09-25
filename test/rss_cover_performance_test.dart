import 'package:extended_image/extended_image.dart';
import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/rss/rss_card.dart';
import 'package:xta/plugins/rss/rss_models.dart';
import 'package:xta/plugins/rss/rss_store.dart';

void main() {
  testWidgets('RSS thumbnail decodes at its painted width', (tester) async {
    tester.view.physicalSize = const Size(640, 1200);
    tester.view.devicePixelRatio = 2;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);

    final read = RssReadStore(PrefServiceCache());
    addTearDown(read.destroy);

    await tester.pumpWidget(
      Provider<RssReadStore>.value(
        value: read,
        child: MaterialApp(
          localizationsDelegates: const [
            L10n.delegate,
            GlobalMaterialLocalizations.delegate,
            GlobalWidgetsLocalizations.delegate,
            GlobalCupertinoLocalizations.delegate,
          ],
          supportedLocales: L10n.delegate.supportedLocales,
          home: Scaffold(
            body: RssItemCard(
              item: const RssItem(
                id: 'post-1',
                feedId: 'feed-1',
                feedTitle: 'Example feed',
                title: 'Article with a cover',
                link: 'https://example.com/article',
                imageUrl: 'https://example.com/large-cover.jpg',
              ),
            ),
          ),
        ),
      ),
    );

    final image = tester.widget<ExtendedImage>(find.byType(ExtendedImage));
    expect(image.image, isA<ExtendedResizeImage>());
    expect((image.image as ExtendedResizeImage).width, 176);
  });
}
