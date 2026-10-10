import 'package:flutter/foundation.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/plugins/booru/booru_models.dart';
import 'package:xta/plugins/booru/booru_store.dart';

@immutable
class BooruPagerState {
  final List<BooruPost> posts;
  final int index;

  const BooruPagerState({required this.posts, required this.index});

  BooruPost get current => posts[index];
}

/// The posts the viewer swipes through. Following [feed], it grows as the
/// feed loads more, and asks for more as the reader nears the end; a feed
/// that was refreshed into different posts leaves the viewer as it was.
class BooruPagerStore extends Store<BooruPagerState> {
  static const prefetchDistance = 4;

  final BooruFeedStore? feed;
  Disposer? _observer;

  BooruPagerStore({required List<BooruPost> posts, required int index, this.feed})
    : assert(posts.isNotEmpty),
      super(BooruPagerState(posts: posts, index: index.clamp(0, posts.length - 1))) {
    _observer = feed?.observer(onState: _follow);
  }

  void show(int index) {
    if (index < 0 || index >= state.posts.length) return;
    update(BooruPagerState(posts: state.posts, index: index));
    if (index >= state.posts.length - prefetchDistance) feed?.loadMore();
  }

  void _follow(List<BooruPost> next) {
    if (!booruPostsExtend(state.posts, next)) return;
    update(BooruPagerState(posts: next, index: state.index));
  }

  @override
  Future<void> destroy() async {
    final observer = _observer;
    _observer = null;
    await observer?.call();
    await super.destroy();
  }
}

/// Whether [next] is [current] with more posts after it.
bool booruPostsExtend(List<BooruPost> current, List<BooruPost> next) {
  if (next.length <= current.length || current.isEmpty) return false;
  bool same(int i) => current[i].key == next[i].key;
  return same(0) && same(current.length - 1);
}
