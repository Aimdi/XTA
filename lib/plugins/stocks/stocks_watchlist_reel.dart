import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/stocks/crypto_asset.dart';
import 'package:xta/plugins/stocks/stocks_format.dart';
import 'package:xta/plugins/stocks/stocks_quote_row.dart';
import 'package:xta/plugins/stocks/stocks_row_state.dart';
import 'package:xta/tweet/ticker/ticker_quote.dart';
import 'package:xta/tweet/ticker/ticker_symbol.dart';

/// The watched (or trending) tickers as a flat strip above the posts: each
/// entry its symbol over its price and move, thin lines between them, no
/// chips. Tapping an entry filters the posts to it; a long press opens it.
///
/// Quotes are passed in rather than fetched here: every entry wants one, and a
/// strip that fetched its own would race the parent loading the same symbols.
class StocksWatchlistReel extends StatelessWidget {
  final List<String> symbols;
  final Map<String, TickerQuote> quotes;

  /// Symbols and token ids whose last lookup found nothing.
  final Set<String> failed;

  /// The entry filtering the posts, if any.
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
    final theme = Theme.of(context);
    final entryHeight = math.max(48.0, MediaQuery.textScalerOf(context).scale(40));
    return DecoratedBox(
      decoration: BoxDecoration(
        border: Border(bottom: BorderSide(color: theme.dividerColor)),
      ),
      child: SizedBox(
        height: entryHeight + 12,
        child: ListView.separated(
          scrollDirection: Axis.horizontal,
          padding: const EdgeInsetsDirectional.fromSTEB(8, 6, 8, 6),
          itemCount: symbols.length,
          separatorBuilder: (_, _) => VerticalDivider(width: 1, indent: 8, endIndent: 8, color: theme.dividerColor),
          itemBuilder: (context, index) {
            final symbol = symbols[index];
            final asset = assets[symbol];
            return _StripEntry(
              label: asset?.bareLabel ?? spokenCashtag(symbol),
              network: asset?.chainName,
              quote: quotes[symbol],
              failed: failed.contains(symbol),
              selected: selected == symbol,
              onTap: () => onSelected == null ? open(symbol) : onSelected!(symbol),
              onLongPress: () => open(symbol),
            );
          },
        ),
      ),
    );
  }
}

/// `DOT Base` over `0,0₂875 +3,98%`; `Kein Kurs` when there is none, a grey
/// bar while it loads. The entry filtering the posts is in the accent, with
/// a line under it.
class _StripEntry extends StatelessWidget {
  final String label;
  final String? network;
  final TickerQuote? quote;
  final bool failed;
  final bool selected;
  final VoidCallback onTap;
  final VoidCallback onLongPress;

  const _StripEntry({
    required this.label,
    required this.network,
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
    final accent = stocksAccent(context);
    return Semantics(
      button: true,
      selected: selected,
      label: _label(context, state, now),
      excludeSemantics: true,
      child: InkWell(
        onTap: onTap,
        onLongPress: onLongPress,
        child: Container(
          constraints: const BoxConstraints(minHeight: 48),
          padding: const EdgeInsets.symmetric(horizontal: 12),
          decoration: BoxDecoration(
            border: Border(bottom: BorderSide(width: 2, color: selected ? accent : Colors.transparent)),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisAlignment: MainAxisAlignment.center,
            mainAxisSize: MainAxisSize.min,
            children: [
              Text.rich(
                TextSpan(
                  children: [
                    TextSpan(
                      text: label,
                      style: TextStyle(fontWeight: FontWeight.w700, color: selected ? accent : null),
                    ),
                    if (network != null)
                      TextSpan(
                        text: ' $network',
                        style: TextStyle(fontSize: 12, color: theme.colorScheme.onSurfaceVariant),
                      ),
                  ],
                ),
                maxLines: 1,
                style: theme.textTheme.labelLarge!.copyWith(fontSize: 14),
              ),
              const SizedBox(height: 2),
              _figures(context, state),
            ],
          ),
        ),
      ),
    );
  }

  Widget _figures(BuildContext context, StockRowState state) {
    final theme = Theme.of(context);
    final muted = theme.colorScheme.onSurfaceVariant;
    final style = theme.textTheme.bodySmall!.copyWith(fontSize: 13, fontFeatures: kStockFigures);
    final quote = this.quote;
    if (state == StockRowState.loading) return const StocksFiguresSkeleton(inline: true);
    if (state == StockRowState.noData || quote == null) {
      return Text(L10n.of(context).plugin_stocks_no_price, style: style.copyWith(color: muted));
    }
    final closed = state == StockRowState.closed;
    final percent = quote.changePercent;
    return Text.rich(
      TextSpan(
        children: [
          TextSpan(text: stockPrice(quote.displayPrice!)),
          if (percent != null)
            TextSpan(
              text: ' ${stockPercentLabel(percent)}',
              style: TextStyle(fontWeight: FontWeight.w500, color: closed ? muted : stockTrendColour(context, percent)),
            ),
        ],
      ),
      maxLines: 1,
      style: style.copyWith(color: closed ? muted : theme.colorScheme.onSurface),
    );
  }

  String _label(BuildContext context, StockRowState state, DateTime now) {
    final l10n = L10n.of(context);
    final quote = this.quote;
    final percent = quote?.changePercent;
    return [
      label,
      ?network,
      ...switch (state) {
        StockRowState.loading => [l10n.plugin_stocks_loading_price],
        StockRowState.noData => [l10n.plugin_stocks_no_price],
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
