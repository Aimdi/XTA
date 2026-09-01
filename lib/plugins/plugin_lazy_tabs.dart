import 'package:flutter/material.dart';

/// Builds only the selected tab.
///
/// [IndexedStack] kept every visited pane in the tree, so Substack Home and
/// Inbox both rebuilt on the same feed store, and Bluesky / Threads Liked
/// stayed mounted (and decoding images) while the reader was on Home. Scroll
/// offset lives on the controllers the parent already holds.
class PluginLazyTabs extends StatelessWidget {
  final int index;
  final List<WidgetBuilder> children;

  const PluginLazyTabs({
    super.key,
    required this.index,
    required this.children,
  });

  @override
  Widget build(BuildContext context) {
    return KeyedSubtree(
      key: ValueKey<int>(index),
      child: children[index](context),
    );
  }
}
