import 'package:flutter/material.dart';
import 'package:xta/generated/l10n.dart';

/// Posts / Replies / Media — the same three tabs official clients show.
enum PluginProfileFeedTab { posts, replies, media }

class PluginProfileTabBar extends StatelessWidget {
  const PluginProfileTabBar({
    super.key,
    required this.selected,
    required this.onSelected,
  });

  final PluginProfileFeedTab selected;
  final ValueChanged<PluginProfileFeedTab> onSelected;

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 0, 16, 8),
      child: SegmentedButton<PluginProfileFeedTab>(
        showSelectedIcon: false,
        segments: [
          ButtonSegment(
            value: PluginProfileFeedTab.posts,
            label: Text(l10n.tweets),
          ),
          ButtonSegment(
            value: PluginProfileFeedTab.replies,
            label: Text(l10n.plugin_profile_replies),
          ),
          ButtonSegment(
            value: PluginProfileFeedTab.media,
            label: Text(l10n.media),
          ),
        ],
        selected: {selected},
        onSelectionChanged: (next) {
          if (next.isNotEmpty) {
            onSelected(next.first);
          }
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
