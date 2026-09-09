import 'dart:convert';

import 'package:xta/tweet/ticker/ticker_quote.dart';
import 'package:xta/utils/json.dart';

/// A ticker is a label; a network and contract identify a token.
class CryptoAsset {
  final String chain;
  final String address;
  final String symbol;
  final String name;

  const CryptoAsset({required this.chain, required this.address, required this.symbol, required this.name});

  String get id => 'crypto:${chain.toLowerCase()}:${canonicalAddress(address)}';
  String get label => '\$$symbol';
  String get shortAddress =>
      address.length > 16 ? '${address.substring(0, 7)}…${address.substring(address.length - 6)}' : address;
  String get subtitle => '$chain · $shortAddress';
  String get searchQuery => '"${canonicalAddress(address)}"';

  String encode() => 'crypto:v1:${jsonEncode({'chain': chain, 'address': address, 'symbol': symbol, 'name': name})}';

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
        !RegExp(r'^[a-zA-Z0-9_-]{16,128}$').hasMatch(address))
      return null;
    final symbol = json['symbol'].string?.trim();
    final name = json['name'].string?.trim();
    return CryptoAsset(
      chain: chain,
      address: canonicalAddress(address),
      symbol: symbol == null || symbol.isEmpty ? address : symbol,
      name: name == null || name.isEmpty ? address : name,
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

class CryptoMarket {
  final CryptoAsset asset;
  final String? pairAddress;
  final double? price;
  final double? changePercent;
  final double liquidity;

  const CryptoMarket({required this.asset, this.pairAddress, this.price, this.changePercent, this.liquidity = 0});

  TickerQuote get quote => TickerQuote(
    symbol: asset.id,
    currency: 'USD',
    price: price,
    previousClose: price != null && changePercent != null && changePercent! > -100
        ? price! / (1 + changePercent! / 100)
        : null,
    shortName: asset.name,
    points: const [],
  );
}

/// One liquid base-token market per identity, without merging equal symbols.
List<CryptoMarket> cryptoMarketsFromJson(Object? payload) {
  final pairs = payload is List ? Json(payload).list : Json(payload)['pairs'].list;
  final markets = <String, CryptoMarket>{};
  for (final pair in pairs) {
    final token = pair['baseToken'];
    final asset = CryptoAsset.fromJson(
      Json({
        'chain': pair['chainId'].string,
        'address': token['address'].string,
        'symbol': token['symbol'].string,
        'name': token['name'].string,
      }),
    );
    if (asset == null) continue;
    final price = pair['priceUsd'].number;
    final change = pair['priceChange']['h24'].number;
    final liquidity = pair['liquidity']['usd'].number;
    final market = CryptoMarket(
      asset: asset,
      pairAddress: pair['pairAddress'].string,
      price: price != null && price.isFinite && price >= 0 ? price : null,
      changePercent: change != null && change.isFinite ? change : null,
      liquidity: liquidity != null && liquidity.isFinite ? liquidity : 0,
    );
    if (!markets.containsKey(asset.id) || market.liquidity > markets[asset.id]!.liquidity) {
      markets[asset.id] = market;
    }
  }
  return markets.values.toList()..sort((a, b) => b.liquidity.compareTo(a.liquidity));
}
