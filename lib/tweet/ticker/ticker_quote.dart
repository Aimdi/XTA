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

/// The part of a trading day a moment falls in.
enum TickerSession { regular, pre, post, closed }

/// One stretch of an exchange's day — pre-market, regular, or after hours.
class TickerTradingPeriod {
  final DateTime start;
  final DateTime end;

  const TickerTradingPeriod({required this.start, required this.end});

  bool contains(DateTime moment) =>
      !moment.isBefore(start) && moment.isBefore(end);

  /// Reads `{start, end}` in epoch seconds; anything else is no period.
  static TickerTradingPeriod? fromJson(Json json) {
    final start = json['start'].integer;
    final end = json['end'].integer;
    if (start == null || end == null || end <= start) {
      return null;
    }
    return TickerTradingPeriod(start: _epoch(start), end: _epoch(end));
  }
}

DateTime _epoch(int seconds) =>
    DateTime.fromMillisecondsSinceEpoch(seconds * 1000, isUtc: true).toLocal();

/// A print outside regular hours, and which session it belongs to.
typedef TickerExtendedPrint = ({TickerSession session, double price});

class TickerQuote {
  final String symbol;
  final String? currency;

  /// The latest regular-session price, and the close it is measured against.
  /// Either may be absent — a symbol can return history with no live quote.
  final double? price;
  final double? previousClose;

  /// What the rest of the market page is made of: how much changed hands, and
  /// where today sits inside the year. All optional — the chart endpoint only
  /// carries them for symbols it has them for.
  final double? volume;
  final double? yearHigh;
  final double? yearLow;

  /// Company or index name, day's range and opening print. All optional.
  final String? shortName;
  final double? dayHigh;
  final double? dayLow;
  final double? dayOpen;

  /// The session the host says is live, with its extended prints. Older
  /// payloads carried these; the chart host now sends trading periods instead.
  final String? marketState;
  final double? preMarketPrice;
  final double? postMarketPrice;

  /// Today's trading periods. Null when the host did not send them.
  final TickerTradingPeriod? prePeriod;
  final TickerTradingPeriod? regularPeriod;
  final TickerTradingPeriod? postPeriod;

  /// Coins trade around the clock, so they are never "closed".
  final bool tradesAroundTheClock;

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
    this.dayOpen,
    this.marketState,
    this.preMarketPrice,
    this.postMarketPrice,
    this.prePeriod,
    this.regularPeriod,
    this.postPeriod,
    this.tradesAroundTheClock = false,
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

  /// The regular-session print. The change beside it is measured on the same
  /// session, so an after-hours price never sits next to a regular-hours move.
  double? get displayPrice => price ?? points.lastOrNull?.close;

  /// The latest pre-market or after-hours print, when there is one.
  TickerExtendedPrint? get extendedPrint =>
      _legacyExtendedPrint ?? _extendedPrintFromBars;

  TickerExtendedPrint? get _legacyExtendedPrint {
    if (marketState == 'PRE' && preMarketPrice != null) {
      return (session: TickerSession.pre, price: preMarketPrice!);
    }
    if ((marketState == 'POST' || marketState == 'POSTPOST') &&
        postMarketPrice != null) {
      return (session: TickerSession.post, price: postMarketPrice!);
    }
    return null;
  }

  /// With pre/post bars included, the last bar past the close is the
  /// after-hours print and one inside today's pre-market is the early print.
  TickerExtendedPrint? get _extendedPrintFromBars {
    final regular = regularPeriod;
    final last = points.lastOrNull;
    if (tradesAroundTheClock || regular == null || last == null) {
      return null;
    }
    if (!last.at.isBefore(regular.end)) {
      return (session: TickerSession.post, price: last.close);
    }
    if (prePeriod?.contains(last.at) ?? false) {
      return (session: TickerSession.pre, price: last.close);
    }
    return null;
  }

  /// The extended print's move against the regular price.
  double? get extendedChangePercent {
    final extended = extendedPrint?.price;
    final regular = price;
    if (extended == null || regular == null || regular == 0) {
      return null;
    }
    return (extended - regular) / regular * 100;
  }

  bool get isPreMarket => extendedPrint?.session == TickerSession.pre;

  bool get isAfterHours => extendedPrint?.session == TickerSession.post;

  /// Which session [now] falls in. Null when the payload cannot say.
  TickerSession? sessionAt(DateTime now) {
    if (tradesAroundTheClock) {
      return TickerSession.regular;
    }
    final regular = regularPeriod;
    if (regular == null) {
      return _legacySession;
    }
    if (regular.contains(now)) return TickerSession.regular;
    if (prePeriod?.contains(now) ?? false) return TickerSession.pre;
    if (postPeriod?.contains(now) ?? false) return TickerSession.post;
    return TickerSession.closed;
  }

  TickerSession? get _legacySession => switch (marketState) {
    'REGULAR' => TickerSession.regular,
    'PRE' || 'PREPRE' => TickerSession.pre,
    'POST' || 'POSTPOST' => TickerSession.post,
    'CLOSED' => TickerSession.closed,
    _ => null,
  };

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
    final points = _pointsFrom(result);
    if (points.isEmpty) {
      return null;
    }
    final periods = meta['currentTradingPeriod'];
    final regular = TickerTradingPeriod.fromJson(periods['regular']);

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
      dayOpen: _openFrom(result, regular),
      marketState: meta['marketState'].string,
      preMarketPrice: meta['preMarketPrice'].number,
      postMarketPrice: meta['postMarketPrice'].number,
      prePeriod: TickerTradingPeriod.fromJson(periods['pre']),
      regularPeriod: regular,
      postPeriod: TickerTradingPeriod.fromJson(periods['post']),
      tradesAroundTheClock: meta['instrumentType'].string == 'CRYPTOCURRENCY',
    );
  }

  static List<TickerPoint> _pointsFrom(Json result) {
    final timestamps = result['timestamp'].list;
    final closes = result['indicators']['quote'][0]['close'].list;
    return [
      for (var i = 0; i < timestamps.length && i < closes.length; i++)
        if (timestamps[i].integer != null && closes[i].number != null)
          TickerPoint(
            at: _epoch(timestamps[i].integer!),
            close: closes[i].number!,
          ),
    ];
  }

  /// The first opening print inside today's regular session. Without a
  /// session there is no telling which bar opened the day, so no open.
  static double? _openFrom(Json result, TickerTradingPeriod? regular) {
    if (regular == null) return null;
    final timestamps = result['timestamp'].list;
    final opens = result['indicators']['quote'][0]['open'].list;
    for (var i = 0; i < timestamps.length && i < opens.length; i++) {
      final seconds = timestamps[i].integer;
      final open = opens[i].number;
      if (seconds == null || open == null) continue;
      if (regular.contains(_epoch(seconds))) return open;
    }
    return null;
  }
}
