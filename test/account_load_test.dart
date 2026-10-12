import 'package:flutter_test/flutter_test.dart';
import 'package:xta/client/account_load.dart';

void main() {
  test('counts requests in flight per account and endpoint', () {
    AccountLoad.begin('a', '/Search');
    AccountLoad.begin('a', '/Search');
    AccountLoad.begin('a', '/Detail');
    expect(AccountLoad.of('a', '/Search'), 2);
    expect(AccountLoad.of('a', '/Detail'), 1);
    expect(AccountLoad.of('b', '/Search'), 0);

    AccountLoad.end('a', '/Search');
    AccountLoad.end('a', '/Search');
    AccountLoad.end('a', '/Search');
    AccountLoad.end('a', '/Detail');
    expect(AccountLoad.of('a', '/Search'), 0, reason: 'an extra end never goes negative');
    expect(AccountLoad.of('a', '/Detail'), 0);
  });
}
