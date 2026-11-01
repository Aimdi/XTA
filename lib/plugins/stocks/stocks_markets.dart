import 'package:flutter/material.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/plugin_feed_insets.dart';
import 'package:xta/plugins/stocks/stocks_format.dart';
import 'package:xta/plugins/stocks/stocks_quote_row.dart';
import 'package:xta/plugins/stocks/stocks_row_state.dart';
import 'package:xta/tweet/ticker/ticker_quote.dart';
import 'package:xta/tweet/ticker/ticker_symbol.dart';

/// A vertical tape of the indices and coins a markets page always shows, in
/// the same rows as the watchlist so both read alike. Each row has its name
/// and badge before any price arrives.
class StocksMarketsList extends StatelessWidget {
  final Map<String, TickerQuote> quotes;

  /// Symbols whose last lookup found nothing.
  final Set<String> failed;
  final ValueChanged<String>? onOpen;
  final ValueChanged<String>? onRetry;
  final Future<void> Function()? onRefresh;
  final ScrollController? controller;

  const StocksMarketsList({
    super.key,
    required this.quotes,
    this.failed = const {},
    this.onOpen,
    this.onRetry,
    this.onRefresh,
    this.controller,
  });

  @override
  Widget build(BuildContext context) {
    final list = ListView.separated(
      controller: controller,
      physics: const AlwaysScrollableScrollPhysics(),
      padding: pluginFeedPadding(context),
      itemCount: kMarketIndexSymbols.length,
      separatorBuilder: (_, _) => const Divider(height: 1, indent: 16, endIndent: 16),
      itemBuilder: (context, index) => _row(context, kMarketIndexSymbols[index]),
    );
    return onRefresh == null ? list : RefreshIndicator(onRefresh: onRefresh!, child: list);
  }

  Widget _row(BuildContext context, String symbol) {
    final quote = quotes[symbol];
    return StocksQuoteRow(
      title: '\$$symbol',
      subtitle: tickerName(symbol) ?? quote?.shortName,
      badge: stockBadge(L10n.of(context), stockKindOf(symbol, quote)),
      quote: quote,
      failed: failed.contains(symbol),
      onTap: () => (onOpen ?? (s) => openTicker(context, s))(symbol),
      onRetry: onRetry == null ? null : () => onRetry!(symbol),
    );
  }
}
