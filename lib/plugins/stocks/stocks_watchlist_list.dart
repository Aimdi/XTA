import 'package:flutter/material.dart';
import 'package:xta/plugins/plugin_feed_insets.dart';
import 'package:xta/plugins/stocks/crypto_asset.dart';
import 'package:xta/plugins/stocks/crypto_copy.dart';
import 'package:xta/plugins/stocks/stocks_quote_row.dart';
import 'package:xta/tweet/ticker/ticker_quote.dart';
import 'package:xta/tweet/ticker/ticker_symbol.dart';

/// The watchlist as a quote list: one dense row per symbol or token. Tapping
/// opens its page; long-pressing a token copies its contract address.
class StocksWatchlistList extends StatelessWidget {
  final List<String> symbols;
  final Map<String, TickerQuote> quotes;
  final Map<String, CryptoAsset> assets;
  final ValueChanged<String> onOpen;
  final Future<void> Function() onRefresh;
  final ScrollController? controller;

  const StocksWatchlistList({
    super.key,
    required this.symbols,
    required this.quotes,
    required this.onOpen,
    required this.onRefresh,
    this.assets = const {},
    this.controller,
  });

  @override
  Widget build(BuildContext context) {
    return RefreshIndicator(
      onRefresh: onRefresh,
      child: ListView.separated(
        controller: controller,
        physics: const AlwaysScrollableScrollPhysics(),
        padding: pluginFeedPadding(context),
        itemCount: symbols.length,
        separatorBuilder: (_, _) => const Divider(height: 1, indent: 16, endIndent: 16),
        itemBuilder: (context, index) => _row(context, symbols[index]),
      ),
    );
  }

  Widget _row(BuildContext context, String symbol) {
    final asset = assets[symbol];
    final quote = quotes[symbol];
    return StocksQuoteRow(
      title: asset?.label ?? '\$${spokenCashtag(symbol)}',
      subtitle: asset?.subtitle ?? quote?.shortName,
      quote: quote,
      onTap: () => onOpen(symbol),
      onLongPress: asset == null ? null : () => copyCryptoAddress(context, asset),
    );
  }
}
