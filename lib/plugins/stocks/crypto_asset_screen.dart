import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/stocks/crypto_asset.dart';
import 'package:xta/plugins/stocks/crypto_copy.dart';
import 'package:xta/plugins/stocks/crypto_detail_store.dart';
import 'package:xta/plugins/stocks/stocks_format.dart';
import 'package:xta/plugins/stocks/stocks_posts_feed.dart';
import 'package:xta/plugins/stocks/stocks_quote_row.dart';
import 'package:xta/plugins/stocks/stocks_store.dart';
import 'package:xta/tweet/ticker/ticker_stats.dart';
import 'package:xta/utils/urls.dart';

/// A token followed by its contract: price and pool figures from DEX
/// Screener, then the posts on X that mention the contract address.
class CryptoAssetScreen extends StatefulWidget {
  final CryptoAsset asset;
  const CryptoAssetScreen({super.key, required this.asset});
  @override
  State<CryptoAssetScreen> createState() => _CryptoAssetScreenState();
}

class _CryptoAssetScreenState extends State<CryptoAssetScreen> {
  late final _market = CryptoDetailStore(includeCashtag: widget.asset.includeCashtag);

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

  StocksWatchlistStore get _watchlist => context.read<StocksWatchlistStore>();

  bool get _watched => _watchlist.state.contains(widget.asset.id);

  void _toggleWatch() {
    final asset = widget.asset.withCashtag(_market.state.includeCashtag);
    _watched ? _watchlist.remove(asset.id) : _watchlist.add(asset.encode());
  }

  /// The choice is the token's, so a watched token remembers it.
  void _setIncludeCashtag(bool include) {
    _market.setIncludeCashtag(include);
    if (_watched) _watchlist.setIncludeCashtag(widget.asset, include);
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return Scaffold(
      appBar: AppBar(
        title: Text(widget.asset.label),
        actions: [
          ScopedBuilder<StocksWatchlistStore, List<String>>(
            store: _watchlist,
            onState: (_, ids) => IconButton(
              tooltip: ids.contains(widget.asset.id) ? l10n.plugin_stocks_unwatch : l10n.plugin_stocks_watch,
              icon: Icon(ids.contains(widget.asset.id) ? Icons.star : Icons.star_outline),
              onPressed: _toggleWatch,
            ),
          ),
        ],
      ),
      body: ScopedBuilder<CryptoDetailStore, CryptoDetailState>(
        store: _market,
        onState: (context, state) {
          final asset = widget.asset.withCashtag(state.includeCashtag);
          return NestedScrollView(
            headerSliverBuilder: (context, _) => [
              SliverToBoxAdapter(
                child: CryptoAssetHeader(
                  asset: asset,
                  state: state,
                  onRetry: _loadQuote,
                  onIncludeCashtag: _setIncludeCashtag,
                ),
              ),
            ],
            body: StocksPostsFeed(
              key: ValueKey(asset.searchQuery),
              query: asset.searchQuery,
              onRefreshQuotes: _loadQuote,
            ),
          );
        },
      ),
    );
  }
}

/// Name, contract, price, pool figures and the posts choice above a token's
/// feed.
class CryptoAssetHeader extends StatelessWidget {
  final CryptoAsset asset;
  final CryptoDetailState state;
  final Future<void> Function() onRetry;
  final ValueChanged<bool> onIncludeCashtag;

  const CryptoAssetHeader({
    super.key,
    required this.asset,
    required this.state,
    required this.onRetry,
    required this.onIncludeCashtag,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 8),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(asset.name, style: theme.textTheme.titleLarge!.copyWith(fontWeight: FontWeight.w900)),
          _ContractLine(asset: asset),
          if (state.loading) const LinearProgressIndicator(),
          if (state.failed && state.market == null) _unavailable(context),
          _CryptoPrice(market: state.market),
          const SizedBox(height: 8),
          _CryptoStats(market: state.market),
          const SizedBox(height: 8),
          _links(context, state.market),
          _postsChoice(context),
        ],
      ),
    );
  }

  Widget _unavailable(BuildContext context) {
    final l10n = L10n.of(context);
    return ListTile(
      contentPadding: EdgeInsets.zero,
      leading: const Icon(Icons.cloud_off_outlined),
      title: Text(l10n.plugin_stocks_data_unavailable),
      trailing: TextButton(onPressed: onRetry, child: Text(l10n.retry)),
    );
  }

  Widget _links(BuildContext context, CryptoMarket? market) {
    final l10n = L10n.of(context);
    final pair = market?.pairAddress;
    return Wrap(
      spacing: 8,
      children: [
        OutlinedButton.icon(
          icon: const Icon(Icons.copy_outlined),
          label: Text(l10n.plugin_stocks_crypto_copy_contract),
          onPressed: () => copyCryptoAddress(context, asset),
        ),
        OutlinedButton.icon(
          icon: const Icon(Icons.open_in_new),
          label: Text(l10n.open_in_browser),
          onPressed: pair == null
              ? null
              : () => openUri(context, Uri.https('dexscreener.com', '/${asset.chain}/$pair').toString()),
        ),
      ],
    );
  }

  /// Contract-only by default; the cashtag is one tap away, with the caveat
  /// that other tokens can share it spelled out beside the switch.
  Widget _postsChoice(BuildContext context) {
    final l10n = L10n.of(context);
    final cashtag = asset.cashtag;
    final hint = state.includeCashtag && cashtag != null
        ? l10n.plugin_stocks_crypto_posts_hint_cashtag(cashtag)
        : l10n.plugin_stocks_crypto_posts_hint;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        if (cashtag != null)
          FilterChip(
            label: Text(l10n.plugin_stocks_crypto_include_cashtag(cashtag)),
            selected: state.includeCashtag,
            onSelected: onIncludeCashtag,
          ),
        Text(hint, style: Theme.of(context).textTheme.bodySmall),
      ],
    );
  }
}

