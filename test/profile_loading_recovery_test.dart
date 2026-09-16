import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:xta/profile/profile_model.dart';
import 'package:xta/user.dart';
import 'package:xta/utils/cached_page.dart';
import 'package:xta/utils/local_json_store.dart';

class _Storage implements JsonStore {
  Future<Object?> Function() reader = () async => null;
  @override
  Future<Object?> read(String key) => reader();
  @override
  Future<void> write(String key, Object? value) async {}
  @override
  Future<void> remove(String key) async {}
  @override
  Future<Map<String, Object?>> readPrefix(String prefix) async => {};
}

Profile _profile(String id) => Profile(UserWithExtra.fromArguments(idStr: id, screenName: id, name: id), []);

void main() {
  testWidgets('stalled profile cache does not prevent the network load', (tester) async {
    final storage = _Storage()..reader = () => Completer<Object?>().future;
    final model = ProfileModel(storage: storage, byId: (id) async => _profile(id));
    addTearDown(model.destroy);
    final loading = model.loadProfileById('new');
    await tester.pump();
    await tester.pump(const Duration(seconds: 2));
    await loading;
    expect(model.state.user.idStr, 'new');
    expect(model.isLoading, isFalse);
  });

  testWidgets('timed out profile recovers and ignores the old response', (tester) async {
    final old = Completer<Profile>();
    var calls = 0;
    final model = ProfileModel(
      storage: _Storage(),
      byId: (id) => ++calls == 1 ? old.future : Future.value(_profile(id)),
    );
    addTearDown(model.destroy);
    final loading = model.loadProfileById('old');
    await tester.pump();
    await tester.pump(const Duration(seconds: 31));
    await loading;
    expect(model.isLoading, isFalse);
    expect(model.error, isA<TimeoutException>());
    await model.loadProfileById('new');
    old.complete(_profile('old'));
    await tester.pump();
    expect(model.state.user.idStr, 'new');
    expect(model.error, isNull);
  });

  testWidgets('closing a stalled profile permits another profile to load', (tester) async {
    final old = Completer<Profile>();
    final first = ProfileModel(storage: _Storage(), byId: (_) => old.future);
    final loading = first.loadProfileById('old');
    await tester.pump();
    await first.destroy();
    await loading;
    final second = ProfileModel(storage: _Storage(), byId: (id) async => _profile(id));
    await second.loadProfileById('new');
    old.completeError(StateError('late failure'));
    await tester.pump();
    expect(second.state.user.idStr, 'new');
    expect(second.isLoading, isFalse);
    expect(tester.takeException(), isNull);
    await second.destroy();
  });

  testWidgets('stalled cache open and write cannot hold successful posts', (tester) async {
    final page = loadCachedPage<String>(
      readFresh: () => Completer<String?>().future,
      readStale: () async => null,
      fetch: () async => 'posts',
      write: (_) => Completer<void>().future,
    );
    await tester.pump(const Duration(seconds: 2));
    expect(await page, 'posts');
  });

  testWidgets('a stalled stale-cache read preserves the original network error', (tester) async {
    final error = StateError('network failed');
    final page = loadCachedPage<String>(
      bypassCache: true,
      readFresh: () async => throw StateError('must bypass'),
      readStale: () => Completer<String?>().future,
      fetch: () async => throw error,
      write: (_) async {},
    );
    final check = expectLater(page, throwsA(same(error)));
    await tester.pump();
    await tester.pump(const Duration(seconds: 2));
    await check;
  });

  test('cached page remains available when the network fails', () async {
    final page = await loadCachedPage<String>(
      readFresh: () async => null,
      readStale: () async => 'saved posts',
      fetch: () async => throw StateError('offline'),
      write: (_) async {},
    );
    expect(page, 'saved posts');
  });
}
