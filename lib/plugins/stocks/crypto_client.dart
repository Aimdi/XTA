import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:xta/plugins/stocks/crypto_address.dart';
import 'package:xta/plugins/stocks/crypto_asset.dart';

class CryptoException implements Exception {
  const CryptoException();
}

/// Public read-only endpoints documented at docs.dexscreener.com/api/reference.
///
/// No key, no account, no cookies: `/latest/dex/search` finds tokens by name,
/// symbol or contract address on every network at once, and
/// `/token-pairs/v1/{chain}/{address}` prices one exact contract.
class CryptoClient {
  final http.Client httpClient;
  CryptoClient({http.Client? httpClient}) : httpClient = httpClient ?? http.Client();

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

  Future<CryptoMarket?> fetch(CryptoAsset asset) async {
    final markets = cryptoMarketsFromJson(await _get(tokenUri(asset)));
    // Token endpoints can also return pools where the requested token is quote
    // currency. Their priceUsd describes the base, never the watched token.
    return markets.where((market) => market.asset.id == asset.id).firstOrNull;
  }

  Future<Object?> _get(Uri uri) async {
    try {
      final response = await httpClient.get(uri).timeout(const Duration(seconds: 15));
      if (response.statusCode != 200) throw const CryptoException();
      return jsonDecode(response.body);
    } catch (_) {
      throw const CryptoException();
    }
  }

  void close() => httpClient.close();
}
