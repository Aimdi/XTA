import 'dart:convert';

import 'package:xta/plugins/stocks/crypto_address.dart';
import 'package:xta/tweet/ticker/ticker_quote.dart';
import 'package:xta/utils/json.dart';

final RegExp _cashtagSymbol = RegExp(r'^[A-Za-z][A-Za-z0-9]{0,9}$');

/// A ticker is a label; a network and contract identify a token.
class CryptoAsset {
  final String chain;
  final String address;
  final String symbol;
  final String name;

  /// Whether the posts feed also searches the `$SYMBOL` cashtag. Off by
  /// default: unrelated tokens routinely share a symbol, the contract never.
  final bool includeCashtag;

  const CryptoAsset({
    required this.chain,
    required this.address,
    required this.symbol,
    required this.name,
    this.includeCashtag = false,
  });

  String get id => 'crypto:${chain.toLowerCase()}:${canonicalAddress(address)}';
  String get label => '\$$symbol';
  String get chainName => cryptoChainName(chain);
  String get shortAddress => shortCryptoAddress(address);
  String get subtitle => '$chainName · $shortAddress';

  /// `$PEPE`, or null when the symbol cannot be written as an X cashtag.
  String? get cashtag => _cashtagSymbol.hasMatch(symbol) ? '\$${symbol.toUpperCase()}' : null;

  /// X search terms for posts: always the contract, plus the cashtag when the
  /// reader asked for it.
  List<String> get searchTerms => ['"${canonicalAddress(address)}"', if (includeCashtag && cashtag != null) cashtag!];

  String get searchQuery => searchTerms.length == 1 ? searchTerms.single : '(${searchTerms.join(' OR ')})';

  CryptoAsset withCashtag(bool include) =>
      CryptoAsset(chain: chain, address: address, symbol: symbol, name: name, includeCashtag: include);

  /// Older rows have no `cashtag` key; leaving it out when off keeps them
  /// byte-identical through a backup round trip.
  String encode() =>
      'crypto:v1:${jsonEncode({'chain': chain, 'address': address, 'symbol': symbol, 'name': name, if (includeCashtag) 'cashtag': true})}';

  static CryptoAsset? decode(String raw) {
    if (!raw.startsWith('crypto:v1:')) return null;
    try {
      return fromJson(Json(jsonDecode(raw.substring(10))));
    } on FormatException {
      return null;
    }
  }

  static CryptoAsset? fromJson(Json json) {
    final chain = json['chain'].string?.trim().toLowerCase();
    final address = json['address'].string?.trim();
    if (chain == null ||
        address == null ||
        !RegExp(r'^[a-z0-9-]+$').hasMatch(chain) ||
        !RegExp(r'^[a-zA-Z0-9_-]{16,128}$').hasMatch(address)) {
      return null;
    }
    final symbol = json['symbol'].string?.trim();
    final name = json['name'].string?.trim();
    return CryptoAsset(
      chain: chain,
      address: canonicalAddress(address),
      symbol: symbol == null || symbol.isEmpty ? address : symbol,
      name: name == null || name.isEmpty ? address : name,
      includeCashtag: json['cashtag'].boolean ?? false,
    );
  }

  static String canonicalAddress(String address) =>
      RegExp(r'^0x[0-9a-fA-F]{40}$').hasMatch(address) ? address.toLowerCase() : address;

  static String? contractForId(String id) {
    final parts = id.split(':');
    if (parts.length != 3 || parts[0] != 'crypto') return null;
    return fromJson(Json({'chain': parts[1], 'address': parts[2]}))?.address;
  }
}

/// A token's best market: price, moves and size, all in USD.
class CryptoMarket {
  final CryptoAsset asset;
  final String? pairAddress;
  final double? price;

  /// 24-hour move, the figure DEX pages lead with.
  final double? changePercent;
  final double? change1h;
  final double? change6h;
  final double liquidity;
  final double? volume24h;
  final double? marketCap;
  final double? fdv;

  const CryptoMarket({
    required this.asset,
    this.pairAddress,
    this.price,
    this.changePercent,
    this.change1h,
    this.change6h,
    this.liquidity = 0,
    this.volume24h,
    this.marketCap,
    this.fdv,
  });

  bool get priced => price != null;

  TickerQuote get quote => TickerQuote(
    symbol: asset.id,
    currency: 'USD',
    price: price,
    previousClose: price != null && changePercent != null && changePercent! > -100
        ? price! / (1 + changePercent! / 100)
        : null,
    shortName: asset.name,
    volume: volume24h,
    points: const [],
    tradesAroundTheClock: true,
  );
}

double? _finite(double? value, {double? atLeast}) =>
    value != null && value.isFinite && (atLeast == null || value >= atLeast) ? value : null;

List<Json> _pairsIn(Object? payload) => payload is List ? Json(payload).list : Json(payload)['pairs'].list;

/// The market for [token] in [pair]. Only the base token's price is the
/// pair's `priceUsd`; as quote currency a token gets its identity, no price.
CryptoMarket? _marketFor(Json pair, Json token, {required bool priced}) {
  final asset = CryptoAsset.fromJson(
    Json({
      'chain': pair['chainId'].string,
      'address': token['address'].string,
      'symbol': token['symbol'].string,
      'name': token['name'].string,
    }),
  );
  if (asset == null) return null;
  final stats = priced ? pair : const Json(null);
  return CryptoMarket(
    asset: asset,
    pairAddress: pair['pairAddress'].string,
    price: _finite(stats['priceUsd'].number, atLeast: 0),
    changePercent: _finite(stats['priceChange']['h24'].number),
    change1h: _finite(stats['priceChange']['h1'].number),
    change6h: _finite(stats['priceChange']['h6'].number),
    liquidity: _finite(pair['liquidity']['usd'].number) ?? 0,
    volume24h: _finite(stats['volume']['h24'].number, atLeast: 0),
    marketCap: _finite(stats['marketCap'].number, atLeast: 0),
    fdv: _finite(stats['fdv'].number, atLeast: 0),
  );
}

/// One market per identity: a priced one beats an unpriced one, then the
/// deeper pool wins. Equal symbols on different contracts never merge.
List<CryptoMarket> _bestPerIdentity(Iterable<CryptoMarket> markets) {
  final best = <String, CryptoMarket>{};
  for (final market in markets) {
    final current = best[market.asset.id];
    final better =
        current == null ||
        (market.priced && !current.priced) ||
        (market.priced == current.priced && market.liquidity > current.liquidity);
    if (better) best[market.asset.id] = market;
  }
  return best.values.toList()..sort((a, b) => b.liquidity.compareTo(a.liquidity));
}

/// One liquid base-token market per identity, without merging equal symbols.
List<CryptoMarket> cryptoMarketsFromJson(Object? payload) =>
    _bestPerIdentity(_pairsIn(payload).map((pair) => _marketFor(pair, pair['baseToken'], priced: true)).nonNulls);

/// Every network [address] is a token on, from pairs that mention it on
/// either side. A token seen only as quote currency is listed unpriced.
List<CryptoMarket> cryptoMarketsForAddress(Object? payload, String address) {
  bool matches(Json token) => sameCryptoAddress(token['address'].string ?? '', address);
  final markets = _pairsIn(payload).map((pair) {
    if (matches(pair['baseToken'])) return _marketFor(pair, pair['baseToken'], priced: true);
    if (matches(pair['quoteToken'])) return _marketFor(pair, pair['quoteToken'], priced: false);
    return null;
  });
  return _bestPerIdentity(markets.nonNulls);
}
