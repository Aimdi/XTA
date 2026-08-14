import 'dart:async';

import 'package:flutter/material.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/stocks/stocks_store.dart';
import 'package:xta/tweet/ticker/ticker_client.dart';
import 'package:xta/tweet/ticker/ticker_search.dart';
import 'package:xta/tweet/ticker/ticker_symbol.dart';

/// Search-as-you-type add, the way a finance app finds a name rather than
/// making the reader already know the ticker.
Future<String?> showStocksAddSheet(
  BuildContext context, {
  TickerClient? client,
}) {
  return showModalBottomSheet<String>(
    context: context,
    isScrollControlled: true,
    showDragHandle: true,
    builder: (sheetContext) =>
        _StocksAddSheet(client: client ?? TickerClient()),
  );
}

class _StocksAddSheet extends StatefulWidget {
  final TickerClient client;

  const _StocksAddSheet({required this.client});

  @override
  State<_StocksAddSheet> createState() => _StocksAddSheetState();
}

class _StocksAddSheetState extends State<_StocksAddSheet> {
  final TextEditingController _controller = TextEditingController();
  Timer? _debounce;
  List<TickerSearchHit> _hits = const [];
  bool _searching = false;

  @override
  void dispose() {
    _debounce?.cancel();
    _controller.dispose();
    super.dispose();
  }

  void _onChanged(String value) {
    _debounce?.cancel();
    _debounce = Timer(const Duration(milliseconds: 300), () => _search(value));
  }

  Future<void> _search(String value) async {
    final query = value.trim();
    if (query.isEmpty) {
      if (mounted) {
        setState(() => _hits = const []);
      }
      return;
    }

    setState(() => _searching = true);
    try {
      final hits = await widget.client.searchSymbols(query);
      if (mounted && _controller.text.trim() == query) {
        setState(() {
          _hits = hits;
          _searching = false;
        });
      }
    } on TickerException {
      if (mounted && _controller.text.trim() == query) {
        setState(() {
          _hits = const [];
          _searching = false;
        });
      }
    }
  }

  void _pick(String raw) {
    final symbol = StocksWatchlistStore.normaliseTicker(spokenCashtag(raw));
    Navigator.pop(context, symbol ?? raw.trim());
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final typed = _controller.text.trim();
    final typedSymbol = StocksWatchlistStore.normaliseTicker(typed);

    return Padding(
      padding: EdgeInsets.only(bottom: MediaQuery.viewInsetsOf(context).bottom),
      child: SafeArea(
        child: SizedBox(
          height: 420,
          child: Column(
            children: [
              Padding(
                padding: const EdgeInsets.fromLTRB(16, 0, 16, 8),
                child: TextField(
                  controller: _controller,
                  autofocus: true,
                  autocorrect: false,
                  textCapitalization: TextCapitalization.characters,
                  decoration: InputDecoration(
                    hintText: l10n.plugin_stocks_search,
                    prefixIcon: const Icon(Icons.search),
                    suffixIcon: _searching
                        ? const Padding(
                            padding: EdgeInsets.all(12),
                            child: SizedBox(
                              width: 16,
                              height: 16,
                              child: CircularProgressIndicator(strokeWidth: 2),
                            ),
                          )
                        : null,
                  ),
                  onChanged: _onChanged,
                  onSubmitted: _pick,
                ),
              ),
              Expanded(
                child: ListView(
                  children: [
                    if (typedSymbol != null &&
                        _hits.every(
                          (h) => spokenCashtag(h.symbol) != typedSymbol,
                        ))
                      ListTile(
                        leading: const Icon(Icons.add),
                        title: Text('\$$typedSymbol'),
                        onTap: () => _pick(typedSymbol),
                      ),
                    for (final hit in _hits)
                      ListTile(
                        title: Text('\$${spokenCashtag(hit.symbol)}'),
                        subtitle: Text(
                          [
                            if (hit.name != null) hit.name,
                            if (hit.exchange != null) hit.exchange,
                          ].join(' · '),
                        ),
                        onTap: () => _pick(hit.symbol),
                      ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
