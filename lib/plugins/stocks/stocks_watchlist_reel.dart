import 'package:flutter/material.dart';
import 'package:xta/plugins/stocks/stocks_format.dart';
import 'package:xta/tweet/ticker/ticker_quote.dart';

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

  const StocksWatchlistReel({
    super.key,
    required this.symbols,
    required this.quotes,
    this.selected,
    this.onSelected,
  });

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      height: kStockWatchlistStripHeight,
      child: ListView.separated(
        scrollDirection: Axis.horizontal,
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
        itemCount: symbols.length,
        separatorBuilder: (_, _) => const SizedBox(width: 8),
        itemBuilder: (context, index) {
          final symbol = symbols[index];
          return _WatchlistChip(
            symbol: symbol,
            quote: quotes[symbol],
            selected: selected == symbol,
            onTap: () {
              onSelected?.call(symbol);
              if (onSelected == null) {
                openTicker(context, symbol);
              }
            },
            onLongPress: () => openTicker(context, symbol),
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
  final bool selected;
  final VoidCallback onTap;
  final VoidCallback onLongPress;

  const _WatchlistChip({
    required this.symbol,
    required this.quote,
    required this.selected,
    required this.onTap,
    required this.onLongPress,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final price = quote?.displayPrice;
    final percent = quote?.changePercent;
    final colour = percent == null
        ? theme.colorScheme.outline
        : stockChangeColour(quote?.isUp);
    final outline = selected
        ? theme.colorScheme.primary
        : theme.colorScheme.outlineVariant;

    return Material(
      color: selected
          ? theme.colorScheme.primaryContainer
          : theme.colorScheme.surfaceContainerHighest,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(12),
        side: BorderSide(color: outline),
      ),
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
                '\$$symbol',
                maxLines: 1,
                style: theme.textTheme.labelLarge!.copyWith(
                  fontWeight: FontWeight.w800,
                ),
              ),
              const SizedBox(height: 2),
              Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text(
                    price == null
                        ? kStockPlaceholder
                        : stockMoneyFormat.format(price),
                    style: theme.textTheme.bodySmall!.copyWith(
                      fontWeight: FontWeight.w600,
                      color: price == null
                          ? theme.colorScheme.outline
                          : theme.colorScheme.onSurface,
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
    );
  }
}

class _ChangePill extends StatelessWidget {
  final double? percent;
  final Color colour;

  const _ChangePill({required this.percent, required this.colour});

  @override
  Widget build(BuildContext context) {
    final label = percent == null
        ? kStockPlaceholder
        : stockPercentLabel(percent!);

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 1),
      decoration: BoxDecoration(
        color: percent == null
            ? Theme.of(context).colorScheme.surfaceContainerHighest
            : colour.withValues(alpha: 0.18),
        borderRadius: BorderRadius.circular(6),
      ),
      child: Text(
        label,
        style: Theme.of(context).textTheme.labelSmall!.copyWith(
          fontWeight: FontWeight.w800,
          color: percent == null
              ? Theme.of(context).colorScheme.outline
              : colour,
        ),
      ),
    );
  }
}
