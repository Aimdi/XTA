import 'package:dart_twitter_api/twitter_api.dart' hide Size;
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/client/client.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/profile/media_grid/broadcast_media_card.dart';
import 'package:xta/profile/media_grid/media_grid_items/media_grid_item.dart';
import 'package:xta/status.dart';
import 'package:xta/ui/x_look_theme.dart';

const _reviewRender = ValueKey('broadcast-review-render');

BroadcastGridItem _broadcast({bool space = false}) {
  final tweet = TweetWithCard()
    ..idStr = '123'
    ..fullText = 'An evening by the coast https://t.co/live'
    ..createdAt = DateTime.utc(2026, 9, 9);
  return BroadcastGridItem(
    tweet: tweet,
    tweetId: '123',
    username: 'coast',
    thumbnailUrl: '',
    aspectRatio: 16 / 9,
    mediaIndex: 0,
    media: Media.fromJson({'type': 'video'}),
    broadcastId: space ? null : 'broadcast-123',
    spaceId: space ? 'space-123' : null,
  );
}

Future<void> _pumpCard(
  WidgetTester tester, {
  required BroadcastGridItem item,
  double textScale = 1,
  bool rtl = false,
  bool dark = false,
  ValueChanged<RouteSettings>? onRoute,
}) => tester.pumpWidget(
  RepaintBoundary(
    key: _reviewRender,
    child: MaterialApp(
      debugShowCheckedModeBanner: false,
      theme: dark ? xLookLightsOutTheme(null) : xLookLightTheme(null),
      localizationsDelegates: const [
        L10n.delegate,
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
      ],
      supportedLocales: L10n.delegate.supportedLocales,
      onGenerateRoute: (settings) {
        onRoute?.call(settings);
        return MaterialPageRoute<void>(builder: (_) => const Scaffold());
      },
      home: Builder(
        builder: (context) => MediaQuery(
          data: MediaQuery.of(context).copyWith(textScaler: TextScaler.linear(textScale)),
          child: Directionality(
            textDirection: rtl ? TextDirection.rtl : TextDirection.ltr,
            child: Scaffold(
              body: SingleChildScrollView(
                child: BroadcastMediaCard(
                  item: item,
                  preview: const AspectRatio(
                    aspectRatio: 16 / 9,
                    child: DecoratedBox(
                      decoration: BoxDecoration(
                        color: Color(0xFF233F4D),
                        borderRadius: BorderRadius.all(Radius.circular(16)),
                      ),
                      child: Center(child: Icon(Icons.play_circle_outline, size: 52, color: Colors.white)),
                    ),
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    ),
  ),
);

void main() {
  setUpAll(() async {
    autoUpdateGoldenFiles = true;
    await (FontLoader('Inter')..addFont(rootBundle.load('assets/fonts/Inter-Regular.ttf'))).load();
    await (FontLoader('MaterialIcons')..addFont(rootBundle.load('fonts/MaterialIcons-Regular.otf'))).load();
  });
  testWidgets('broadcast cards identify the stream and keep open-post context', (tester) async {
    tester.view.physicalSize = const Size(320, 844);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    RouteSettings? route;
    final item = _broadcast();
    await _pumpCard(tester, item: item, textScale: 1.8, onRoute: (value) => route = value);
    await tester.pumpAndSettle();
    expect(find.text('An evening by the coast'), findsOneWidget);
    expect(find.text('@coast'), findsOneWidget);
    expect(find.textContaining('t.co'), findsNothing);
    expect(find.text('Broadcasts'), findsOneWidget);
    expect(tester.takeException(), isNull);
    await expectLater(
      find.byKey(_reviewRender),
      matchesGoldenFile('../review-artifacts/renders/broadcast-card-light-large.png'),
    );

    await tester.tap(find.text('Open post'));
    await tester.pumpAndSettle();
    expect(route!.name, routeStatus);
    final arguments = route!.arguments! as StatusScreenArguments;
    expect(arguments.id, '123');
    expect(arguments.initialTweet, same(item.tweet));
  });

  testWidgets('Space cards fit a narrow RTL viewport with large text', (tester) async {
    tester.view.physicalSize = const Size(320, 900);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    await _pumpCard(tester, item: _broadcast(space: true), textScale: 2, rtl: true, dark: true);
    await tester.pumpAndSettle();
    expect(find.text('Spaces'), findsOneWidget);
    expect(find.text('Open post'), findsOneWidget);
    expect(tester.takeException(), isNull);
    await expectLater(
      find.byKey(_reviewRender),
      matchesGoldenFile('../review-artifacts/renders/broadcast-card-dark-large-rtl.png'),
    );
  });
}
