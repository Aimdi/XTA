import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/tweet/tweet_chrome.dart';
import 'package:xta/tweet/tweet_header.dart';
import 'package:xta/ui/theme_presets.dart';
import 'package:xta/ui/x_look_theme.dart';

void main() {
  final themes = <String, ThemeData>{
    'light': xLookLightTheme(null),
    'dark': xLookDimTheme(null),
    'true black': xLookLightsOutTheme(null),
    'fairy forest': fairyForestTheme(null),
    'pitch black': pitchBlackTheme(null),
  };

  for (final entry in themes.entries) {
    testWidgets(
      '${entry.key} author header preserves hierarchy and touch target',
      (tester) async {
        var profileOpens = 0;
        await tester.pumpWidget(
          MaterialApp(
            theme: entry.value,
            home: Scaffold(
              body: TweetHeader(
                avatar: const ColoredBox(key: Key('avatar'), color: Colors.red),
                onOpenProfile: () => profileOpens++,
                displayName: 'Long Display Name',
                handle: 'reader',
                verified: true,
                timestamp: const Text('2h'),
                trailing: const Icon(Icons.translate),
              ),
            ),
          ),
        );

        expect(
          tester.getSize(find.byKey(const Key('avatar'))),
          const Size.square(kTweetAvatarSize),
        );
        expect(
          tester.getSize(find.byType(InkResponse)),
          const Size.square(kTweetTouchTarget),
        );
        expect(find.text('Long Display Name'), findsOneWidget);
        expect(find.text('@reader'), findsOneWidget);
        expect(find.text('2h'), findsOneWidget);

        await tester.tap(find.byType(InkResponse));
        expect(profileOpens, 1);
      },
    );
  }

  testWidgets('quoted header uses the subordinate avatar size', (tester) async {
    await tester.pumpWidget(
      MaterialApp(
        theme: xLookLightTheme(null),
        home: Scaffold(
          body: TweetHeader(
            compact: true,
            avatar: const ColoredBox(key: Key('avatar'), color: Colors.red),
            onOpenProfile: () {},
            displayName: 'Quoted author',
            handle: 'quoted',
            verified: false,
          ),
        ),
      ),
    );

    expect(
      tester.getSize(find.byKey(const Key('avatar'))),
      const Size.square(kTweetQuotedAvatarSize),
    );
    expect(
      tester.getSize(find.byType(InkResponse)),
      const Size.square(kTweetTouchTarget),
    );
  });

  testWidgets('tappable context rows meet the interaction minimum', (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: TweetContextRow(
            icon: Icons.repeat,
            label: const Text('Reposted'),
            onTap: () {},
          ),
        ),
      ),
    );

    expect(
      tester.getSize(find.byType(InkWell)).height,
      greaterThanOrEqualTo(kTweetTouchTarget),
    );
  });

  testWidgets('media frame owns clipping, border, and the shared radius', (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        theme: xLookDimTheme(null),
        home: const Scaffold(
          body: TweetMediaFrame(child: SizedBox(width: 200, height: 100)),
        ),
      ),
    );

    final container = tester
        .widgetList<Container>(find.byType(Container))
        .firstWhere((candidate) => candidate.decoration is BoxDecoration);
    final decoration = container.decoration! as BoxDecoration;
    expect(decoration.border, isNotNull);
    expect(decoration.borderRadius, BorderRadius.circular(kTweetMediaRadius));
    expect(container.clipBehavior, Clip.antiAlias);
  });
}
