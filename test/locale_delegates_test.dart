import 'package:flutter/cupertino.dart' show CupertinoLocalizations;
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/main.dart';

/// The settings screen builds its language list straight from
/// [L10n.delegate.supportedLocales], so anything in here is a locale a reader
/// can pick -- and Flutter ships Material strings for fewer locales than XTA
/// translates itself. Esperanto had none, so choosing it threw
/// "No MaterialLocalizations found" from the first Scaffold.
void main() {
  for (final locale in L10n.delegate.supportedLocales) {
    testWidgets('a Scaffold builds in ${locale.toLanguageTag()}', (tester) async {
      await tester.pumpWidget(
        MaterialApp(
          locale: locale,
          localizationsDelegates: xtaLocalizationsDelegates,
          supportedLocales: L10n.delegate.supportedLocales,
          home: Scaffold(
            appBar: AppBar(title: const Text('title')),
            body: const TextField(),
          ),
        ),
      );
      await tester.pumpAndSettle();

      // Reading these is what actually throws when no delegate answers.
      final context = tester.element(find.byType(TextField));
      expect(MaterialLocalizations.of(context), isNotNull);
      expect(CupertinoLocalizations.of(context), isNotNull);
    });
  }
}
