import 'package:flutter/material.dart';
import 'package:xta/plugins/plugin_home_chrome.dart';
import 'package:xta/ui/reader_swipe_navigation.dart';

/// Builds only the selected tab.
///
/// [IndexedStack] kept every visited pane in the tree, so Substack Home and
/// Inbox both rebuilt on the same feed store, and Bluesky / Threads Liked
/// stayed mounted (and decoding images) while the reader was on Home. Scroll
/// offset lives on the controllers the parent already holds.
class PluginLazyTabs extends StatelessWidget {
  final int index;
  final List<WidgetBuilder> children;
  final ValueChanged<int>? onSelected;

  const PluginLazyTabs({super.key, required this.index, required this.children, this.onSelected});

  @override
  Widget build(BuildContext context) {
    final content = KeyedSubtree(key: PageStorageKey<int>(index), child: children[index](context));
    // Home owns body swipes when a reader is embedded; its header still changes
    // sections. A full client owns its own section navigation.
    if (onSelected == null || PluginEmbedded.maybeOf(context)) return content;
    return ReaderSwipeNavigation(
      index: index,
      count: children.length,
      identity: children.length,
      onChanged: (next) {
        onSelected!(next);
        return true;
      },
      child: content,
    );
  }
}
