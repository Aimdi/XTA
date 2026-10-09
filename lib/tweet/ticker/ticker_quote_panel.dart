import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:intl/intl.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/stocks/stocks_format.dart';
import 'package:xta/plugins/stocks/stocks_quote_row.dart';
import 'package:xta/tweet/ticker/ticker_chart.dart';
import 'package:xta/tweet/ticker/ticker_detail_store.dart';
import 'package:xta/tweet/ticker/ticker_quote.dart';
import 'package:xta/tweet/ticker/ticker_range.dart';
import 'package:xta/tweet/ticker/ticker_stats.dart';
import 'package:xta/tweet/ticker/ticker_symbol.dart';

/// Everything above a ticker's posts: which instrument, the price and its
/// move, the session, the range-selectable chart, and today's statistics —
/// the order Apple Stocks and Google Finance share.
class TickerQuotePanel extends StatelessWidget {
  final TickerDetailStore store;

  const TickerQuotePanel({super.key, required this.store});

  @override
  Widget build(BuildContext context) {
    return ScopedBuilder<TickerDetailStore, TickerDetailState>(
      store: store,
      onState: (context, state) => Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          if (isAmbiguousTicker(store.symbol))
            _InstrumentPicker(state: state, store: store),
          ..._body(context, state),
          _RangePicker(selected: state.range, onSelected: store.selectRange),
          if (state.dayQuote != null)
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 0, 16, 8),
              child: TickerStats(quote: state.dayQuote!),
            ),
        ],
      ),
    );
  }

  List<Widget> _body(BuildContext context, TickerDetailState state) {
    final quote = state.quote;
    if (quote != null) {
      return [
        _QuoteHeader(state: state, quote: quote, symbol: store.symbol),
        const SizedBox(height: 8),
        TickerChart(quote: quote, onScrub: store.scrub),
      ];
    }
    if (state.failed) {
      return [_Unavailable(onRetry: store.load)];
    }
    // The picker stays below, so the row does not vanish under the finger
    // that just tapped a range.
    return const [
      SizedBox(height: 200, child: Center(child: CircularProgressIndicator())),
    ];
  }
}

class _QuoteHeader extends StatelessWidget {
  final TickerDetailState state;
  final TickerQuote quote;
  final String symbol;

  const _QuoteHeader({
    required this.state,
    required this.quote,
    required this.symbol,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scrubbed = state.scrubbed;
    final price = scrubbed?.close ?? quote.displayPrice;
    final session = (state.dayQuote ?? quote).sessionAt(DateTime.now());

    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 0),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: Text(
                  '\$${spokenCashtag(symbol)}',
                  style: theme.textTheme.titleLarge!.copyWith(
                    fontWeight: FontWeight.w900,
                  ),
                ),
              ),
              if (session != null && !quote.tradesAroundTheClock)
                _SessionLabel(session: session),
            ],
          ),
          if (quote.shortName != null)
            Text(
              quote.shortName!,
              style: theme.textTheme.bodyMedium!.copyWith(
                color: theme.colorScheme.onSurfaceVariant,
              ),
            ),
          const SizedBox(height: 4),
          _price(context, price),
          _move(context, price),
          if (scrubbed == null && quote.extendedPrint != null)
            _ExtendedLine(quote: quote),
        ],
      ),
    );
  }

  Widget _price(BuildContext context, double? price) {
    final theme = Theme.of(context);
    return Wrap(
      spacing: 6,
      crossAxisAlignment: WrapCrossAlignment.end,
      children: [
        Text(
          price == null ? kStockPlaceholder : stockPrice(price),
          style: theme.textTheme.headlineMedium!.copyWith(
            fontWeight: FontWeight.w800,
            fontFeatures: kStockFigures,
          ),
        ),
        if (quote.currency != null)
          Padding(
            padding: const EdgeInsets.only(bottom: 4),
            child: Text(
              quote.currency!,
              style: theme.textTheme.bodyMedium!.copyWith(
                color: theme.colorScheme.onSurfaceVariant,
              ),
            ),
          ),
      ],
    );
  }

  /// The move since the range began — or since then up to the scrubbed
  /// point — with what it is measured over beside it.
  Widget _move(BuildContext context, double? price) {
    final theme = Theme.of(context);
    final base = quote.previousClose;
    final change = price == null || base == null ? null : price - base;
    final percent = change == null || base == 0 ? null : change / base! * 100;
    final scrubbed = state.scrubbed;
    final caption = scrubbed == null
        ? tickerRangeLabel(context, state.range)
        : DateFormat.yMMMd().add_Hm().format(scrubbed.at);

    return Wrap(
      spacing: 8,
      crossAxisAlignment: WrapCrossAlignment.center,
      children: [
        if (change != null && percent != null)
          Text(
            '${stockChangeLabel(change)} (${stockPercentLabel(percent)})',
            style: theme.textTheme.titleSmall!.copyWith(
              fontWeight: FontWeight.w700,
              fontFeatures: kStockFigures,
              color: stockTrendColour(context, percent),
            ),
          ),
        Text(
          caption,
          style: theme.textTheme.bodySmall!.copyWith(
            color: theme.colorScheme.onSurfaceVariant,
          ),
        ),
      ],
    );
  }
}

