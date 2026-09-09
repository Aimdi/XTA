import 'dart:async';

import 'package:flutter_triple/flutter_triple.dart';
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
  const StocksSearchState({
    this.query = '',
    this.crypto = false,
    this.loading = false,
    this.failed = false,
    this.stocks = const [],
    this.tokens = const [],
  });
}

class StocksSearchStore extends Store<StocksSearchState> {
  final TickerClient tickerClient;
  final CryptoClient cryptoClient;
  Timer? _debounce;
  int _generation = 0;
  bool _closed = false;
  StocksSearchStore({required this.tickerClient, required this.cryptoClient}) : super(const StocksSearchState());

  void change(String query, {bool? crypto, bool immediate = false}) {
    _debounce?.cancel();
    final generation = ++_generation;
    final isCrypto = crypto ?? state.crypto;
    update(StocksSearchState(query: query.trim(), crypto: isCrypto, loading: query.trim().isNotEmpty));
    if (query.trim().isEmpty) return;
    if (immediate) {
      unawaited(_search(state.query, isCrypto, generation));
    } else {
      _debounce = Timer(const Duration(milliseconds: 300), () => _search(state.query, isCrypto, generation));
    }
  }

  Future<void> _search(String query, bool crypto, int generation) async {
    try {
      final stocks = crypto ? <TickerSearchHit>[] : await tickerClient.searchSymbols(query);
      final tokens = crypto ? await cryptoClient.search(query) : <CryptoMarket>[];
      if (_closed || generation != _generation) return;
      update(StocksSearchState(query: query, crypto: crypto, stocks: stocks, tokens: tokens));
    } catch (_) {
      if (_closed || generation != _generation) return;
      update(StocksSearchState(query: query, crypto: crypto, failed: true));
    }
  }

  @override
  Future<void> destroy() {
    _closed = true;
    _debounce?.cancel();
    return super.destroy();
  }
}
