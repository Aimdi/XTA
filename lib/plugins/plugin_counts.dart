/// Engagement counts, short, in the reader's own language.
///
/// Eleven plugin cards each declared `NumberFormat.compact(locale: 'en_US')`
/// at file scope, so a German reader saw "1.2K" where German writes "1200" and
/// a French reader saw a decimal point where the language uses a comma. The
/// locale was pinned because building a [NumberFormat] is not free and these
/// run on every card in a scrolling feed — so the format is still built once,
/// just once *per locale* rather than once for English.
library;

import 'package:intl/intl.dart';

final Map<String, NumberFormat> _compactByLocale = {};

/// `1234` as the current locale writes it short — "1.2K", "1200", "1,2 k".
String compactCount(num value) {
  final locale = Intl.getCurrentLocale();
  return _compactByLocale
      .putIfAbsent(locale, () => NumberFormat.compact(locale: locale))
      .format(value);
}
