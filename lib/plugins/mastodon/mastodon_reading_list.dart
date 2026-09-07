import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:xta/plugins/mastodon/mastodon_models.dart';
import 'package:xta/plugins/mastodon/mastodon_post_card.dart';
import 'package:xta/plugins/mastodon/mastodon_reading_store.dart';
import 'package:xta/plugins/plugin_feed_insets.dart';

class MastodonReadingList extends StatefulWidget {
  final String slot;
  final List<MastodonPost> posts;
  final List<MastodonTrendingTag> tags;
  final String? instance;
  final ScrollController controller;
  final Widget? heading;
  final bool loadingMore;
  const MastodonReadingList({
    super.key,
    required this.slot,
    required this.posts,
    required this.controller,
    this.heading,
    this.tags = const [],
    this.instance,
    this.loadingMore = false,
  });
  @override
  State<MastodonReadingList> createState() => _MastodonReadingListState();
}

class _MastodonReadingListState extends State<MastodonReadingList> with WidgetsBindingObserver {
  late final MastodonReadingStore _memory;
  final _viewport = GlobalKey();
  final _center = GlobalKey();
  final _rows = <String, GlobalKey>{};
  String? _anchor;
  MastodonReadPoint? _initial;
  bool _restoring = true;

  @override
  void initState() {
    super.initState();
    _memory = context.read<MastodonReadingStore>();
    final layout = _memory.layoutPoint(widget.slot);
    _initial = layout.point;
    _anchor = _initial?.anchor;
    WidgetsBinding.instance.addObserver(this);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      final controller = pluginInnerScrollController(context, widget.controller);
      if (layout.position &&
          _anchor != null &&
          controller?.hasClients == true &&
          widget.posts.any((post) => post.url == _anchor)) {
        controller!.jumpTo(
          (-_initial!.leading).clamp(controller.position.minScrollExtent, controller.position.maxScrollExtent),
        );
      }
      _restoring = false;
      _remember();
    });
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state != AppLifecycleState.resumed) {
      _remember();
      _memory.flush();
    }
  }

  @override
  void didUpdateWidget(covariant MastodonReadingList oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.posts != widget.posts) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) _remember();
      });
    }
  }

  void _remember() {
    if (_restoring || !mounted || widget.posts.isEmpty) return;
    final viewport = _viewport.currentContext?.findRenderObject();
    if (viewport is! RenderBox || !viewport.hasSize) return;
    final top = viewport.localToGlobal(Offset.zero).dy;
    var visible = 0;
    var leading = 0.0;
    for (var index = 0; index < widget.posts.length; index++) {
      final box = _rows[widget.posts[index].url]?.currentContext?.findRenderObject();
      if (box is! RenderBox || !box.hasSize) continue;
      final y = box.localToGlobal(Offset.zero).dy - top;
      if (y + box.size.height > 1 && y < viewport.size.height) {
        visible = index;
        leading = y;
        break;
      }
    }
    final start = (visible - 24).clamp(0, widget.posts.length);
    _memory.remember(
      widget.slot,
      MastodonReadPoint(
        posts: widget.posts.skip(start).take(mastodonReadingLimit).toList(),
        tags: widget.tags,
        anchor: widget.posts[visible].url,
        leading: leading,
        instance: widget.instance,
      ),
    );
  }

  @override
  void dispose() {
    _memory.flush();
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  Widget _post(int index) {
    final post = widget.posts[index];
    return KeyedSubtree(
      key: _rows.putIfAbsent(post.url, GlobalKey.new),
      child: MastodonPostCard(post: post, showSourceBadge: false),
    );
  }

  @override
  Widget build(BuildContext context) {
    _rows.removeWhere((url, key) => !widget.posts.any((post) => post.url == url));
    final anchorIndex = _anchor == null ? -1 : widget.posts.indexWhere((post) => post.url == _anchor);
    final prefix = widget.heading == null ? 0 : 1;
    final padding = pluginFeedPadding(context);
    final footer = widget.loadingMore ? 1 : 0;
    Widget tail() => const Padding(
      padding: EdgeInsets.all(16),
      child: Center(child: CircularProgressIndicator()),
    );
    return NotificationListener<ScrollNotification>(
      onNotification: (notification) {
        if (notification is ScrollEndNotification && notification.depth == 0) {
          WidgetsBinding.instance.addPostFrameCallback((_) {
            if (mounted) _remember();
          });
        }
        return false;
      },
      child: CustomScrollView(
        key: _viewport,
        controller: pluginInnerScrollController(context, widget.controller),
        primary: false,
        physics: const AlwaysScrollableScrollPhysics(),
        center: anchorIndex < 0 ? null : _center,
        slivers: [
          if (anchorIndex >= 0)
            SliverList.builder(
              itemCount: anchorIndex + prefix,
              itemBuilder: (context, index) {
                final before = anchorIndex - index - 1;
                return before < 0 ? widget.heading! : _post(before);
              },
            ),
          SliverList.builder(
            key: anchorIndex < 0 ? null : _center,
            itemCount: anchorIndex < 0
                ? widget.posts.length + prefix + footer
                : widget.posts.length - anchorIndex + footer,
            itemBuilder: (context, index) {
              if (anchorIndex < 0 && index < prefix) return widget.heading!;
              final postIndex = anchorIndex < 0 ? index - prefix : index + anchorIndex;
              return postIndex >= widget.posts.length ? tail() : _post(postIndex);
            },
          ),
          SliverPadding(padding: padding),
        ],
      ),
    );
  }
}
