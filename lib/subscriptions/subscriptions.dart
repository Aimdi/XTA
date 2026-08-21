import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/group/subscription_pack.dart';
import 'package:xta/home/edge_swipe.dart';
import 'package:xta/group/group_model.dart';
import 'package:xta/subscriptions/_cleanup.dart';
import 'package:xta/subscriptions/_groups.dart';
import 'package:xta/subscriptions/_import.dart';
import 'package:xta/subscriptions/_import_list.dart';
import 'package:xta/subscriptions/_list.dart';
import 'package:xta/subscriptions/group_ungrouped_screen.dart';
import 'package:xta/subscriptions/users_model.dart';
import 'package:xta/ui/icon_label.dart';
import 'package:provider/provider.dart';

/// Folder for Groups, people list for Abos — same icon+label row as chips.
const IconData subscriptionGroupsTabIcon = Icons.folder_outlined;
const IconData subscriptionPeopleTabIcon = Icons.people_outlined;

/// The Gruppen | Abos [TabBar] entries.
List<Widget> subscriptionSectionTabs(L10n l10n) => [
  Tab(
    child: IconLabel(icon: subscriptionGroupsTabIcon, label: l10n.groups),
  ),
  Tab(
    child: IconLabel(
      icon: subscriptionPeopleTabIcon,
      label: l10n.subscriptions,
    ),
  ),
];

/// Subscriptions home tab: Groups | People, with management actions in the app bar.
class SubscriptionsScreen extends StatefulWidget {
  final ScrollController scrollController;

  const SubscriptionsScreen({super.key, required this.scrollController});

  @override
  State<SubscriptionsScreen> createState() => _SubscriptionsScreenState();
}

