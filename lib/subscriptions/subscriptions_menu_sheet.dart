import 'package:flutter/material.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/group/group_model.dart';
import 'package:xta/subscriptions/users_model.dart';
import 'package:xta/ui/contrast.dart';
import 'package:xta/ui/x_controls.dart';

/// Replaces the old 12-row overflow menu: view, sort, and the rarer
/// actions, grouped so the current choice is visible.
Future<void> showSubscriptionsMenuSheet({
  required BuildContext context,
  required bool onGroups,
  required VoidCallback onImportPack,
  required VoidCallback onSortUngrouped,
  required VoidCallback onImportList,
  required VoidCallback onFindBroken,
  required VoidCallback onAntennas,
  required VoidCallback onDeck,
  required VoidCallback onSettings,
}) {
  return showModalBottomSheet<void>(
    context: context,
    showDragHandle: true,
    isScrollControlled: true,
    useSafeArea: true,
    builder: (sheetContext) => SubscriptionsMenuSheet(
      onGroups: onGroups,
      onImportPack: () {
        Navigator.pop(sheetContext);
        onImportPack();
      },
      onSortUngrouped: () {
        Navigator.pop(sheetContext);
        onSortUngrouped();
      },
      onImportList: () {
        Navigator.pop(sheetContext);
        onImportList();
      },
      onFindBroken: () {
        Navigator.pop(sheetContext);
        onFindBroken();
      },
      onAntennas: () {
        Navigator.pop(sheetContext);
        onAntennas();
      },
      onDeck: () {
        Navigator.pop(sheetContext);
        onDeck();
      },
      onSettings: () {
        Navigator.pop(sheetContext);
        onSettings();
      },
    ),
  );
}

class SubscriptionsMenuSheet extends StatefulWidget {
  final bool onGroups;
  final VoidCallback onImportPack;
  final VoidCallback onSortUngrouped;
  final VoidCallback onImportList;
  final VoidCallback onFindBroken;
  final VoidCallback onAntennas;
  final VoidCallback onDeck;
  final VoidCallback onSettings;

  const SubscriptionsMenuSheet({
    super.key,
    required this.onGroups,
    required this.onImportPack,
    required this.onSortUngrouped,
    required this.onImportList,
    required this.onFindBroken,
    required this.onAntennas,
    required this.onDeck,
    required this.onSettings,
  });

  @override
  State<SubscriptionsMenuSheet> createState() => _SubscriptionsMenuSheetState();
}

