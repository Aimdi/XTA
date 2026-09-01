import 'package:flutter/material.dart';
import 'package:xta/generated/l10n.dart';

/// Posts / Replies / Media, plus Saved on Bluesky (local likes by that author).
enum PluginProfileFeedTab { posts, replies, media, saved }

class PluginProfileTabBar extends StatelessWidget {
  const PluginProfileTabBar({
    super.key,
    required this.selected,
    required this.onSelected,
    this.tabs = const [
      PluginProfileFeedTab.posts,
      PluginProfileFeedTab.replies,
      PluginProfileFeedTab.media,
    ],
  });

  final PluginProfileFeedTab selected;
  final ValueChanged<PluginProfileFeedTab> onSelected;

  /// Threads keeps Posts / Replies / Media. Bluesky also shows [saved].
  final List<PluginProfileFeedTab> tabs;

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    String label(PluginProfileFeedTab tab) => switch (tab) {
      PluginProfileFeedTab.posts => l10n.tweets,
      PluginProfileFeedTab.replies => l10n.plugin_profile_replies,
      PluginProfileFeedTab.media => l10n.media,
      PluginProfileFeedTab.saved => l10n.saved,
    };

    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 0, 16, 8),
      child: LayoutBuilder(
        builder: (context, constraints) {
          return SingleChildScrollView(
            scrollDirection: Axis.horizontal,
            child: ConstrainedBox(
              constraints: BoxConstraints(minWidth: constraints.maxWidth),
              child: SegmentedButton<PluginProfileFeedTab>(
                showSelectedIcon: false,
                segments: [
                  for (final tab in tabs)
                    ButtonSegment(value: tab, label: Text(label(tab))),
                ],
                selected: {selected},
                onSelectionChanged: (next) {
                  if (next.isNotEmpty) {
                    onSelected(next.first);
                  }
                },
              ),
            ),
          );
        },
      ),
    );
  }
}

/// “Replying to @handle” chrome official clients put above a reply.
class PluginReplyingTo extends StatelessWidget {
  const PluginReplyingTo({super.key, required this.name});

  final String name;

  @override
  Widget build(BuildContext context) {
    if (name.isEmpty) {
      return const SizedBox.shrink();
    }
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.only(bottom: 4),
      child: Text(
        L10n.of(context).plugin_replying_to(name),
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
        style: theme.textTheme.bodySmall!.copyWith(
          color: theme.colorScheme.onSurfaceVariant,
        ),
      ),
    );
  }
}
