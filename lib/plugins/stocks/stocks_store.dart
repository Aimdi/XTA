import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/database/repository.dart';
import 'package:sqflite/sqflite.dart';

/// The tickers the reader watches, kept in the database.
///
/// Symbols are stored uppercase and the row id is the lowercased symbol, so a
/// watchlist entry can be joined against group membership like every other
/// subscription kind.
class StocksWatchlistStore extends Store<List<String>> {
  StocksWatchlistStore() : super(const []);

  Future<void> load() async {
    await execute(_read);
  }

  Future<List<String>> _read() async {
    final database = await Repository.readOnly();
    final rows = await database.query(
      tableStockSubscription,
      orderBy: 'symbol COLLATE NOCASE',
    );

    return rows.map((e) => e['symbol'] as String).toList(growable: false);
  }

  Future<void> _write(String symbol) async {
    final normalised = normaliseTicker(symbol);
    if (normalised == null) {
      return;
    }

    final database = await Repository.writable();
    await database.insert(
      tableStockSubscription,
      StockSubscription(
        id: normalised.toLowerCase(),
        symbol: normalised,
        createdAt: DateTime.now(),
        inFeed: true,
      ).toMap(),
      conflictAlgorithm: ConflictAlgorithm.replace,
    );
  }

  Future<void> add(String symbol) async {
    await execute(() async {
      await _write(symbol);
      return _read();
    });
  }

  Future<void> remove(String symbol) async {
    await execute(() async {
      final id = symbol.toLowerCase();
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
