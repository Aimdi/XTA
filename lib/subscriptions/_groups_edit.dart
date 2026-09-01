import 'dart:io';

import 'package:flutter/material.dart';
import 'package:path_provider/path_provider.dart';
import 'package:share_plus/share_plus.dart';
import 'package:pref/pref.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/group/deck_groups.dart';
import 'package:xta/group/group_model.dart';
import 'package:xta/group/group_tree.dart';
import 'package:xta/group/subscription_pack.dart';
import 'package:xta/plugins/plugin_marks.dart';
import 'package:xta/plugins/plugin_registry.dart';
import 'package:xta/plugins/subscription_source.dart';
import 'package:xta/subscriptions/_group_add_member.dart';
import 'package:xta/subscriptions/group_identity.dart';
import 'package:xta/subscriptions/subscription_look.dart';
import 'package:xta/subscriptions/widgets/group_color_picker.dart';
import 'package:xta/subscriptions/group_mark_style.dart';
import 'package:xta/subscriptions/users_model.dart';
import 'package:provider/provider.dart';

Future openSubscriptionGroupDialog(
  BuildContext context,
  String? id,
  String name,
  String icon,
) {
  return showModalBottomSheet(
    context: context,
    isScrollControlled: true,
    useSafeArea: true,
    showDragHandle: true,
    builder: (context) {
      return Padding(
        padding: EdgeInsets.only(
          bottom: MediaQuery.of(context).viewInsets.bottom,
        ),
        child: FractionallySizedBox(
          heightFactor: 0.85,
          child: SubscriptionGroupEditDialog(id: id, name: name, icon: icon),
        ),
      );
    },
  );
}

class SubscriptionGroupEditDialog extends StatefulWidget {
  final String? id;
  final String name;
  final String icon;

  const SubscriptionGroupEditDialog({
    super.key,
    required this.id,
    required this.name,
    required this.icon,
  });

  @override
  State<SubscriptionGroupEditDialog> createState() =>
      _SubscriptionGroupEditDialogState();
}

/// Small, low-contrast styling for the secondary actions in the edit sheet.
ButtonStyle _discreetActionStyle(BuildContext context) => TextButton.styleFrom(
  foregroundColor: Theme.of(context).textTheme.bodySmall?.color,
  textStyle: Theme.of(context).textTheme.bodySmall,
  visualDensity: VisualDensity.compact,
);

