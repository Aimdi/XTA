import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/plugins/stocks/crypto_asset.dart';
import 'package:xta/plugins/stocks/crypto_client.dart';
import 'package:xta/tweet/ticker/quote_failures.dart';

/// Token prices by asset id. A token whose lookup found no market is in
/// [failed], so its row says so rather than looking like it loads.
class CryptoQuoteStore extends Store<Map<String, CryptoMarket>> with QuoteFailures<Map<String, CryptoMarket>> {
  final CryptoClient client;
  final _fetched = <String, DateTime>{};
  final _inflight = <String>{};
  bool _closed = false;
  CryptoQuoteStore({CryptoClient? client}) : client = client ?? CryptoClient(), super(const {});

  Future<void> ensure(Iterable<CryptoAsset> assets, {bool force = false}) async {
    if (force) retrying(assets.map((asset) => asset.id));
    final needed = assets
        .where(
          (asset) =>
              !_inflight.contains(asset.id) &&
              (force ||
                  !_fetched.containsKey(asset.id) ||
                  DateTime.now().difference(_fetched[asset.id]!) >= const Duration(minutes: 2)),
        )
        .toList();
    for (var i = 0; i < needed.length && !_closed; i += 4) {
      await Future.wait(needed.skip(i).take(4).map(_fetch));
    }
  }

  Future<void> _fetch(CryptoAsset asset) async {
    _inflight.add(asset.id);
    try {
      final market = await client.fetch(asset);
      if (_closed) return;
      if (market == null) {
        recordFailure(asset.id);
      } else {
        recordAnswer(asset.id);
        update({...state, asset.id: market});
      }
    } on CryptoException {
      // A failed price lookup never replaces the asset with an equal ticker.
      if (!_closed) recordFailure(asset.id);
    } finally {
      _inflight.remove(asset.id);
      _fetched[asset.id] = DateTime.now();
    }
  }

  @override
  Future<void> destroy() {
    _closed = true;
    client.close();
    return super.destroy();
  }
}
