import 'package:flutter/material.dart';
import 'package:xta/plugins/stocks/stocks_format.dart';
import 'package:xta/tweet/ticker/ticker_quote.dart';
import 'package:xta/tweet/ticker/ticker_symbol.dart';

/// A vertical tape of the indices and coins a markets page always shows.
class StocksMarketsList extends StatelessWidget {
  final Map<String, TickerQuote> quotes;
  final ValueChanged<String>? onOpen;

  const StocksMarketsList({super.key, required this.quotes, this.onOpen});

  @override
  Widget build(BuildContext context) {
    return ListView.separated(
      padding: const EdgeInsets.symmetric(vertical: 4),
      itemCount: kMarketIndexSymbols.length,
      separatorBuilder: (_, _) => const Divider(height: 1),
      itemBuilder: (context, index) {
        final symbol = kMarketIndexSymbols[index];
        return _MarketRow(
          symbol: symbol,
          quote: quotes[symbol],
          onTap: () => (onOpen ?? (s) => openTicker(context, s))(symbol),
        );
      },
    );
  }
}

class _MarketRow extends StatelessWidget {
  final String symbol;
  final TickerQuote? quote;
  final VoidCallback onTap;

  const _MarketRow({
    required this.symbol,
    required this.quote,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final price = quote?.displayPrice;
    final percent = quote?.changePercent;
    final colour = percent == null
        ? theme.colorScheme.outline
        : stockChangeColour(quote?.isUp);

    return ListTile(
      onTap: onTap,
      onLongPress: () => openTicker(context, symbol),
      title: Text(
        '\$$symbol',
        style: theme.textTheme.titleSmall!.copyWith(
          fontWeight: FontWeight.w800,
        ),
      ),
      subtitle: quote?.shortName == null ? null : Text(quote!.shortName!),
      trailing: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        crossAxisAlignment: CrossAxisAlignment.end,
        children: [
          Text(
            price == null ? kStockPlaceholder : stockMoneyFormat.format(price),
            style: theme.textTheme.titleSmall!.copyWith(
              fontWeight: FontWeight.w700,
            ),
          ),
          Text(
            percent == null ? kStockPlaceholder : stockPercentLabel(percent),
            style: theme.textTheme.labelMedium!.copyWith(
              fontWeight: FontWeight.w800,
              color: colour,
            ),
          ),
        ],
      ),
    );
  }
}
