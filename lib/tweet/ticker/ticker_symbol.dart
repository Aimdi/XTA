/// Spoken cashtags versus the names the price service quotes them by.
///
/// FinTwit writes `$SPX` and `$BTC`. The chart host wants `^GSPC` and
/// `BTC-USD`. One map, used both to ask for a price and to put a service
/// symbol back into a cashtag.
library;

/// Index (and a few well-known) names as people say them → as the host files
/// them. Crypto is handled separately by appending `-USD`.
const Map<String, String> kTickerAliases = {
  'SPX': '^GSPC',
  'SP500': '^GSPC',
  'DJI': '^DJI',
  'DOW': '^DJI',
  'NDX': '^NDX',
  'IXIC': '^IXIC',
  'NASDAQ': '^IXIC',
  'RUT': '^RUT',
  'VIX': '^VIX',
  'DAX': '^GDAXI',
  'FTSE': '^FTSE',
  'CAC': '^FCHI',
  'N225': '^N225',
  'NIKKEI': '^N225',
  'HSI': '^HSI',
};

/// The reverse of [kTickerAliases], plus the pair form of a few coins FinTwit
/// talks about as a single word.
const Map<String, String> kSpokenTickers = {
  '^GSPC': 'SPX',
  '^DJI': 'DJI',
  '^NDX': 'NDX',
  '^IXIC': 'NASDAQ',
  '^RUT': 'RUT',
  '^VIX': 'VIX',
  '^GDAXI': 'DAX',
  '^FTSE': 'FTSE',
  '^FCHI': 'CAC',
  '^N225': 'N225',
  '^HSI': 'HSI',
};

/// Indices and coins a markets page always shows — getquin / Yahoo Finance's
/// tape, not a personal watchlist.
const List<String> kMarketIndexSymbols = [
  'SPX',
  'NDX',
  'DJI',
  'RUT',
  'VIX',
  'DAX',
  'FTSE',
  'N225',
  'BTC',
  'ETH',
];

/// The cashtag people would search for, given whatever the price service
/// called the symbol.
String spokenCashtag(String raw) {
  var text = raw.trim().toUpperCase();
  text = kSpokenTickers[text] ?? text;
  if (text.endsWith('-USD')) {
    text = text.substring(0, text.length - 4);
  }
  if (text.startsWith('^')) {
    text = text.substring(1);
  }
  return text;
}

/// Coins whose cashtag means the coin far more often than the listing that
/// shares its letters: `$ETH` is Ethereum, not Ethan Allen; `$BTC` is bitcoin,
/// not a bitcoin ETF; `$SOL` is Solana, not Emeren. Only coins the chart host
/// files under a plain `-USD` pair belong here.
const Set<String> kCoinFirstTickers = {
  'BTC',
  'ETH',
  'SOL',
  'XRP',
  'DOGE',
  'ADA',
  'AVAX',
  'LINK',
  'DOT',
  'LTC',
  'BNB',
  'TRX',
  'XLM',
  'BCH',
  'ETC',
};

/// Which reading of an ambiguous cashtag the reader wants.
enum TickerInstrument { auto, stock, crypto }

/// True when a cashtag could name both a coin and a listed security, so a
/// ticker page should let the reader pick which one it charts.
bool isAmbiguousTicker(String symbol) =>
    kCoinFirstTickers.contains(symbol.toUpperCase().trim());

/// Every name to try for a cashtag, in order.
List<String> tickerCandidates(
  String symbol, {
  TickerInstrument prefer = TickerInstrument.auto,
}) {
  final upper = symbol.toUpperCase().trim();
  final alias = kTickerAliases[upper];
  final pairable = !upper.contains('-') && !upper.startsWith('^');
  final pair = pairable ? '$upper-USD' : null;

  return switch (prefer) {
    TickerInstrument.crypto => [pair ?? upper],
    TickerInstrument.stock => [upper, ?alias],
    TickerInstrument.auto when kCoinFirstTickers.contains(upper) => [
      ?pair,
      upper,
    ],
    TickerInstrument.auto => [upper, ?alias, ?pair],
  };
}
