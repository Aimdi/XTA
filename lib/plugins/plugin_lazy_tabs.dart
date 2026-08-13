import 'package:flutter/material.dart';

/// Builds each tab the first time it is selected, then keeps it.
///
/// [IndexedStack] constructs every child on the first frame, so opening
/// Substack also built Inbox / Notes / Library (and Bluesky built Liked)
/// before the home feed had painted. Home-strip remounts made that worse.
class PluginLazyTabs extends StatefulWidget {
  final int index;
  final List<WidgetBuilder> children;

  const PluginLazyTabs({
    super.key,
    required this.index,
    required this.children,
  });

  @override
  State<PluginLazyTabs> createState() => _PluginLazyTabsState();
}

class _PluginLazyTabsState extends State<PluginLazyTabs> {
  final _activated = <int>{};

  @override
  void initState() {
    super.initState();
    _activated.add(widget.index);
  }

  @override
  void didUpdateWidget(covariant PluginLazyTabs oldWidget) {
    super.didUpdateWidget(oldWidget);
    _activated.add(widget.index);
  }

  @override
  Widget build(BuildContext context) {
    return IndexedStack(
      index: widget.index,
      children: [
        for (var i = 0; i < widget.children.length; i++)
          _activated.contains(i)
              ? widget.children[i](context)
              : const SizedBox.shrink(),
      ],
    );
  }
}
