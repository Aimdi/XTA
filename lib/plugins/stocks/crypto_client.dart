import 'package:http/http.dart' as http;
import 'package:xta/plugins/stocks/crypto_address.dart';
import 'package:xta/plugins/stocks/crypto_asset.dart';
import 'package:xta/plugins/stocks/gecko_terminal.dart';
import 'package:xta/tweet/ticker/price_http.dart';
import 'package:xta/tweet/ticker/ticker_client.dart';

class CryptoException implements Exception {
  const CryptoException();
}

/// Public read-only endpoints documented at docs.dexscreener.com/api/reference.
///
/// No key, no account, no cookies: `/latest/dex/search` finds tokens by name,
/// symbol or contract address on every network at once, and
/// `/token-pairs/v1/{chain}/{address}` prices one exact contract. A price is
/// asked of GeckoTerminal when DexScreener refuses, rests or has no market
/// for the token; a host that refuses rests as the coin exchanges do.
class CryptoClient {
  final http.Client httpClient;
  final PriceSourceHealth health;

  CryptoClient({http.Client? httpClient, PriceSourceHealth? health})
    : httpClient = httpClient ?? http.Client(),
      health = health ?? PriceSourceHealth.shared;

  static const dexScreener = 'DexScreener';
  static const geckoTerminal = 'GeckoTerminal';

  static Uri searchUri(String query) =>
      Uri.https('api.dexscreener.com', '/latest/dex/search', {'q': query.trim().replaceFirst(RegExp(r'^\$'), '')});

  static Uri tokenUri(CryptoAsset asset) =>
      Uri.https('api.dexscreener.com', '/token-pairs/v1/${asset.chain}/${asset.address}');

  Future<List<CryptoMarket>> search(String query) async => cryptoMarketsFromJson(await _get(searchUri(query)));

  /// Every network a pasted contract address is a token on, with its price.
  ///
  /// The search answers for the address on all networks; its pairs are then
  /// narrowed to that exact contract. A network where the token only showed
  /// up as quote currency is priced through its own pools, a few at a time.
  Future<List<CryptoMarket>> resolve(CryptoAddressInput input) async {
    final found = cryptoMarketsForAddress(await _get(searchUri(input.address)), input.address);
    return Future.wait(found.take(_maxNetworks).map(_priced));
  }

  static const _maxNetworks = 6;

  Future<CryptoMarket> _priced(CryptoMarket market) async {
    if (market.priced) return market;
    try {
      return await fetch(market.asset) ?? market;
    } on CryptoException {
      return market;
    }
  }

  /// [asset]'s market from DexScreener, else from GeckoTerminal; null when
  /// neither has one, a [CryptoException] when neither could say.
  Future<CryptoMarket?> fetch(CryptoAsset asset) async {
    final dex = await _ask(dexScreener, () async => _dexScreenerMarket(await _get(tokenUri(asset)), asset));
    if (dex.market != null) return dex.market;
    final gecko = await _ask(
      geckoTerminal,
      () async => geckoTerminalMarket(await _get(geckoTerminalPoolsUri(asset), notFound: const {404}), asset),
    );
    if (gecko.market == null && dex.refused && gecko.refused) throw const CryptoException();
    return gecko.market;
  }

  /// Token endpoints can also return pools where the requested token is quote
  /// currency. Their priceUsd describes the base, never the watched token.
  static CryptoMarket? _dexScreenerMarket(Object? json, CryptoAsset asset) =>
      cryptoMarketsFromJson(json).where((market) => market.asset.id == asset.id).firstOrNull;

  /// What [source] says, unless it rests; a refusal rests it.
  Future<({CryptoMarket? market, bool refused})> _ask(String source, Future<CryptoMarket?> Function() lookup) async {
    if (health.resting(source)) return (market: null, refused: true);
    try {
      final market = await lookup();
      health.answered(source);
      return (market: market, refused: false);
    } on CryptoException {
      health.refused(source);
      return (market: null, refused: true);
    }
  }

  /// The decoded answer; null for a status in [notFound], which says the host
  /// has nothing for it. Anything else that is not an answer is a
  /// [CryptoException].
  Future<Object?> _get(Uri uri, {Set<int> notFound = const {}}) async {
    try {
      return await fetchPriceJson(httpClient, uri, userAgent: TickerClient.userAgent, notFound: notFound);
    } on TickerException catch (e) {
      if (e.kind == TickerErrorKind.notFound) return null;
      throw const CryptoException();
    }
  }

  void close() => httpClient.close();
}
