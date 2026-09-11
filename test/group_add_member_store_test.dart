import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:xta/subscriptions/group_add_member_store.dart';
import 'package:xta/user.dart';

void main() {
  test('failed addition releases busy state and can be retried', () async {
    final store = GroupAddMemberStore();
    expect(await store.add('x:1', () async => throw StateError('write failed')), isFalse);
    expect(store.state.adding, isFalse);
    expect(store.state.added, isEmpty);
    expect(await store.add('x:1', () async => '1'), isTrue);
    expect(store.state.added, {'1'});
    expect(store.state.adding, isFalse);
    await store.destroy();
  });

  test('double tap does not repeat a follow while pending or after success', () async {
    final store = GroupAddMemberStore();
    final result = Completer<String>();
    var calls = 0;
    Future<String> follow() {
      calls++;
      return result.future;
    }

    final adding = store.add('bluesky:alice', follow);
    expect(store.state.adding, isTrue);
    expect(await store.add('bluesky:alice', follow), isFalse);
    result.complete('alice.bsky.social');
    expect(await adding, isTrue);
    expect(await store.add('bluesky:alice', follow), isFalse);
    expect(calls, 1);
    expect(store.state.added, {'alice.bsky.social'});
    await store.destroy();
  });

  test('dismissal while adding does not update a disposed store', () async {
    final store = GroupAddMemberStore();
    final result = Completer<String>();
    final adding = store.add('x:1', () => result.future);
    await store.destroy();
    result.complete('1');
    expect(await adding, isTrue);
  });

  test('an earlier search cannot replace the latest result for the same query', () async {
    final store = GroupAddMemberStore();
    final old = Completer<List<UserWithExtra>>();
    final searching = store.search('alice', (_) => old.future);
    final newest = UserWithExtra.fromArguments(idStr: 'new');
    await store.search('alice', (_) async => [newest]);
    old.complete([UserWithExtra.fromArguments(idStr: 'old')]);
    await searching;
    expect(store.state.users!.single.idStr, 'new');
    expect(store.state.searching, isFalse);
    await store.destroy();
  });

  testWidgets('a hanging search times out and allows a new search', (tester) async {
    final store = GroupAddMemberStore();
    final searching = store.search('alice', (_) => Completer<List<UserWithExtra>>().future);
    await tester.pump(const Duration(seconds: 31));
    await searching;
    expect(store.state.searching, isFalse);
    expect(store.state.searchError, isA<TimeoutException>());
    await store.search('bob', (_) async => []);
    expect(store.state.searchError, isNull);
    expect(store.state.searching, isFalse);
    await store.destroy();
  });
}
