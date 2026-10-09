import 'dart:async';

import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/plugins/stocks/crypto_address.dart';
import 'package:xta/plugins/stocks/crypto_asset.dart';
import 'package:xta/plugins/stocks/crypto_client.dart';
import 'package:xta/tweet/ticker/ticker_client.dart';
import 'package:xta/tweet/ticker/ticker_search.dart';

class StocksSearchState {
  final String query;
  final bool crypto;
  final bool loading;
  final bool failed;
  final List<TickerSearchHit> stocks;
  final List<CryptoMarket> tokens;

  /// Set when the query is a pasted contract address: the results are then
  /// that exact contract on each network it exists on, not a name search.
  final CryptoAddressInput? address;

  const StocksSearchState({
    this.query = '',
    this.crypto = false,
    this.loading = false,
    this.failed = false,
    this.stocks = const [],
    this.tokens = const [],
    this.address,
  });
}

class StocksSearchStore extends Store<StocksSearchState> {
  final TickerClient tickerClient;
  final CryptoClient cryptoClient;
  Timer? _debounce;
  int _generation = 0;
  bool _closed = false;
  StocksSearchStore({required this.tickerClient, required this.cryptoClient}) : super(const StocksSearchState());

  /// A pasted address switches to crypto on its own: no stock has one, and
  /// the reader should not have to find the right chip first.
  void change(String query, {bool? crypto, bool immediate = false}) {
    _debounce?.cancel();
    final generation = ++_generation;
    final trimmed = query.trim();
    final address = detectCryptoAddress(trimmed);
    final isCrypto = address != null || (crypto ?? state.crypto);
    update(StocksSearchState(query: trimmed, crypto: isCrypto, address: address, loading: trimmed.isNotEmpty));
    if (trimmed.isEmpty) return;
    void run() => _search(trimmed, isCrypto, address, generation);
    if (immediate || address != null) {
      run();
    } else {
      _debounce = Timer(const Duration(milliseconds: 300), run);
    }
  }

  Future<void> _search(String query, bool crypto, CryptoAddressInput? address, int generation) async {
    try {
      final stocks = crypto ? <TickerSearchHit>[] : await tickerClient.searchSymbols(query);
      final tokens = address != null
          ? await cryptoClient.resolve(address)
          : (crypto ? await cryptoClient.search(query) : <CryptoMarket>[]);
      if (_closed || generation != _generation) return;
      update(StocksSearchState(query: query, crypto: crypto, address: address, stocks: stocks, tokens: tokens));
    } catch (_) {
      if (_closed || generation != _generation) return;
      update(StocksSearchState(query: query, crypto: crypto, address: address, failed: true));
    }
  }

  @override
  Future<void> destroy() {
    _closed = true;
    _debounce?.cancel();
    return super.destroy();
  }
}
