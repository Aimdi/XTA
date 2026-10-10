import 'dart:async';

import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/plugins/pixiv/pixiv_store.dart';

/// The loads a screen's stores have running. triple does not cancel a load
/// when its store is destroyed, and one that lands afterwards writes to a
/// disposed store, so a closing screen destroys its stores once these settle.
class PixivLoads {
  final _pending = <Future<void>>{};

  /// Watches [load] and hands it back.
  Future<void> track(Future<void> load) {
    final settled = load.then<void>((_) {}, onError: (Object _) {});
    _pending.add(settled);
    unawaited(settled.whenComplete(() => _pending.remove(settled)));
    return load;
  }

  /// Destroys [stores] after every load tracked so far has settled.
  void destroyAfter(List<Store<Object?>> stores) => unawaited(
    Future.wait(_pending).whenComplete(() {
      for (final store in stores) {
        store.destroy();
      }
    }),
  );
}

/// A paged list that tracks every page it fetches, whoever asked for it (its
/// screen, a pull to refresh or the grid scrolling on), so its owner can let
/// it go with [destroyWhenSettled].
mixin PixivTrackedPages<T> on PixivPagedListStore<T> {
  final _loads = PixivLoads();

  @override
  Future<void> refresh() => _loads.track(super.refresh());

  @override
  Future<void> loadMore() => _loads.track(super.loadMore());

  void destroyWhenSettled() => _loads.destroyAfter([this]);
}
