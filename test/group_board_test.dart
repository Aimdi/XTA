import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/group/group_model.dart';
import 'package:xta/subscriptions/widgets/avatar_mosaic.dart';
import 'package:xta/subscriptions/widgets/fallback_avatar.dart';
import 'package:xta/subscriptions/widgets/group_tile.dart';
import 'package:xta/ui/group_board_tokens.dart';
import 'package:xta/ui/theme_presets.dart';
import 'package:xta/ui/x_look_theme.dart';

Widget _wrap(Widget child, {ThemeData? theme}) => MaterialApp(
      theme: theme,
      localizationsDelegates: const [
        L10n.delegate,
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
      ],
      supportedLocales: L10n.delegate.supportedLocales,
      home: Scaffold(body: child),
    );

List<GroupMemberPreview> _members(int count, {bool withAvatars = true}) => [
      for (var i = 0; i < count; i++)
        GroupMemberPreview(
          id: 'id-$i',
          name: 'Member $i',
          // A URL that cannot resolve in tests, exercising the failure path.
          avatarUrl: withAvatars ? 'https://example.invalid/$i.jpg' : null,
        ),
    ];

SubscriptionGroup _group({
  String name = 'Anime',
  int members = 15,
  List<GroupMemberPreview> previews = const [],
  bool pinned = false,
}) =>
    SubscriptionGroup(
      id: 'g1',
      name: name,
      icon: defaultGroupIcon,
      color: null,
      numberOfMembers: members,
      createdAt: DateTime.utc(2024),
      pinned: pinned,
      memberPreviews: previews,
    );

/// WCAG 2.1 relative luminance.
double _luminance(Color c) {
  double channel(double v) => v <= 0.03928 ? v / 12.92 : math.pow((v + 0.055) / 1.055, 2.4).toDouble();
  return 0.2126 * channel(c.r) + 0.7152 * channel(c.g) + 0.0722 * channel(c.b);
}

double _contrast(Color a, Color b) {
  final la = _luminance(a);
  final lb = _luminance(b);
  final hi = math.max(la, lb);
  final lo = math.min(la, lb);
  return (hi + 0.05) / (lo + 0.05);
}

