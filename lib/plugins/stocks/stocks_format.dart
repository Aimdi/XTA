import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:xta/constants.dart';
import 'package:xta/tweet/ticker_screen.dart';
import 'package:xta/ui/contrast.dart';

/// X's own green and red, so a rising price reads the same here as it does on
/// a cashtag in the timeline. Draw them through [stockTrendColour], which
/// darkens them on a light background where they would fail contrast.
const Color kStockUpColour = Color(0xFF00BA7C);
const Color kStockDownColour = Color(0xFFF4212E);

/// A move this small prints as 0.00% and is drawn as flat, not as a gain.
const double _flatPercent = 0.005;

/// Green for a gain, red for a loss, readable on [background] (the scaffold by
/// default). Unknown or flat is neutral: green for "no data" would claim good
/// news nobody reported.
Color stockTrendColour(BuildContext context, double? percent, {Color? background}) {
  final theme = Theme.of(context);
  if (percent == null || percent.abs() < _flatPercent) {
    return theme.colorScheme.onSurfaceVariant;
  }
  final base = percent > 0 ? kStockUpColour : kStockDownColour;
  return ensureContrast(base, background ?? theme.scaffoldBackgroundColor);
}

/// The reader's locale when number symbols exist for it, else English —
/// `intl` has none for Esperanto and throws rather than falling back.
String stockNumberLocale() {
  final locale = Intl.getCurrentLocale();
  return NumberFormat.localeExists(locale) ? locale : 'en';
}

final Map<String, NumberFormat> _formats = {};

NumberFormat _format(String kind, NumberFormat Function(String locale) build) {
  final locale = stockNumberLocale();
  return _formats.putIfAbsent('$kind/$locale', () => build(locale));
}

NumberFormat _digits(int digits) =>
    _format('d$digits', (locale) => NumberFormat.decimalPatternDigits(locale: locale, decimalDigits: digits));

/// `−` rather than `-`: the minus sign lines up with `+` in a column of moves.
String _signed(String digits, double value) => value > 0 ? '+$digits' : (value < 0 ? '−$digits' : digits);

/// A price in the reader's locale. Two decimals from one unit up, four below
/// it, and enough significant digits for a token worth a fraction of a cent.
String stockPrice(double price) {
  final magnitude = price.abs();
  if (price == 0 || magnitude >= 1) return _digits(2).format(price);
  if (magnitude >= 0.01) return _digits(4).format(price);
  final digits = 2 - (math.log(magnitude) / math.ln10).floor();
  if (digits > 12) return price.toStringAsExponential(4);
  return _digits(digits).format(price);
}

/// `+1.23%`, `−0.40%`, `0.00%` — signed, locale-grouped, two decimals.
String stockPercentLabel(double percent) {
  final rounded = percent.abs() < _flatPercent ? 0.0 : percent;
  return '${_signed(_digits(2).format(rounded.abs()), rounded)}%';
}

/// `+1.23`, `−0.0004` — the absolute move, at the precision of its price.
String stockChangeLabel(double change) => _signed(stockPrice(change.abs()), change);

/// `48.2M` — volume and supply, shortened the way every market page does.
String stockCompact(double value) => _format('c', (locale) => NumberFormat.compact(locale: locale)).format(value);

/// `$1.2B` — market cap, liquidity and dollar volume.
String stockCompactUsd(double value) =>
    _format('cu', (locale) => NumberFormat.compactSimpleCurrency(locale: locale, name: 'USD')).format(value);

/// Stands in for a number that has not arrived. Punctuation rather than a
/// sentence, so it needs no translation and takes the space the price will.
const String kStockPlaceholder = '—';

void openTicker(BuildContext context, String symbol) {
  Navigator.pushNamed(context, routeTicker, arguments: TickerScreenArguments(symbol: symbol));
}
