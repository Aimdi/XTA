import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/stocks/crypto_client.dart';
import 'package:xta/plugins/stocks/stocks_search_store.dart';
import 'package:xta/plugins/stocks/stocks_store.dart';
import 'package:xta/tweet/ticker/ticker_client.dart';

Future<String?> showStocksAddSheet(BuildContext context, {TickerClient? client}) =>
    showModalBottomSheet<String>(
      context: context, isScrollControlled: true, showDragHandle: true,
      builder: (_) => _StocksAddSheet(client: client ?? TickerClient()),
    );

class _StocksAddSheet extends StatefulWidget {
  final TickerClient client;
  const _StocksAddSheet({required this.client});
  @override
  State<_StocksAddSheet> createState() => _StocksAddSheetState();
}

class _StocksAddSheetState extends State<_StocksAddSheet> {
  final _controller = TextEditingController();
  final _crypto = CryptoClient();
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
    final l10n = L10n.of(context);
    return Padding(
      padding: EdgeInsets.only(bottom: MediaQuery.viewInsetsOf(context).bottom),
      child: SafeArea(
        child: SizedBox(
          height: MediaQuery.sizeOf(context).height * 0.68,
          child: ScopedBuilder<StocksSearchStore, StocksSearchState>(
            store: _search,
            onState: (_, state) => Column(children: [
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 16),
                child: Row(children: [
                  ChoiceChip(label: Text(l10n.plugin_stocks_title), selected: !state.crypto,
                    onSelected: (_) => _search.change(_controller.text, crypto: false)),
                  const SizedBox(width: 8),
                  ChoiceChip(label: Text(l10n.plugin_stocks_crypto), selected: state.crypto,
                    onSelected: (_) => _search.change(_controller.text, crypto: true)),
                ]),
              ),
              Padding(
                padding: const EdgeInsets.all(16),
                child: TextField(
                  controller: _controller, autofocus: true, autocorrect: false,
                  decoration: InputDecoration(
                    hintText: state.crypto ? l10n.plugin_stocks_crypto_search : l10n.plugin_stocks_search,
                    prefixIcon: const Icon(Icons.search),
                  ),
                  onChanged: _search.change,
                  onSubmitted: (value) => _search.change(value, immediate: true),
                ),
              ),
              if (state.crypto) Padding(
                padding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
                child: Text(l10n.plugin_stocks_crypto_identity_hint,
                  style: Theme.of(context).textTheme.bodySmall),
              ),
              if (state.loading) const LinearProgressIndicator(),
              Expanded(child: _results(state, l10n)),
            ]),
          ),
        ),
      ),
    );
  }

  Widget _results(StocksSearchState state, L10n l10n) {
    final symbol = StocksWatchlistStore.normaliseTicker(state.query);
    return ListView(children: [
      if (state.failed) ListTile(
        title: Text(l10n.plugin_stocks_error),
        trailing: TextButton(onPressed: () => _search.change(state.query, immediate: true),
          child: Text(l10n.retry)),
      ),
      if (state.crypto && !state.loading && !state.failed && state.query.isNotEmpty && state.tokens.isEmpty)
        ListTile(title: Text(l10n.no_results)),
      if (!state.crypto && symbol != null && state.stocks.every((h) => h.symbol != symbol))
        ListTile(leading: const Icon(Icons.add), title: Text('\$$symbol'),
          onTap: () => Navigator.pop(context, symbol)),
      for (final hit in state.stocks)
        ListTile(title: Text('\$${hit.symbol}'),
          subtitle: Text([?hit.name, ?hit.exchange].join(' · ')),
          onTap: () => Navigator.pop(context, hit.symbol)),
      for (final hit in state.tokens)
        ListTile(
          leading: const Icon(Icons.currency_bitcoin),
          title: Text('${hit.asset.label} · ${hit.asset.name}'),
          subtitle: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text(hit.asset.chain),
            Text(hit.asset.address, style: Theme.of(context).textTheme.bodySmall),
          ]),
          contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
          onTap: () => Navigator.pop(context, hit.asset.encode()),
        ),
    ]);
  }
}
