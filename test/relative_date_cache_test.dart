import 'package:flutter_test/flutter_test.dart';
import 'package:intl/intl.dart';
import 'package:timeago/timeago.dart' as timeago;
import 'package:xta/ui/dates.dart';

/// Counts how often timeago is actually asked to build a string.
class _CountingMessages extends timeago.EnMessages {
  int calls = 0;

  @override
  String minutes(int minutes) {
    calls++;
    return super.minutes(minutes);
  }

  @override
  String hours(int hours) {
    calls++;
    return super.hours(hours);
  }
}

void main() {
  final messages = _CountingMessages();

  setUp(() {
    timeago.setLocaleMessages('en', messages);
    Intl.defaultLocale = 'en';
    messages.calls = 0;
  });

  test('the same timestamp is only formatted once', () {
    final when = DateTime.now().subtract(const Duration(hours: 3));

    final first = createRelativeDate(when);
    for (var i = 0; i < 50; i++) {
      expect(createRelativeDate(when), first);
    }

    expect(messages.calls, 1);
  });

  test('different timestamps still get their own wording', () {
    final now = DateTime.now();
    final recent = now.subtract(const Duration(minutes: 5));
    final older = now.subtract(const Duration(hours: 5));

    expect(createRelativeDate(recent), isNot(createRelativeDate(older)));
  });

  test('the compact form is cached apart from the full one', () {
    final when = DateTime.now().subtract(const Duration(hours: 3));
    compactDateLocales.add('en');
    timeago.setLocaleMessages('en_short', timeago.EnShortMessages());
    addTearDown(() => compactDateLocales.remove('en'));

    expect(createCompactDate(when), isNot(createRelativeDate(when)));
  });
}
