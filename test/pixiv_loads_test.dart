import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:xta/plugins/pixiv/pixiv_loads.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_user_list_screen.dart';
import 'package:xta/plugins/plugin_view_store.dart';

import 'support/pixiv_social_fakes.dart';

void main() {
  test('destroys the stores only once the loads tracked so far settle', () async {
    final loads = PixivLoads();
    final store = PluginViewStore<int>(0);
    final gate = Completer<void>();
    final write = loads.track(gate.future.then((_) => store.select(1)));
    loads.destroyAfter([store]);
    await pumpEventQueue();

    gate.complete();
    await write;
    expect(store.state, 1);
    await pumpEventQueue();
    expect(() => store.select(2), throwsA(anything));
  });

  test('a failed load still lets the stores go', () async {
    final loads = PixivLoads();
    final store = PluginViewStore<int>(0);
    final failing = loads.track(Future<void>.error(StateError('offline')));
    loads.destroyAfter([store]);
    await expectLater(failing, throwsStateError);
    await pumpEventQueue();
    expect(() => store.select(2), throwsA(anything));
  });

  test('a tracked list waits for the page it is fetching before it is destroyed', () async {
    final gate = Completer<void>();
    final store = PixivUserListStore(({nextUrl}) async {
      await gate.future;
      return PixivPage([pixivPreviewOf(1)]);
    });
    final refresh = store.refresh();
    store.destroyWhenSettled();
    gate.complete();
    await refresh;
    expect(store.state.single.user.id, 1);
  });
}
