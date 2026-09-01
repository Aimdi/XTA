import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/constants.dart';
import 'package:xta/tweet/tweet_chrome.dart';
import 'package:xta/tweet/tweet_header.dart';
import 'package:xta/ui/contrast.dart';
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

  testWidgets('author hierarchy reflows on a narrow large-text screen', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(320, 640));
    addTearDown(() => tester.binding.setSurfaceSize(null));

    await tester.pumpWidget(
      MaterialApp(
        theme: xLookLightTheme(null),
        home: MediaQuery(
          data: const MediaQueryData(textScaler: TextScaler.linear(2)),
          child: Scaffold(
            body: TweetHeader(
              avatar: const ColoredBox(color: Colors.red),
              onOpenProfile: () {},
              displayName: 'A very long translated display name',
              handle: 'a_very_long_handle_for_a_narrow_screen',
              verified: true,
              timestamp: const Text('2h'),
              trailing: const Icon(Icons.translate),
            ),
          ),
        ),
      ),
    );

    expect(tester.takeException(), isNull);
    expect(find.text('A very long translated display name'), findsOneWidget);
  });

  testWidgets('community note uses the shared embedded hierarchy', (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        theme: xLookDimTheme(null),
        home: const Scaffold(
          body: TweetCommunityNote(
            title: 'Community Notes',
            child: Text('Supporting context'),
          ),
        ),
      ),
    );

    expect(find.text('Community Notes'), findsOneWidget);
    expect(find.text('Supporting context'), findsOneWidget);
    expect(find.byIcon(Icons.group_outlined), findsOneWidget);
  });

  const tokenThemes = <String, XLookTokens>{
    'light': XLookTokens.light,
    'dim': XLookTokens.dim,
    'lights out': XLookTokens.lightsOut,
  };
  for (final tokenTheme in tokenThemes.entries) {
    for (final accent in xLookAccents.entries) {
      testWidgets(
        '${tokenTheme.key}/${accent.key} keeps accent states readable',
        (tester) async {
          late Color onAccent;
          late Color readableAccent;
          final tokens = tokenTheme.value.copyWith(accent: accent.value);
          await tester.pumpWidget(
            MaterialApp(
              theme: xLookThemeData(tokens, null),
              home: Builder(
                builder: (context) {
                  onAccent = tweetOnAccentColor(context);
                  readableAccent = tweetReadableAccentColor(context);
                  return const SizedBox();
                },
              ),
            ),
          );

          expect(
            contrastRatio(onAccent, accent.value),
            greaterThanOrEqualTo(4.5),
          );
          expect(
            contrastRatio(readableAccent, tokens.background),
            greaterThanOrEqualTo(4.5),
          );
        },
      );
    }
  }

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
