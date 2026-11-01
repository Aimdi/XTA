import 'package:flutter/material.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/stocks/stocks_format.dart';
import 'package:xta/plugins/stocks/crypto_asset.dart';
import 'package:xta/plugins/stocks/stocks_quote_row.dart';
import 'package:xta/plugins/stocks/stocks_row_state.dart';
import 'package:xta/tweet/ticker/ticker_quote.dart';
import 'package:xta/ui/x_controls.dart';

/// The watchlist as a horizontal strip of symbol chips — price + day's move —
/// pinned above the social feed, the way StockTwits puts watched tickers over
/// the stream rather than repeating them as a second list.
///
/// Quotes are passed in rather than fetched here: every chip wants one, and a
/// strip that fetched its own would race the parent loading the same symbols.
class StocksWatchlistReel extends StatelessWidget {
  final List<String> symbols;
  final Map<String, TickerQuote> quotes;

  /// Symbols and token ids whose last lookup found nothing.
  final Set<String> failed;

  /// Which symbol is "selected" in the strip, if any — used when the strip
  /// filters the feed to one ticker.
  final String? selected;
  final ValueChanged<String>? onSelected;
  final ValueChanged<String>? onOpen;
  final Map<String, CryptoAsset> assets;

  const StocksWatchlistReel({
    super.key,
    required this.symbols,
    required this.quotes,
    this.failed = const {},
    this.selected,
    this.onSelected,
    this.onOpen,
    this.assets = const {},
  });

  @override
  Widget build(BuildContext context) {
    void open(String symbol) => (onOpen ?? (symbol) => openTicker(context, symbol))(symbol);
    return SizedBox(
      height: 48 + MediaQuery.textScalerOf(context).scale(36),
      child: ListView.separated(
        scrollDirection: Axis.horizontal,
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
        itemCount: symbols.length,
        separatorBuilder: (_, _) => const SizedBox(width: 8),
        itemBuilder: (context, index) {
          final symbol = symbols[index];
          return _WatchlistChip(
            label: assets[symbol]?.label ?? '\$$symbol',
            quote: quotes[symbol],
            failed: failed.contains(symbol),
            selected: selected == symbol,
            onTap: () => onSelected == null ? open(symbol) : onSelected!(symbol),
            onLongPress: () => open(symbol),
          );
        },
      ),
    );
  }
}

/// Compact chip: `$AAPL` over its price and a green/red % pill, StockTwits's
/// watchlist reading rather than a mini chart card. Like a row, it shimmers
/// while it loads, mutes a closed market's move and says when it has no data.
class _WatchlistChip extends StatelessWidget {
  final String label;
  final TickerQuote? quote;
  final bool failed;
  final bool selected;
  final VoidCallback onTap;
  final VoidCallback onLongPress;

  const _WatchlistChip({
    required this.label,
    required this.quote,
    required this.failed,
    required this.selected,
    required this.onTap,
    required this.onLongPress,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final now = DateTime.now();
    final state = stockRowState(quote, failed: failed, now: now);
    final outline = selected ? theme.colorScheme.primary : theme.colorScheme.outlineVariant;

    return Semantics(
      selected: selected,
      button: true,
      label: _label(context, state, now),
      excludeSemantics: true,
      child: Material(
        color: selected ? theme.colorScheme.primaryContainer : xControlFill(context),
        shape: StadiumBorder(side: BorderSide(color: outline)),
        clipBehavior: Clip.antiAlias,
        child: InkWell(
          onTap: onTap,
          onLongPress: onLongPress,
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Text(
                  '${selected ? '✓ ' : ''}$label',
                  maxLines: 1,
                  style: theme.textTheme.labelLarge!.copyWith(fontWeight: FontWeight.w800),
                ),
                const SizedBox(height: 2),
                _figures(context, state),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _figures(BuildContext context, StockRowState state) {
    final theme = Theme.of(context);
    final quote = this.quote;
    if (state == StockRowState.loading) return const StocksFiguresSkeleton(inline: true);
    if (state == StockRowState.noData || quote == null) {
      return Text(
        L10n.of(context).plugin_stocks_no_data,
        style: theme.textTheme.bodySmall!.copyWith(color: theme.colorScheme.onSurfaceVariant),
      );
    }
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Text(
          stockPrice(quote.displayPrice!),
          style: theme.textTheme.bodySmall!.copyWith(
            fontWeight: FontWeight.w600,
            fontFeatures: kStockFigures,
            color: theme.colorScheme.onSurface,
          ),
        ),
        if (quote.changePercent != null) ...[
          const SizedBox(width: 6),
          StocksChangeChip(percent: quote.changePercent, muted: state == StockRowState.closed),
        ],
      ],
    );
  }

  String _label(BuildContext context, StockRowState state, DateTime now) {
    final l10n = L10n.of(context);
    final quote = this.quote;
    final percent = quote?.changePercent;
    return [
      label,
      ...switch (state) {
        StockRowState.loading => [l10n.plugin_stocks_loading_price],
        StockRowState.noData => [l10n.plugin_stocks_no_data],
        StockRowState.closed => [
          stockPriceSpoken(quote!.displayPrice!),
          stockClosedTag(context, quote, now: now),
          if (percent != null) stockPercentLabel(percent),
        ],
        StockRowState.live => [
          stockPriceSpoken(quote!.displayPrice!),
          if (percent != null) ...[stockPercentLabel(percent), stockWindowLabel(l10n, quote.changeWindow)],
        ],
      },
    ].join(', ');
  }
}
