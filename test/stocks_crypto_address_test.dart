import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:xta/plugins/stocks/crypto_address.dart';
import 'package:xta/plugins/stocks/crypto_asset.dart';
import 'package:xta/plugins/stocks/crypto_client.dart';
import 'package:xta/plugins/stocks/stocks_search_store.dart';
import 'package:xta/plugins/stocks/stocks_watchlist_query.dart';
import 'package:xta/tweet/ticker/ticker_client.dart';

/// PEPE's mainnet contract, in its EIP-55 checksum casing.
const _pepe = '0x6982508145454Ce325dDbE47a25d4ec3d2311933';
const _pepeLower = '0x6982508145454ce325ddbe47a25d4ec3d2311933';
const _weth = '0xc02aaa39b223fe8d0a0e5c4f27ead9083c756cc2';

/// BONK's Solana mint.
const _bonk = 'DezXAZ8z7PnrnRJjz3wXBoRgixCa6xjnB7YaB1pPB263';

Map<String, Object?> _token(String address, String symbol, String name) => {
  'address': address,
  'symbol': symbol,
  'name': name,
};

/// The documented `/latest/dex/search` pair shape, trimmed to what is read.
Map<String, Object?> _pair({
  required String chain,
  required Map<String, Object?> base,
  required Map<String, Object?> quote,
  String price = '0.0000123',
  double liquidity = 1000,
}) => {
  'chainId': chain,
  'dexId': 'uniswap',
  'pairAddress': '0xpool$chain',
  'baseToken': base,
  'quoteToken': quote,
  'priceUsd': price,
  'priceChange': {'m5': 0.1, 'h1': -1.5, 'h6': 3.25, 'h24': 12.5},
  'volume': {'h24': 4200000, 'h6': 900000},
  'liquidity': {'usd': liquidity, 'base': 1, 'quote': 2},
  'fdv': 5100000000,
  'marketCap': 4900000000,
};

