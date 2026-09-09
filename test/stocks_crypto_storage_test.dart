import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/database/repository.dart';
import 'package:xta/plugins/stocks/crypto_asset.dart';
import 'package:xta/plugins/stocks/stocks_store.dart';

const _first = CryptoAsset(
  chain: 'solana',
  address: 'AbCdEfGh123456789AbCdEfGh123456789',
  symbol: 'SAME',
  name: 'First',
);
const _second = CryptoAsset(
  chain: 'solana',
  address: 'ZbCdEfGh123456789AbCdEfGh123456789',
  symbol: 'SAME',
  name: 'Second',
);

void main() {
  setUpAll(() async {
    sqfliteFfiInit();
    databaseFactory = databaseFactoryFfi;
    final dir = await Directory.systemTemp.createTemp('xta_crypto_storage');
    await databaseFactory.setDatabasesPath(dir.path);
    await Repository().migrate();
  });

  setUp(() async {
    final database = await Repository.writable();
    await database.delete(tableStockSubscription);
  });

  test('legacy stocks and equal-symbol tokens survive reload and backup mapping', () async {
    final store = StocksWatchlistStore();
    await store.add('AAPL');
    await store.add(_first.encode());
    await store.add(_second.encode());
    final reopened = StocksWatchlistStore();
    await reopened.load();
    expect(reopened.state.toSet(), {'AAPL', _first.id, _second.id});
    expect(reopened.assetFor(_first.id)!.name, 'First');
    expect(reopened.assetFor(_second.id)!.name, 'Second');
    final database = await Repository.readOnly();
    final rows = await database.query(tableStockSubscription, where: 'id = ?', whereArgs: [_first.id]);
    final backupRow = StockSubscription.fromMap(rows.single).toMap();
    expect(CryptoAsset.decode(backupRow['symbol'] as String)!.id, _first.id);
    await store.destroy();
    await reopened.destroy();
  });

  test('duplicate add preserves feed choice; removal preserves case-sensitive identity', () async {
    final store = StocksWatchlistStore();
    await store.add(_first.encode());
    await store.add(_second.encode());
    final database = await Repository.writable();
    await database.update(tableStockSubscription, {'in_feed': 0}, where: 'id = ?', whereArgs: [_first.id]);
    await store.add(_first.encode());
    final rows = await database.query(tableStockSubscription, where: 'id = ?', whereArgs: [_first.id]);
    expect(rows.single['in_feed'], 0);
    await store.remove(_first.id);
    expect(store.state, isNot(contains(_first.id)));
    expect(store.state, contains(_second.id));
    await store.destroy();
  });
}
