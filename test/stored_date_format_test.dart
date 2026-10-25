import 'package:flutter_test/flutter_test.dart';
import 'package:intl/intl.dart';
import 'package:xta/database/entities.dart';

void main() {
  final afternoon = DateTime(2026, 9, 4, 13, 37, 5);

  // First, so the format is built while the reader's language has no date data.
  test('saving works in a language intl has no date data for', () {
    final previous = Intl.defaultLocale;
    Intl.defaultLocale = 'eo';
    addTearDown(() => Intl.defaultLocale = previous);
    final saved = SearchSubscription(id: 'list:1', createdAt: afternoon).toMap();
    expect(saved['created_at'], '2026-09-04 13:37:05');
  });

  test('a stored date keeps the afternoon and reads back the same', () {
    final stored = sqliteDateFormat.format(afternoon);
    expect(stored, '2026-09-04 13:37:05');
    expect(DateTime.parse(stored), afternoon);
  });
}
