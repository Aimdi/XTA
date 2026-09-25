import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:xta/plugins/mastodon/mastodon_models.dart';
import 'package:xta/plugins/mastodon/mastodon_post_card.dart';
import 'package:xta/plugins/mastodon/mastodon_reading_store.dart';
import 'package:xta/plugins/plugin_reading_view.dart';

List<MastodonPost> uniqueMastodonReadingPosts(List<MastodonPost> posts) {
  final seen = <String>{};
  final unique = [
    for (final post in posts)
      if (seen.add(post.url)) post,
  ];
  return unique.length == posts.length ? posts : unique;
}

class MastodonReadingList extends StatefulWidget {
  final String slot;
  final List<MastodonPost> posts;
  final List<MastodonPost>? snapshotPosts;
  final List<MastodonTrendingTag> tags;
  final String? instance;
  final ScrollController controller;
  final Widget? heading;
  final bool loadingMore;
  final Widget? footer;
  MastodonReadingList({
    super.key,
    required this.slot,
    required List<MastodonPost> posts,
    required this.controller,
    this.snapshotPosts,
    this.heading,
    this.tags = const [],
    this.instance,
    this.loadingMore = false,
    this.footer,
  }) : posts = uniqueMastodonReadingPosts(posts);
  @override
  State<MastodonReadingList> createState() => _MastodonReadingListState();
}

class _MastodonReadingListState extends State<MastodonReadingList> {
  late final MastodonReadingStore _memory;
  late final ({MastodonReadPoint? point, bool position}) _layout;
  @override
  void initState() {
    super.initState();
    _memory = context.read<MastodonReadingStore>();
    _layout = _memory.layoutPoint(widget.slot);
  }

  @override
  Widget build(BuildContext context) => PluginReadingView<MastodonPost>(
    posts: widget.posts,
    snapshotPosts: widget.snapshotPosts,
    controller: widget.controller,
    keyOf: (post) => post.url,
    itemBuilder: (_, post) => MastodonPostCard(post: post, showSourceBadge: false),
    heading: widget.heading,
    footer: widget.footer,
    loadingMore: widget.loadingMore,
    initial: _layout.point == null
        ? null
        : PluginReadingPosition(
            posts: _layout.point!.posts,
            anchor: _layout.point!.anchor,
            leading: _layout.point!.leading,
          ),
    restoreInitial: _layout.position,
    onFlush: _memory.flush,
    onRemember: (position) => _memory.remember(
      widget.slot,
      MastodonReadPoint(
        posts: position.posts,
        anchor: position.anchor,
        leading: position.leading,
        tags: widget.tags,
        instance: widget.instance,
      ),
    ),
  );
}
