import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
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
class HomeTimelinePicker extends StatefulWidget {
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

  @override
  State<HomeTimelinePicker> createState() => _HomeTimelinePickerState();
}

class _PickerQuery extends Store<String> {
  _PickerQuery() : super('');
  void search(String value) => update(value.trim().toLowerCase());
}

class _HomeTimelinePickerState extends State<HomeTimelinePicker> {
  final _query = _PickerQuery();
  final _text = TextEditingController();

  @override
  void dispose() {
    _query.destroy();
    _text.dispose();
    super.dispose();
  }

  bool _matches(HomeTimelineOption option, String query) =>
      query.isEmpty ||
      query
          .split(RegExp(r'\s+'))
          .every(
            (term) => '${option.label} ${option.subtitle ?? ''}'
                .toLowerCase()
                .contains(term),
          );

  List<HomeTimelineOption> _displayOptions(BuildContext context) {
    final byId = {for (final option in widget.options) option.id: option};
    final members = widget.options
        .where((option) => isAltMicrobloggingSource(option.id))
        .toList();
    return [
      for (final id in groupedMicrobloggingIds(
        byId.keys,
        grouped: widget.groupMicroblogs,
      ))
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
  Widget build(BuildContext context) => ScopedBuilder<_PickerQuery, String>(
    store: _query,
    onState: (context, query) => _buildPicker(context, query),
  );

  Widget _buildPicker(BuildContext context, String query) {
    final l10n = L10n.of(context);
    final displayed = query.isEmpty
        ? _displayOptions(context)
        : widget.options.where((option) => _matches(option, query)).toList();
    final groups = widget.groups
        .where((option) => _matches(option, query))
        .toList();
    final hasSearch =
        widget.options.length + widget.groups.length > 8 || query.isNotEmpty;
    final keyboard = MediaQuery.viewInsetsOf(context).bottom;
    return Padding(
      padding: EdgeInsets.only(bottom: keyboard),
      child: SafeArea(
        top: false,
        child: ConstrainedBox(
          key: const ValueKey('home-source-sheet'),
          constraints: BoxConstraints(
            maxHeight:
                ((MediaQuery.sizeOf(context).height -
                            keyboard -
                            MediaQuery.paddingOf(context).top) *
                        0.9)
                    .clamp(0.0, MediaQuery.sizeOf(context).height * 0.75),
          ),
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
                        style: Theme.of(context).textTheme.headlineSmall
                            ?.copyWith(fontWeight: FontWeight.w700),
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
              if (hasSearch)
                Padding(
                  padding: const EdgeInsets.fromLTRB(16, 0, 16, 8),
                  child: TextField(
                    key: const ValueKey('home-source-search'),
                    controller: _text,
                    onChanged: _query.search,
                    decoration: InputDecoration(
                      labelText: l10n.search,
                      prefixIcon: const Icon(Icons.search),
                      suffixIcon: query.isEmpty
                          ? null
                          : IconButton(
                              tooltip: l10n.plugin_mastodon_clear_search,
                              onPressed: () {
                                _text.clear();
                                _query.search('');
                              },
                              icon: const Icon(Icons.clear),
                            ),
                    ),
                  ),
                ),
              if (displayed.isEmpty && groups.isEmpty)
                Padding(
                  padding: const EdgeInsets.all(16),
                  child: Text(l10n.no_results),
                ),
              Flexible(
                child: ListView.builder(
                  shrinkWrap: true,
                  padding: const EdgeInsets.symmetric(horizontal: 12),
                  itemCount:
                      displayed.length +
                      (groups.isEmpty ? 0 : groups.length + 1),
                  itemBuilder: (context, index) {
                    if (index < displayed.length)
                      return _row(context, displayed[index]);
                    if (index == displayed.length)
                      return Padding(
                        padding: const EdgeInsetsDirectional.fromSTEB(
                          12,
                          20,
                          12,
                          8,
                        ),
                        child: Text(
                          l10n.groups,
                          style: Theme.of(context).textTheme.titleSmall,
                        ),
                      );
                    return _row(
                      context,
                      groups[index - displayed.length - 1],
                      group: true,
                    );
                  },
                ),
              ),
              if (widget.showAdd)
                Padding(
                  padding: const EdgeInsets.fromLTRB(16, 8, 16, 8),
                  child: OutlinedButton.icon(
                    key: const ValueKey('home-add-timeline'),
                    style: OutlinedButton.styleFrom(
                      minimumSize: const Size(48, 48),
                    ),
                    onPressed: () => Navigator.pop(
                      context,
                      const HomeTimelineSelection.add(),
                    ),
                    icon: const Icon(Icons.add),
                    label: Text(l10n.feed_strip_add),
                  ),
                ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _row(
    BuildContext context,
    HomeTimelineOption option, {
    bool group = false,
  }) {
    final microblogs = !group && option.id == altMicrobloggingSectionId;
    final isSelected =
        !group &&
        (option.id == widget.selected ||
            (microblogs && isAltMicrobloggingSource(widget.selected)));
    final accent = tweetReadableAccentColor(context);
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 2),
      child: Semantics(
        selected: isSelected,
        child: Material(
          color: isSelected
              ? tweetAccentColor(context).withValues(alpha: 0.12)
              : Colors.transparent,
          borderRadius: BorderRadius.circular(16),
          child: ListTile(
            key: ValueKey('home-${group ? 'group' : 'source'}-${option.id}'),
            minTileHeight: 52,
            minLeadingWidth: 24,
            horizontalTitleGap: 12,
            contentPadding: const EdgeInsets.symmetric(horizontal: 12),
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(16),
            ),
            leading: SizedBox(
              width: 24,
              height: 24,
              child: ExcludeSemantics(child: option.mark),
            ),
            title: Text(
              option.label,
              style: TextStyle(
                fontWeight: isSelected ? FontWeight.w700 : FontWeight.w500,
              ),
            ),
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
                      decoration: BoxDecoration(
                        color: accent,
                        shape: BoxShape.circle,
                      ),
                    ),
                  ),
                if (isSelected) ...[
                  const SizedBox(width: 12),
                  Icon(Icons.check, color: accent),
                ],
              ],
            ),
            onTap: () => Navigator.pop(
              context,
              group
                  ? HomeTimelineSelection.group(option.id)
                  : HomeTimelineSelection.source(
                      microblogs
                          ? altMicrobloggingDestination(
                              widget.options.map((option) => option.id),
                              selected: widget.selected,
                              remembered: widget.rememberedMicroblog,
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
