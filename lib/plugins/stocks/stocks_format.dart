import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:xta/constants.dart';
import 'package:xta/tweet/ticker_screen.dart';
import 'package:xta/tweet/tweet_chrome.dart';
import 'package:xta/ui/contrast.dart';
import 'package:xta/utils/formatting_locale.dart';

/// X's own green and red, so a rising price reads the same here as it does on
/// a cashtag in the timeline. Draw them through [stockTrendColour], which
/// darkens them on a light background where they would fail contrast.
const Color kStockUpColour = Color(0xFF00BA7C);
const Color kStockDownColour = Color(0xFFF4212E);

/// X's amber: a caution about a row or a post, never a price move.
const Color kStockWarningColour = Color(0xFFFFAD1F);

/// A move this small prints as 0.00% and is drawn as flat, not as a gain.
const double _flatPercent = 0.005;

/// The app's accent as Stocks draws selection with it (the tab, the strip
/// entry, the reading shown), readable on the scaffold.
Color stocksAccent(BuildContext context) =>
    ensureContrast(tweetReadableAccentColor(context), Theme.of(context).scaffoldBackgroundColor);

/// How wide [text] is drawn in [style] at the reader's text size, on one line.
double stocksTextWidth(BuildContext context, String text, TextStyle style) {
  final painter = TextPainter(
    text: TextSpan(text: text, style: style),
    textDirection: Directionality.of(context),
    textScaler: MediaQuery.textScalerOf(context),
    maxLines: 1,
  )..layout();
  final width = painter.width;
  painter.dispose();
  return width;
}

/// [kStockWarningColour], readable on [background] (the scaffold by default).
Color stockWarningColour(BuildContext context, {Color? background}) =>
    ensureContrast(kStockWarningColour, background ?? Theme.of(context).scaffoldBackgroundColor);

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

final Map<String, NumberFormat> _formats = {};

NumberFormat _format(String kind, NumberFormat Function(String locale) build) {
  final locale = currentFormattingLocale();
  return _formats.putIfAbsent('$kind/$locale', () => build(locale));
}

NumberFormat _digits(int digits) =>
    _format('d$digits', (locale) => NumberFormat.decimalPatternDigits(locale: locale, decimalDigits: digits));

/// `−` rather than `-`: the minus sign lines up with `+` in a column of moves.
String _signed(String digits, double value) => value > 0 ? '+$digits' : (value < 0 ? '−$digits' : digits);

const int _significant = 4;

/// From this many zeros after the decimal separator on, they are counted.
const int _countedZeros = 3;

const String _subscripts = '₀₁₂₃₄₅₆₇₈₉';

/// A price under one unit as its zeros after the separator and its
/// [_significant] digits, after rounding. Null for zero and from one unit up.
({int zeros, String digits})? _small(double price) {
  final magnitude = price.abs();
  if (magnitude == 0 || !magnitude.isFinite || magnitude >= 1) return null;
  final [mantissa, exponent] = magnitude.toStringAsExponential(_significant - 1).split('e');
  final power = int.parse(exponent);
  if (power >= 0) return null;
  return (zeros: -power - 1, digits: mantissa.replaceFirst('.', ''));
}

String _subscript(int count) => [for (final digit in '$count'.split('')) _subscripts[int.parse(digit)]].join();

final RegExp _countedZerosPattern = RegExp('0([$_subscripts]+)');

int _subscriptValue(String digits) =>
    int.parse([for (final digit in digits.split('')) _subscripts.indexOf(digit)].join());

/// A price in the reader's locale. Two decimals from one unit up; below it
/// four significant digits (`0,1234`, `0,001810`), and from three zeros after
/// the separator on those zeros counted (`0,0₃1234`), so a token worth a
/// fraction of a cent stays readable. A screen reader gets [stockSpoken].
String stockPrice(double price) {
  final small = _small(price);
  if (small == null) return _digits(2).format(price);
  final symbols = _digits(2).symbols;
  if (small.zeros < _countedZeros || symbols.ZERO_DIGIT != '0') {
    final decimals = small.zeros + _significant;
    return decimals > 15 ? price.toStringAsExponential(_significant - 1) : _digits(decimals).format(price);
  }
  return '${price < 0 ? '-' : ''}0${symbols.DECIMAL_SEP}0${_subscript(small.zeros)}${small.digits}';
}

/// [shown] the way it is read aloud: counted zeros written out (`0,0₃1234`
/// is `0,0001234`), anything else as it is.
String stockSpoken(String shown) =>
    shown.replaceAllMapped(_countedZerosPattern, (match) => '0' * _subscriptValue(match[1]!));

/// [stockPrice], read aloud.
String stockPriceSpoken(double price) => stockSpoken(stockPrice(price));

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
