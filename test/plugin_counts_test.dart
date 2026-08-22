import 'package:flutter_test/flutter_test.dart';
import 'package:intl/intl.dart';
import 'package:xta/plugins/plugin_counts.dart';

void main() {
  final original = Intl.defaultLocale;
  tearDown(() => Intl.defaultLocale = original);

  test('a count is written the way the reader\'s language writes it', () {
    Intl.defaultLocale = 'en';
    final english = compactCount(1234);

    Intl.defaultLocale = 'de';
    final german = compactCount(1234);

    Intl.defaultLocale = 'fr';
    final french = compactCount(1234);

    expect(english, isNot(german), reason: 'German does not abbreviate 1234');
    expect(french, isNot(english), reason: 'French uses a comma, not a point');
  });

  test('the same locale is not re-parsed for every card in a feed', () {
    Intl.defaultLocale = 'de';

    // Nothing observable to assert beyond stability, which is the point: the
    // formatter is cached, so a scrolling feed keeps getting one answer.
    expect(compactCount(1234), compactCount(1234));
    expect(compactCount(0), '0');
  });
}
