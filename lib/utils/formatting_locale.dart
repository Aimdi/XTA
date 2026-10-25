import 'package:flutter/widgets.dart';
import 'package:intl/intl.dart';

/// [locale] as intl knows it for numbers and dates. A few app languages, such
/// as Esperanto, have neither there, and every [NumberFormat] or [DateFormat]
/// built for them throws; they are written in English instead. Numbers and
/// dates resolve together, so a line holding both never mixes two languages.
String verifiedFormattingLocale(String locale) => Intl.verifiedLocale(locale, _canFormat, onFailure: (_) => 'en')!;

/// The reader's locale as intl's formats know it.
String formattingLocale(BuildContext context) => verifiedFormattingLocale(Localizations.localeOf(context).toString());

/// The app's locale as intl's formats know it, for code with no [BuildContext].
String currentFormattingLocale() => verifiedFormattingLocale(Intl.getCurrentLocale());

/// [value] written in full, grouped the way the reader's language groups digits.
String decimalCount(BuildContext context, num value) =>
    NumberFormat.decimalPattern(formattingLocale(context)).format(value);

bool _canFormat(String locale) => NumberFormat.localeExists(locale) && _hasDateSymbols(locale);

/// intl's date symbols arrive with the app's first [Localizations]. Before
/// that it throws for every locale but en_US, so numbers decide alone.
bool _hasDateSymbols(String locale) {
  try {
    return DateFormat.localeExists(locale);
  } on Exception {
    return true;
  }
}
