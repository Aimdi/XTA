import 'dart:math' as math;
import 'dart:ui' show ImageFilter;

import 'package:flutter/material.dart';
import 'package:xta/tweet/tweet_chrome.dart';
import 'package:xta/ui/contrast.dart';
import 'package:xta/ui/motion.dart';
import 'package:xta/ui/reader_chrome.dart';
import 'package:xta/ui/x_look_theme.dart';

const double kHomeNavigationHeight = 54;

/// Icons alone need only one touch target's height.
const double kHomeNavigationIconOnlyHeight = kTweetTouchTarget;

double homeNavigationHeight({required bool showLabels}) =>
    showLabels ? kHomeNavigationHeight : kHomeNavigationIconOnlyHeight;

/// Space between the floating bar and the screen's side edges.
const double kHomeNavigationFloatInset = 16;

/// Space above the floating bar, and below it when the system adds no inset.
const double kHomeNavigationFloatGap = 8;

/// How much of the pill's surface colour covers the page behind it.
const double kHomeNavigationLightGlass = 0.74;
const double kHomeNavigationDarkGlass = 0.78;
const double kHomeNavigationBlurSigma = 16;
const double kHomeNavigationHighlightInset = 3;
const double kHomeNavigationHighlightRadius = 20;
const double kHomeNavigationLabelSize = 11;
const double kHomeNavigationIconSize = 22;

/// The extra bottom space balances the gap the icon slot leaves under its
/// glyph, so icon and label sit centred in the highlight.
const EdgeInsets kHomeNavigationLabelPadding = EdgeInsets.fromLTRB(6, 1, 6, 3);

const double kHomeFeedStripHeight = 64;
const double kHomeFeedTabHorizontalPadding = 12;
const double kHomeFeedIndicatorThickness = 2;
const double kHomeAppBarEndInset = kTweetSpace1;

/// Leading-aligned Home title that cannot compete with feed actions at large
/// text scales.
class HomeAppBarTitle extends StatelessWidget {
  final String label;

  const HomeAppBarTitle({super.key, required this.label});

  @override
  Widget build(BuildContext context) {
    return Text(label, maxLines: 1, overflow: TextOverflow.ellipsis);
  }
}

/// Keeps Home's existing actions in predictable, independent touch targets.
class HomeAppBarActions extends StatelessWidget {
  final List<Widget> children;

  const HomeAppBarActions({super.key, required this.children});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsetsDirectional.only(end: kHomeAppBarEndInset),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          for (final child in children)
            SizedBox.square(
              dimension: kTweetTouchTarget,
              child: Center(child: child),
            ),
        ],
      ),
    );
  }
}

/// Source dock above the app navigation, separate from the reading controls.
class HomeFeedStrip extends StatelessWidget {
  final List<Widget> tabs;
  final ValueChanged<int>? onTap;
  final String addTooltip;
  final VoidCallback onAdd;

  const HomeFeedStrip({
    super.key,
    required this.tabs,
    this.onTap,
    required this.addTooltip,
    required this.onAdd,
  });

