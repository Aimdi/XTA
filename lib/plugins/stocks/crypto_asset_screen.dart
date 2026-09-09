import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/stocks/crypto_asset.dart';
import 'package:xta/plugins/stocks/crypto_detail_store.dart';
import 'package:xta/plugins/stocks/stocks_format.dart';
import 'package:xta/plugins/stocks/stocks_posts_feed.dart';
import 'package:xta/plugins/stocks/stocks_store.dart';
import 'package:xta/utils/urls.dart';

class CryptoAssetScreen extends StatefulWidget {
  final CryptoAsset asset;
  const CryptoAssetScreen({super.key, required this.asset});
  @override
  State<CryptoAssetScreen> createState() => _CryptoAssetScreenState();
}

class _CryptoAssetScreenState extends State<CryptoAssetScreen> {
  final _market = CryptoDetailStore();

  @override
  void initState() {
    super.initState();
    _loadQuote();
  }

  Future<void> _loadQuote() => _market.load(widget.asset);

  @override
  void dispose() {
    _market.destroy();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final asset = widget.asset;
    final l10n = L10n.of(context);
    return Scaffold(
      appBar: AppBar(
        title: Text(asset.label),
        actions: [
          ScopedBuilder<StocksWatchlistStore, List<String>>(
            store: context.read<StocksWatchlistStore>(),
            onState: (_, ids) => IconButton(
              tooltip: ids.contains(asset.id) ? l10n.plugin_stocks_unwatch : l10n.plugin_stocks_watch,
              icon: Icon(ids.contains(asset.id) ? Icons.star : Icons.star_outline),
              onPressed: () {
                final store = context.read<StocksWatchlistStore>();
                ids.contains(asset.id) ? store.remove(asset.id) : store.add(asset.encode());
              },
            ),
          ),
        ],
      ),
      body: NestedScrollView(
        headerSliverBuilder: (context, _) => [
          SliverToBoxAdapter(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(asset.name, style: Theme.of(context).textTheme.headlineSmall),
                  Text(asset.chain, style: Theme.of(context).textTheme.titleMedium),
                  const SizedBox(height: 12),
                  ScopedBuilder<CryptoDetailStore, CryptoDetailState>(
                    store: _market,
                    onState: (_, state) => Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        if (state.loading) const LinearProgressIndicator(),
                        if (state.failed)
                          ListTile(
                            contentPadding: EdgeInsets.zero,
                            title: Text(l10n.plugin_stocks_error),
                            trailing: TextButton(onPressed: _loadQuote, child: Text(l10n.retry)),
                          ),
                        _price(context, state.market),
                      ],
                    ),
                  ),
                  const SizedBox(height: 16),
                  Text(l10n.plugin_stocks_crypto_contract, style: Theme.of(context).textTheme.labelLarge),
                  const SizedBox(height: 4),
                  SelectableText(asset.address),
                  const SizedBox(height: 8),
                  Wrap(
                    spacing: 8,
                    children: [
                      OutlinedButton.icon(
                        icon: const Icon(Icons.copy_outlined),
                        label: Text(l10n.plugin_stocks_crypto_copy_contract),
                        onPressed: () => Clipboard.setData(ClipboardData(text: asset.address)),
                      ),
                      ScopedBuilder<CryptoDetailStore, CryptoDetailState>(
                        store: _market,
                        onState: (_, state) => OutlinedButton.icon(
                          icon: const Icon(Icons.open_in_new),
                          label: Text(l10n.open_in_browser),
                          onPressed: state.market?.pairAddress == null
                              ? null
                              : () => openUri(
                                  context,
                                  Uri.https(
                                    'dexscreener.com',
                                    '/${asset.chain}/${state.market!.pairAddress}',
                                  ).toString(),
                                ),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 12),
                  Text(l10n.plugin_stocks_crypto_posts_hint, style: Theme.of(context).textTheme.bodySmall),
                ],
              ),
            ),
          ),
        ],
        body: StocksPostsFeed(query: asset.searchQuery, onRefreshQuotes: _loadQuote),
      ),
    );
  }

  Widget _price(BuildContext context, CryptoMarket? market) {
    final percent = market?.changePercent;
    return Wrap(
      spacing: 12,
      crossAxisAlignment: WrapCrossAlignment.center,
      children: [
        Text(
          market?.price == null ? kStockPlaceholder : '${stockAssetPrice(market!.price!)} USD',
          style: Theme.of(context).textTheme.headlineMedium,
        ),
        if (percent != null)
          Text(
            stockPercentLabel(percent),
            style: Theme.of(context).textTheme.titleMedium!.copyWith(color: stockChangeColour(percent >= 0)),
          ),
      ],
    );
  }
}
