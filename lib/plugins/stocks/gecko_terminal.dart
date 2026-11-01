import 'package:xta/plugins/stocks/crypto_address.dart';
import 'package:xta/plugins/stocks/crypto_asset.dart';
import 'package:xta/utils/json.dart';

/// GeckoTerminal's own ids for the networks DexScreener spells otherwise.
const Map<String, String> _networks = {
  'ethereum': 'eth',
  'polygon': 'polygon_pos',
  'avalanche': 'avax',
  'fantom': 'ftm',
  'cronos': 'cro',
  'sui': 'sui-network',
  'polygonzkevm': 'polygon-zkevm',
};

/// The network [chain] (as DexScreener spells it) is at GeckoTerminal.
String geckoTerminalNetwork(String chain) => _networks[chain.toLowerCase()] ?? chain.toLowerCase();

/// The pools [asset] trades in, the deepest and busiest first, from
/// GeckoTerminal's public API (no key, no account).
Uri geckoTerminalPoolsUri(CryptoAsset asset) => Uri.https(
  'api.geckoterminal.com',
  '/api/v2/networks/${geckoTerminalNetwork(asset.chain)}/tokens/${asset.address}/pools',
  {'page': '1'},
);

/// [asset]'s market from its pools: the first one it is the base token of,
/// whose figures are its own. A pool where it is the quote currency prices
/// the other token, so it is passed over. Null when none is.
CryptoMarket? geckoTerminalMarket(Object? json, CryptoAsset asset) {
  final pool = Json(json)['data'].list.where((pool) => _isBase(pool, asset)).firstOrNull;
  if (pool == null) return null;
  final figures = pool['attributes'];
  final price = finiteFigure(figures['base_token_price_usd'].number, atLeast: 0);
  if (price == null) return null;
  return CryptoMarket(
    asset: asset,
    pairAddress: figures['address'].string,
    price: price,
    changePercent: finiteFigure(figures['price_change_percentage']['h24'].number),
    change1h: finiteFigure(figures['price_change_percentage']['h1'].number),
    change6h: finiteFigure(figures['price_change_percentage']['h6'].number),
    liquidity: finiteFigure(figures['reserve_in_usd'].number) ?? 0,
    volume24h: finiteFigure(figures['volume_usd']['h24'].number, atLeast: 0),
    marketCap: finiteFigure(figures['market_cap_usd'].number, atLeast: 0),
    fdv: finiteFigure(figures['fdv_usd'].number, atLeast: 0),
  );
}

/// A base token's id is `{network}_{address}`.
bool _isBase(Json pool, CryptoAsset asset) {
  final id = pool['relationships']['base_token']['data']['id'].string;
  final prefix = '${geckoTerminalNetwork(asset.chain)}_';
  return id != null && id.startsWith(prefix) && sameCryptoAddress(id.substring(prefix.length), asset.address);
}
