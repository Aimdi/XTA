import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/group/group_chrome.dart';
import 'package:xta/home/home_chrome.dart';
import 'package:xta/plugins/plugin_home_chrome.dart';
import 'package:xta/tweet/tweet_chrome.dart';
import 'package:xta/ui/contrast.dart';
import 'package:xta/ui/label_width.dart';
import 'package:xta/ui/motion.dart';
import 'package:xta/ui/reader_chrome.dart';

const double kHomeFollowingBarHeight = 48;

/// The tabs' names and marker as the Ink canvas draws them (docs/specs/feed-ink.md): 16 px,
/// 28 dp apart, a 4 dp marker.
const double kHomeTabNameSize = 16;
const double kHomeTabPadding = 14;
const double kHomeTabMarkerHeight = 4;

/// The dot on Home feed accounts while something is turned off.
const double kHomeAccountsDotSize = 8;

/// A tab's name in Following's bar.
TextStyle homeTabNameStyle({required bool selected}) =>
    readerTabNameStyle(selected: selected).copyWith(fontSize: kHomeTabNameSize);

/// How Following's bar fits Posts | Media beside its controls.
enum HomeFollowingBarLayout {
  /// One row, as the canvas draws it.
  oneRow,

  /// One row with the tabs closer together.
  compact,

  /// The tabs under the controls: even closer together they do not fit.
  twoRows,
}

/// The fit for a bar [width] wide with the order button and [actions] more 48 dp buttons, measured
/// with both tabs in the selected style so the widest state is the one that fits.
HomeFollowingBarLayout homeFollowingBarLayout(BuildContext context, {required double width, required int actions}) {
  final l10n = L10n.of(context);
  final style = (Theme.of(context).textTheme.labelLarge ?? const TextStyle()).merge(homeTabNameStyle(selected: true));
  double tabs(double padding) => labelsWidth(context, [l10n.tweets, l10n.media], style, padding: 2 * padding);
  final controls = kTweetSpace1 + (1 + actions) * kTweetTouchTarget + kHomeAppBarEndInset;
  return switch (width) {
    _ when tabs(kHomeTabPadding) + controls <= width => HomeFollowingBarLayout.oneRow,
    _ when tabs(kTweetSpace2) + controls <= width => HomeFollowingBarLayout.compact,
    _ => HomeFollowingBarLayout.twoRows,
  };
}

double homeFollowingBarHeight(HomeFollowingBarLayout layout) =>
    layout == HomeFollowingBarLayout.twoRows ? 2 * kHomeFollowingBarHeight : kHomeFollowingBarHeight;

/// Reclaims space while retaining the controls' selection and scroll state.
class HomeCollapsingControls extends StatelessWidget {
  final bool visible;
  final Widget child;

  const HomeCollapsingControls({super.key, required this.visible, required this.child});

  @override
  Widget build(BuildContext context) => IgnorePointer(
    ignoring: !visible,
    child: ExcludeFocus(
      excluding: !visible,
      child: ExcludeSemantics(
        excluding: !visible,
        child: TweenAnimationBuilder<double>(
          tween: Tween(begin: visible ? 1 : 0, end: visible ? 1 : 0),
          duration: xtaMotionDuration(context, kXtaMotionStandard),
          curve: Curves.easeInOut,
          child: child,
          builder: (_, factor, child) => ClipRect(
            child: Align(alignment: Alignment.topCenter, heightFactor: factor, child: child),
          ),
        ),
      ),
    ),
  );
}

/// The source picker drawn the way docked plugin headers draw theirs: the mark alone.
class HomeTimelineTitle extends StatelessWidget {
  final String label;
  final Widget mark;
  final bool unread;
  final VoidCallback onPressed;

  const HomeTimelineTitle({
    super.key,
    required this.label,
    required this.mark,
    required this.onPressed,
    this.unread = false,
  });

  @override
  Widget build(BuildContext context) => Align(
    alignment: AlignmentDirectional.centerStart,
    child: Semantics(
      label: [label, if (unread) L10n.of(context).group_has_unread].join(', '),
      child: IconButton(
        key: const ValueKey('home-source-picker'),
        style: pluginActionButtonStyle,
        tooltip: L10n.of(context).home_networks,
        onPressed: onPressed,
        icon: Badge(isLabelVisible: unread, smallSize: 7, child: mark),
      ),
    ),
  );
}

/// Following's header in one bar: Posts | Media at the start, then ordering
/// and filters, and the source picker and feed actions in [actions].
class HomeFollowingBar extends StatelessWidget {
  /// Null until Following's group has loaded; the order chip waits for it.
  final SubscriptionGroupGet? group;
  final bool mediaOnly;
  final ValueChanged<int> onOrderSelected;
  final VoidCallback onMediaToggle;
  final VoidCallback onFilters;
  final List<Widget> actions;
  final HomeFollowingBarLayout layout;

