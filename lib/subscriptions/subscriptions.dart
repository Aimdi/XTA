import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
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
import 'package:xta/subscriptions/subscriptions_menu_sheet.dart';
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

  void _openMenu() {
    showSubscriptionsMenuSheet(
      context: context,
      onGroups: _onGroups,
      onImportPack: _importPack,
      onSortUngrouped: _sortUngrouped,
      onImportList: _importListAsGroup,
      onFindBroken: _findBroken,
      onAntennas: () => Navigator.pushNamed(context, routeAntennas),
      onDeck: () => Navigator.pushNamed(context, routeDeck),
      onSettings: () => Navigator.pushNamed(context, routeSettings),
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
          IconButton(
            tooltip: MaterialLocalizations.of(context).showMenuTooltip,
            icon: const Icon(Icons.tune),
            onPressed: _openMenu,
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
