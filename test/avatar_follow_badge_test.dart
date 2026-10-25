import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/tweet/avatar_follow_badge.dart';

Widget _app(Widget child) => MaterialApp(
  localizationsDelegates: const [
    L10n.delegate,
    GlobalMaterialLocalizations.delegate,
    GlobalWidgetsLocalizations.delegate,
    GlobalCupertinoLocalizations.delegate,
  ],
  supportedLocales: L10n.delegate.supportedLocales,
  locale: const Locale('en'),
  home: Scaffold(body: Center(child: child)),
);

void main() {
  const badge = ValueKey('avatar-follow-badge');

  for (final size in [32.0, 40.0, 48.0]) {
    testWidgets('the badge sits on the avatar rim at the lower-right diagonal ($size)', (tester) async {
      await tester.pumpWidget(
        _app(
          AvatarFollowBadge(
            avatar: SizedBox.square(key: const ValueKey('avatar'), dimension: size),
            avatarSize: size,
            followed: false,
            onFollow: () {},
            onMore: () {},
          ),
        ),
      );
      final avatar = tester.getRect(find.byKey(const ValueKey('avatar')));
      final center = tester.getCenter(find.byKey(badge));
      final rim = avatar.center + Offset.fromDirection(math.pi / 4, size / 2);
      expect((center - rim).distance, lessThan(.01));
      expect(tester.getSize(find.byKey(badge)), const Size.square(AvatarFollowBadge.target));
      expect(avatarFollowBadgeSize(size), inInclusiveRange(14, 20));
    });
  }

  testWidgets('one tap follows, the plus becomes a check, then the badge leaves', (tester) async {
    var follows = 0;
    var more = 0;
    Widget build(bool followed) => _app(
      AvatarFollowBadge(
        avatar: const SizedBox.square(dimension: 40),
        avatarSize: 40,
        followed: followed,
        onFollow: () => follows++,
        onMore: () => more++,
      ),
    );
    await tester.pumpWidget(build(false));
    expect(find.byIcon(Icons.add_rounded), findsOneWidget);
    await tester.longPress(find.byKey(badge));
    expect(more, 1);
    await tester.tap(find.byKey(badge));
    expect(follows, 1);

    await tester.pumpWidget(build(true));
    await tester.pump(const Duration(milliseconds: 150));
    expect(find.byIcon(Icons.check_rounded), findsOneWidget);
    expect(tester.widget<AnimatedScale>(find.byType(AnimatedScale)).scale, 0);
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(badge), warnIfMissed: false);
    expect(follows, 1);
    expect(tester.takeException(), isNull);
  });

  testWidgets('following taps lightly and a long press clicks, unless haptic feedback is off', (tester) async {
    final taps = <Object?>[];
    tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(SystemChannels.platform, (call) async {
      // Android's own long-press buzz carries no type and is not XTA's.
      if (call.method == 'HapticFeedback.vibrate' && call.arguments != null) taps.add(call.arguments);
      return null;
    });
    addTearDown(() => tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(SystemChannels.platform, null));
    Widget build({required bool haptics}) => PrefService(
      service: PrefServiceCache(cache: {optionHapticFeedback: haptics}),
      child: _app(
        AvatarFollowBadge(
          avatar: const SizedBox.square(dimension: 40),
          avatarSize: 40,
          followed: false,
          onFollow: () {},
          onMore: () {},
        ),
      ),
    );

    await tester.pumpWidget(build(haptics: true));
    await tester.tap(find.byKey(badge));
    await tester.longPress(find.byKey(badge));
    expect(taps, ['HapticFeedbackType.lightImpact', 'HapticFeedbackType.selectionClick']);

    taps.clear();
    await tester.pumpWidget(build(haptics: false));
    await tester.tap(find.byKey(badge));
    await tester.longPress(find.byKey(badge));
    expect(taps, isEmpty);
  });
}
