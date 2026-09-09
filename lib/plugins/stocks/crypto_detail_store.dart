import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/plugins/stocks/crypto_asset.dart';
import 'package:xta/plugins/stocks/crypto_client.dart';

class CryptoDetailState {
  final CryptoMarket? market;
  final bool loading;
  final bool failed;
  const CryptoDetailState({this.market, this.loading = false, this.failed = false});
}

class CryptoDetailStore extends Store<CryptoDetailState> {
  final CryptoClient client;
  bool _closed = false;
  CryptoDetailStore({CryptoClient? client}) : client = client ?? CryptoClient(), super(const CryptoDetailState());

  Future<void> load(CryptoAsset asset) async {
    if (state.loading || _closed) return;
    update(CryptoDetailState(market: state.market, loading: true));
    try {
      final market = await client.fetch(asset);
      if (!_closed) update(CryptoDetailState(market: market, failed: market == null));
    } on CryptoException {
      if (!_closed) update(CryptoDetailState(market: state.market, failed: true));
    }
  }

  @override
  Future<void> destroy() {
    _closed = true;
    client.close();
    return super.destroy();
  }
}
