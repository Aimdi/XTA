import 'package:extended_image/extended_image.dart' show ExtendedRawImage;
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/subscriptions/widgets/avatar_mosaic.dart';
import 'package:xta/subscriptions/widgets/fallback_avatar.dart';
import 'package:xta/ui/capped_network_image.dart';
import 'package:xta/ui/image_fade_in.dart';
import 'package:xta/user.dart';

// Answers every image fetch with a picture, whatever the network.
import 'support/bluesky_reading_images.dart' show installBlueskyReadingImages;

Widget _app(Widget child, {bool reduceMotion = false}) => MaterialApp(
  home: MediaQuery(
    data: MediaQueryData(disableAnimations: reduceMotion),
    child: Scaffold(body: Center(child: child)),
  ),
);

/// Lets the fetch and the decode finish on the real clock, until an image
/// starts to fade in.
Future<void> _untilFetched(WidgetTester tester) async {
  for (var wait = 0; wait < 100 && find.byType(ImageFadeIn).evaluate().isEmpty; wait++) {
    await tester.runAsync(() => Future<void>.delayed(const Duration(milliseconds: 20)));
    await tester.pump();
  }
  expect(find.byType(ImageFadeIn), findsOneWidget, reason: 'the image arrived and is fading in');
}

double _imageOpacity(WidgetTester tester) =>
    tester.widget<Opacity>(find.descendant(of: find.byType(ImageFadeIn), matching: find.byType(Opacity)).first).opacity;

void main() {
  testWidgets('a fetched image waits on its placeholder, not a spinner, then fades in over it', (tester) async {
    await installBlueskyReadingImages(tester);
    const placeholder = ColoredBox(key: ValueKey('placeholder'), color: Colors.grey);
    final image = _app(
      const SizedBox(
        width: 200,
        height: 100,
        child: CappedNetworkImage(url: 'https://pbs.twimg.com/media/poster.jpg', placeholder: placeholder),
      ),
    );

    await tester.pumpWidget(image);
    expect(find.byType(CircularProgressIndicator), findsNothing);
    expect(find.byKey(const ValueKey('placeholder')), findsOneWidget);

    await _untilFetched(tester);
    expect(_imageOpacity(tester), lessThan(1));
    expect(find.byKey(const ValueKey('placeholder')), findsOneWidget, reason: 'under the image as it fades in');
    await tester.pumpAndSettle();
    expect(_imageOpacity(tester), 1);
    expect(find.byKey(const ValueKey('placeholder')), findsNothing, reason: 'gone once the image is in');

    // Scrolled away and back: drawn from memory, at once.
    await tester.pumpWidget(const SizedBox());
    await tester.pumpWidget(image);
    expect(tester.widget<ExtendedRawImage>(find.byType(ExtendedRawImage)).image, isNotNull);
    expect(find.byType(ImageFadeIn), findsNothing);
  });

  testWidgets('a fetched avatar fades in; one in memory is drawn at once', (tester) async {
    await installBlueskyReadingImages(tester);
    final avatar = _app(const UserAvatar(uri: 'https://pbs.twimg.com/profile_images/1/avatar_normal.jpg'));

    await tester.pumpWidget(avatar);
    await _untilFetched(tester);
    expect(_imageOpacity(tester), lessThan(1));
    await tester.pumpAndSettle();
    expect(_imageOpacity(tester), 1);

    await tester.pumpWidget(const SizedBox());
    await tester.pumpWidget(avatar);
    expect(tester.widget<ExtendedRawImage>(find.byType(ExtendedRawImage)).image, isNotNull);
    expect(find.byType(ImageFadeIn), findsNothing);
  });

  testWidgets('a group face fades in over its monogram', (tester) async {
    await installBlueskyReadingImages(tester);
    await tester.pumpWidget(
      _app(
        const AvatarMosaic(
          extent: 64,
          members: [GroupMemberPreview(id: '1', name: 'Ice Universe', avatarUrl: 'https://pbs.twimg.com/1.jpg')],
          groupName: 'Phones',
          groupColor: Colors.teal,
          accent: Colors.blue,
          ringColor: Colors.black,
        ),
      ),
    );
    expect(find.byType(FallbackAvatar), findsOneWidget, reason: 'the monogram while the photo loads');

    await _untilFetched(tester);
    expect(find.byType(FallbackAvatar), findsOneWidget, reason: 'under the photo as it fades in');
    await tester.pumpAndSettle();
    expect(find.byType(FallbackAvatar), findsNothing);
  });

  testWidgets('with reduced motion an arriving image is there at once', (tester) async {
    await tester.pumpWidget(
      _app(
        const ImageFadeIn(
          placeholder: ColoredBox(key: ValueKey('placeholder'), color: Colors.grey),
          image: SizedBox(key: ValueKey('image'), width: 40, height: 40),
        ),
        reduceMotion: true,
      ),
    );
    expect(_imageOpacity(tester), 1);
    expect(find.byKey(const ValueKey('placeholder')), findsNothing);
  });
}
