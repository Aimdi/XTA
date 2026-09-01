/// A symbol's recent price history.
///
/// Parsed out of a chart endpoint whose shape, like X's, is not promised to
/// anyone — so every field is read defensively and a payload that no longer
/// fits yields null rather than throwing inside a screen.
library;

import 'package:xta/utils/json.dart';

class TickerPoint {
  final DateTime at;
  final double close;

  const TickerPoint({required this.at, required this.close});
}

class TickerQuote {
  final String symbol;
  final String? currency;

  /// The latest price, and the close it is measured against. Either may be
  /// absent — a symbol can return history with no live quote attached.
  final double? price;
  final double? previousClose;

  /// What the rest of the market page is made of: how much changed hands, and
  /// where today sits inside the year. All optional — the chart endpoint only
  /// carries them for symbols it has them for.
  final double? volume;
  final double? yearHigh;
  final double? yearLow;

  /// Company or index name, day's range, and the session the last print
  /// belongs to. All optional — the chart host only sends them for some
  /// symbols, and pre/post prints vanish outside those sessions.
  final String? shortName;
  final double? dayHigh;
  final double? dayLow;
  final String? marketState;
  final double? preMarketPrice;
  final double? postMarketPrice;

  final List<TickerPoint> points;

  const TickerQuote({
    required this.symbol,
    required this.currency,
    required this.price,
    required this.previousClose,
    required this.points,
    this.volume,
    this.yearHigh,
    this.yearLow,
    this.shortName,
    this.dayHigh,
    this.dayLow,
    this.marketState,
    this.preMarketPrice,
    this.postMarketPrice,
  });

  double? get change {
    final now = price ?? points.lastOrNull?.close;
    final before = previousClose;
    if (now == null || before == null) {
      return null;
    }
    return now - before;
  }

  double? get changePercent {
    final delta = change;
    final before = previousClose;
    if (delta == null || before == null || before == 0) {
      return null;
    }
    return delta / before * 100;
  }

  /// True when the symbol is up on the day. Null when there is nothing to
  /// compare against, which is not the same as flat.
  bool? get isUp {
    final delta = change;
    return delta == null ? null : delta >= 0;
  }

  /// The print a tape should show: pre/post when that session is live, else
  /// the regular last, else the last close on the chart.
  double? get displayPrice {
    if (marketState == 'PRE' && preMarketPrice != null) {
      return preMarketPrice;
    }
    if ((marketState == 'POST' || marketState == 'POSTPOST') &&
        postMarketPrice != null) {
      return postMarketPrice;
    }
    return price ?? points.lastOrNull?.close;
  }

  bool get isPreMarket => marketState == 'PRE' && preMarketPrice != null;

  bool get isAfterHours =>
      (marketState == 'POST' || marketState == 'POSTPOST') &&
      postMarketPrice != null;

  /// Reads the `chart.result[0]` shape: a list of timestamps alongside a
  /// parallel list of closes, plus a `meta` block.
  ///
  /// Gaps are expected — a market holiday leaves a null close against a real
  /// timestamp — so points are only kept where both halves are present. The
  /// stepping is [Json], which cannot throw: a payload that no longer fits
  /// simply reads as nothing and yields null at the end.
  static TickerQuote? fromChartJson(Object? json, {required String symbol}) {
    final result = Json(json)['chart']['result'][0];
    final meta = result['meta'];
    final timestamps = result['timestamp'].list;
    final closes = result['indicators']['quote'][0]['close'].list;

    final points = <TickerPoint>[];
    for (var i = 0; i < timestamps.length && i < closes.length; i++) {
      final seconds = timestamps[i].integer;
      final close = closes[i].number;
      if (seconds == null || close == null) {
        continue;
      }
      points.add(
        TickerPoint(
          at: DateTime.fromMillisecondsSinceEpoch(
            seconds * 1000,
            isUtc: true,
          ).toLocal(),
          close: close,
        ),
      );
    }

    if (points.isEmpty) {
      return null;
    }

    return TickerQuote(
      symbol: meta['symbol'].string ?? symbol,
      currency: meta['currency'].string,
      price: meta['regularMarketPrice'].number,
      previousClose:
          meta['chartPreviousClose'].number ?? meta['previousClose'].number,
      points: points,
      volume: meta['regularMarketVolume'].number,
      yearHigh: meta['fiftyTwoWeekHigh'].number,
      yearLow: meta['fiftyTwoWeekLow'].number,
      shortName: meta['shortName'].string ?? meta['longName'].string,
      dayHigh: meta['regularMarketDayHigh'].number,
      dayLow: meta['regularMarketDayLow'].number,
      marketState: meta['marketState'].string,
      preMarketPrice: meta['preMarketPrice'].number,
      postMarketPrice: meta['postMarketPrice'].number,
    );
  }
}
