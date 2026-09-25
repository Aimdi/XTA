import 'package:flutter/material.dart';
import 'package:xta/plugins/plugin_feed_insets.dart';

class PluginReadingPosition<T> {
  final List<T> posts;
  final String anchor;
  final double leading;
  const PluginReadingPosition({required this.posts, required this.anchor, this.leading = 0});
}

/// Anchors variable-height reader rows without changing the source page order.
class PluginReadingView<T> extends StatefulWidget {
  final List<T> posts;
  final List<T>? snapshotPosts;
  final ScrollController controller;
  final String Function(T) keyOf;
  final Widget Function(BuildContext, T) itemBuilder;
  final ValueChanged<PluginReadingPosition<T>> onRemember;
  final VoidCallback? onFlush;
  final PluginReadingPosition<T>? initial;
  final bool restoreInitial;
  final int snapshotLimit;
  final Widget? heading;
  final Widget? footer;
  final bool loadingMore;
  const PluginReadingView({
    super.key,
    required this.posts,
    required this.controller,
    required this.keyOf,
    required this.itemBuilder,
    required this.onRemember,
    this.snapshotPosts,
    this.onFlush,
    this.initial,
    this.restoreInitial = true,
    this.snapshotLimit = 144,
    this.heading,
    this.footer,
    this.loadingMore = false,
  });
  @override
  State<PluginReadingView<T>> createState() => _PluginReadingViewState<T>();
}

class _PluginReadingViewState<T> extends State<PluginReadingView<T>> with WidgetsBindingObserver {
  final _viewport = GlobalKey();
  final _center = GlobalKey();
  final _rows = <String, GlobalKey>{};
  String? _anchor;
  PluginReadingPosition<T>? _initial;
  bool _restoring = true;

  @override
  void initState() {
    super.initState();
    _initial = widget.initial;
    _anchor = _initial?.anchor;
    WidgetsBinding.instance.addObserver(this);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      final controller = pluginInnerScrollController(context, widget.controller);
      if (widget.restoreInitial &&
          _anchor != null &&
          controller?.hasClients == true &&
          widget.posts.any((post) => widget.keyOf(post) == _anchor)) {
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
      widget.onFlush?.call();
    }
  }

  @override
  void didUpdateWidget(covariant PluginReadingView<T> oldWidget) {
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
      final box = _rows[widget.keyOf(widget.posts[index])]?.currentContext?.findRenderObject();
      if (box is! RenderBox || !box.hasSize) continue;
      final y = box.localToGlobal(Offset.zero).dy - top;
      if (y + box.size.height > 1 && y < viewport.size.height) {
        visible = index;
        leading = y;
        break;
      }
    }
    final source = widget.snapshotPosts ?? widget.posts;
    final sourceIndex = source.indexWhere((post) => widget.keyOf(post) == widget.keyOf(widget.posts[visible]));
    final start = ((sourceIndex < 0 ? 0 : sourceIndex) - 24).clamp(0, source.length);
    widget.onRemember(
      PluginReadingPosition(
        posts: source.skip(start).take(widget.snapshotLimit).toList(),
        anchor: widget.keyOf(widget.posts[visible]),
        leading: leading,
      ),
    );
  }

  @override
  void dispose() {
    widget.onFlush?.call();
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  Widget _post(int index) {
    final post = widget.posts[index];
    return KeyedSubtree(
      key: _rows.putIfAbsent(widget.keyOf(post), GlobalKey.new),
      child: widget.itemBuilder(context, post),
    );
  }

  @override
  Widget build(BuildContext context) {
    _rows.removeWhere((url, key) => !widget.posts.any((post) => widget.keyOf(post) == url));
    final anchorIndex = _anchor == null ? -1 : widget.posts.indexWhere((post) => widget.keyOf(post) == _anchor);
    final prefix = widget.heading == null ? 0 : 1;
    final padding = pluginFeedPadding(context);
    final footer = widget.loadingMore || widget.footer != null ? 1 : 0;
    Widget tail() =>
        widget.footer ??
        const Padding(
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
