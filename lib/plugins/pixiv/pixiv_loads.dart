import 'dart:async';

import 'package:flutter/widgets.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
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

/// Any paged list that tracks its pages, for a screen part that owns it.
class PixivTrackedListStore<T> extends PixivPagedListStore<T> with PixivTrackedPages<T> {
  PixivTrackedListStore(super.loader, {required super.keyOf, super.filter});
}

/// An illustration list that tracks its pages, for a screen part that owns it.
class PixivTrackedIllustStore extends PixivIllustListStore with PixivTrackedPages<PixivIllust> {
  PixivTrackedIllustStore(super.loader, {super.filter});
}

/// A paged list a screen part owns, such as a profile tab: [create] makes it
/// when the part is first shown, it loads then, and it is let go, once its
/// loads settle, with the part. [feed] shows it.
class PixivOwnedFeed<S extends PixivTrackedPages<Object?>> extends StatefulWidget {
  final S Function(BuildContext context) create;
  final Widget Function(S store) feed;

  const PixivOwnedFeed({super.key, required this.create, required this.feed});

  @override
  State<PixivOwnedFeed<S>> createState() => _PixivOwnedFeedState<S>();
}

class _PixivOwnedFeedState<S extends PixivTrackedPages<Object?>> extends State<PixivOwnedFeed<S>> {
  late final S _store;

  @override
  void initState() {
    super.initState();
    _store = widget.create(context)..refresh();
  }

  @override
  void dispose() {
    _store.destroyWhenSettled();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => widget.feed(_store);
}