/// `After hours  190.10  +0.14%` — the extended print, kept apart from the
/// regular price so the move above stays the regular session's.
class _ExtendedLine extends StatelessWidget {
  final TickerQuote quote;

  const _ExtendedLine({required this.quote});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final l10n = L10n.of(context);
    final extended = quote.extendedPrint!;
    final percent = quote.extendedChangePercent;
    final label = extended.session == TickerSession.pre
        ? l10n.plugin_stocks_pre_market
        : l10n.plugin_stocks_after_hours;

    return Padding(
      padding: const EdgeInsets.only(top: 4),
      child: Wrap(
        spacing: 8,
        children: [
          Text(
            label,
            style: theme.textTheme.bodySmall!.copyWith(
              color: theme.colorScheme.onSurfaceVariant,
            ),
          ),
          Text(
            stockPrice(extended.price),
            style: theme.textTheme.bodySmall!.copyWith(
              fontWeight: FontWeight.w700,
              fontFeatures: kStockFigures,
            ),
          ),
          if (percent != null)
            Text(
              stockPercentLabel(percent),
              style: theme.textTheme.bodySmall!.copyWith(
                fontWeight: FontWeight.w700,
                color: stockTrendColour(context, percent),
              ),
            ),
        ],
      ),
    );
  }
}

class _SessionLabel extends StatelessWidget {
  final TickerSession session;

  const _SessionLabel({required this.session});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final l10n = L10n.of(context);
    final open = session == TickerSession.regular;
    final text = switch (session) {
      TickerSession.regular => l10n.plugin_stocks_market_open,
      TickerSession.pre => l10n.plugin_stocks_pre_market,
      TickerSession.post => l10n.plugin_stocks_after_hours,
      TickerSession.closed => l10n.plugin_stocks_market_closed,
    };
    final dot = open
        ? stockTrendColour(context, 1)
        : theme.colorScheme.onSurfaceVariant;

    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Icon(Icons.circle, size: 8, color: dot),
        const SizedBox(width: 6),
        Text(
          text,
          style: theme.textTheme.labelMedium!.copyWith(
            color: theme.colorScheme.onSurfaceVariant,
          ),
        ),
      ],
    );
  }
}

/// `$ETH` is a coin and a listed company; the reader picks which is charted.
class _InstrumentPicker extends StatelessWidget {
  final TickerDetailState state;
  final TickerDetailStore store;

  const _InstrumentPicker({required this.state, required this.store});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final stock = state.instrument == TickerInstrument.stock;

    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 8, 16, 0),
      child: Wrap(
        spacing: 8,
        children: [
          ChoiceChip(
            label: Text(l10n.plugin_stocks_crypto),
            selected: !stock,
            onSelected: (_) {
              if (stock) store.selectInstrument(TickerInstrument.crypto);
            },
          ),
          ChoiceChip(
            label: Text(l10n.plugin_stocks_title),
            selected: stock,
            onSelected: (_) => store.selectInstrument(TickerInstrument.stock),
          ),
        ],
      ),
    );
  }
}

class _RangePicker extends StatelessWidget {
  final TickerRange selected;
  final ValueChanged<TickerRange> onSelected;

  const _RangePicker({required this.selected, required this.onSelected});

  @override
  Widget build(BuildContext context) {
    return SingleChildScrollView(
      scrollDirection: Axis.horizontal,
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
      child: Row(
        children: [
          for (final range in TickerRange.values)
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 4),
              child: ChoiceChip(
                label: Text(tickerRangeLabel(context, range)),
                selected: range == selected,
                onSelected: (_) => onSelected(range),
              ),
            ),
        ],
      ),
    );
  }
}

class _Unavailable extends StatelessWidget {
  final Future<void> Function() onRetry;

  const _Unavailable({required this.onRetry});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return ListTile(
      leading: const Icon(Icons.cloud_off_outlined),
      title: Text(l10n.plugin_stocks_data_unavailable),
      trailing: TextButton(onPressed: onRetry, child: Text(l10n.retry)),
    );
  }
}
