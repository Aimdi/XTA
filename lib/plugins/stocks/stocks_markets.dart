import 'package:flutter/material.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/plugin_feed_insets.dart';
import 'package:xta/plugins/stocks/stocks_format.dart';
import 'package:xta/plugins/stocks/stocks_quote_row.dart';
import 'package:xta/plugins/stocks/stocks_row_state.dart';
import 'package:xta/plugins/stocks/stocks_section_header.dart';
import 'package:xta/tweet/ticker/ticker_quote.dart';
import 'package:xta/tweet/ticker/ticker_symbol.dart';

/// The indices and coins a markets page always shows, in two sections that
/// each say once whether their markets trade and what the moves measure, so
/// the rows need not repeat it. Every row has its name before a price arrives.
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
    final l10n = L10n.of(context);
    final now = DateTime.now();
    final indices = [
      for (final symbol in kMarketIndexSymbols)
        if (knownTickerKind(symbol) != TickerKind.crypto) symbol,
    ];
    final coins = [
      for (final symbol in kMarketIndexSymbols)
        if (knownTickerKind(symbol) == TickerKind.crypto) symbol,
    ];
    final list = ListView(
      controller: controller,
      physics: const AlwaysScrollableScrollPhysics(),
      padding: pluginFeedPadding(context),
      children: [
        ..._section(context, l10n.plugin_stocks_section_indices, indices, now),
        ..._section(context, l10n.plugin_stocks_type_crypto, coins, now),
      ],
    );
    return onRefresh == null ? list : RefreshIndicator(onRefresh: onRefresh!, child: list);
  }

  List<Widget> _section(BuildContext context, String title, List<String> symbols, DateTime now) {
    final state = stocksSectionState([for (final symbol in symbols) quotes[symbol]], now: now);
    return [
      StocksSectionHeader(
        title: title,
        status: stocksSectionStatus(context, state),
        live: state.kind == StocksSectionKind.unknown ? null : state.kind != StocksSectionKind.closed,
      ),
      for (final symbol in symbols) ...[_row(context, symbol, state.kind), const Divider(height: 1)],
    ];
  }

  /// A row in a closed section leaves its day to the header; one closed among
  /// live markets says its own.
  Widget _row(BuildContext context, String symbol, StocksSectionKind section) {
    final quote = quotes[symbol];
    return StocksQuoteRow(
      title: tickerName(symbol) ?? quote?.shortName ?? symbol,
      subtitle: stockSubtitle(L10n.of(context), symbol, stockKindOf(symbol, quote), named: true),
      quote: quote,
      failed: failed.contains(symbol),
      showWindow: false,
      showCloseDay: section != StocksSectionKind.closed,
      onTap: () => (onOpen ?? (s) => openTicker(context, s))(symbol),
      onRetry: onRetry == null ? null : () => onRetry!(symbol),
    );
  }
}
