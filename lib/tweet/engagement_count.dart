/// Post engagement counts written the way X writes them: "38", "9.1K",
/// "77K", "1.2M" — in the reader's own language where it has a short form
/// ("1,2 Mio.", "7.7万", "9,1 k").
library;

import 'package:intl/intl.dart';

final Map<String, NumberFormat> _compactByLocale = {};
final Map<String, NumberFormat> _decimalByLocale = {};
final RegExp _letter = RegExp(r'\p{L}', unicode: true);

const List<(int, String)> _units = [(1000000000, 'B'), (1000000, 'M'), (1000, 'K')];

String _resolveLocale(String? locale) =>
    Intl.verifiedLocale(locale ?? Intl.getCurrentLocale(), NumberFormat.localeExists, onFailure: (_) => 'en')!;

/// Cuts [value] down to what X displays: one decimal below ten of a unit,
/// none above, and always rounded down — 9,190 is "9.1K", never "9.2K".
int truncateToDisplayedCount(int value) {
  for (final (unit, _) in _units) {
    if (value >= unit) {
      final step = value < unit * 10 ? unit ~/ 10 : unit;
      return value ~/ step * step;
    }
  }
  return value;
}

/// [value] as X shows it in a post's footer, for [locale] (the app's locale
/// when omitted).
///
/// Where the language has no short form at a magnitude — German and Japanese
/// write thousands out in full — X still says "9,1K", so this falls back to
/// the K/M/B suffix with the language's own decimal separator.
String formatEngagementCount(num value, [String? locale]) {
  final resolved = _resolveLocale(locale);
  final shown = truncateToDisplayedCount(value.toInt());
  final compact = _compactByLocale.putIfAbsent(resolved, () => NumberFormat.compact(locale: resolved)).format(shown);
  if (shown < 1000 || _letter.hasMatch(compact)) {
    return compact;
  }
  final (unit, suffix) = _units.firstWhere((u) => shown >= u.$1);
  final decimal = _decimalByLocale.putIfAbsent(resolved, () => NumberFormat('0.#', resolved));
  return '${decimal.format(shown / unit)}$suffix';
}

/// The number that picks a views label's plural form. Once the count is
/// abbreviated the noun is counted by "thousand"/"million", which takes the
/// many/other form in every language — "77 тыс. просмотров", not "просмотра".
int viewsPluralCount(int views) => views < 1000 ? views : 1000;
