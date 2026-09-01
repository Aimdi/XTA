import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:xta/constants.dart';
import 'package:xta/tweet/ticker_screen.dart';

/// X's own green and red, so a rising price reads the same here as it does on
/// a cashtag in the timeline.
const Color kStockUpColour = Color(0xFF00BA7C);
const Color kStockDownColour = Color(0xFFF4212E);

/// Null means there is nothing to compare against, which is not the same as
/// flat — but a colour has to be chosen, and green reads as "no bad news".
Color stockChangeColour(bool? isUp) =>
    (isUp ?? true) ? kStockUpColour : kStockDownColour;

final NumberFormat stockMoneyFormat = NumberFormat.decimalPatternDigits(
  decimalDigits: 2,
);

String stockPercentLabel(double percent) =>
    '${percent >= 0 ? '+' : ''}${percent.toStringAsFixed(2)}%';

String stockChangeLabel(double change) =>
    '${change >= 0 ? '+' : ''}${stockMoneyFormat.format(change)}';

/// Stands in for a number that has not arrived. Punctuation rather than a
/// sentence, so it needs no translation and takes the space the price will.
const String kStockPlaceholder = '—';

void openTicker(BuildContext context, String symbol) {
  Navigator.pushNamed(
    context,
    routeTicker,
    arguments: TickerScreenArguments(symbol: symbol),
  );
}
