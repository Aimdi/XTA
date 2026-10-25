import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/profile/profile_chrome.dart';
import 'package:xta/ui/rolling_count.dart';
import 'package:xta/ui/x_look_theme.dart';

Widget _count(String count, int value) => MaterialApp(
  theme: xLookLightTheme(null),
  home: Scaffold(
    body: ProfileCountButton(count: count, value: value, label: 'Followers', onTap: () {}),
  ),
);

void main() {
  testWidgets('a count is plain text until X\'s copy changes it, then rolls to the new number', (tester) async {
    await tester.pumpWidget(_count('13.9K', 13900));
    expect(find.byType(RollingCount), findsNothing);

    await tester.pumpWidget(_count('14K', 14000));
    expect(find.byType(RollingCount), findsOneWidget);
    expect(tester.hasRunningAnimations, isTrue);
    await tester.pumpAndSettle();
    expect(find.text('14K'), findsOneWidget);
  });

  testWidgets('a rolling count is read as one line with its label', (tester) async {
    final semantics = tester.ensureSemantics();
    await tester.pumpWidget(_count('13.9K', 13900));
    await tester.pumpWidget(_count('14K', 14000));
    await tester.pumpAndSettle();
    expect(find.semantics.byLabel('14K Followers'), findsOne);
    semantics.dispose();
  });
}
