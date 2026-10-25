import 'package:extended_image/extended_image.dart' show ExtendedImageGesturePageView;
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/main.dart' show xtaLocalizationsDelegates;
import 'package:xta/tweet/_media.dart';
import 'package:xta/ui/rolling_count.dart';

import 'support/fixture_images.dart';

void main() {
  testWidgets('swiping to the next photo rolls the counter to its number', (tester) async {
    failImageDiskCache();
    ignoreFixtureImageFailures();
    await tester.pumpWidget(
      PrefService(
        service: PrefServiceCache(cache: const {}),
        child: MaterialApp(
          localizationsDelegates: xtaLocalizationsDelegates,
          supportedLocales: const [Locale('en')],
          home: TweetMediaView(
            initialIndex: 0,
            media: [for (var i = 1; i <= 3; i++) createMediaFromUrl('https://pbs.twimg.com/media/$i.jpg', null)],
            username: 'reader',
            tweetMedia: false,
          ),
        ),
      ),
    );
    await settleFixtureImages(tester);
    final counter = find.byType(RollingCount);
    expect(tester.widget<RollingCount>(counter).text, '1');

    await tester.fling(find.byType(ExtendedImageGesturePageView), const Offset(-400, 0), 2000);
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 300));
    expect(tester.widget<RollingCount>(counter).text, '2');
    await settleFixtureImages(tester);
    expect(find.bySemanticsLabel(RegExp(r'2/3$')), findsOneWidget);
    await disposeFixtureScreen(tester);
  });
}
