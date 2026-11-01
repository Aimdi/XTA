import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/plugins/stocks/crypto_asset.dart';
import 'package:xta/database/repository.dart';
import 'package:sqflite/sqflite.dart';
import 'package:xta/utils/pref_lists.dart';

/// The tickers the reader watches, kept in the database.
///
/// Legacy tickers retain their uppercase symbol and lowercase id. Crypto rows
/// use network/contract ids and versioned metadata in the existing symbol
/// column, so the standard backup format preserves exact project identities.
class StocksWatchlistStore extends Store<List<String>> {
  /// [prefs] keeps the order the reader drags the rows into; without it the
  /// order lasts until the list is read again.
  StocksWatchlistStore({this.prefs}) : super(const []);

  final BasePrefService? prefs;

  Map<String, CryptoAsset> _assets = const {};
  Map<String, CryptoAsset> get cryptoAssets => _assets;
  CryptoAsset? assetFor(String id) => _assets[id];
  String labelFor(String id) => _assets[id]?.label ?? '\$$id';

  Future<void> load() async {
    await execute(_read);
  }

  Future<List<String>> _read() async {
    final database = await Repository.readOnly();
    final rows = await database.query(
      tableStockSubscription,
      orderBy: 'symbol COLLATE NOCASE',
    );

    final assets = <String, CryptoAsset>{};
    final symbols = <String>[];
    for (final row in rows) {
      final stored = row['symbol'] as String;
      final asset = CryptoAsset.decode(stored);
      if (asset != null) assets[asset.id] = asset;
      symbols.add(asset?.id ?? stored);
    }
    _assets = Map.unmodifiable(assets);
    symbols.sort((a, b) => labelFor(a).compareTo(labelFor(b)));
    final prefs = this.prefs;
    return stocksOrdered(symbols, prefs == null ? null : stringListPref(prefs, optionPluginStocksWatchlistOrder));
  }

  /// Moves the row at [from] so it ends up at [to], and keeps the new order.
  Future<void> reorder(int from, int to) async {
    if (from < 0 || from >= state.length) return;
    final next = [...state];
    final moved = next.removeAt(from);
    next.insert(to.clamp(0, next.length), moved);
    update(next);
    await prefs?.set(optionPluginStocksWatchlistOrder, next);
  }

  Future<void> _write(String symbol) async {
    final asset = CryptoAsset.decode(symbol);
    final normalised = asset?.encode() ?? normaliseTicker(symbol);
    if (normalised == null) {
      return;
    }

    final database = await Repository.writable();
    await database.insert(
      tableStockSubscription,
      StockSubscription(
        id: asset?.id ?? normalised.toLowerCase(),
        symbol: normalised,
        createdAt: DateTime.now(),
        inFeed: true,
      ).toMap(),
      conflictAlgorithm: ConflictAlgorithm.ignore,
    );
  }

  Future<void> add(String symbol) async {
    await execute(() async {
      await _write(symbol);
      return _read();
    });
  }

  /// Rewrites a watched token's stored metadata with the reader's choice of
  /// searching its cashtag too. The id is unchanged, so groups keep it.
  Future<void> setIncludeCashtag(CryptoAsset asset, bool include) async {
    await execute(() async {
      final database = await Repository.writable();
      await database.update(
        tableStockSubscription,
        {'symbol': asset.withCashtag(include).encode()},
        where: 'id = ?',
        whereArgs: [asset.id],
      );
      return _read();
    });
  }

  Future<void> remove(String symbol) async {
    await execute(() async {
      final id = CryptoAsset.contractForId(symbol) == null ? symbol.toLowerCase() : symbol;
      final database = await Repository.writable();
      await database.delete(
        tableStockSubscription,
        where: 'id = ?',
        whereArgs: [id],
      );
      // A ticker that is gone should not linger as a member of a group.
      await database.delete(
        tableSubscriptionGroupMember,
        where: 'profile_id = ?',
        whereArgs: [id],
      );
      return _read();
    });
  }

  /// Pulls a ticker out of whatever the reader typed: `aapl`, `$AAPL`,
  /// `BRK.B`, `^GSPC`. Returns null when it is not a symbol at all.
  static String? normaliseTicker(String raw) {
    var text = raw.trim();
    if (text.startsWith(r'$')) {
      text = text.substring(1).trim();
    }

    return _tickerPattern.hasMatch(text) ? text.toUpperCase() : null;
  }
}

final RegExp _tickerPattern = RegExp(r'^[A-Za-z0-9.^-]{1,10}$');

/// [symbols] in the reader's [order], those it does not name after them in
/// the order they came: an id that left the watchlist is skipped, a new one
/// joins at the end.
List<String> stocksOrdered(List<String> symbols, List<String>? order) {
  if (order == null || order.isEmpty) return symbols;
  final present = symbols.toSet();
  final arranged = [for (final id in order) if (present.contains(id)) id];
  final placed = arranged.toSet();
  return [...arranged, for (final id in symbols) if (!placed.contains(id)) id];
}
