import 'dart:async';
import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:xta/plugins/stocks/crypto_asset.dart';
import 'package:xta/plugins/stocks/crypto_client.dart';
import 'package:xta/plugins/stocks/stocks_format.dart';
import 'package:xta/plugins/stocks/stocks_search_store.dart';
import 'package:xta/plugins/stocks/stocks_watchlist_query.dart';
import 'package:xta/tweet/ticker/ticker_client.dart';

const _addressA = '0x1111111111111111111111111111111111111111';
const _addressB = '0x2222222222222222222222222222222222222222';
const _asset = CryptoAsset(chain: 'ethereum', address: _addressA, symbol: 'SAME', name: 'First project');

Map<String, Object?> _pair({String address = _addressA, String chain = 'ethereum',
  double liquidity = 100, String price = '0.0000000123'}) => {
  'chainId': chain,
  'baseToken': {'address': address, 'symbol': 'SAME', 'name': 'Project'},
  'quoteToken': {'address': _addressB, 'symbol': 'USD'},
  'pairAddress': 'pool-address',
  'priceUsd': price,
  'priceChange': {'h24': 25},
  'liquidity': {'usd': liquidity},
};

void main() {
  test('same symbol keeps separate network and contract identities', () {
    final assets = cryptoMarketsFromJson({'pairs': [
      _pair(), _pair(address: _addressB), _pair(chain: 'base'),
    ]});
    expect(assets.map((m) => m.asset.id).toSet(), hasLength(3));
    expect(assets.map((m) => m.asset.symbol).toSet(), {'SAME'});
  });

  test('only identical contracts merge, retaining the more liquid base pair', () {
    final assets = cryptoMarketsFromJson({'pairs': [
      _pair(price: '1'), _pair(liquidity: 900, price: '2'),
    ]});
    expect(assets, hasLength(1));
    expect(assets.single.price, 2);
    expect(assets.single.quote.changePercent, closeTo(25, 0.000001));
  });

  test('EVM casing canonicalizes while non-EVM addresses preserve case', () {
    const evm = '0xABCDEFabcdefABCDEFabcdefABCDEFabcdefABCD';
    expect(CryptoAsset.canonicalAddress(evm), evm.toLowerCase());
    const solana = 'AbCdEfGh123456789AbCdEfGh123456789';
    expect(CryptoAsset.canonicalAddress(solana), solana);
    expect(CryptoAsset.canonicalAddress(solana), isNot(solana.toLowerCase()));
  });

  test('versioned metadata preserves network, contract, name and symbol', () {
    final decoded = CryptoAsset.decode(_asset.encode())!;
    expect(decoded.id, _asset.id);
    expect(decoded.name, _asset.name);
    expect(decoded.symbol, _asset.symbol);
    expect(CryptoAsset.decode('AAPL'), isNull);
    expect(CryptoAsset.decode('crypto:v1:{broken'), isNull);
  });

  test('malformed payloads cannot invent tokens or infinite prices', () {
    expect(cryptoMarketsFromJson(null), isEmpty);
    expect(cryptoMarketsFromJson({'pairs': [null, 5, {}, {'baseToken': []}]}), isEmpty);
    expect(cryptoMarketsFromJson([_pair(price: 'NaN')]).single.price, isNull);
    expect(cryptoMarketsFromJson([_pair(price: '-1')]).single.price, isNull);
  });

  test('mixed feeds search contracts literally and keep legacy cashtags', () {
    expect(watchlistCashtagQuery(['AAPL', _asset.id]), '(\$AAPL OR "$_addressA")');
    expect(watchlistCashtagQuery([_asset.id]), '"$_addressA"');
    expect(watchlistCashtagQuery(['BTC-USD']), r'$BTC');
    expect(watchlistCashtagQuery(['', 'AAPL', 'AAPL']), r'$AAPL');
  });

  test('token quote rejects a pool where the selected token is quote currency', () async {
    final client = CryptoClient(httpClient: MockClient((request) async {
      expect(request.url.path, '/token-pairs/v1/ethereum/$_addressA');
      return http.Response(jsonEncode([
        _pair(address: _addressB, liquidity: 1000, price: '9000'),
        _pair(chain: 'base', liquidity: 900, price: '8000'),
        _pair(price: '0.05'),
      ]), 200);
    }));
    expect((await client.fetch(_asset))!.price, 0.05);
    client.close();
  });

  test('contract search retains case and removes only the cashtag prefix', () {
    const address = 'AbCdEfGh123456789AbCdEfGh123456789';
    expect(CryptoClient.searchUri(address).queryParameters['q'], address);
    expect(CryptoClient.searchUri(r'$PEPE').queryParameters['q'], 'PEPE');
  });

  test('tiny token prices remain nonzero and useful', () {
    expect(stockAssetPrice(0.0000000123), contains('123'));
    expect(stockAssetPrice(0.000000000000123), contains('e-13'));
    expect(stockAssetPrice(0), '0.00');
  });

  test('clearing a search invalidates the request already in flight', () async {
    final response = Completer<http.Response>();
    final crypto = CryptoClient(httpClient: MockClient((_) => response.future));
    final ticker = TickerClient(httpClient: MockClient((_) async => http.Response('{}', 200)));
    final store = StocksSearchStore(tickerClient: ticker, cryptoClient: crypto);
    store.change('SAME', crypto: true, immediate: true);
    store.change('');
    response.complete(http.Response(jsonEncode({'pairs': [_pair()]}), 200));
    await Future<void>.delayed(Duration.zero);
    expect(store.state.query, isEmpty);
    expect(store.state.tokens, isEmpty);
    expect(store.state.loading, isFalse);
    await store.destroy();
    crypto.close();
    ticker.httpClient.close();
  });
}
