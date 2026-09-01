import 'package:flutter/widgets.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/utils/native_locale_names.dart';

/// This map replaced a dependency whose 17 MB of assets shipped in every APK
/// for the sake of it. What matters is that the language picker still has a
/// name to show for every locale the app is translated into — a regression here
/// would show readers a bare code like `zh_Hant` instead of 繁體中文.
void main() {
  test('every supported locale has a name in its own language', () {
    final missing = <String>[];

    for (final locale in L10n.delegate.supportedLocales) {
      final code = locale.toLanguageTag().replaceAll('-', '_');
      if (nativeLocaleNameOf(code) == null) {
        missing.add(code);
      }
    }

    expect(missing, isEmpty, reason: 'the language picker would fall back to showing the raw code');
  });

  test('the names are the ones each language uses for itself', () {
    expect(nativeLocaleNames['de'], 'Deutsch');
    expect(nativeLocaleNames['fr'], 'français');
    expect(nativeLocaleNames['ja'], '日本語');
    expect(nativeLocaleNames['pt_BR'], isNotNull);
  });

  test('an unknown code is absent rather than empty, so the caller can fall back', () {
    expect(nativeLocaleNameOf('not_a_locale'), isNull);
    expect(nativeLocaleNames.values.any((name) => name.isEmpty), isFalse);
  });

  test('a locale CLDR does not carry is still named', () {
    // CLDR has `be` and `be_BY` but not Latin-script Belarusian, so the picker
    // showed the code to exactly the readers who had chosen that language.
    expect(nativeLocaleNames['be_Latn'], isNull, reason: 'still absent upstream');
    expect(nativeLocaleNameOf('be_Latn'), 'biełaruskaja (łacinka)');
  });

  test('it still covers far more than the app is translated into', () {
    // The picker lists the app's locales, but the map is also asked about the
    // device's, which can be anything.
    expect(nativeLocaleNames.length, greaterThan(500));
    expect(const Locale('en'), isNotNull);
  });
}
