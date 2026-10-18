/// Which popular list the Popular tab shows, and how its date steps.
library;

import 'package:flutter/foundation.dart';

enum BooruPopularScale { day, week, month }

@immutable
class BooruPopularQuery {
  final BooruPopularScale scale;

  /// A calendar day; time of day is dropped.
  final DateTime date;

  BooruPopularQuery({required this.scale, required DateTime date}) : date = _dateOnly(date);

  factory BooruPopularQuery.today([BooruPopularScale scale = BooruPopularScale.day]) =>
      BooruPopularQuery(scale: scale, date: booruPopularToday());

  BooruPopularQuery withScale(BooruPopularScale next) => BooruPopularQuery(scale: next, date: date);

  /// One day, week or month earlier ([direction] -1) or later (1), never past
  /// [today].
  BooruPopularQuery step(int direction, {DateTime? today}) {
    final next = switch (scale) {
      BooruPopularScale.day => DateTime(date.year, date.month, date.day + direction),
      BooruPopularScale.week => DateTime(date.year, date.month, date.day + 7 * direction),
      BooruPopularScale.month => _addMonths(date, direction),
    };
    final limit = _dateOnly(today ?? booruPopularToday());
    return BooruPopularQuery(scale: scale, date: next.isAfter(limit) ? limit : next);
  }

  bool isLatest({DateTime? today}) => !date.isBefore(_dateOnly(today ?? booruPopularToday()));

  @override
  bool operator ==(Object other) => other is BooruPopularQuery && other.scale == scale && other.date == date;

  @override
  int get hashCode => Object.hash(scale, date);
}

/// The newest day a host can have a popular list for: its own date, taken as
/// UTC's, but never past the reader's own. East of UTC the reader's morning is
/// a day the host has not reached; west of it, the host's new day has only
/// just begun in the reader's evening. [now] and its UTC [offset] default to
/// this moment and the device's zone.
DateTime booruPopularToday({DateTime? now, Duration? offset}) {
  final utc = (now ?? DateTime.now()).toUtc();
  final local = utc.add(offset ?? DateTime.now().timeZoneOffset);
  final hostDay = DateTime(utc.year, utc.month, utc.day);
  final readerDay = DateTime(local.year, local.month, local.day);
  return hostDay.isBefore(readerDay) ? hostDay : readerDay;
}

/// The same day [months] away, kept inside a shorter month (Mar 31 − 1 → Feb 28).
DateTime _addMonths(DateTime date, int months) {
  final first = DateTime(date.year, date.month + months);
  final days = DateTime(first.year, first.month + 1, 0).day;
  return DateTime(first.year, first.month, date.day > days ? days : date.day);
}

DateTime _dateOnly(DateTime date) => DateTime(date.year, date.month, date.day);
