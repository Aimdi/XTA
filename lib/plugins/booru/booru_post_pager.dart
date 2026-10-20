import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/plugins/booru/booru_image.dart';
import 'package:xta/plugins/booru/booru_models.dart';
import 'package:xta/plugins/booru/booru_pager_store.dart';
import 'package:xta/plugins/booru/booru_post_actions.dart';
import 'package:xta/plugins/booru/booru_post_lookups.dart';
import 'package:xta/plugins/booru/booru_post_screen.dart';
import 'package:xta/plugins/booru/booru_search_store.dart';
import 'package:xta/plugins/booru/booru_store.dart';

/// The post viewer: swipe sideways through the list a post was opened from.
/// Given its [feed], it keeps going as the feed loads more.
class BooruPostPager extends StatefulWidget {
  final List<BooruPost> posts;
  final int initialIndex;

  /// The search the posts came from; a post's tags can be added to it.
  final BooruSearchStore? search;
  final BooruFeedStore? feed;

  const BooruPostPager({super.key, required this.posts, this.initialIndex = 0, this.search, this.feed});

  @override
  State<BooruPostPager> createState() => _BooruPostPagerState();
}

class _BooruPostPagerState extends State<BooruPostPager> {
  late final _store = BooruPagerStore(posts: widget.posts, index: widget.initialIndex, feed: widget.feed);
  late final _pages = PageController(initialPage: _store.state.index);
  final _lookups = BooruPostLookups();

  /// Waits for the reader to stop on a post before loading its neighbours,
  /// so a fast run of swipes loads nothing ahead.
  Timer? _preload;
  static const _settle = Duration(milliseconds: 300);

  @override
  void initState() {
    super.initState();
    _preload = Timer(_settle, () => _preloadAround(_store.state.index));
  }

  void _show(int index) {
    _store.show(index);
    _preload?.cancel();
    _preload = Timer(_settle, () => _preloadAround(index));
  }

  /// The neighbours' pictures at the width the page shows them.
  void _preloadAround(int index) {
    if (!mounted) return;
    final width = (MediaQuery.sizeOf(context).width * MediaQuery.devicePixelRatioOf(context)).ceil();
    for (final url in booruPreloadUrls(_store.state.posts, index)) {
      unawaited(precacheImage(booruImageProvider(url, cacheWidth: width), context, onError: (_, _) {}));
    }
  }

  @override
  void dispose() {
    _preload?.cancel();
    _pages.dispose();
    unawaited(_store.destroy());
    super.dispose();
  }

  /// A page change rebuilds the app bar alone; the pages are rebuilt only
  /// when the feed adds posts.
  @override
  Widget build(BuildContext context) {
    return Provider<BooruPostLookups>.value(
      value: _lookups,
      child: Scaffold(
        appBar: PreferredSize(
          preferredSize: const Size.fromHeight(kToolbarHeight),
          child: ScopedBuilder<BooruPagerStore, BooruPagerState>(
            store: _store,
            distinct: (state) => state.current.key,
            onState: (context, state) => AppBar(
              title: Text('#${state.current.id}'),
              actions: [BooruPostActions(key: ValueKey(state.current.key), post: state.current)],
            ),
          ),
        ),
        body: ScopedBuilder<BooruPagerStore, BooruPagerState>(
          store: _store,
          distinct: (state) => state.posts,
          onState: (context, state) => PageView.builder(
            controller: _pages,
            itemCount: state.posts.length,
            onPageChanged: _show,
            itemBuilder: (context, index) => BooruPostDetails(
              key: ValueKey(state.posts[index].key),
              post: state.posts[index],
              search: widget.search,
            ),
          ),
        ),
      ),
    );
  }
}