  @override
  Widget build(BuildContext context) {
    final tokens = XLookTokens.maybeOf(context);
    final theme = Theme.of(context);

    return SizedBox(
      height: kHomeFeedStripHeight,
      child: DecoratedBox(
        decoration: BoxDecoration(
          color: tokens?.background ?? theme.colorScheme.surface,
          border: Border(
            top: BorderSide(
              color: tweetDividerColor(context),
              width: kTweetDividerThickness,
            ),
          ),
        ),
        child: Padding(
          padding: const EdgeInsets.symmetric(vertical: 8),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Expanded(
                child: TabBar(
                  dividerHeight: 0,
                  isScrollable: true,
                  tabAlignment: TabAlignment.start,
                  labelPadding: const EdgeInsets.symmetric(
                    horizontal: kHomeFeedTabHorizontalPadding,
                  ),
                  indicatorColor: tweetReadableAccentColor(context),
                  indicatorSize: TabBarIndicatorSize.tab,
                  indicatorWeight: kHomeFeedIndicatorThickness,
                  indicator: BoxDecoration(
                    color: tweetAccentColor(context).withValues(alpha: 0.12),
                    border: Border.all(
                      color: tweetReadableAccentColor(
                        context,
                      ).withValues(alpha: 0.5),
                    ),
                    borderRadius: BorderRadius.circular(12),
                  ),
                  indicatorPadding: const EdgeInsets.symmetric(horizontal: 4),
                  labelColor: tweetReadableAccentColor(context),
                  unselectedLabelColor: tweetSecondaryColor(context),
                  labelStyle: tweetLabelStyle(context),
                  unselectedLabelStyle: tweetLabelStyle(
                    context,
                  ).copyWith(fontWeight: FontWeight.w500),
                  tabs: tabs,
                  onTap: onTap,
                ),
              ),
              DecoratedBox(
                decoration: BoxDecoration(
                  border: BorderDirectional(
                    start: BorderSide(
                      color: tweetDividerColor(context),
                      width: kTweetDividerThickness,
                    ),
                  ),
                ),
                child: SizedBox.square(
                  dimension: kTweetTouchTarget,
                  child: IconButton(
                    tooltip: addTooltip,
                    icon: const Icon(Icons.add),
                    onPressed: onAdd,
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

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

  const HomeFeedSwitcher({
    super.key,
    required this.selected,
    required this.options,
    required this.onSelected,
  });

  @override
  Widget build(BuildContext context) {
    final selectedOption = options.firstWhere(
      (option) => option.value == selected,
    );
    if (options.length == 1) {
      return Text(
        selectedOption.label,
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
      );
    }

    return PopupMenuButton<T>(
      initialValue: selected,
      onSelected: onSelected,
      tooltip: selectedOption.label,
      position: PopupMenuPosition.under,
      constraints: const BoxConstraints(minWidth: 200, maxWidth: 280),
      itemBuilder: (context) => options
          .map((option) => _menuItem(context, option, option.value == selected))
          .toList(growable: false),
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
            Icon(
              Icons.expand_more,
              size: kTweetActionIconSize,
              color: tweetSecondaryColor(context),
            ),
          ],
        ),
      ),
    );
  }

  PopupMenuItem<T> _menuItem(
    BuildContext context,
    HomeSwitcherOption<T> option,
    bool isSelected,
  ) {
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
                  ? Icon(
                      Icons.check,
                      size: kTweetActionIconSize,
                      color: tweetReadableAccentColor(context),
                    )
                  : null,
            ),
            const SizedBox(width: kTweetSpace2),
            Expanded(
              child: Text(
                option.label,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                  color: isSelected
                      ? tweetPrimaryColor(context)
                      : tweetSecondaryColor(context),
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

  const HomeNavigationItem({
    required this.label,
    required this.icon,
    required this.selectedIcon,
  });
}

/// Colours of the floating bar.
///
/// The pill is translucent, so the selected colour is checked against a
/// mid-tone page showing through it rather than against the bare background.
@immutable
class HomeNavigationPalette {
  final Color glass;
  final Color border;
  final Color shadow;
  final Color highlight;
  final Color selected;
  final Color unselected;

  const HomeNavigationPalette({
    required this.glass,
    required this.border,
    required this.shadow,
    required this.highlight,
    required this.selected,
    required this.unselected,
  });

  factory HomeNavigationPalette.of(BuildContext context) {
    final theme = Theme.of(context);
    final tokens = XLookTokens.maybeOf(context);
    final dark = theme.brightness == Brightness.dark;
    final ink = tweetPrimaryColor(context);
    final page = tokens?.background ?? theme.colorScheme.surface;
    final glass = _glassBase(theme, tokens).withValues(
      alpha: dark ? kHomeNavigationDarkGlass : kHomeNavigationLightGlass,
    );
    final accent = tweetAccentColor(context);
    final highlight = accent.withValues(alpha: dark ? 0.2 : 0.14);
    final showingThrough = Color.lerp(page, ink, 0.5)!;
    final underLabel = Color.alphaBlend(
      highlight,
      Color.alphaBlend(glass, showingThrough),
    );

    return HomeNavigationPalette(
      glass: glass,
      border: ink.withValues(alpha: dark ? 0.16 : 0.1),
      shadow: Colors.black.withValues(alpha: dark ? 0.5 : 0.12),
      highlight: highlight,
      selected: ensureContrast(accent, underLabel),
      unselected: ink,
    );
  }

  /// Light themes frost with the page's own surface; dark ones lift it, so the
  /// pill still reads as a surface over Lights Out's true black.
  static Color _glassBase(ThemeData theme, XLookTokens? tokens) {
    final dark = theme.brightness == Brightness.dark;
    if (tokens == null) {
      return dark
          ? theme.colorScheme.surfaceContainerHigh
          : theme.colorScheme.surface;
    }
    return dark ? xLookFloatingSurface(tokens) : tokens.card;
  }
}

/// Home's navigation: a frosted pill floating over the pages, which scroll on
/// behind it. The selected item sits on a soft accent highlight that spans its
/// icon and label.
class HomeNavigationBar extends StatelessWidget {
  final int selectedIndex;
  final List<HomeNavigationItem> items;
  final bool showLabels;
  final bool disableAnimations;
  final ValueChanged<int> onSelected;
  final ValueChanged<int>? onLongPress;
  final int longPressIndex;

  const HomeNavigationBar({
    super.key,
    required this.selectedIndex,
    required this.items,
    required this.showLabels,
    required this.disableAnimations,
    required this.onSelected,
    this.onLongPress,
    this.longPressIndex = 0,
  });

  @override
  Widget build(BuildContext context) {
    final reduceMotion =
        disableAnimations || MediaQuery.disableAnimationsOf(context);
    final palette = HomeNavigationPalette.of(context);

    return SafeArea(
      top: false,
      minimum: const EdgeInsets.only(bottom: kHomeNavigationFloatGap),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(
          kHomeNavigationFloatInset,
          kHomeNavigationFloatGap,
          kHomeNavigationFloatInset,
          0,
        ),
        child: _FrostedPill(
          palette: palette,
          height: homeNavigationHeight(showLabels: showLabels),
          child: Stack(
            children: [
              Positioned.fill(
                child: _highlight(context, palette, reduceMotion),
              ),
              NavigationBarTheme(
                data: _navigationTheme(context, palette),
                child: _navigationBar(context, reduceMotion),
              ),
            ],
          ),
        ),
      ),
    );
  }

  NavigationBar _navigationBar(BuildContext context, bool reduceMotion) {
    return NavigationBar(
      selectedIndex: selectedIndex,
      height: homeNavigationHeight(showLabels: showLabels),
      animationDuration: reduceMotion ? Duration.zero : null,
      labelBehavior: showLabels
          ? NavigationDestinationLabelBehavior.alwaysShow
          : NavigationDestinationLabelBehavior.alwaysHide,
      destinations: items
          .asMap()
          .entries
          .map(
            (entry) => _holdable(context, entry.key, entry.value, reduceMotion),
          )
          .toList(growable: false),
      onDestinationSelected: onSelected,
    );
  }

  Widget _holdable(
    BuildContext context,
    int index,
    HomeNavigationItem item,
    bool reduceMotion,
  ) {
    final holds = onLongPress != null && index == longPressIndex;
    return GestureDetector(
      behavior: HitTestBehavior.opaque,
      onLongPress: holds ? () => onLongPress!(index) : null,
      // Below NavigationBar's Material, which resets the default text style:
      // a long label ends in an ellipsis instead of wrapping out of the pill.
      child: DefaultTextStyle.merge(
        maxLines: 1,
        softWrap: false,
        overflow: TextOverflow.ellipsis,
        child: _destination(
          context,
          item,
          index == selectedIndex,
          reduceMotion,
          customLongPress: holds,
        ),
      ),
    );
  }

  /// One highlight that slides to the selected item.
  Widget _highlight(
    BuildContext context,
    HomeNavigationPalette palette,
    bool reduceMotion,
  ) {
    final count = items.length;
    if (count == 0) return const SizedBox.shrink();
    final index = selectedIndex.clamp(0, count - 1);

    return AnimatedAlign(
      alignment: AlignmentDirectional(
        count == 1 ? 0 : index * 2 / (count - 1) - 1,
        0,
      ),
      duration: reduceMotion
          ? Duration.zero
          : xtaMotionDuration(context, kXtaMotionNavigation),
      curve: Curves.easeOutCubic,
      child: FractionallySizedBox(
        widthFactor: 1 / count,
        heightFactor: 1,
        child: Padding(
          padding: const EdgeInsets.all(kHomeNavigationHighlightInset),
          child: DecoratedBox(
            key: const ValueKey('home-navigation-highlight'),
            decoration: BoxDecoration(
              color: palette.highlight,
              borderRadius: BorderRadius.circular(
                kHomeNavigationHighlightRadius,
              ),
            ),
          ),
        ),
      ),
    );
  }

  NavigationBarThemeData _navigationTheme(
    BuildContext context,
    HomeNavigationPalette palette,
  ) {
    final inherited = NavigationBarTheme.of(context);
    final fallbackLabel = Theme.of(context).textTheme.labelMedium;
    Color colorOf(Set<WidgetState> states) =>
        states.contains(WidgetState.selected)
        ? palette.selected
        : palette.unselected;

    return inherited.copyWith(
      backgroundColor: Colors.transparent,
      elevation: 0,
      shadowColor: Colors.transparent,
      surfaceTintColor: Colors.transparent,
      indicatorColor: Colors.transparent,
      indicatorShape: const StadiumBorder(),
      labelPadding: kHomeNavigationLabelPadding,
      labelTextStyle: WidgetStateProperty.resolveWith((states) {
        final style = inherited.labelTextStyle?.resolve(states);
        return (style ?? fallbackLabel)?.copyWith(
          fontSize: kHomeNavigationLabelSize,
          color: colorOf(states),
          fontWeight: states.contains(WidgetState.selected)
              ? FontWeight.w700
              : FontWeight.w500,
        );
      }),
      iconTheme: WidgetStateProperty.resolveWith((states) {
        final icon = inherited.iconTheme?.resolve(states);
        return (icon ?? const IconThemeData()).copyWith(
          color: colorOf(states),
          size: kHomeNavigationIconSize,
        );
      }),
    );
  }

  NavigationDestination _destination(
    BuildContext context,
    HomeNavigationItem item,
    bool selected,
    bool reduceMotion, {
    bool customLongPress = false,
  }) {
    final duration = reduceMotion
        ? Duration.zero
        : xtaMotionDuration(context, kXtaMotionStandard);
    final scale = selected ? 1.08 : 1.0;
    final icon = selected ? item.selectedIcon : item.icon;

    return NavigationDestination(
      tooltip: customLongPress ? '' : null,
      icon: AnimatedScale(
        scale: scale,
        duration: duration,
        curve: Curves.easeOutCubic,
        child: icon,
      ),
      selectedIcon: AnimatedScale(
        scale: scale,
        duration: duration,
        curve: Curves.easeOutCubic,
        child: icon,
      ),
      label: item.label,
    );
  }
}

/// The bar's surface: page content blurred and tinted through a rounded pill,
/// edged by a hairline and lifted by a shadow drawn only outside it.
class _FrostedPill extends StatelessWidget {
  final HomeNavigationPalette palette;
  final Widget child;

  final double height;

  const _FrostedPill({
    required this.palette,
    required this.height,
    required this.child,
  });

  BorderRadius get _radius => BorderRadius.circular(height / 2);

  @override
  Widget build(BuildContext context) {
    return CustomPaint(
      painter: _OuterShadowPainter(radius: _radius, color: palette.shadow),
      child: ClipRRect(
        borderRadius: _radius,
        child: BackdropFilter(
          filter: ImageFilter.blur(
            sigmaX: kHomeNavigationBlurSigma,
            sigmaY: kHomeNavigationBlurSigma,
          ),
          child: DecoratedBox(
            decoration: BoxDecoration(
              color: palette.glass,
              borderRadius: _radius,
              border: Border.all(
                color: palette.border,
                width: kTweetDividerThickness,
              ),
            ),
            child: child,
          ),
        ),
      ),
    );
  }
}

/// A box shadow kept off the pill's interior, where it would otherwise darken
/// the glass.
class _OuterShadowPainter extends CustomPainter {
  final BorderRadius radius;
  final Color color;

  const _OuterShadowPainter({required this.radius, required this.color});

  static const _shadow = BoxShadow(blurRadius: 24, offset: Offset(0, 4));

  @override
  void paint(Canvas canvas, Size size) {
    final pill = radius.toRRect(Offset.zero & size);
    final outside = Path()
      ..fillType = PathFillType.evenOdd
      ..addRect((Offset.zero & size).inflate(_shadow.blurRadius * 3))
      ..addRRect(pill);
    canvas
      ..save()
      ..clipPath(outside)
      ..drawRRect(
        pill.shift(_shadow.offset),
        _shadow.copyWith(color: color).toPaint(),
      )
      ..restore();
  }

  @override
  bool shouldRepaint(_OuterShadowPainter oldDelegate) =>
      oldDelegate.color != color || oldDelegate.radius != radius;
}

/// Keeps a page's own floating button clear of the floating bar.
///
/// With [Scaffold.extendBody] the bar's height reaches the pages as padding,
/// but a nested [Scaffold] places its floating button from view padding.
class HomeNavigationClearance extends StatelessWidget {
  final Widget child;

  const HomeNavigationClearance({super.key, required this.child});

  @override
  Widget build(BuildContext context) {
    final media = MediaQuery.of(context);
    final bottom = math.max(media.viewPadding.bottom, media.padding.bottom);
    return MediaQuery(
      data: media.copyWith(
        viewPadding: media.viewPadding.copyWith(bottom: bottom),
      ),
      child: child,
    );
  }
}

class HomeLoadingState extends StatelessWidget {
  const HomeLoadingState({super.key});

  @override
  Widget build(BuildContext context) {
    return XtaSystemBars(
      child: Scaffold(
        body: SafeArea(
          child: Center(
            child: SizedBox.square(
              dimension: kTweetTouchTarget,
              child: Padding(
                padding: const EdgeInsets.all(kTweetSpace3),
                child: CircularProgressIndicator(
                  strokeWidth: 2,
                  color: tweetReadableAccentColor(context),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
