import 'package:flutter/material.dart';
import 'package:xta/plugins/stocks/stocks_format.dart';
import 'package:xta/plugins/stocks/crypto_asset.dart';
import 'package:xta/tweet/ticker/ticker_quote.dart';
import 'package:xta/ui/x_controls.dart';

/// Height of the StockTwits-style watchlist strip above the feed.
const double kStockWatchlistStripHeight = 72;

/// The watchlist as a horizontal strip of symbol chips — price + day's move —
/// pinned above the social feed, the way StockTwits puts watched tickers over
/// the stream rather than repeating them as a second list.
///
/// Quotes are passed in rather than fetched here: every chip wants one, and a
/// strip that fetched its own would race the parent loading the same symbols.
class StocksWatchlistReel extends StatelessWidget {
  final List<String> symbols;
  final Map<String, TickerQuote> quotes;

  /// Which symbol is "selected" in the strip, if any — used when the strip
  /// filters the feed to one ticker.
  final String? selected;
  final ValueChanged<String>? onSelected;
  final ValueChanged<String>? onOpen;
  final Map<String, CryptoAsset> assets;

  const StocksWatchlistReel({super.key, required this.symbols, required this.quotes, this.selected, this.onSelected, this.onOpen, this.assets = const {}});

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      height: 48 + MediaQuery.textScalerOf(context).scale(assets.isEmpty ? 30 : 46),
      child: ListView.separated(
        scrollDirection: Axis.horizontal,
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
        itemCount: symbols.length,
        separatorBuilder: (_, _) => const SizedBox(width: 8),
        itemBuilder: (context, index) {
          final symbol = symbols[index];
          return _WatchlistChip(
            symbol: assets[symbol]?.label ?? '\$$symbol',
            subtitle: assets[symbol]?.subtitle,
            quote: quotes[symbol],
            selected: selected == symbol,
            onTap: () {
              onSelected?.call(symbol);
              if (onSelected == null) {
                (onOpen ?? (symbol) => openTicker(context, symbol))(symbol);
              }
            },
            onLongPress: () => (onOpen ?? (symbol) => openTicker(context, symbol))(symbol),
          );
        },
      ),
    );
  }
}

/// Compact chip: `$AAPL`, price, and a green/red % pill — StockTwits's
/// watchlist reading rather than a mini chart card.
class _WatchlistChip extends StatelessWidget {
  final String symbol;
  final TickerQuote? quote;
  final String? subtitle;
  final bool selected;
  final VoidCallback onTap;
  final VoidCallback onLongPress;

  const _WatchlistChip({
    required this.symbol,
    required this.quote,
    this.subtitle,
    required this.selected,
    required this.onTap,
    required this.onLongPress,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final price = quote?.displayPrice;
    final percent = quote?.changePercent;
    final colour = percent == null ? theme.colorScheme.outline : stockChangeColour(quote?.isUp);
    final outline = selected ? theme.colorScheme.primary : theme.colorScheme.outlineVariant;

    return Semantics(
      selected: selected,
      button: true,
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
                  '${selected ? '✓ ' : ''}$symbol',
                  maxLines: 1,
                  style: theme.textTheme.labelLarge!.copyWith(fontWeight: FontWeight.w800),
                ),
                if (subtitle != null) Text(subtitle!, style: theme.textTheme.labelSmall, maxLines: 1),
                const SizedBox(height: 2),
                Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Text(
                      price == null ? kStockPlaceholder : stockAssetPrice(price),
                      style: theme.textTheme.bodySmall!.copyWith(
                        fontWeight: FontWeight.w600,
                        color: price == null ? theme.colorScheme.outline : theme.colorScheme.onSurface,
                      ),
                    ),
                    const SizedBox(width: 6),
                    _ChangePill(percent: percent, colour: colour),
                  ],
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _ChangePill extends StatelessWidget {
  final double? percent;
  final Color colour;

  const _ChangePill({required this.percent, required this.colour});

  @override
  Widget build(BuildContext context) {
    final label = percent == null ? kStockPlaceholder : stockPercentLabel(percent!);

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 1),
      decoration: BoxDecoration(
        color: percent == null ? Theme.of(context).colorScheme.surfaceContainerHighest : colour.withValues(alpha: 0.18),
        borderRadius: BorderRadius.circular(6),
      ),
      child: Text(
        label,
        style: Theme.of(context).textTheme.labelSmall!.copyWith(
          fontWeight: FontWeight.w800,
          color: percent == null ? Theme.of(context).colorScheme.outline : colour,
        ),
      ),
    );
  }
}
