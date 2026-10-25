import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/group/subscription_pack.dart';
import 'package:xta/ui/reader_tab_view.dart';
import 'package:xta/group/group_model.dart';
import 'package:xta/subscriptions/_cleanup.dart';
import 'package:xta/subscriptions/_groups.dart';
import 'package:xta/subscriptions/_import.dart';
import 'package:xta/subscriptions/_import_list.dart';
import 'package:xta/subscriptions/_list.dart';
import 'package:xta/subscriptions/group_ungrouped_screen.dart';
import 'package:xta/subscriptions/subscriptions_menu_sheet.dart';
import 'package:xta/subscriptions/users_model.dart';
import 'package:xta/subscriptions/subscriptions_header.dart';
import 'package:xta/ui/icon_label.dart';
import 'package:xta/utils/reader_value_store.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/plugins/plugin_home_chrome.dart';
import 'package:provider/provider.dart';

/// Folder for Groups, people list for Abos — same icon+label row as chips.
const IconData subscriptionGroupsTabIcon = Icons.folder_outlined;
const IconData subscriptionPeopleTabIcon = Icons.people_outlined;

/// The Gruppen | Abos [TabBar] entries, with their icons unless [icons] is
/// false because the header has no room for them, [height] tall when set.
List<Widget> subscriptionSectionTabs(
  L10n l10n, {
  bool icons = true,
  double? height,
}) => [
  for (final (icon, label) in [
    (subscriptionGroupsTabIcon, l10n.groups),
    (subscriptionPeopleTabIcon, l10n.subscriptions),
  ])
    Tab(
      height: height,
      child: icons
          ? IconLabel(icon: icon, label: label)
          : Text(label, maxLines: 1, softWrap: false),
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

  /// Whether the header holds the search field, and what it filters by.
  final _searching = ReaderValueStore<bool>(false);
  final _query = ReaderValueStore<String>('');
  final _queryField = TextEditingController();

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
    _searching.destroy();
    _query.destroy();
    _queryField.dispose();
    super.dispose();
  }

  void _openSearch() => _searching.update(true);

  void _closeSearch() {
    _queryField.clear();
    _query.update('');
    _searching.update(false);
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

  List<Widget> _actions(L10n l10n) => [
    IconButton(
      tooltip: l10n.search,
      icon: const Icon(Icons.search),
      onPressed: _openSearch,
    ),
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
  ];

  /// The switch where the title stood, beside the actions; the title above
  /// the switch, as before, only when its names do not fit beside them.
  AppBar _header(BuildContext context, L10n l10n) {
    final actions = _actions(l10n);
    final toolbarHeight = pluginToolbarHeight(context);
    final labels = [l10n.groups, l10n.subscriptions];
    final layout = subscriptionsHeaderLayout(
      room: subscriptionsHeaderRoom(context, actions: actions.length),
      withIcons: subscriptionTabsWidth(context, labels, icons: true),
      labelsOnly: subscriptionTabsWidth(context, labels, icons: false),
    );
    if (layout == SubscriptionsHeaderLayout.twoRows) {
      return AppBar(
        title: Text(l10n.subscriptions),
        bottom: TabBar(controller: _tabs, tabs: subscriptionSectionTabs(l10n)),
        actions: actions,
      );
    }
    return AppBar(
      toolbarHeight: toolbarHeight,
      titleSpacing: 0,
      excludeHeaderSemantics: true,
      shape: subscriptionsHeaderHairline(context),
      title: TabBar(
        controller: _tabs,
        isScrollable: true,
        tabAlignment: TabAlignment.start,
        labelPadding: subscriptionsHeaderTabPadding,
        dividerColor: Colors.transparent,
        // The tabs fill the row, so the indicator sits on its hairline.
        tabs: subscriptionSectionTabs(
          l10n,
          icons: layout == SubscriptionsHeaderLayout.oneRow,
          height: toolbarHeight - 2,
        ),
      ),
      actions: actions,
    );
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);

    return ScopedBuilder<ReaderValueStore<bool>, bool>(
      store: _searching,
      onState: (context, searching) => PopScope(
        // Back closes an open search before it leaves the screen.
        canPop: !searching,
        onPopInvokedWithResult: (didPop, _) {
          if (!didPop) _closeSearch();
        },
        child: Scaffold(
          appBar: searching
              ? subscriptionsSearchBar(
                  context,
                  controller: _queryField,
                  hint: _onGroups ? l10n.search : l10n.search_subscriptions,
                  onChanged: _query.update,
                  onClose: _closeSearch,
                )
              : _header(context, l10n),
          body: ReaderTabView(
            controller: _tabs,
            children: [
              SubscriptionGroupsPage(
                scrollController: _groupsScrollController,
                query: _query,
              ),
              SubscriptionUsersPage(
                scrollController: widget.scrollController,
                query: _query,
              ),
            ],
          ),
        ),
      ),
    );
  }
}
