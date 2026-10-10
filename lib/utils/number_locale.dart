import 'package:flutter/widgets.dart';
import 'package:intl/intl.dart';

/// [locale] as intl's number formats know it. A few app languages, such as
/// Esperanto, have no number symbols there and would throw; they get English
/// digits instead.
String verifiedNumberLocale(String locale) =>
    Intl.verifiedLocale(locale, NumberFormat.localeExists, onFailure: (_) => 'en')!;

/// The reader's locale as intl's number formats know it.
String numberFormatLocale(BuildContext context) => verifiedNumberLocale(Localizations.localeOf(context).toString());

/// [value] written in full, grouped the way the reader's language groups digits.
String decimalCount(BuildContext context, num value) =>
    NumberFormat.decimalPattern(numberFormatLocale(context)).format(value);
