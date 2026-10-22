import 'package:dart_twitter_api/twitter_api.dart' show Media;
import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/tweet/_media.dart';
import 'package:xta/tweet/tweet_photo_flight.dart';

/// X's photo zoom: a photo flies from its tile into the viewer and back.
void main() {
  Media photo(String id) => Media()
    ..type = 'photo'
    ..mediaUrlHttps = 'https://pbs.twimg.com/media/$id.jpg';

  Media notPhoto() => Media()..type = 'unknown';

  Future<void> pump(WidgetTester tester, List<List<Media>> grids) async {
    await tester.pumpWidget(
      PrefService(
        service: PrefServiceCache(),
        child: MaterialApp(
          localizationsDelegates: const [
            L10n.delegate,
            GlobalMaterialLocalizations.delegate,
            GlobalWidgetsLocalizations.delegate,
          ],
          supportedLocales: L10n.delegate.supportedLocales,
          home: Scaffold(
            body: ListView(
              children: [
                for (final media in grids)
                  SizedBox(
                    width: 360,
                    child: TweetMedia(sensitive: false, username: 'fixture', media: media),
                  ),
              ],
            ),
          ),
        ),
      ),
    );
    await tester.pump();
  }

  List<Object> tags(WidgetTester tester, [Finder? within]) => [
    for (final hero in tester.widgetList<Hero>(
      within == null ? find.byType(Hero) : find.descendant(of: within, matching: find.byType(Hero)),
    ))
      hero.tag,
  ];

  testWidgets('each photo tile has a tag of its own, even with its post shown twice', (tester) async {
    final media = [photo('a'), photo('b')];
    await pump(tester, [media, media]);

    final all = tags(tester);
    expect(all, hasLength(4));
    expect(all.toSet(), hasLength(4), reason: 'two grids of one post never share a tag');
  });

  testWidgets("only photos fly: a video's pooled player cannot be in two places", (tester) async {
    await pump(tester, [
      [photo('a'), notPhoto()],
    ]);

    expect(tags(tester), hasLength(1));
  });

  testWidgets('the viewer opened from a tile carries that tile\'s tag', (tester) async {
    await pump(tester, [
      [photo('a'), photo('b')],
    ]);
    final tileTags = tags(tester);

    await tester.tap(find.byType(Hero).first);
    await tester.pump(const Duration(milliseconds: 250));
    // The viewer's photo never loads here, and its spinner never settles.
    await tester.pump(const Duration(milliseconds: 400));

    final viewer = find.byType(TweetMediaView);
    expect(viewer, findsOneWidget);
    expect(tags(tester, viewer), contains(tileTags.first));
  });

  test("a flight's crop opens from the tile's to the whole photo", () {
    const box = Size(300, 600);
    expect(photoFlightSize(box, 2, 0), const Size(1200, 600), reason: 'covering the box');
    expect(photoFlightSize(box, 2, 1), const Size(300, 150), reason: 'contained in it');
    expect(photoFlightSize(box, 2, 0.5), const Size(750, 375));
    expect(photoFlightSize(const Size(400, 100), 1, 1), const Size(100, 100));
  });
}
