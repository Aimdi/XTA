import 'package:flutter/material.dart';
import 'package:xta/plugins/plugin_home_chrome.dart';
import 'package:xta/ui/label_width.dart';
import 'package:xta/ui/x_controls.dart';

/// How the subscriptions header fits the Gruppen | Abos switch beside its actions.
enum SubscriptionsHeaderLayout {
  /// One row, each section with its icon and name.
  oneRow,

  /// One row with the names alone: the icons would not leave room.
  oneRowLabelsOnly,

  /// The title above the switch, as before: even the names do not fit.
  twoRows,
}

/// The layout for [room], the width left beside Back and the actions, when the
/// switch takes [withIcons] with its icons and [labelsOnly] without them.
SubscriptionsHeaderLayout subscriptionsHeaderLayout({
  required double room,
  required double withIcons,
  required double labelsOnly,
}) => switch (room) {
  _ when withIcons <= room => SubscriptionsHeaderLayout.oneRow,
  _ when labelsOnly <= room => SubscriptionsHeaderLayout.oneRowLabelsOnly,
  _ => SubscriptionsHeaderLayout.twoRows,
};

/// The one-row switch's padding beside each label, and the icon and gap
/// before it.
const subscriptionsHeaderTabPadding = EdgeInsets.symmetric(horizontal: 12);
const _tabIcon = 16.0 + 6.0;

/// The width of the switch as a scrollable [TabBar] of [labels], measured in
/// the selected style so the widest state is the one that has to fit.
double subscriptionTabsWidth(BuildContext context, List<String> labels, {required bool icons}) {
  final style = (Theme.of(context).textTheme.titleSmall ?? const TextStyle())
      .merge(TabBarTheme.of(context).labelStyle)
      .copyWith(fontWeight: FontWeight.w700);
  return labelsWidth(
    context,
    labels,
    style,
    padding: subscriptionsHeaderTabPadding.horizontal + (icons ? _tabIcon : 0),
  );
}

/// The width beside the switch: the screen less Back, when the route has one,
/// and [actions] 48 dp buttons, with a little slack for rounding.
double subscriptionsHeaderRoom(BuildContext context, {required int actions}) {
  final back = ModalRoute.of(context)?.impliesAppBarDismissal ?? false;
  return MediaQuery.sizeOf(context).width - (back ? kToolbarHeight : 0) - actions * 48 - 8;
}

/// The hairline under the one-row header, where the tab bar's divider used to be.
ShapeBorder subscriptionsHeaderHairline(BuildContext context) => Border(
  bottom: BorderSide(
    color: TabBarTheme.of(context).dividerColor ?? Theme.of(context).colorScheme.outlineVariant,
    width: TabBarTheme.of(context).dividerHeight ?? 1,
  ),
);

/// The header while searching: the field in the row, focused, and a way back.
AppBar subscriptionsSearchBar(
  BuildContext context, {
  required TextEditingController controller,
  required String hint,
  required ValueChanged<String> onChanged,
  required VoidCallback onClose,
}) => AppBar(
  toolbarHeight: pluginToolbarHeight(context),
  automaticallyImplyLeading: false,
  leading: IconButton(
    tooltip: MaterialLocalizations.of(context).backButtonTooltip,
    icon: const BackButtonIcon(),
    onPressed: onClose,
  ),
  titleSpacing: 0,
  shape: subscriptionsHeaderHairline(context),
  title: Padding(
    padding: const EdgeInsetsDirectional.only(end: 12),
    child: XSearchField(controller: controller, hintText: hint, onChanged: onChanged, autofocus: true),
  ),
);
