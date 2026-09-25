import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:xta/search/search_model.dart';
import 'package:xta/user.dart';

UserWithExtra _user(String handle) => UserWithExtra.fromArguments(idStr: handle, screenName: handle);

void main() {
  test('an obsolete success cannot replace the latest people results', () async {
    final old = Completer<List<UserWithExtra>>();
    final latest = Completer<List<UserWithExtra>>();
    final store = SearchUsersModel(search: (query) => query == 'old' ? old.future : latest.future);
    addTearDown(store.destroy);
    final first = store.searchUsers('old');
    final second = store.searchUsers(' latest ');
    latest.complete([_user('latest')]);
    await second;
    old.complete([_user('old')]);
    await first;

    expect(store.state.single.screenName, 'latest');
    expect(store.isLoading, isFalse);
    expect(store.error, isNull);
  });

  test('an obsolete failure cannot stop a newer pending search', () async {
    final old = Completer<List<UserWithExtra>>();
    final latest = Completer<List<UserWithExtra>>();
    final store = SearchUsersModel(search: (query) => query == 'old' ? old.future : latest.future);
    addTearDown(store.destroy);
    final first = store.searchUsers('old');
    final second = store.searchUsers('latest');
    old.completeError(StateError('old failure'));
    await first;

    expect(store.isLoading, isTrue);
    expect(store.error, isNull);
    latest.complete([_user('latest')]);
    await second;
    expect(store.state.single.screenName, 'latest');
  });

  test('clearing people search cancels pending results without another request', () async {
    final result = Completer<List<UserWithExtra>>();
    var requests = 0;
    final store = SearchUsersModel(
      search: (_) {
        requests++;
        return result.future;
      },
    );
    addTearDown(store.destroy);
    final pending = store.searchUsers('old');
    await store.searchUsers('  ');
    result.complete([_user('old')]);
    await pending;

    expect(requests, 1);
    expect(store.state, isEmpty);
    expect(store.error, isNull);
    expect(store.isLoading, isFalse);
  });

  test('a failed people search can be retried and clears its error', () async {
    var requests = 0;
    final store = SearchUsersModel(
      search: (_) async {
        if (++requests == 1) throw StateError('offline');
        return [_user('recovered')];
      },
    );
    addTearDown(store.destroy);
    await store.searchUsers('retry');
    expect(store.error, isA<StateError>());
    await store.searchUsers('retry');

    expect(store.state.single.screenName, 'recovered');
    expect(store.error, isNull);
    expect(store.isLoading, isFalse);
  });

  test('disposing cancels the wait and ignores late success and new searches', () async {
    final result = Completer<List<UserWithExtra>>();
    var requests = 0;
    final store = SearchUsersModel(
      search: (_) {
        requests++;
        return result.future;
      },
    );
    final pending = store.searchUsers('old');
    await store.destroy();
    await pending;
    result.complete([_user('old')]);
    await store.searchUsers('new');
    expect(requests, 1);
    expect(store.state, isEmpty);
  });

  testWidgets('a stalled people request times out and permits retry', (tester) async {
    final stalled = Completer<List<UserWithExtra>>();
    var requests = 0;
    final store = SearchUsersModel(
      requestTimeout: const Duration(seconds: 1),
      search: (_) => ++requests == 1 ? stalled.future : Future.value([_user('retry')]),
    );
    addTearDown(store.destroy);
    final pending = store.searchUsers('query');
    await tester.pump(const Duration(seconds: 1));
    await pending;
    expect(store.error, isA<TimeoutException>());
    expect(store.isLoading, isFalse);
    await store.searchUsers('query');
    stalled.complete([_user('stale')]);
    await tester.pump();
    expect(store.state.single.screenName, 'retry');
    expect(store.error, isNull);
  });
}
