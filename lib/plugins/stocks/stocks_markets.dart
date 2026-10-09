import 'package:flutter/material.dart';
import 'package:xta/plugins/plugin_feed_insets.dart';
import 'package:xta/plugins/stocks/stocks_format.dart';
import 'package:xta/plugins/stocks/stocks_quote_row.dart';
import 'package:xta/tweet/ticker/ticker_quote.dart';
import 'package:xta/tweet/ticker/ticker_symbol.dart';

/// A vertical tape of the indices and coins a markets page always shows, in
/// the same rows as the watchlist so both read alike.
class StocksMarketsList extends StatelessWidget {
  final Map<String, TickerQuote> quotes;
  final ValueChanged<String>? onOpen;
  final Future<void> Function()? onRefresh;
  final ScrollController? controller;

  const StocksMarketsList({super.key, required this.quotes, this.onOpen, this.onRefresh, this.controller});

  @override
  Widget build(BuildContext context) {
    final list = ListView.separated(
      controller: controller,
      physics: const AlwaysScrollableScrollPhysics(),
      padding: pluginFeedPadding(context),
      itemCount: kMarketIndexSymbols.length,
      separatorBuilder: (_, _) => const Divider(height: 1, indent: 16, endIndent: 16),
      itemBuilder: (context, index) {
        final symbol = kMarketIndexSymbols[index];
        return StocksQuoteRow(
          title: '\$$symbol',
          subtitle: quotes[symbol]?.shortName,
          quote: quotes[symbol],
          onTap: () => (onOpen ?? (s) => openTicker(context, s))(symbol),
        );
      },
    );
    return onRefresh == null ? list : RefreshIndicator(onRefresh: onRefresh!, child: list);
  }
}
