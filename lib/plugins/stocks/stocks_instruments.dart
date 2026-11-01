import 'package:flutter/material.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/stocks/stocks_format.dart';
import 'package:xta/plugins/stocks/stocks_row_state.dart';
import 'package:xta/tweet/ticker/ticker_quote_cache.dart';
import 'package:xta/tweet/ticker/ticker_symbol.dart';

/// One way to read a cashtag: the coin, or the listed security of the same
/// letters.
typedef StocksReading = ({TickerInstrument instrument, String name, TickerKind kind});

/// The two readings [cache] found for [symbol] while the reader has not
/// picked one, the coin (shown until then) first; else null.
List<StocksReading>? stocksReadings(TickerQuoteCache cache, String symbol) {
  final listing = cache.listingFor(symbol);
  if (listing == null || cache.instrumentFor(symbol) != TickerInstrument.auto) return null;
  final spoken = spokenCashtag(symbol);
  return [
    (
      instrument: TickerInstrument.crypto,
      name: stockRowName(symbol, cache.peek(symbol)) ?? spoken,
      kind: TickerKind.crypto,
    ),
    (instrument: TickerInstrument.stock, name: listing.shortName ?? spoken, kind: listing.kind ?? TickerKind.stock),
  ];
}

/// Two outlined choices under a watchlist row, name over kind, the reading
/// shown now outlined in the accent.
class StocksReadingChoices extends StatelessWidget {
  final List<StocksReading> readings;
  final TickerInstrument shown;
  final ValueChanged<TickerInstrument> onChoose;

  const StocksReadingChoices({super.key, required this.readings, required this.shown, required this.onChoose});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsetsDirectional.fromSTEB(16, 0, 16, 12),
      child: Row(spacing: 8, children: [for (final reading in readings) Expanded(child: _choice(context, reading))]),
    );
  }

  Widget _choice(BuildContext context, StocksReading reading) {
    final theme = Theme.of(context);
    final current = reading.instrument == shown;
    final kind = stockKindWord(L10n.of(context), reading.kind);
    return Semantics(
      selected: current,
      child: OutlinedButton(
        key: ValueKey('stocks-reading-${reading.instrument.name}'),
        onPressed: () => onChoose(reading.instrument),
        style: OutlinedButton.styleFrom(
          minimumSize: const Size(0, 48),
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
          alignment: AlignmentDirectional.centerStart,
          foregroundColor: theme.colorScheme.onSurface,
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
          side: BorderSide(
            color: current ? stocksAccent(context) : theme.colorScheme.outlineVariant,
            width: current ? 1.5 : 1,
          ),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(
              reading.name,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: theme.textTheme.bodyMedium!.copyWith(fontSize: 14, fontWeight: FontWeight.w500),
            ),
            if (kind != null)
              Text(
                kind,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: theme.textTheme.bodySmall!.copyWith(fontSize: 12, color: theme.colorScheme.onSurfaceVariant),
              ),
          ],
        ),
      ),
    );
  }
}
