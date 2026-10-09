import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/stocks/crypto_asset.dart';
import 'package:xta/plugins/stocks/crypto_client.dart';
import 'package:xta/plugins/stocks/stocks_quote_row.dart';
import 'package:xta/plugins/stocks/stocks_search_store.dart';
import 'package:xta/plugins/stocks/stocks_store.dart';
import 'package:xta/tweet/ticker/ticker_client.dart';

/// Pops with a ticker (`AAPL`) or an encoded token (`crypto:v1:…`).
Future<String?> showStocksAddSheet(BuildContext context, {TickerClient? client, CryptoClient? cryptoClient}) =>
    showModalBottomSheet<String>(
      context: context,
      isScrollControlled: true,
      showDragHandle: true,
      builder: (_) => StocksAddSheet(client: client ?? TickerClient(), cryptoClient: cryptoClient),
    );

/// Search by name or symbol, or paste a contract address: an address is
/// recognised as typed and resolved to the token on every network it exists.
class StocksAddSheet extends StatefulWidget {
  final TickerClient client;
  final CryptoClient? cryptoClient;
  const StocksAddSheet({super.key, required this.client, this.cryptoClient});
  @override
  State<StocksAddSheet> createState() => _StocksAddSheetState();
}

class _StocksAddSheetState extends State<StocksAddSheet> {
  final _controller = TextEditingController();
  late final _crypto = widget.cryptoClient ?? CryptoClient();
  late final _search = StocksSearchStore(tickerClient: widget.client, cryptoClient: _crypto);

  @override
  void dispose() {
    _search.destroy();
    _crypto.close();
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: EdgeInsets.only(bottom: MediaQuery.viewInsetsOf(context).bottom),
      child: SafeArea(
        child: SizedBox(
          height: MediaQuery.sizeOf(context).height * 0.68,
          child: ScopedBuilder<StocksSearchStore, StocksSearchState>(
            store: _search,
            onState: (_, state) => Column(
              children: [
                _kinds(state),
                _field(state),
                if (state.crypto)
                  Padding(
                    padding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
                    child: Text(
                      L10n.of(context).plugin_stocks_crypto_identity_hint,
                      style: Theme.of(context).textTheme.bodySmall,
                    ),
                  ),
                if (state.loading) const LinearProgressIndicator(),
                Expanded(child: _results(state, L10n.of(context))),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _kinds(StocksSearchState state) {
    final l10n = L10n.of(context);
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16),
      child: Wrap(
        spacing: 8,
        children: [
          ChoiceChip(
            label: Text(l10n.plugin_stocks_title),
            selected: !state.crypto,
            onSelected: (_) => _search.change(_controller.text, crypto: false),
          ),
          ChoiceChip(
            label: Text(l10n.plugin_stocks_crypto),
            selected: state.crypto,
            onSelected: (_) => _search.change(_controller.text, crypto: true),
          ),
        ],
      ),
    );
  }

  Widget _field(StocksSearchState state) {
    final l10n = L10n.of(context);
    return Padding(
      padding: const EdgeInsets.all(16),
      child: TextField(
        controller: _controller,
        autofocus: true,
        autocorrect: false,
        decoration: InputDecoration(
          hintText: state.crypto ? l10n.plugin_stocks_crypto_search : l10n.plugin_stocks_search,
          prefixIcon: const Icon(Icons.search),
        ),
        onChanged: _search.change,
        onSubmitted: (value) => _search.change(value, immediate: true),
      ),
    );
  }

  Widget _results(StocksSearchState state, L10n l10n) {
    final symbol = StocksWatchlistStore.normaliseTicker(state.query);
    final settled = !state.loading && !state.failed && state.query.isNotEmpty;
    return ListView(
      children: [
        if (state.failed)
          ListTile(
            title: Text(l10n.plugin_stocks_data_unavailable),
            trailing: TextButton(
              onPressed: () => _search.change(state.query, immediate: true),
              child: Text(l10n.retry),
            ),
          ),
        if (settled && state.crypto && state.tokens.isEmpty)
          ListTile(title: Text(state.address == null ? l10n.no_results : l10n.plugin_stocks_crypto_no_token)),
        if (settled && !state.crypto && symbol == null && state.stocks.isEmpty)
          ListTile(title: Text(l10n.plugin_stocks_error)),
        if (!state.crypto && symbol != null && state.stocks.every((h) => h.symbol != symbol))
          ListTile(
            leading: const Icon(Icons.add),
            title: Text('\$$symbol'),
            onTap: () => Navigator.pop(context, symbol),
          ),
        for (final hit in state.stocks)
          ListTile(
            title: Text('\$${hit.symbol}'),
            subtitle: Text([?hit.name, ?hit.exchange].join(' · ')),
            onTap: () => Navigator.pop(context, hit.symbol),
          ),
        for (final hit in state.tokens) _TokenHit(market: hit, onTap: () => Navigator.pop(context, hit.asset.encode())),
      ],
    );
  }
}

/// `$PEPE · Pepe` over `Ethereum · 0x6982…1933`, priced on the right, so two
/// tokens sharing a symbol are told apart by network and contract at a glance.
class _TokenHit extends StatelessWidget {
  final CryptoMarket market;
  final VoidCallback onTap;

  const _TokenHit({required this.market, required this.onTap});

  @override
  Widget build(BuildContext context) {
    final asset = market.asset;
    return StocksQuoteRow(
      title: '${asset.label} · ${asset.name}',
      subtitle: asset.subtitle,
      quote: market.quote,
      onTap: onTap,
    );
  }
}