class _SubscriptionsMenuSheetState extends State<SubscriptionsMenuSheet> {
  bool get _sortReversed {
    final prefs = PrefService.of(context);
    if (widget.onGroups) {
      return prefs.get<bool>(optionSubscriptionGroupsOrderByAscending) != true;
    }
    return prefs.get<bool>(optionSubscriptionOrderByAscending) != true;
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final theme = Theme.of(context);
    final prefs = PrefService.of(context);
    final title = widget.onGroups ? l10n.groups : l10n.subscriptions;

    return SafeArea(
      child: SingleChildScrollView(
        child: Padding(
          padding: const EdgeInsets.only(bottom: 12),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Padding(
                padding: const EdgeInsets.fromLTRB(20, 0, 20, 12),
                child: Text(
                  title,
                  style: theme.textTheme.titleLarge?.copyWith(
                    fontWeight: FontWeight.w700,
                  ),
                ),
              ),
              if (widget.onGroups) ...[
                _ViewModeRow(prefs: prefs, onChanged: () => setState(() {})),
                if (prefs.get<String>(optionSubscriptionGroupsLayout) !=
                    subscriptionGroupsLayoutList)
                  _ColumnsRow(prefs: prefs, onChanged: () => setState(() {})),
                const SizedBox(height: 8),
              ],
              ..._sortRows(context, l10n, prefs),
              _SwitchRow(
                icon: Icons.swap_vert,
                label: l10n.toggle_sort_direction,
                value: _sortReversed,
                onChanged: (_) {
                  if (widget.onGroups) {
                    context
                        .read<GroupsModel>()
                        .toggleOrderSubscriptionGroupsAscending();
                  } else {
                    context
                        .read<SubscriptionsModel>()
                        .toggleOrderSubscriptionsAscending();
                  }
                  setState(() {});
                },
              ),
              const _SheetDivider(),
              if (widget.onGroups) ...[
                _ActionRow(
                  icon: Icons.file_download_outlined,
                  label: l10n.subscription_pack_import,
                  onTap: widget.onImportPack,
                ),
                _ActionRow(
                  icon: Icons.auto_awesome_outlined,
                  label: l10n.sort_ungrouped,
                  onTap: widget.onSortUngrouped,
                ),
                _ActionRow(
                  icon: Icons.list_alt_outlined,
                  label: l10n.import_list_as_group,
                  onTap: widget.onImportList,
                ),
              ] else ...[
                _ActionRow(
                  icon: Icons.auto_awesome_outlined,
                  label: l10n.sort_ungrouped,
                  onTap: widget.onSortUngrouped,
                ),
                _ActionRow(
                  icon: Icons.link_off,
                  label: l10n.find_broken_subscriptions,
                  onTap: widget.onFindBroken,
                ),
              ],
              const _SheetDivider(),
              _ActionRow(
                icon: Icons.sensors_outlined,
                label: l10n.antenna_title,
                onTap: widget.onAntennas,
              ),
              if (widget.onGroups)
                _ActionRow(
                  icon: Icons.view_column_outlined,
                  label: l10n.deck_title,
                  onTap: widget.onDeck,
                ),
              _ActionRow(
                icon: Icons.settings_outlined,
                label: l10n.settings,
                onTap: widget.onSettings,
              ),
            ],
          ),
        ),
      ),
    );
  }

  List<Widget> _sortRows(
    BuildContext context,
    L10n l10n,
    BasePrefService prefs,
  ) {
    if (widget.onGroups) {
      final current =
          prefs.get<String>(optionSubscriptionGroupsOrderByField) ?? 'name';
      void pick(String field) {
        context.read<GroupsModel>().changeOrderSubscriptionGroupsBy(field);
        setState(() {});
      }

      return [
        _CheckRow(
          icon: Icons.sort_by_alpha,
          label: l10n.name,
          selected: current == 'name',
          onTap: () => pick('name'),
        ),
        _CheckRow(
          icon: Icons.schedule_outlined,
          label: l10n.date_created,
          selected: current == 'created_at',
          onTap: () => pick('created_at'),
        ),
        _CheckRow(
          icon: Icons.tune,
          label: l10n.custom,
          selected: current == 'position',
          onTap: () => pick('position'),
        ),
      ];
    }

    final current =
        prefs.get<String>(optionSubscriptionOrderByField) ?? 'name';
    void pick(String field) {
      context.read<SubscriptionsModel>().changeOrderSubscriptionsBy(field);
      setState(() {});
    }

    return [
      _CheckRow(
        icon: Icons.sort_by_alpha,
        label: l10n.name,
        selected: current == 'name',
        onTap: () => pick('name'),
      ),
      _CheckRow(
        icon: Icons.alternate_email,
        label: l10n.username,
        selected: current == 'screen_name',
        onTap: () => pick('screen_name'),
      ),
      _CheckRow(
        icon: Icons.schedule_outlined,
        label: l10n.date_subscribed,
        selected: current == 'created_at',
        onTap: () => pick('created_at'),
      ),
    ];
  }
}

class _ViewModeRow extends StatelessWidget {
  final BasePrefService prefs;
  final VoidCallback onChanged;

  const _ViewModeRow({required this.prefs, required this.onChanged});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final asList =
        prefs.get<String>(optionSubscriptionGroupsLayout) ==
        subscriptionGroupsLayoutList;

    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 0, 16, 8),
      child: Row(
        children: [
          Expanded(
            child: _ModeButton(
              icon: Icons.grid_view_rounded,
              label: l10n.subscription_groups_layout_board,
              selected: !asList,
              onTap: () async {
                await prefs.set(
                  optionSubscriptionGroupsLayout,
                  subscriptionGroupsLayoutBoard,
                );
                onChanged();
              },
            ),
          ),
          const SizedBox(width: 8),
          Expanded(
            child: _ModeButton(
              icon: Icons.view_agenda_outlined,
              label: l10n.subscription_groups_layout_list,
              selected: asList,
              onTap: () async {
                await prefs.set(
                  optionSubscriptionGroupsLayout,
                  subscriptionGroupsLayoutList,
                );
                onChanged();
              },
            ),
          ),
        ],
      ),
    );
  }
}

class _ColumnsRow extends StatelessWidget {
  final BasePrefService prefs;
  final VoidCallback onChanged;

  const _ColumnsRow({required this.prefs, required this.onChanged});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final columns = prefs.get<int>(optionSubscriptionGroupsColumns) ?? 2;