void main() {
  group('a pasted contract address', () {
    test('an EVM address is recognised whatever its checksum casing', () {
      for (final raw in [_pepe, _pepeLower, _pepe.toUpperCase().replaceFirst('0X', '0x')]) {
        final input = detectCryptoAddress(raw)!;
        expect(input.kind, CryptoAddressKind.evm);
        expect(input.address, _pepeLower);
      }
    });

    test('a Solana mint is recognised and keeps its case', () {
      final input = detectCryptoAddress('  $_bonk ')!;
      expect(input.kind, CryptoAddressKind.solana);
      expect(input.address, _bonk);
    });

    test('a cashtag prefix and explorer links are looked through', () {
      expect(detectCryptoAddress('\$$_pepe')?.address, _pepeLower);
      expect(detectCryptoAddress('https://etherscan.io/token/$_pepe')?.address, _pepeLower);
      expect(detectCryptoAddress('https://dexscreener.com/solana/$_bonk')?.address, _bonk);
    });

    test('things that only look close are not addresses', () {
      for (final raw in [
        '',
        'PEPE',
        '\$AAPL',
        '0x6982508145454ce325ddbe47a25d4ec3d231193', // 39 hex digits
        '0x6982508145454ce325ddbe47a25d4ec3d23119333', // 41 hex digits
        '0x6982508145454ce325ddbe47a25d4ec3d231193g', // not hex
        'DezXAZ8z7PnrnRJjz3wXBoRgixCa6xjnB7YaB1pPB26O', // O is not base58
        'bitcoincashbitcoincashbitcoincashx', // letters only: a name, not a mint
        'Dez', // far too short
      ]) {
        expect(detectCryptoAddress(raw), isNull, reason: raw);
      }
    });

    test('rows show the network and a shortened address', () {
      expect(shortCryptoAddress(_pepeLower), '0x6982…1933');
      expect(shortCryptoAddress(_bonk), 'DezX…B263');
      expect(shortCryptoAddress('short'), 'short');
      expect(cryptoChainName('bsc'), 'BNB Chain');
      expect(cryptoChainName('newchain'), 'newchain');
      const asset = CryptoAsset(chain: 'ethereum', address: _pepeLower, symbol: 'PEPE', name: 'Pepe');
      expect(asset.subtitle, 'Ethereum · 0x6982…1933');
    });

    test('addresses compare case-insensitively only on EVM', () {
      expect(sameCryptoAddress(_pepe, _pepeLower), isTrue);
      expect(sameCryptoAddress(_bonk, _bonk.toLowerCase()), isFalse);
    });
  });

  group('resolving an address to tokens', () {
    test('one market per network, from either side of a pair', () {
      final markets = cryptoMarketsForAddress({
        'pairs': [
          _pair(chain: 'ethereum', base: _token(_pepe, 'PEPE', 'Pepe'), quote: _token(_weth, 'WETH', 'Wrapped Ether')),
          _pair(
            chain: 'ethereum',
            base: _token(_pepe, 'PEPE', 'Pepe'),
            quote: _token(_weth, 'WETH', 'Wrapped Ether'),
            liquidity: 9000,
            price: '0.0000125',
          ),
          _pair(chain: 'base', base: _token(_weth, 'WETH', 'Wrapped Ether'), quote: _token(_pepe, 'PEPE', 'Pepe')),
          _pair(chain: 'ethereum', base: _token(_weth, 'WETH', 'Wrapped Ether'), quote: _token(_weth, 'X', 'X')),
        ],
      }, _pepeLower);

      expect(markets.map((m) => m.asset.chain), ['ethereum', 'base']);
      final mainnet = markets.first;
      expect(mainnet.price, closeTo(0.0000125, 1e-12), reason: 'the deeper pool wins');
      expect(mainnet.asset.id, 'crypto:ethereum:$_pepeLower');
      expect(mainnet.asset.symbol, 'PEPE');
      expect(markets.last.price, isNull, reason: 'as quote currency the pair price is the other token');
      expect(markets.last.asset.symbol, 'PEPE');
    });

    test('pool figures are read with the price', () {
      final market = cryptoMarketsForAddress([
        _pair(chain: 'ethereum', base: _token(_pepe, 'PEPE', 'Pepe'), quote: _token(_weth, 'WETH', 'WETH')),
      ], _pepe).single;
      expect(market.changePercent, 12.5);
      expect(market.change1h, -1.5);
      expect(market.change6h, 3.25);
      expect(market.volume24h, 4200000);
      expect(market.marketCap, 4900000000);
      expect(market.fdv, 5100000000);
      expect(market.liquidity, 1000);
      expect(market.quote.changePercent, closeTo(12.5, 1e-9));
      expect(market.quote.tradesAroundTheClock, isTrue);
    });

    test('a Solana mint differing only in case is a different token', () {
      final markets = cryptoMarketsForAddress([
        _pair(chain: 'solana', base: _token(_bonk.toLowerCase(), 'FAKE', 'Fake'), quote: _token('So1', 'SOL', 'SOL')),
      ], _bonk);
      expect(markets, isEmpty);
    });

    test('the client searches the address and prices quote-only networks', () async {
      final asked = <Uri>[];
      final client = CryptoClient(
        httpClient: MockClient((request) async {
          asked.add(request.url);
          if (request.url.path == '/latest/dex/search') {
            return http.Response(
              jsonEncode({
                'pairs': [
                  _pair(chain: 'base', base: _token(_weth, 'WETH', 'WETH'), quote: _token(_pepe, 'PEPE', 'Pepe')),
                ],
              }),
              200,
            );
          }
          return http.Response(
            jsonEncode([
              _pair(chain: 'base', base: _token(_pepe, 'PEPE', 'Pepe'), quote: _token(_weth, 'WETH', 'WETH')),
            ]),
            200,
          );
        }),
      );

      final markets = await client.resolve(detectCryptoAddress(_pepe)!);

      expect(asked.first.host, 'api.dexscreener.com');
      expect(asked.first.queryParameters['q'], _pepeLower);
      expect(asked.last.path, '/token-pairs/v1/base/$_pepeLower');
      expect(markets.single.asset.chain, 'base');
      expect(markets.single.price, closeTo(0.0000123, 1e-12));
      client.close();
    });

    test('pasting an address switches the add sheet to crypto at once', () async {
      final crypto = CryptoClient(
        httpClient: MockClient(
          (_) async => http.Response(
            jsonEncode({
              'pairs': [
                _pair(chain: 'solana', base: _token(_bonk, 'Bonk', 'Bonk'), quote: _token('So1', 'SOL', 'SOL')),
              ],
            }),
            200,
          ),
        ),
      );
      final ticker = TickerClient(httpClient: MockClient((_) async => fail('no stock search for an address')));
      final store = StocksSearchStore(tickerClient: ticker, cryptoClient: crypto);

      store.change(_bonk, crypto: false);
      expect(store.state.crypto, isTrue);
      expect(store.state.address?.kind, CryptoAddressKind.solana);
      await Future<void>.delayed(Duration.zero);
      await Future<void>.delayed(Duration.zero);

      expect(store.state.loading, isFalse);
      expect(store.state.tokens.single.asset.address, _bonk);
      await store.destroy();
      crypto.close();
      ticker.httpClient.close();
    });
  });

  group('posts for a token', () {
    const asset = CryptoAsset(chain: 'ethereum', address: _pepeLower, symbol: 'PEPE', name: 'Pepe');

    test('search the contract alone unless the cashtag is chosen', () {
      expect(asset.searchQuery, '"$_pepeLower"');
      expect(asset.withCashtag(true).searchQuery, '("$_pepeLower" OR \$PEPE)');
    });

    test('a symbol X cannot write as a cashtag is never added', () {
      const odd = CryptoAsset(
        chain: 'solana',
        address: _bonk,
        symbol: 'W I F',
        name: 'dogwifhat',
        includeCashtag: true,
      );
      expect(odd.cashtag, isNull);
      expect(odd.searchQuery, '"$_bonk"');
    });

    test('the cashtag choice survives encoding; old rows read as off', () {
      final on = CryptoAsset.decode(asset.withCashtag(true).encode())!;
      expect(on.includeCashtag, isTrue);
      expect(asset.encode(), isNot(contains('cashtag')), reason: 'rows saved before stay byte-identical');
      expect(CryptoAsset.decode(asset.encode())!.includeCashtag, isFalse);
    });

    test('the watchlist feed follows each token\'s choice', () {
      final chosen = asset.withCashtag(true);
      expect(
        watchlistCashtagQuery(['AAPL', chosen.id], assets: {chosen.id: chosen}),
        '(\$AAPL OR "$_pepeLower" OR \$PEPE)',
      );
      expect(watchlistCashtagQuery([asset.id], assets: {asset.id: asset}), '"$_pepeLower"');
    });
  });
}
