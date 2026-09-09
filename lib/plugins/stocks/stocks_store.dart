import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/plugins/stocks/crypto_asset.dart';
import 'package:xta/database/repository.dart';
import 'package:sqflite/sqflite.dart';

/// The tickers the reader watches, kept in the database.
///
/// Legacy tickers retain their uppercase symbol and lowercase id. Crypto rows
/// use network/contract ids and versioned metadata in the existing symbol
/// column, so the standard backup format preserves exact project identities.
class StocksWatchlistStore extends Store<List<String>> {
  StocksWatchlistStore() : super(const []);

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
    return symbols;
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
