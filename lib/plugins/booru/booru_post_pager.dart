import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/plugins/booru/booru_models.dart';
import 'package:xta/plugins/booru/booru_pager_store.dart';
import 'package:xta/plugins/booru/booru_post_actions.dart';
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

  @override
  void dispose() {
    _pages.dispose();
    unawaited(_store.destroy());
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return ScopedBuilder<BooruPagerStore, BooruPagerState>(
      store: _store,
      onState: (context, state) => Scaffold(
        appBar: AppBar(
          title: Text('#${state.current.id}'),
          actions: [BooruPostActions(key: ValueKey(state.current.key), post: state.current)],
        ),
        body: PageView.builder(
          controller: _pages,
          itemCount: state.posts.length,
          onPageChanged: _store.show,
          itemBuilder: (context, index) =>
              BooruPostDetails(key: ValueKey(state.posts[index].key), post: state.posts[index], search: widget.search),
        ),
      ),
    );
  }
}
