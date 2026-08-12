import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/profile/profile.dart';
import 'package:xta/tweet/sensitive_media_gate.dart';

void main() {
  testWidgets('ordinary media does not rebuild when hideSensitive flips', (
    tester,
  ) async {
    final model = TweetContextState(true);
    var builds = 0;

    await tester.pumpWidget(
      ChangeNotifierProvider.value(
        value: model,
        child: MaterialApp(
          home: SensitiveMediaGate(
            sensitive: false,
            child: Builder(
              builder: (context) {
                builds++;
                return const Text('media');
              },
            ),
          ),
        ),
      ),
    );

    expect(find.text('media'), findsOneWidget);
    expect(builds, 1);

    model.setHideSensitive(false);
    await tester.pump();

    expect(builds, 1);
  });

  testWidgets('gated media appears once hideSensitive is cleared', (
    tester,
  ) async {
    final model = TweetContextState(true);

    await tester.pumpWidget(
      PrefService(
        service: PrefServiceCache(),
        child: ChangeNotifierProvider.value(
          value: model,
          child: MaterialApp(
            localizationsDelegates: const [
              L10n.delegate,
              GlobalMaterialLocalizations.delegate,
              GlobalWidgetsLocalizations.delegate,
              GlobalCupertinoLocalizations.delegate,
            ],
            supportedLocales: L10n.delegate.supportedLocales,
            home: SensitiveMediaGate(
              sensitive: true,
              child: const Text('media'),
            ),
          ),
        ),
      ),
    );

    expect(find.text('media'), findsNothing);

    model.setHideSensitive(false);
    await tester.pump();

    expect(find.text('media'), findsOneWidget);
  });
}
