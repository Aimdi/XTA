import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/plugins/stocks/crypto_asset.dart';
import 'package:xta/plugins/stocks/crypto_client.dart';

class CryptoDetailState {
  final CryptoMarket? market;
  final bool loading;
  final bool failed;

  /// Whether the posts below also search the token's cashtag.
  final bool includeCashtag;

  const CryptoDetailState({this.market, this.loading = false, this.failed = false, this.includeCashtag = false});

  CryptoDetailState copyWith({CryptoMarket? market, bool? loading, bool? failed, bool? includeCashtag}) =>
      CryptoDetailState(
        market: market ?? this.market,
        loading: loading ?? this.loading,
        failed: failed ?? this.failed,
        includeCashtag: includeCashtag ?? this.includeCashtag,
      );
}

class CryptoDetailStore extends Store<CryptoDetailState> {
  final CryptoClient client;
  bool _closed = false;
  CryptoDetailStore({CryptoClient? client, bool includeCashtag = false})
    : client = client ?? CryptoClient(),
      super(CryptoDetailState(includeCashtag: includeCashtag));

  Future<void> load(CryptoAsset asset) async {
    if (state.loading || _closed) return;
    update(state.copyWith(loading: true, failed: false));
    try {
      final market = await client.fetch(asset);
      if (!_closed) update(state.copyWith(market: market, loading: false, failed: market == null));
    } on CryptoException {
      if (!_closed) update(state.copyWith(loading: false, failed: true));
    }
  }

  void setIncludeCashtag(bool include) => update(state.copyWith(includeCashtag: include));

  @override
  Future<void> destroy() {
    _closed = true;
    client.close();
    return super.destroy();
  }
}