    return Padding(
      padding: const EdgeInsets.fromLTRB(20, 0, 16, 4),
      child: Row(
        children: [
          Expanded(
            child: Text(
              l10n.subscription_groups_columns,
              style: Theme.of(context).textTheme.bodyLarge,
            ),
          ),
          _CountChip(
            label: '2',
            selected: columns != 3,
            onTap: () async {
              await prefs.set(optionSubscriptionGroupsColumns, 2);
              onChanged();
            },
          ),
          const SizedBox(width: 8),
          _CountChip(
            label: '3',
            selected: columns == 3,
            onTap: () async {
              await prefs.set(optionSubscriptionGroupsColumns, 3);
              onChanged();
            },
          ),
        ],
      ),
    );
  }
}

class _ModeButton extends StatelessWidget {
  final IconData icon;
  final String label;
  final bool selected;
  final VoidCallback onTap;

  const _ModeButton({
    required this.icon,
    required this.label,
    required this.selected,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final accent = xAccent(context);
    final readableAccent = xReadableAccent(context);
    final onSurface = xOnSurface(context);
    final fill = selected
        ? accent.withValues(alpha: 0.16)
        : xControlFill(context);
    final tint = selected ? readableAccent : onSurface;

    return Semantics(
      button: true,
      selected: selected,
      child: Material(
        color: fill,
        shape: const StadiumBorder(),
        child: InkWell(
          customBorder: const StadiumBorder(),
          onTap: onTap,
          child: ConstrainedBox(
            constraints: const BoxConstraints(
              minHeight: kMinInteractiveDimension,
            ),
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 12),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Icon(icon, size: 20, color: tint),
                  const SizedBox(width: 8),
                  Flexible(
                    child: Text(
                      label,
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                      textAlign: TextAlign.center,
                      style: TextStyle(
                        color: tint,
                        fontWeight: selected
                            ? FontWeight.w700
                            : FontWeight.w500,
                        fontSize: 13,
                        height: 1.15,
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _CountChip extends StatelessWidget {
  final String label;
  final bool selected;
  final VoidCallback onTap;

  const _CountChip({
    required this.label,
    required this.selected,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final accent = xAccent(context);
    final onSurface = xOnSurface(context);

    return Semantics(
      button: true,
      selected: selected,
      child: Material(
        color: selected ? accent : xControlFill(context),
        shape: const StadiumBorder(),
        child: InkWell(
          customBorder: const StadiumBorder(),
          onTap: onTap,
          child: SizedBox.square(
            dimension: kMinInteractiveDimension,
            child: Center(
              child: Text(
                label,
                style: TextStyle(
                  color: selected
                      ? contrastingForeground(accent)
                      : onSurface,
                  fontWeight: FontWeight.w700,
                  fontSize: 15,
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _CheckRow extends StatelessWidget {
  final IconData icon;
  final String label;
  final bool selected;
  final VoidCallback onTap;

  const _CheckRow({
    required this.icon,
    required this.label,
    required this.selected,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final accent = xReadableAccent(context);
    final tint = selected ? accent : xOnSurface(context);

    return ListTile(
      selected: selected,
      leading: Icon(icon, color: tint),
      title: Text(
        label,
        style: TextStyle(
          color: tint,
          fontWeight: selected ? FontWeight.w700 : FontWeight.w500,
        ),
      ),
      trailing: selected ? Icon(Icons.check, color: accent) : null,
      onTap: onTap,
      visualDensity: VisualDensity.compact,
    );
  }
}

class _SwitchRow extends StatelessWidget {
  final IconData icon;
  final String label;
  final bool value;
  final ValueChanged<bool> onChanged;

  const _SwitchRow({
    required this.icon,
    required this.label,
    required this.value,
    required this.onChanged,
  });

  @override
  Widget build(BuildContext context) {
    return SwitchListTile(
      secondary: Icon(icon, color: xOnSurface(context)),
      title: Text(label),
      value: value,
      onChanged: onChanged,
      visualDensity: VisualDensity.compact,
    );
  }
}

class _ActionRow extends StatelessWidget {
  final IconData icon;
  final String label;
  final VoidCallback onTap;

  const _ActionRow({
    required this.icon,
    required this.label,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return ListTile(
      leading: Icon(icon, color: xOnSurface(context)),
      title: Text(label),
      onTap: onTap,
      visualDensity: VisualDensity.compact,
    );
  }
}

class _SheetDivider extends StatelessWidget {
  const _SheetDivider();

  @override
  Widget build(BuildContext context) {
    return Divider(
      height: 20,
      thickness: 0.5,
      color: Theme.of(context).colorScheme.outlineVariant,
    );
  }
}
