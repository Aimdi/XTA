import 'package:flutter/material.dart';
import 'package:quax/tweet/tweet_chrome.dart';
import 'package:quax/ui/x_look_theme.dart';

const double kHomeNavigationHeight = 64;

@immutable
class HomeSwitcherOption<T> {
  final T value;
  final String label;

  const HomeSwitcherOption({required this.value, required this.label});
}

/// Compact app-bar switcher used for alternate views of the same Home feed.
class HomeFeedSwitcher<T> extends StatelessWidget {
  final T selected;
  final List<HomeSwitcherOption<T>> options;
  final ValueChanged<T> onSelected;

  const HomeFeedSwitcher({super.key, required this.selected, required this.options, required this.onSelected});

  @override
  Widget build(BuildContext context) {
    final selectedOption = options.firstWhere((option) => option.value == selected);
    if (options.length == 1) {
      return Text(selectedOption.label, maxLines: 1, overflow: TextOverflow.ellipsis);
    }

    return PopupMenuButton<T>(
      initialValue: selected,
      onSelected: onSelected,
      tooltip: selectedOption.label,
      position: PopupMenuPosition.under,
      constraints: const BoxConstraints(minWidth: 200, maxWidth: 280),
      itemBuilder: (context) =>
          options.map((option) => _menuItem(context, option, option.value == selected)).toList(growable: false),
      child: ConstrainedBox(
        constraints: const BoxConstraints(minHeight: kTweetTouchTarget),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Flexible(
              child: Text(
                selectedOption.label,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: Theme.of(context).appBarTheme.titleTextStyle,
              ),
            ),
            const SizedBox(width: kTweetSpace1),
            Icon(Icons.expand_more, size: kTweetActionIconSize, color: tweetSecondaryColor(context)),
          ],
        ),
      ),
    );
  }

  PopupMenuItem<T> _menuItem(BuildContext context, HomeSwitcherOption<T> option, bool isSelected) {
    return PopupMenuItem<T>(
      value: option.value,
      height: kTweetTouchTarget,
      child: Semantics(
        selected: isSelected,
        child: Row(
          children: [
            SizedBox(
              width: kTweetSpace6,
              child: isSelected
                  ? Icon(Icons.check, size: kTweetActionIconSize, color: tweetAccentColor(context))
                  : null,
            ),
            const SizedBox(width: kTweetSpace2),
            Expanded(
              child: Text(
                option.label,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                  color: isSelected ? tweetPrimaryColor(context) : tweetSecondaryColor(context),
                  fontWeight: isSelected ? FontWeight.w700 : FontWeight.w500,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

@immutable
class HomeNavigationItem {
  final String label;
  final Widget icon;
  final Widget selectedIcon;

  const HomeNavigationItem({required this.label, required this.icon, required this.selectedIcon});
}

/// Home's edge-to-edge navigation surface with quiet, explicit selection.
class HomeNavigationBar extends StatelessWidget {
  final int selectedIndex;
  final List<HomeNavigationItem> items;
  final bool showLabels;
  final bool disableAnimations;
  final ValueChanged<int> onSelected;

  const HomeNavigationBar({
    super.key,
    required this.selectedIndex,
    required this.items,
    required this.showLabels,
    required this.disableAnimations,
    required this.onSelected,
  });

  @override
  Widget build(BuildContext context) {
    final tokens = XLookTokens.maybeOf(context);
    final reduceMotion = disableAnimations || MediaQuery.disableAnimationsOf(context);

    return DecoratedBox(
      decoration: BoxDecoration(
        color: tokens?.background ?? Theme.of(context).colorScheme.surface,
        border: Border(
          top: BorderSide(color: tweetDividerColor(context), width: kTweetDividerThickness),
        ),
      ),
      child: NavigationBar(
        selectedIndex: selectedIndex,
        labelBehavior: showLabels
            ? NavigationDestinationLabelBehavior.alwaysShow
            : NavigationDestinationLabelBehavior.alwaysHide,
        height: kHomeNavigationHeight,
        destinations: items
            .asMap()
            .entries
            .map((entry) => _destination(entry.value, entry.key == selectedIndex, reduceMotion))
            .toList(growable: false),
        onDestinationSelected: onSelected,
      ),
    );
  }

  NavigationDestination _destination(HomeNavigationItem item, bool selected, bool reduceMotion) {
    final duration = Duration(milliseconds: reduceMotion ? 0 : 180);
    final scale = selected ? 1.08 : 1.0;
    final icon = selected ? item.selectedIcon : item.icon;

    return NavigationDestination(
      icon: AnimatedScale(scale: scale, duration: duration, curve: Curves.easeOutCubic, child: icon),
      selectedIcon: AnimatedScale(scale: scale, duration: duration, curve: Curves.easeOutCubic, child: icon),
      label: item.label,
    );
  }
}

class HomeLoadingState extends StatelessWidget {
  const HomeLoadingState({super.key});

  @override
  Widget build(BuildContext context) {
    return const Scaffold(
      body: SafeArea(
        child: Center(
          child: SizedBox.square(
            dimension: kTweetTouchTarget,
            child: Padding(padding: EdgeInsets.all(kTweetSpace3), child: CircularProgressIndicator(strokeWidth: 2)),
          ),
        ),
      ),
    );
  }
}