  const HomeFollowingBar({
    super.key,
    required this.group,
    required this.mediaOnly,
    required this.onOrderSelected,
    required this.onMediaToggle,
    required this.onFilters,
    required this.actions,
    this.layout = HomeFollowingBarLayout.oneRow,
  });

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final group = this.group;
    final padding = layout == HomeFollowingBarLayout.oneRow ? kHomeTabPadding : kTweetSpace2;
    final tabs = SingleChildScrollView(
      scrollDirection: Axis.horizontal,
      child: Row(
        children: [_modeTab(context, false, l10n.tweets, padding), _modeTab(context, true, l10n.media, padding)],
      ),
    );
    final controls = [
      // The canvas draws the bar's icons in the text colour, not the
      // secondary grey X Look gives icon buttons elsewhere.
      IconButtonTheme(
        data: IconButtonThemeData(
          style: IconButton.styleFrom(
            foregroundColor: tweetPrimaryColor(context),
          ).merge(IconButtonTheme.of(context).style),
        ),
        child: HomeAppBarActions(
          children: [
            if (group != null) HomeOrderButton(group: group, onOrderSelected: onOrderSelected, onFilters: onFilters),
            ...actions,
          ],
        ),
      ),
    ];
    return SizedBox(
      key: const ValueKey('home-following-bar'),
      height: homeFollowingBarHeight(layout),
      child: Padding(
        padding: const EdgeInsetsDirectional.only(start: kTweetSpace1),
        child: layout == HomeFollowingBarLayout.twoRows
            ? Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  SizedBox(
                    height: kHomeFollowingBarHeight,
                    child: Row(mainAxisAlignment: MainAxisAlignment.end, children: controls),
                  ),
                  SizedBox(
                    height: kHomeFollowingBarHeight,
                    child: Align(alignment: AlignmentDirectional.centerStart, child: tabs),
                  ),
                ],
              )
            : Row(
                children: [
                  Expanded(child: tabs),
                  ...controls,
                ],
              ),
      ),
    );
  }

  Widget _modeTab(BuildContext context, bool media, String label, double padding) {
    final selected = mediaOnly == media;
    return MergeSemantics(
      child: Semantics(
        selected: selected,
        child: TextButton(
          key: ValueKey(media ? 'home-media-toggle' : 'home-posts-tab'),
          onPressed: () {
            if (!selected) onMediaToggle();
          },
          style: TextButton.styleFrom(
            minimumSize: const Size(kTweetTouchTarget, kHomeFollowingBarHeight),
            foregroundColor: selected ? tweetPrimaryColor(context) : tweetSecondaryColor(context),
            padding: EdgeInsets.symmetric(horizontal: padding),
            shape: const RoundedRectangleBorder(),
          ),
          child: SizedBox(
            height: kHomeFollowingBarHeight,
            child: Stack(
              alignment: Alignment.center,
              children: [
                Text(label, style: homeTabNameStyle(selected: selected)),
                PositionedDirectional(
                  start: 0,
                  end: 0,
                  bottom: 0,
                  child: AnimatedContainer(
                    duration: xtaMotionDuration(context, kXtaMotionStandard),
                    height: kHomeTabMarkerHeight,
                    decoration: BoxDecoration(
                      color: selected ? tweetReadableAccentColor(context) : Colors.transparent,
                      borderRadius: BorderRadius.circular(kHomeTabMarkerHeight),
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

/// Ordering and filters behind the filter icon. The order's name is said, not drawn; when filters
/// are on, their count sits on the icon's corner in the accent colour.
class HomeOrderButton extends StatelessWidget {
  final SubscriptionGroupGet group;
  final ValueChanged<int> onOrderSelected;
  final VoidCallback onFilters;

  const HomeOrderButton({super.key, required this.group, required this.onOrderSelected, required this.onFilters});

  int get _order => group.custom ? 2 : (group.popular ? 1 : 0);

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final labels = [l10n.recent, l10n.popular, l10n.custom];
    final filters = groupActiveFilterCount(group);
    final accent = tweetAccentColor(context);
    return PopupMenuButton<int>(
      key: const ValueKey('home-order-menu'),
      initialValue: _order,
      tooltip: '${labels[_order]} · ${l10n.filters}',
      position: PopupMenuPosition.under,
      onSelected: (value) => value == 3 ? onFilters() : onOrderSelected(value),
      itemBuilder: (_) => [
        for (var index = 0; index < labels.length; index++)
          PopupMenuItem(
            value: index,
            height: kTweetTouchTarget,
            child: Row(
              children: [
                SizedBox(
                  width: 24,
                  child: index == _order ? Icon(Icons.check, color: tweetReadableAccentColor(context)) : null,
                ),
                const SizedBox(width: 12),
                Expanded(child: Text(labels[index])),
                if (index == 2) const Icon(Icons.tune, size: 20),
              ],
            ),
          ),
        const PopupMenuDivider(),
        PopupMenuItem(
          value: 3,
          height: kTweetTouchTarget,
          child: Row(
            children: [
              const Icon(Icons.build_outlined, size: 24),
              const SizedBox(width: 12),
              Expanded(child: Text(l10n.filters)),
              if (filters > 0) Badge.count(count: filters),
            ],
          ),
        ),
      ],
      child: Semantics(
        button: true,
        child: SizedBox.square(
          dimension: kTweetTouchTarget,
          child: Center(
            child: Badge.count(
              count: filters,
              isLabelVisible: filters > 0,
              backgroundColor: accent,
              textColor: contrastingForeground(accent),
              child: Icon(Icons.filter_list, color: tweetPrimaryColor(context)),
            ),
          ),
        ),
      ),
    );
  }
}

/// Home feed accounts: a person, with a small dot while accounts or groups are turned off. A screen
/// reader hears how many. A plain [IconButton], so a compact header still lists it among its
/// options, under the same name it was pinned by.
IconButton homeFeedAccountsButton(BuildContext context, {required int turnedOff, required VoidCallback onPressed}) {
  final locale = Localizations.localeOf(context).toLanguageTag();
  return IconButton(
    tooltip: L10n.of(context).home_feed_accounts,
    icon: Badge(
      isLabelVisible: turnedOff > 0,
      smallSize: kHomeAccountsDotSize,
      child: Semantics(
        value: turnedOff > 0 ? NumberFormat.decimalPattern(locale).format(turnedOff) : null,
        child: const Icon(Icons.person_outline),
      ),
    ),
    onPressed: onPressed,
  );
}