class _SubscriptionGroupEditDialogState
    extends State<SubscriptionGroupEditDialog> {
  final GlobalKey<FormState> _formKey = GlobalKey();

  SubscriptionGroupEdit? _group;

  late String? id;
  late String? name;
  late String icon;
  Color? color;
  String? emoji;
  int markStyle = GroupMarkStyle.auto;
  Set<String> members = <String>{};
  List<Subscription> orderedSubscriptions = [];
  final _memberSearch = TextEditingController();

  /// `null` = all networks; `x` = X only; otherwise a plugin id.
  String? _sourceFilter;

  @override
  void dispose() {
    _memberSearch.dispose();
    super.dispose();
  }

  List<Subscription> get _visibleSubscriptions {
    final query = _memberSearch.text.trim().toLowerCase();
    return orderedSubscriptions
        .where((subscription) {
          if (query.isNotEmpty) {
            final hay = '${subscription.name} ${subscription.screenName}'
                .toLowerCase();
            if (!hay.contains(query)) {
              return false;
            }
          }
          final source = sourceOf(subscription);
          if (_sourceFilter == null) {
            return true;
          }
          if (_sourceFilter == 'x') {
            return source == null;
          }
          final selected = pluginById(_sourceFilter!);
          return selected is SubscriptionSource && identical(selected, source);
        })
        .toList(growable: false);
  }

  @override
  void initState() {
    super.initState();

    setState(() {
      icon = widget.icon;
    });

    final subscriptions = context.read<SubscriptionsModel>().state;

    context
        .read<GroupsModel>()
        .loadGroupEdit(widget.id)
        .then(
          (group) => setState(() {
            _group = group;

            id = group.id;
            name = group.name;
            icon = group.icon;
            color = group.color;
            emoji = group.emoji;
            markStyle = group.markStyle;
            members = group.members;
            orderedSubscriptions = [
              ...subscriptions.where((s) => group.members.contains(s.id)),
              ...subscriptions.where((s) => !group.members.contains(s.id)),
            ];
          }),
        );
  }

  /// Follows something new and ticks it, so a group can be filled from inside
  /// the group rather than by going elsewhere to subscribe first.
  Future<void> _addMembers() async {
    final subscriptionsModel = context.read<SubscriptionsModel>();
    final added = await openGroupAddMemberSheet(context);
    if (!mounted || added.isEmpty) {
      return;
    }

    final subscriptions = subscriptionsModel.state;
    setState(() {
      members.addAll(added);
      orderedSubscriptions = [
        ...subscriptions.where((s) => members.contains(s.id)),
        ...subscriptions.where((s) => !members.contains(s.id)),
      ];
    });
  }

  Future<void> _exportPack() async {
    final subscriptions = context.read<SubscriptionsModel>().state.where(
      (s) => members.contains(s.id),
    );
    final pack = packFromSubscriptions(name ?? '', subscriptions);
    final dir = await getTemporaryDirectory();
    final file = File(
      '${dir.path}/xta-pack-${DateTime.now().millisecondsSinceEpoch}.json',
    );
    await file.writeAsString(encodeSubscriptionPack(pack));
    await Share.shareXFiles([XFile(file.path, mimeType: 'application/json')]);
    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(L10n.of(context).subscription_pack_exported)),
      );
    }
  }

  Future<void> _openMergeSheet(BuildContext context) async {
    final groupsModel = context.read<GroupsModel>();
    final others = groupsModel.state
        .where((g) => g.id != widget.id)
        .toList(growable: false);

    // An empty sheet is indistinguishable from a broken button.
    if (others.isEmpty) {
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(L10n.of(context).no_other_groups)));
      return;
    }

    final target = await showModalBottomSheet<SubscriptionGroup>(
      context: context,
      builder: (sheetContext) => SafeArea(
        child: ListView(
          shrinkWrap: true,
          children: [
            for (final g in others)
              ListTile(
                leading: GroupMark.forGroup(g, size: 32),
                title: Text(
                  g.name,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
                onTap: () => Navigator.pop(sheetContext, g),
              ),
          ],
        ),
      ),
    );

    if (target == null || !context.mounted) return;
    await groupsModel.mergeGroups(widget.id!, target.id);
    if (context.mounted) Navigator.pop(context);
  }

  /// Chooses the group this one sits inside, the way a browser nests tab
  /// groups. The parent's feed becomes the union of both.
  ///
  /// Groups that would close a loop are left out of the list rather than
  /// offered and then refused.
  Future<void> _openNestSheet(BuildContext context) async {
    final groupsModel = context.read<GroupsModel>();
    final parents = {for (final g in groupsModel.state) g.id: g.parentId};
    final candidates = groupsModel.state
        .where((g) => !wouldNestInsideItself(widget.id!, g.id, parents))
        .toList(growable: false);
    final current = parents[widget.id!];

    if (candidates.isEmpty && current == null) {
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(L10n.of(context).no_other_groups)));
      return;
    }

    final choice = await showModalBottomSheet<String>(
      context: context,
      builder: (sheetContext) => SafeArea(
        child: ListView(
          shrinkWrap: true,
          children: [
            ListTile(
              leading: const Icon(Icons.north),
              title: Text(L10n.of(sheetContext).nest_inside_nothing),
              selected: current == null,
              onTap: () => Navigator.pop(sheetContext, ''),
            ),
            for (final g in candidates)
              ListTile(
                leading: GroupMark.forGroup(g, size: 32),
                title: Text(
                  g.name,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
                selected: current == g.id,
                onTap: () => Navigator.pop(sheetContext, g.id),
              ),
          ],
        ),
      ),
    );

    if (choice == null || !context.mounted) return;
    final applied = await groupsModel.setGroupParent(
      widget.id!,
      choice.isEmpty ? null : choice,
    );
    if (!context.mounted) return;
    if (!applied) {
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(L10n.of(context).no_other_groups)));
      return;
    }
    Navigator.pop(context);
  }

  void openDeleteSubscriptionGroupDialog(String id, String name) {
    showDialog(
      context: context,
      builder: (context) {
        return AlertDialog(
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context),
              child: Text(L10n.of(context).no),
            ),
            TextButton(
              onPressed: () async {
                final navigator = Navigator.of(context);
                await context.read<GroupsModel>().deleteGroup(id);

                navigator.pop();
                navigator.pop();
              },
              child: Text(L10n.of(context).yes),
            ),
          ],
          title: Text(L10n.of(context).are_you_sure),
          content: Text(
            L10n.of(
              context,
            ).are_you_sure_you_want_to_delete_the_subscription_group_name_of_group(
              name,
            ),
          ),
        );
      },
    );
  }

  Future<void> _pickEmoji() async {
    final controller = TextEditingController(text: emoji ?? '');
    final result = await showDialog<String>(
      context: context,
      builder: (context) {
        final l10n = L10n.of(context);
        return AlertDialog(
          title: Text(l10n.choose_emoji),
          content: TextField(
            controller: controller,
            autofocus: true,
            textAlign: TextAlign.center,
            style: const TextStyle(fontSize: 40),
            decoration: InputDecoration(hintText: l10n.choose_emoji),
            onSubmitted: (value) => Navigator.pop(context, value.trim()),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context),
              child: Text(l10n.cancel),
            ),
            TextButton(
              onPressed: () => Navigator.pop(context, controller.text.trim()),
              child: Text(l10n.ok),
            ),
          ],
        );
      },
    );
    if (!mounted || result == null) return;
    setState(() {
      emoji = result.isEmpty ? null : result.characters.first;
      markStyle = GroupMarkStyle.emoji;
    });
  }

  Future<void> _pickIcon() async {
    final selected = await showDialog<({String key, IconData data})>(
      context: context,
      builder: (context) {
        final l10n = L10n.of(context);
        return AlertDialog(
          title: Text(l10n.choose_icon),
          // A fixed height rather than shrink-wrapping: the catalogue is long
          // enough now that wrapping to its content would overflow the dialog
          // instead of scrolling inside it.
          content: SizedBox(
            width: 320,
            height: 320,
            child: GridView.builder(
              gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                crossAxisCount: 5,
                mainAxisSpacing: 8,
                crossAxisSpacing: 8,
              ),
              itemCount: curatedGroupIcons.length,
              itemBuilder: (context, index) {
                final entry = curatedGroupIcons[index];
                return IconButton(
                  onPressed: () => Navigator.pop(context, entry),
                  icon: Icon(entry.data),
                );
              },
            ),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context),
              child: Text(l10n.cancel),
            ),
          ],
        );
      },
    );
    if (!mounted || selected == null) return;
    setState(() {
      icon = serializeCuratedGroupIcon(selected.key, selected.data);
      markStyle = GroupMarkStyle.symbol;
    });
  }

  Future<void> _onMarkStyleSelected(Set<int> selection) async {
    final next = selection.first;
    if (next == GroupMarkStyle.emoji) {
      await _pickEmoji();
      return;
    }
    if (next == GroupMarkStyle.symbol) {
      await _pickIcon();
      return;
    }
    setState(() {
      markStyle = GroupMarkStyle.auto;
      emoji = null;
    });
  }

  Widget _markPreview(BuildContext context) {
    final seed = color ?? groupFallbackColor(name ?? '');
    return GroupMark(
      name: name ?? '',
      seed: seed,
      emoji: emoji,
      icon: icon,
      markStyle: markStyle,
      size: 40,
    );
  }

  @override
  Widget build(BuildContext context) {
    var subscriptionsModel = context.read<SubscriptionsModel>();

    var group = _group;
    if (group == null) {
      return const Center(child: CircularProgressIndicator());
    }

    final l10n = L10n.of(context);
    final isPinned =
        widget.id != null &&
        context.read<GroupsModel>().state.any(
          (g) => g.id == widget.id && g.pinned,
        );
    final isNsfw =
        widget.id != null &&
        context.read<GroupsModel>().state.any(
          (g) => g.id == widget.id && g.nsfw,
        );
    final prefs = PrefService.of(context, listen: false);
    final deckPinned = widget.id != null && isDeckPinned(prefs, widget.id!);

    // Group-level actions sit with the group's own fields rather than in the
    // bottom bar: with pin and merge added to this fork, five buttons plus
    // Cancel/OK ran off the edge in languages with long words (upstream
    // 7012ff8f hit the same thing with fewer buttons).
    final groupActions = <Widget>[
      if (widget.id != null)
        TextButton.icon(
          style: _discreetActionStyle(context),
          icon: Icon(
            isPinned ? Icons.push_pin : Icons.push_pin_outlined,
            size: 18,
          ),
          label: Text(isPinned ? l10n.unpin : l10n.pin),
          onPressed: () async {
            final groupsModel = context.read<GroupsModel>();
            await groupsModel.toggleGroupPinned(widget.id!, !isPinned);
            if (mounted) setState(() {});
          },
        ),
      if (widget.id != null)
        TextButton.icon(
          style: _discreetActionStyle(context),
          icon: Icon(
            isNsfw ? Icons.visibility_off : Icons.visibility_off_outlined,
            size: 18,
          ),
          label: Text(isNsfw ? l10n.unmark_group_nsfw : l10n.mark_group_nsfw),
          onPressed: () async {
            final groupsModel = context.read<GroupsModel>();
            await groupsModel.toggleGroupNsfw(widget.id!, !isNsfw);
            if (mounted) setState(() {});
          },
        ),
      if (widget.id != null)
        TextButton.icon(
          style: _discreetActionStyle(context),
          icon: Icon(
            deckPinned ? Icons.view_column : Icons.view_column_outlined,
            size: 18,
          ),
          label: Text(deckPinned ? l10n.deck_unpin_group : l10n.deck_pin_group),
          onPressed: () async {
            await toggleDeckPin(prefs, widget.id!);
            if (mounted) setState(() {});
          },
        ),
      if (widget.id != null)
        TextButton.icon(
          style: _discreetActionStyle(context),
          icon: const Icon(Icons.upload_outlined, size: 18),
          label: Text(l10n.subscription_pack_export),
          onPressed: _exportPack,
        ),
      if (widget.id != null)
        TextButton.icon(
          style: _discreetActionStyle(context),
          icon: const Icon(Icons.merge, size: 18),
          label: Text(l10n.merge_into),
          onPressed: () => _openMergeSheet(context),
        ),
      if (widget.id != null)
        TextButton.icon(
          style: _discreetActionStyle(context),
          icon: const Icon(Icons.folder_outlined, size: 18),
          label: Text(l10n.nest_inside_group),
          onPressed: () => _openNestSheet(context),
        ),
    ];

    List<Widget> buttonsLst1 = [
      TextButton(
        onPressed: id == null
            ? null
            : () => openDeleteSubscriptionGroupDialog(id!, name!),
        child: Text(l10n.delete),
      ),
    ];
    List<Widget> buttonsLst2 = [
      TextButton(
        onPressed: () => Navigator.pop(context),
        child: Text(l10n.cancel),
      ),
      Builder(
        builder: (context) {
          onPressed() async {
            if (_formKey.currentState!.validate()) {
              final navigator = Navigator.of(context);
              await context.read<GroupsModel>().saveGroup(
                id,
                name!,
                icon,
                color,
                members,
                emoji: emoji,
                markStyle: markStyle,
              );

              navigator.pop();
            }
          }

          return TextButton(onPressed: onPressed, child: Text(l10n.ok));
        },
      ),
    ];
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 0, 16, 8),
      child: Form(
        key: _formKey,
        child: SizedBox(
          width: double.maxFinite,
          child: Column(
            mainAxisSize: MainAxisSize.max,
            children: [
              Row(
                children: [
                  _markPreview(context),
                  const SizedBox(width: 12),
                  Expanded(
                    child: TextFormField(
                      initialValue: group.name,
                      decoration: InputDecoration(
                        border: const UnderlineInputBorder(),
                        hintText: l10n.name,
                      ),
                      onChanged: (value) => setState(() {
                        name = value;
                      }),
                      validator: (value) {
                        if (value == null || value.isEmpty) {
                          return l10n.please_enter_a_name;
                        }
                        return null;
                      },
                    ),
                  ),
                  IconButton(
                    icon: Icon(
                      Icons.palette,
                      color: color ?? groupFallbackColor(name ?? ''),
                    ),
                    tooltip: l10n.pick_a_color,
                    onPressed: () async {
                      final chosen = await openGroupColorPicker(
                        context,
                        current: color,
                        name: name ?? '',
                      );
                      // A dismissed dialog answers nothing; "no colour of its
                      // own" is an answer and clears the stored one.
                      if (chosen != null && mounted) {
                        setState(() => color = chosen.color);
                      }
                    },
                  ),
                ],
              ),
              const SizedBox(height: 12),
              Align(
                alignment: Alignment.centerLeft,
                child: Text(
                  l10n.group_mark_style_label,
                  style: Theme.of(context).textTheme.labelLarge,
                ),
              ),
              const SizedBox(height: 8),
              SegmentedButton<int>(
                segments: [
                  ButtonSegment(
                    value: GroupMarkStyle.auto,
                    label: Text(l10n.group_mark_style_auto),
                  ),
                  ButtonSegment(
                    value: GroupMarkStyle.emoji,
                    label: Text(l10n.group_mark_style_emoji),
                  ),
                  ButtonSegment(
                    value: GroupMarkStyle.symbol,
                    label: Text(l10n.group_mark_style_icon),
                  ),
                ],
                selected: {
                  markStyle == GroupMarkStyle.emoji
                      ? GroupMarkStyle.emoji
                      : markStyle == GroupMarkStyle.symbol
                      ? GroupMarkStyle.symbol
                      : GroupMarkStyle.auto,
                },
                onSelectionChanged: _onMarkStyleSelected,
              ),
              // A Wrap, not a Row. Five buttons in one row ran off the edge in
              // German — and a Row does not shrink, it clips: the two that
              // matter most were simply not on screen. Filling the group is why
              // this sheet is open, so it leads and is the only filled button.
              Wrap(
                spacing: 4,
                crossAxisAlignment: WrapCrossAlignment.center,
                children: [
                  FilledButton.tonalIcon(
                    icon: const Icon(Icons.person_add_alt, size: 18),
                    label: Text(
                      l10n.add_to_group,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                    onPressed: _addMembers,
                  ),
                  TextButton.icon(
                    style: _discreetActionStyle(context),
                    icon: const Icon(Icons.checklist, size: 18),
                    label: Text(l10n.toggle_all),
                    onPressed: () {
                      setState(() {
                        if (members.isEmpty) {
                          members = subscriptionsModel.state
                              .map((e) => e.id)
                              .toSet();
                        } else {
                          members.clear();
                        }
                      });
                    },
                  ),
                  ...groupActions,
                ],
              ),
              const SizedBox(height: 8),
              TextField(
                controller: _memberSearch,
                decoration: InputDecoration(
                  isDense: true,
                  prefixIcon: const Icon(Icons.search, size: 20),
                  hintText: l10n.search_subscriptions,
                  border: const OutlineInputBorder(),
                ),
                onChanged: (_) => setState(() {}),
              ),
              const SizedBox(height: 8),
              SingleChildScrollView(
                scrollDirection: Axis.horizontal,
                child: Row(
                  children: [
                    Padding(
                      padding: const EdgeInsets.only(right: 6),
                      child: FilterChip(
                        label: Text(l10n.all),
                        selected: _sourceFilter == null,
                        onSelected: (_) => setState(() => _sourceFilter = null),
                      ),
                    ),
                    Padding(
                      padding: const EdgeInsets.only(right: 6),
                      child: FilterChip(
                        label: Text(l10n.source_x),
                        selected: _sourceFilter == 'x',
                        onSelected: (_) => setState(() => _sourceFilter = 'x'),
                      ),
                    ),
                    for (final plugin in builtInPlugins)
                      if (plugin is SubscriptionSource &&
                          orderedSubscriptions.any(
                            (s) => (plugin as SubscriptionSource).owns(s),
                          ))
                        Padding(
                          padding: const EdgeInsets.only(right: 6),
                          child: FilterChip(
                            avatar: pluginMark(plugin, size: 16),
                            label: Text(plugin.title(context)),
                            selected: _sourceFilter == plugin.id,
                            onSelected: (_) =>
                                setState(() => _sourceFilter = plugin.id),
                          ),
                        ),
                  ],
                ),
              ),
              Expanded(
                child: ListView.builder(
                  shrinkWrap: true,
                  itemCount: _visibleSubscriptions.length,
                  itemBuilder: (context, index) {
                    var subscription = _visibleSubscriptions[index];

                    return CheckboxListTile(
                      dense: true,
                      secondary: subscriptionAvatar(subscription),
                      title: Text(subscription.name),
                      subtitle: Text(subscriptionSubtitle(subscription)),
                      selected: members.contains(subscription.id),
                      value: members.contains(subscription.id),
                      onChanged: (v) => setState(() {
                        if (v == null || v == false) {
                          members.remove(subscription.id);
                        } else {
                          members.add(subscription.id);
                        }
                      }),
                    );
                  },
                ),
              ),
              OverflowBar(
                alignment: MainAxisAlignment.end,
                overflowAlignment: OverflowBarAlignment.end,
                children: [...buttonsLst1, ...buttonsLst2],
              ),
            ],
          ),
        ),
      ),
    );
  }
}
