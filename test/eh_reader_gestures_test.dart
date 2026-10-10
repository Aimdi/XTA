import 'package:extended_image/extended_image.dart';
import 'package:flutter/gestures.dart';
import 'package:flutter_test/flutter_test.dart';

import 'support/bluesky_reading_images.dart';
import 'support/eh_reader_harness.dart';
import 'support/fixture_images.dart';

// Its own file: the image HTTP client is created once per isolate, and the
// reader tests that never load an image would create it first.
void main() {
  testWidgets('a loaded page still turns on a tap, and zooms on a double tap', (tester) async {
    await installBlueskyReadingImages(tester);
    await pumpEhReader(tester, previews: ehFixturePreviews(20), failImages: false);
    await settleBlueskyReadingImages(tester);

    await tester.tapAt(const Offset(195, 422));
    await tester.pump(const Duration(milliseconds: 50));
    await tester.tapAt(const Offset(195, 422));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 400));
    final zoomed = tester.state<ExtendedImageGestureState>(find.byType(ExtendedImageGesture).first);
    expect(zoomed.gestureDetails?.totalScale, 2.5);
    expect(ehReaderCounter(1), findsOneWidget, reason: 'a double tap never turns the page');

    await tester.tapAt(const Offset(370, 422));
    await tester.pump(kDoubleTapTimeout);
    await tester.pump(const Duration(milliseconds: 400));
    expect(ehReaderCounter(2), findsOneWidget, reason: 'the tap waits out a possible double tap, then turns');
    await disposeFixtureScreen(tester);
  });
}