/// `Ethereum · 0x6982…1933` — tap or long-press copies the whole address.
class _ContractLine extends StatelessWidget {
  final CryptoAsset asset;

  const _ContractLine({required this.asset});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final l10n = L10n.of(context);
    return Semantics(
      button: true,
      label: '${l10n.plugin_stocks_crypto_contract}, ${asset.chainName}, ${asset.address}',
      hint: l10n.plugin_stocks_crypto_copy_contract,
      excludeSemantics: true,
      child: InkWell(
        onTap: () => copyCryptoAddress(context, asset),
        onLongPress: () => copyCryptoAddress(context, asset),
        child: ConstrainedBox(
          constraints: const BoxConstraints(minHeight: 48),
          child: Row(
            children: [
              Flexible(
                child: Text(
                  asset.subtitle,
                  style: theme.textTheme.bodyMedium!.copyWith(color: theme.colorScheme.onSurfaceVariant),
                ),
              ),
              const SizedBox(width: 6),
              Icon(Icons.copy_outlined, size: 16, color: theme.colorScheme.onSurfaceVariant),
            ],
          ),
        ),
      ),
    );
  }
}

class _CryptoPrice extends StatelessWidget {
  final CryptoMarket? market;

  const _CryptoPrice({required this.market});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final price = market?.price;
    final percent = market?.changePercent;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Wrap(
          spacing: 6,
          crossAxisAlignment: WrapCrossAlignment.end,
          children: [
            Text(
              price == null ? kStockPlaceholder : stockPrice(price),
              style: theme.textTheme.headlineMedium!.copyWith(fontWeight: FontWeight.w800, fontFeatures: kStockFigures),
            ),
            Padding(
              padding: const EdgeInsets.only(bottom: 4),
              child: Text(
                'USD',
                style: theme.textTheme.bodyMedium!.copyWith(color: theme.colorScheme.onSurfaceVariant),
              ),
            ),
          ],
        ),
        if (percent != null)
          Wrap(
            spacing: 8,
            crossAxisAlignment: WrapCrossAlignment.center,
            children: [
              Text(
                stockPercentLabel(percent),
                style: theme.textTheme.titleSmall!.copyWith(
                  fontWeight: FontWeight.w700,
                  color: stockTrendColour(context, percent),
                ),
              ),
              Text(
                L10n.of(context).plugin_stocks_change_hours(24),
                style: theme.textTheme.bodySmall!.copyWith(color: theme.colorScheme.onSurfaceVariant),
              ),
            ],
          ),
      ],
    );
  }
}

/// The figures DEX pages put under a token: size, depth, turnover, and the
/// shorter moves the 24-hour figure hides.
class _CryptoStats extends StatelessWidget {
  final CryptoMarket? market;

  const _CryptoStats({required this.market});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final market = this.market;
    String? usd(double? value) => value == null ? null : stockCompactUsd(value);
    String? percent(double? value) => value == null ? null : stockPercentLabel(value);
    return TickerStatGrid(
      stats: [
        (label: l10n.plugin_stocks_market_cap, value: usd(market?.marketCap)),
        (label: l10n.plugin_stocks_fdv, value: usd(market?.fdv)),
        (
          label: l10n.plugin_stocks_liquidity,
          value: market == null || market.liquidity == 0 ? null : usd(market.liquidity),
        ),
        (label: l10n.plugin_stocks_volume_24h, value: usd(market?.volume24h)),
        (label: l10n.plugin_stocks_change_hours(1), value: percent(market?.change1h)),
        (label: l10n.plugin_stocks_change_hours(6), value: percent(market?.change6h)),
      ],
    );
  }
}
