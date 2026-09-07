import 'package:flutter/material.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/tweet/tweet_chrome.dart';

class HomeTimelineOption {
  final String id;
  final String label;
  final Widget mark;
  final bool plugin;
  final bool unread;

  const HomeTimelineOption({
    required this.id,
    required this.label,
    required this.mark,
    required this.plugin,
    required this.unread,
  });
}

class HomeTimelineSelection {
  final String? id;

  const HomeTimelineSelection.source(this.id);
  const HomeTimelineSelection.add() : id = null;
}

/// Sources have room for full names without taking a second navigation row.
class HomeTimelinePicker extends StatelessWidget {
  final List<HomeTimelineOption> options;
  final String selected;

  const HomeTimelinePicker({super.key, required this.options, required this.selected});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return SafeArea(
      top: false,
      child: ConstrainedBox(
        key: const ValueKey('home-source-sheet'),
        constraints: BoxConstraints(maxHeight: MediaQuery.sizeOf(context).height * 0.75),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Padding(
              padding: const EdgeInsetsDirectional.fromSTEB(20, 0, 8, 8),
              child: Row(
                children: [
                  Expanded(child: Text(l10n.home, style: Theme.of(context).textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.w700))),
                  IconButton(tooltip: l10n.close, onPressed: () => Navigator.pop(context), icon: const Icon(Icons.close)),
                ],
              ),
            ),
            Flexible(
              child: ListView(
                shrinkWrap: true,
                padding: const EdgeInsets.symmetric(horizontal: 12),
                children: [
                  for (final option in options.where((option) => !option.plugin)) _row(context, option),
                  if (options.any((option) => option.plugin)) ...[
                    Padding(
                      padding: const EdgeInsetsDirectional.fromSTEB(12, 20, 12, 8),
                      child: Text(l10n.feed_strip_add_title, style: Theme.of(context).textTheme.titleSmall?.copyWith(color: tweetSecondaryColor(context))),
                    ),
                    for (final option in options.where((option) => option.plugin)) _row(context, option),
                  ],
                ],
              ),
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 16, 20, 16),
              child: OutlinedButton.icon(
                key: const ValueKey('home-add-timeline'),
                style: OutlinedButton.styleFrom(minimumSize: const Size(48, 48)),
                onPressed: () => Navigator.pop(context, const HomeTimelineSelection.add()),
                icon: const Icon(Icons.add),
                label: Text(l10n.feed_strip_add),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _row(BuildContext context, HomeTimelineOption option) {
    final isSelected = option.id == selected;
    final accent = tweetReadableAccentColor(context);
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 2),
      child: Semantics(
        selected: isSelected,
        child: Material(
          color: isSelected ? tweetAccentColor(context).withValues(alpha: 0.12) : Colors.transparent,
          borderRadius: BorderRadius.circular(16),
          child: ListTile(
            key: ValueKey('home-source-${option.id}'),
            minTileHeight: 64,
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
            leading: Container(
              width: 40,
              height: 40,
              alignment: Alignment.center,
              decoration: BoxDecoration(
                color: tweetSecondaryColor(context).withValues(alpha: 0.07),
                borderRadius: BorderRadius.circular(12),
              ),
              child: ExcludeSemantics(child: option.mark),
            ),
            title: Text(option.label, style: TextStyle(fontWeight: isSelected ? FontWeight.w700 : FontWeight.w500)),
            trailing: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                if (option.unread)
                  Semantics(
                    label: L10n.of(context).group_has_unread,
                    child: Container(width: 8, height: 8, decoration: BoxDecoration(color: accent, shape: BoxShape.circle)),
                  ),
                if (isSelected) ...[const SizedBox(width: 12), Icon(Icons.check, color: accent)],
              ],
            ),
            onTap: () => Navigator.pop(context, HomeTimelineSelection.source(option.id)),
          ),
        ),
      ),
    );
  }
}
