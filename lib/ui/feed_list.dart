import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart' show ScrollCacheExtent;
import 'package:xta/constants.dart';
import 'package:xta/plugins/plugin_feed_insets.dart';

/// A timeline list with the same scroll budget as the X feed.
///
/// Off-screen tiles stay cheap: no keep-alives, and a cache window that only
/// exists because video players wait until they are on screen before allocating.
class FeedListView extends StatelessWidget {
  final int itemCount;
  final IndexedWidgetBuilder itemBuilder;
  final ScrollController? controller;
  final EdgeInsetsGeometry? padding;
  final ScrollPhysics? physics;

  const FeedListView({
    super.key,
    required this.itemCount,
    required this.itemBuilder,
    this.controller,
    this.padding,
    this.physics,
  });

  @override
  Widget build(BuildContext context) {
    return ListView.builder(
      controller: pluginInnerScrollController(context, controller),
      padding: padding,
      physics: physics,
      itemCount: itemCount,
      scrollCacheExtent: const ScrollCacheExtent.pixels(kFeedListCacheExtent),
      addAutomaticKeepAlives: false,
      // Clip so a card cannot paint under a sibling chrome row (plugin home).
      clipBehavior: Clip.hardEdge,
      itemBuilder: itemBuilder,
    );
  }
}
