import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:xta/plugins/stocks/crypto_asset.dart';
import 'package:xta/plugins/stocks/crypto_client.dart';
import 'package:xta/plugins/stocks/gecko_terminal.dart';
import 'package:xta/tweet/ticker/ticker_client.dart';

const _token = '0x1111111111111111111111111111111111111111';
const _weth = '0xc02aaa39b223fe8d0a0e5c4f27ead9083c756cc2';
const _asset = CryptoAsset(chain: 'ethereum', address: _token, symbol: 'SAME', name: 'Same');

/// A pool as GeckoTerminal's `/networks/{network}/tokens/{address}/pools`
/// lists it: figures as strings, tokens by `{network}_{address}` id.
Map<String, Object?> _pool({required String base, required String quote, String price = '0.05'}) => {
  'id': 'eth_0xpool',
  'type': 'pool',
  'attributes': {
    'address': '0xpool',
    'name': 'SAME / WETH',
    'base_token_price_usd': price,
    'price_change_percentage': {'m5': '0.1', 'h1': '1.5', 'h6': '-2.25', 'h24': '12.5'},
    'reserve_in_usd': '1000.5',
    'volume_usd': {'h24': '5000'},
    'market_cap_usd': null,
    'fdv_usd': '123456',
  },
  'relationships': {
    'base_token': {
      'data': {'id': 'eth_$base', 'type': 'token'},
    },
    'quote_token': {
      'data': {'id': 'eth_$quote', 'type': 'token'},
    },
  },
};

Map<String, Object?> _dexPair({String price = '0.04'}) => {
  'chainId': 'ethereum',
  'baseToken': {'address': _token, 'symbol': 'SAME', 'name': 'Same'},
  'quoteToken': {'address': _weth, 'symbol': 'WETH'},
  'pairAddress': '0xdexpool',
  'priceUsd': price,
  'priceChange': {'h24': 3},
  'liquidity': {'usd': 900},
};

void main() {
  group('GeckoTerminal', () {
    test('is asked under its own name for the network', () {
      expect(
        geckoTerminalPoolsUri(_asset).toString(),
        'https://api.geckoterminal.com/api/v2/networks/eth/tokens/$_token/pools?page=1',
      );
      expect(geckoTerminalNetwork('polygon'), 'polygon_pos');
      expect(geckoTerminalNetwork('avalanche'), 'avax');
      expect(geckoTerminalNetwork('solana'), 'solana');
      expect(geckoTerminalNetwork('base'), 'base');
    });

    test("prices the token from the first pool it is the base of, with that pool's figures", () {
      final market = geckoTerminalMarket({
        'data': [
          _pool(base: _weth, quote: _token, price: '3500'),
          _pool(base: _token.toUpperCase().replaceFirst('0X', '0x'), quote: _weth),
        ],
      }, _asset)!;
      expect(market.asset, same(_asset));
      expect(market.price, 0.05, reason: "a pool quoting the token prices the other one");
      expect(market.change1h, 1.5);
      expect(market.change6h, -2.25);
      expect(market.changePercent, 12.5);
      expect(market.liquidity, 1000.5);
      expect(market.volume24h, 5000);
      expect(market.marketCap, isNull);
      expect(market.fdv, 123456);
      expect(market.pairAddress, '0xpool');
    });

    test('has no market when the token is no pool\'s base, or the pool has no price', () {
      expect(
        geckoTerminalMarket({
          'data': [_pool(base: _weth, quote: _token)],
        }, _asset),
        isNull,
      );
      expect(
        geckoTerminalMarket({
          'data': [_pool(base: _token, quote: _weth, price: 'n/a')],
        }, _asset),
        isNull,
      );
      expect(geckoTerminalMarket(null, _asset), isNull);
    });
  });

  group('the token client', () {
    late List<String> hosts;
    late PriceSourceHealth health;

    setUp(() {
      hosts = [];
      health = PriceSourceHealth();
    });

    CryptoClient client({required http.Response dex, required http.Response gecko}) => CryptoClient(
      health: health,
      httpClient: MockClient((request) async {
        hosts.add(request.url.host);
        return request.url.host == 'api.dexscreener.com' ? dex : gecko;
      }),
    );

    http.Response pools() => http.Response(
      jsonEncode({
        'data': [_pool(base: _token, quote: _weth)],
      }),
      200,
    );

    test('asks DexScreener first, and GeckoTerminal not at all when it answers', () async {
      final tokens = client(dex: http.Response(jsonEncode([_dexPair()]), 200), gecko: pools());
      expect((await tokens.fetch(_asset))!.price, 0.04);
      expect(hosts, ['api.dexscreener.com']);
    });

    test('asks GeckoTerminal when DexScreener refuses, and leaves DexScreener resting', () async {
      final tokens = client(dex: http.Response('', 429), gecko: pools());
      expect((await tokens.fetch(_asset))!.price, 0.05);
      expect(health.resting(CryptoClient.dexScreener), isTrue);

      hosts.clear();
      await tokens.fetch(_asset);
      expect(hosts, ['api.geckoterminal.com'], reason: 'DexScreener rests');
    });

    test('asks GeckoTerminal when DexScreener has no market for the token, without resting it', () async {
      final tokens = client(dex: http.Response('[]', 200), gecko: pools());
      expect((await tokens.fetch(_asset))!.price, 0.05);
      expect(health.resting(CryptoClient.dexScreener), isFalse);
    });

    test('no market anywhere is no market; no answer anywhere is a failure', () async {
      final unknown = client(dex: http.Response('[]', 200), gecko: http.Response('{"errors":[]}', 404));
      expect(await unknown.fetch(_asset), isNull);
      expect(health.resting(CryptoClient.geckoTerminal), isFalse, reason: 'a 404 says it has no such token');

      final down = client(dex: http.Response('', 503), gecko: http.Response('', 429));
      await expectLater(down.fetch(_asset), throwsA(isA<CryptoException>()));
      expect(health.resting(CryptoClient.geckoTerminal), isTrue);
    });
  });
}