void main() {
  group('FallbackAvatar', () {
    test('colour is deterministic for a seed and stable across calls', () {
      final palette = fallbackAvatarPalette(XLookTokens.accentBlue);
      final first = fallbackAvatarIndex('user-42', palette.length);
      final second = fallbackAvatarIndex('user-42', palette.length);

      expect(first, second);
      expect(first, inInclusiveRange(0, palette.length - 1));
    });

    test('different seeds land on different colours', () {
      final palette = fallbackAvatarPalette(XLookTokens.accentBlue);
      final indices = {
        for (final seed in ['alice', 'bob', 'carol', 'dave']) fallbackAvatarIndex(seed, palette.length)
      };
      expect(indices.length, greaterThan(1));
    });

    test('index is never negative even for hash overflow', () {
      final palette = fallbackAvatarPalette(XLookTokens.accentBlue);
      for (final seed in ['a', 'a very long screen name that overflows the hash badly', '1234567890', '']) {
        expect(fallbackAvatarIndex(seed, palette.length), greaterThanOrEqualTo(0));
      }
    });

    test('palette is never grey — a missing avatar must look deliberate', () {
      for (final color in fallbackAvatarPalette(XLookTokens.accentBlue)) {
        expect(HSLColor.fromColor(color).saturation, greaterThan(0.2));
      }
    });

    test('initial uses the first alphanumeric of the name, else the seed', () {
      expect(fallbackAvatarInitial('Anime', 'x'), 'A');
      expect(fallbackAvatarInitial('  ", elon', 'x'), 'E');
      expect(fallbackAvatarInitial('', 'zebra'), 'Z');
      expect(fallbackAvatarInitial('', ''), '#');
      expect(fallbackAvatarInitial('42 things', 'x'), '4');
    });

    testWidgets('renders a single initial at the requested size', (tester) async {
      await tester.pumpWidget(_wrap(const FallbackAvatar(
          seed: 'id-1', displayName: 'Anime', size: 34, accent: XLookTokens.accentBlue)));
      await tester.pump();

      expect(find.text('A'), findsOneWidget);
      expect(tester.getSize(find.byType(FallbackAvatar)), const Size(34, 34));
    });
  });

  group('AvatarMosaic', () {
    Future<void> pumpMosaic(WidgetTester tester, int memberCount, {bool withAvatars = true}) async {
      await tester.pumpWidget(_wrap(AvatarMosaic(
        extent: 74,
        members: _members(memberCount, withAvatars: withAvatars),
        groupName: 'Anime',
        groupColor: Colors.orange,
        accent: XLookTokens.accentBlue,
        ringColor: Colors.black,
      )));
      await tester.pump();
    }

    testWidgets('an empty group falls back to its own initial', (tester) async {
      await pumpMosaic(tester, 0);
      expect(find.text('A'), findsOneWidget);
    });

    for (final count in [1, 2, 3, 4]) {
      testWidgets('lays out $count member(s) without overflow', (tester) async {
        await pumpMosaic(tester, count, withAvatars: false);
        expect(tester.takeException(), isNull);
        expect(find.byType(FallbackAvatar), findsNWidgets(count));
        expect(tester.getSize(find.byType(AvatarMosaic)), const Size(74, 74));
      });
    }

    testWidgets('never renders more than four avatars', (tester) async {
      await pumpMosaic(tester, 8, withAvatars: false);
      expect(find.byType(FallbackAvatar), findsNWidgets(4));
    });

    testWidgets('an unreachable image degrades to a same-size monogram', (tester) async {
      await pumpMosaic(tester, 4);
      await tester.pump();

      // Loading and failed both render the fallback, so the mosaic geometry
      // never shifts and no broken-image glyph is ever shown.
      expect(find.byType(FallbackAvatar), findsNWidgets(4));
      expect(tester.getSize(find.byType(AvatarMosaic)), const Size(74, 74));
    });
  });

  group('GroupTile', () {
    testWidgets('shows name, localized count and a semantics label', (tester) async {
      await tester.pumpWidget(_wrap(SizedBox(
        width: 168,
        height: 132,
        child: GroupTile(group: _group(previews: _members(4, withAvatars: false)), onTap: () {}),
      )));
      await tester.pump();

      expect(find.text('Anime'), findsOneWidget);
      expect(find.text('15 subscriptions'), findsOneWidget);

      final semantics = tester.getSemantics(find.byType(GroupTile));
      expect(semantics.label, 'Anime, 15 subscriptions');
    });

    testWidgets('is flat and rippleless — no Card, no InkWell', (tester) async {
      await tester.pumpWidget(_wrap(SizedBox(
        width: 168,
        height: 132,
        child: GroupTile(group: _group(previews: _members(2, withAvatars: false)), onTap: () {}),
      )));
      await tester.pump();

      expect(find.byType(Card), findsNothing);
      expect(find.byType(InkWell), findsNothing);
      expect(find.byType(AnimatedScale), findsOneWidget);
    });

    testWidgets('the card is a flat surface: no colour wash, no accent bar', (tester) async {
      late GroupBoardTokens tokens;
      await tester.pumpWidget(_wrap(Builder(builder: (context) {
        tokens = GroupBoardTokens.resolve(context);
        return SizedBox(
          width: 168,
          height: 132,
          child: GroupTile(
            group: _group(previews: _members(2, withAvatars: false)),
            onTap: () {},
          ),
        );
      })));
      await tester.pump();

      final card = tester.widget<Container>(
        find.descendant(of: find.byType(GroupTile), matching: find.byType(Container)).first,
      );
      final decoration = card.decoration as BoxDecoration;

      // A tinted card is the Material You look; X keeps the surface flat and
      // puts the colour on the round identity disc instead.
      expect(decoration.color, tokens.tile);
      expect(decoration.border, isNotNull, reason: 'depth comes from a hairline border');

      // The 4dp accent rule that used to run down the left edge is gone.
      final rules = find
          .byType(Container)
          .evaluate()
          .map((e) => e.widget as Container)
          .where((c) => c.constraints?.maxWidth == 4);
      expect(rules, isEmpty);
    });

    testWidgets('marks a pinned group', (tester) async {
      await tester.pumpWidget(_wrap(SizedBox(
        width: 168,
        height: 132,
        child: GroupTile(group: _group(pinned: true), onTap: () {}),
      )));
      await tester.pump();

      expect(find.byIcon(Icons.push_pin), findsOneWidget);
    });

    testWidgets('survives a 2x text scale without overflowing', (tester) async {
      await tester.pumpWidget(_wrap(MediaQuery(
        data: const MediaQueryData(textScaler: TextScaler.linear(2.0)),
        child: SizedBox(
          width: 168,
          height: 200,
          child: GroupTile(
              group: _group(name: 'Demographics & German EU', previews: _members(4, withAvatars: false)),
              onTap: () {}),
        ),
      )));
      await tester.pump();

      expect(tester.takeException(), isNull);
    });

    testWidgets('tapping calls onTap', (tester) async {
      var taps = 0;
      await tester.pumpWidget(_wrap(SizedBox(
        width: 168,
        height: 132,
        child: GroupTile(group: _group(), onTap: () => taps++),
      )));
      await tester.pump();

      await tester.tap(find.byType(GroupTile));
      expect(taps, 1);
    });
  });

  group('GroupBoardTokens contrast (WCAG 2.2 SC 1.4.3)', () {
    final themes = <String, ThemeData>{
      'x-look light': xLookLightTheme(null),
      'x-look dim': xLookDimTheme(null),
      'x-look lights out': xLookLightsOutTheme(null),
      'fairy forest': fairyForestTheme(null),
      'pitch black': pitchBlackTheme(null),
      'default light': ThemeData(useMaterial3: true, brightness: Brightness.light),
      'default dark': ThemeData(useMaterial3: true, brightness: Brightness.dark),
      'true black': ThemeData(
        useMaterial3: true,
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.blue, brightness: Brightness.dark)
            .copyWith(surface: Colors.black),
      ),
    };

    themes.forEach((name, theme) {
      testWidgets('$name keeps tile text legible', (tester) async {
        late GroupBoardTokens tokens;
        await tester.pumpWidget(_wrap(
          Builder(builder: (context) {
            tokens = GroupBoardTokens.resolve(context);
            return const SizedBox();
          }),
          theme: theme,
        ));

        // Every tile carries a 10% wash of its group colour, so check the bare
        // token and a spread of realistic group colours, using the same
        // correction the tile applies when painting.
        final washes = <Color>[
          tokens.tile,
          for (final c in [tokens.accent, Colors.orange, Colors.pink, Colors.brown, Colors.yellow, Colors.indigo])
            Color.alphaBlend(c.withValues(alpha: 0.10), tokens.tile),
        ];

        for (final background in washes) {
          expect(_contrast(GroupBoardTokens.ensureContrast(tokens.onSurface, background), background),
              greaterThanOrEqualTo(4.5),
              reason: '$name: title text must reach 4.5:1');
          expect(_contrast(GroupBoardTokens.ensureContrast(tokens.secondary, background), background),
              greaterThanOrEqualTo(4.5),
              reason: '$name: member count text must reach 4.5:1');
        }
      });
    });
  });
}
