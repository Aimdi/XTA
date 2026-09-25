import 'package:flutter/material.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/home/alt_microblogging.dart';
import 'package:xta/tweet/tweet_chrome.dart';

class HomeTimelineOption {
  final String id;
  final String label;
  final Widget mark;
  final bool plugin;
  final bool unread;
  final String? subtitle;

  const HomeTimelineOption({
    required this.id,
    required this.label,
    required this.mark,
    required this.plugin,
    required this.unread,
    this.subtitle,
  });
}

class HomeTimelineSelection {
  final String? id;
  final String? groupId;

  const HomeTimelineSelection.source(this.id) : groupId = null;
  const HomeTimelineSelection.add() : id = null, groupId = null;
  const HomeTimelineSelection.group(this.groupId) : id = null;
}

/// Sources have room for full names without taking a second navigation row.
class HomeTimelinePicker extends StatelessWidget {
  final List<HomeTimelineOption> options;
  final String selected;
  final List<HomeTimelineOption> groups;
  final bool showAdd;
  final bool groupMicroblogs;
  final String? rememberedMicroblog;

  const HomeTimelinePicker({
    super.key,
    required this.options,
    required this.selected,
    this.groups = const [],
    this.showAdd = true,
    this.groupMicroblogs = false,
    this.rememberedMicroblog,
  });

  List<HomeTimelineOption> _displayOptions(BuildContext context) {
    final byId = {for (final option in options) option.id: option};
    final members = options.where((option) => isAltMicrobloggingSource(option.id)).toList();
    return [
      for (final id in groupedMicrobloggingIds(byId.keys, grouped: groupMicroblogs))
        if (id == altMicrobloggingSectionId)
          HomeTimelineOption(
            id: id,
            label: L10n.of(context).alt_microblogging,
            subtitle: members.map((option) => option.label).join(' · '),
            mark: const Icon(Icons.forum_outlined, size: 22),
            plugin: true,
            unread: members.any((option) => option.unread),
          )
        else
          byId[id]!,
    ];
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final displayed = _displayOptions(context);
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
                  Expanded(
                    child: Text(
                      l10n.home,
                      style: Theme.of(context).textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.w700),
                    ),
                  ),
                  IconButton(
                    tooltip: l10n.close,
                    onPressed: () => Navigator.pop(context),
                    icon: const Icon(Icons.close),
                  ),
                ],
              ),
            ),
            Flexible(
              child: ListView.builder(
                shrinkWrap: true,
                padding: const EdgeInsets.symmetric(horizontal: 12),
                itemCount: displayed.length + (groups.isEmpty ? 0 : groups.length + 1),
                itemBuilder: (context, index) {
                  if (index < displayed.length) return _row(context, displayed[index]);
                  if (index == displayed.length)
                    return Padding(
                      padding: const EdgeInsetsDirectional.fromSTEB(12, 20, 12, 8),
                      child: Text(l10n.groups, style: Theme.of(context).textTheme.titleSmall),
                    );
                  return _row(context, groups[index - displayed.length - 1], group: true);
                },
              ),
            ),
            if (showAdd)
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

  Widget _row(BuildContext context, HomeTimelineOption option, {bool group = false}) {
    final microblogs = !group && option.id == altMicrobloggingSectionId;
    final isSelected = !group && (option.id == selected || (microblogs && isAltMicrobloggingSource(selected)));
    final accent = tweetReadableAccentColor(context);
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 2),
      child: Semantics(
        selected: isSelected,
        child: Material(
          color: isSelected ? tweetAccentColor(context).withValues(alpha: 0.12) : Colors.transparent,
          borderRadius: BorderRadius.circular(16),
          child: ListTile(
            key: ValueKey('home-${group ? 'group' : 'source'}-${option.id}'),
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
            subtitle: option.subtitle == null ? null : Text(option.subtitle!),
            trailing: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                if (option.unread)
                  Semantics(
                    label: L10n.of(context).group_has_unread,
                    child: Container(
                      width: 8,
                      height: 8,
                      decoration: BoxDecoration(color: accent, shape: BoxShape.circle),
                    ),
                  ),
                if (isSelected) ...[const SizedBox(width: 12), Icon(Icons.check, color: accent)],
              ],
            ),
            onTap: () => Navigator.pop(
              context,
              group
                  ? HomeTimelineSelection.group(option.id)
                  : HomeTimelineSelection.source(
                      microblogs
                          ? altMicrobloggingDestination(
                              options.map((option) => option.id),
                              selected: selected,
                              remembered: rememberedMicroblog,
                            )
                          : option.id,
                    ),
            ),
          ),
        ),
      ),
    );
  }
}