class _SubscriptionsScreenState extends State<SubscriptionsScreen>
    with SingleTickerProviderStateMixin {
  late final TabController _tabs;
  late final ScrollController _groupsScrollController;

  @override
  void initState() {
    super.initState();
    _tabs = TabController(length: 2, vsync: this);
    _tabs.addListener(() {
      if (!_tabs.indexIsChanging) setState(() {});
    });
    _groupsScrollController = ScrollController();
  }

  @override
  void dispose() {
    _tabs.dispose();
    _groupsScrollController.dispose();
    super.dispose();
  }

  bool get _onGroups => _tabs.index == 0;

  Future<void> _createGroup() {
    return openSubscriptionGroupDialog(context, null, '', defaultGroupIcon);
  }

  void _importListAsGroup() {
    Navigator.push(
      context,
      MaterialPageRoute(builder: (_) => const ListImportScreen()),
    );
  }

  void _importSubscriptions() {
    Navigator.push(
      context,
      MaterialPageRoute(builder: (_) => const SubscriptionImportScreen()),
    );
  }

  void _sortUngrouped() {
    Navigator.push(
      context,
      MaterialPageRoute(builder: (_) => const SortUngroupedScreen()),
    );
  }

  void _importPack() async {
    final groupsModel = context.read<GroupsModel>();
    final subscriptionsModel = context.read<SubscriptionsModel>();
    final picked = await FilePicker.pickFile(
      type: FileType.custom,
      allowedExtensions: const ['json'],
    );
    if (!mounted || picked == null) {
      return;
    }

    final l10n = L10n.of(context);
    final messenger = ScaffoldMessenger.of(context);
    try {
      final bytes = await picked.readAsBytes();
      if (!mounted) {
        return;
      }
      final pack = decodeSubscriptionPack(String.fromCharCodes(bytes));
      final count = await importSubscriptionPack(
        pack,
        groupsModel,
        subscriptionsModel,
      );
      if (!mounted) {
        return;
      }
      messenger.showSnackBar(
        SnackBar(
          content: Text(l10n.subscription_pack_imported(pack.name, count)),
        ),
      );
    } on FormatException {
      if (!mounted) {
        return;
      }
      messenger.showSnackBar(
        SnackBar(content: Text(l10n.subscription_pack_invalid)),
      );
    }
  }

  void _findBroken() {
    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (_) => const BrokenSubscriptionsDialog(),
    );
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);

    return Scaffold(
      appBar: AppBar(
        title: Text(l10n.subscriptions),
        bottom: TabBar(controller: _tabs, tabs: subscriptionSectionTabs(l10n)),
        actions: [
          if (_onGroups)
            IconButton(
              tooltip: l10n.create_subscription_group,
              icon: const Icon(Icons.add),
              onPressed: _createGroup,
            )
          else
            IconButton(
              tooltip: l10n.import_subscriptions,
              icon: const Icon(Icons.person_add_alt_1_outlined),
              onPressed: _importSubscriptions,
            ),
          PopupMenuButton<_SubscriptionsMenuAction>(
            onSelected: (action) {
              switch (action) {
                case _SubscriptionsMenuAction.createGroup:
                  _createGroup();
                case _SubscriptionsMenuAction.sortUngrouped:
                  _sortUngrouped();
                case _SubscriptionsMenuAction.importList:
                  _importListAsGroup();
                case _SubscriptionsMenuAction.sortGroupsByName:
                  context.read<GroupsModel>().changeOrderSubscriptionGroupsBy(
                    'name',
                  );
                case _SubscriptionsMenuAction.sortGroupsByDate:
                  context.read<GroupsModel>().changeOrderSubscriptionGroupsBy(
                    'created_at',
                  );
                case _SubscriptionsMenuAction.sortGroupsByCustom:
                  context.read<GroupsModel>().changeOrderSubscriptionGroupsBy(
                    'position',
                  );
                case _SubscriptionsMenuAction.toggleGroupLayout:
                  final prefs = PrefService.of(context);
                  final asList =
                      prefs.get<String>(optionSubscriptionGroupsLayout) ==
                      subscriptionGroupsLayoutList;
                  prefs.set(
                    optionSubscriptionGroupsLayout,
                    asList
                        ? subscriptionGroupsLayoutBoard
                        : subscriptionGroupsLayoutList,
                  );
                case _SubscriptionsMenuAction.toggleGroupColumns:
                  final prefs = PrefService.of(context);
                  final current =
                      prefs.get<int>(optionSubscriptionGroupsColumns) ?? 2;
                  prefs.set(
                    optionSubscriptionGroupsColumns,
                    current == 2 ? 3 : 2,
                  );
                case _SubscriptionsMenuAction.toggleGroupsOrder:
                  context
                      .read<GroupsModel>()
                      .toggleOrderSubscriptionGroupsAscending();
                case _SubscriptionsMenuAction.importSubscriptions:
                  _importSubscriptions();
                case _SubscriptionsMenuAction.importPack:
                  _importPack();
                case _SubscriptionsMenuAction.findBroken:
                  _findBroken();
                case _SubscriptionsMenuAction.sortSubsByName:
                  context.read<SubscriptionsModel>().changeOrderSubscriptionsBy(
                    'name',
                  );
                case _SubscriptionsMenuAction.sortSubsByUsername:
                  context.read<SubscriptionsModel>().changeOrderSubscriptionsBy(
                    'screen_name',
                  );
                case _SubscriptionsMenuAction.sortSubsByDate:
                  context.read<SubscriptionsModel>().changeOrderSubscriptionsBy(
                    'created_at',
                  );
                case _SubscriptionsMenuAction.toggleSubsOrder:
                  context
                      .read<SubscriptionsModel>()
                      .toggleOrderSubscriptionsAscending();
                case _SubscriptionsMenuAction.settings:
                  Navigator.pushNamed(context, routeSettings);
                case _SubscriptionsMenuAction.antennas:
                  Navigator.pushNamed(context, routeAntennas);
                case _SubscriptionsMenuAction.openDeck:
                  Navigator.pushNamed(context, routeDeck);
              }
            },
            itemBuilder: (context) => [
              if (_onGroups) ...[
                PopupMenuItem(
                  value: _SubscriptionsMenuAction.importPack,
                  child: Text(l10n.subscription_pack_import),
                ),
                PopupMenuItem(
                  value: _SubscriptionsMenuAction.sortUngrouped,
                  child: Text(l10n.sort_ungrouped),
                ),
                PopupMenuItem(
                  value: _SubscriptionsMenuAction.importList,
                  child: Text(l10n.import_list_as_group),
                ),
                const PopupMenuDivider(),
                PopupMenuItem(
                  value: _SubscriptionsMenuAction.sortGroupsByName,
                  child: Text(l10n.name),
                ),
                PopupMenuItem(
                  value: _SubscriptionsMenuAction.sortGroupsByDate,
                  child: Text(l10n.date_created),
                ),
                PopupMenuItem(
                  value: _SubscriptionsMenuAction.sortGroupsByCustom,
                  child: Text(l10n.custom),
                ),
                const PopupMenuDivider(),
                PopupMenuItem(
                  value: _SubscriptionsMenuAction.toggleGroupLayout,
                  child: Text(
                    PrefService.of(
                              context,
                            ).get<String>(optionSubscriptionGroupsLayout) ==
                            subscriptionGroupsLayoutList
                        ? l10n.subscription_groups_layout_board
                        : l10n.subscription_groups_layout_list,
                  ),
                ),
                // Columns only shape the board, so offering them while a list
                // is on screen is a control that does nothing.
                if (PrefService.of(
                      context,
                    ).get<String>(optionSubscriptionGroupsLayout) !=
                    subscriptionGroupsLayoutList)
                  PopupMenuItem(
                    value: _SubscriptionsMenuAction.toggleGroupColumns,
                    child: Text(l10n.subscription_groups_columns),
                  ),
                PopupMenuItem(
                  value: _SubscriptionsMenuAction.toggleGroupsOrder,
                  child: Text(l10n.toggle_sort_direction),
                ),
              ] else ...[
                PopupMenuItem(
                  value: _SubscriptionsMenuAction.sortUngrouped,
                  child: Text(l10n.sort_ungrouped),
                ),
                PopupMenuItem(
                  value: _SubscriptionsMenuAction.findBroken,
                  child: Text(l10n.find_broken_subscriptions),
                ),
                const PopupMenuDivider(),
                PopupMenuItem(
                  value: _SubscriptionsMenuAction.sortSubsByName,
                  child: Text(l10n.name),
                ),
                PopupMenuItem(
                  value: _SubscriptionsMenuAction.sortSubsByUsername,
                  child: Text(l10n.username),
                ),
                PopupMenuItem(
                  value: _SubscriptionsMenuAction.sortSubsByDate,
                  child: Text(l10n.date_subscribed),
                ),
                PopupMenuItem(
                  value: _SubscriptionsMenuAction.toggleSubsOrder,
                  child: Text(l10n.toggle_sort_direction),
                ),
              ],
              const PopupMenuDivider(),
              PopupMenuItem(
                value: _SubscriptionsMenuAction.antennas,
                child: Text(l10n.antenna_title),
              ),
              if (_onGroups)
                PopupMenuItem(
                  value: _SubscriptionsMenuAction.openDeck,
                  child: Text(l10n.deck_title),
                ),
              PopupMenuItem(
                value: _SubscriptionsMenuAction.settings,
                child: Text(l10n.settings),
              ),
            ],
          ),
        ],
      ),
      // Without this the inner tab view keeps every horizontal swipe, so the
      // home page view could never be reached from this tab.
      body: edgeSwipeToChangeHomePage(
        context,
        TabBarView(
          controller: _tabs,
          children: [
            SubscriptionGroupsPage(scrollController: _groupsScrollController),
            SubscriptionUsersPage(scrollController: widget.scrollController),
          ],
        ),
      ),
    );
  }
}

enum _SubscriptionsMenuAction {
  createGroup,
  importPack,
  sortUngrouped,
  importList,
  sortGroupsByName,
  sortGroupsByDate,
  sortGroupsByCustom,
  toggleGroupLayout,
  toggleGroupColumns,
  toggleGroupsOrder,
  importSubscriptions,
  findBroken,
  sortSubsByName,
  sortSubsByUsername,
  sortSubsByDate,
  toggleSubsOrder,
  antennas,
  openDeck,
  settings,
}
