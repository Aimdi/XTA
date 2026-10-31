import 'dart:math' as math;
import 'dart:ui' show ImageFilter;

import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/home/home_navigation_visibility.dart';
import 'package:xta/tweet/tweet_chrome.dart';
import 'package:xta/ui/contrast.dart';
import 'package:xta/ui/motion.dart';
import 'package:xta/ui/reader_chrome.dart';
import 'package:xta/ui/x_look_theme.dart';
import 'package:xta/ui/height_probe.dart';

/// The bottom bar's height above the system's gesture area, with or without
/// labels, as the Ink canvas draws it (docs/specs/feed-ink.md).
const double kHomeNavigationHeight = 64;

/// How much of the page's colour the bar is drawn in.
const double kHomeNavigationBarOpacity = 0.88;

/// How much of a floating pill's surface colour covers the page behind it.
const double kHomeNavigationLightGlass = 0.74;
const double kHomeNavigationDarkGlass = 0.78;
const double kHomeNavigationBlurSigma = 16;
const double kHomeNavigationLabelSize = 11;
const double kHomeNavigationIconSize = 26;
const EdgeInsets kHomeNavigationLabelPadding = EdgeInsets.fromLTRB(6, 2, 6, 0);

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

/// Colours of a frosted pill floating over the pages, such as a notice.
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

/// The colour under the bottom bar: the page's own, mostly opaque.
Color homeNavigationBarColor(BuildContext context) =>
    (XLookTokens.maybeOf(context)?.background ??
            Theme.of(context).colorScheme.surface)
        .withValues(alpha: kHomeNavigationBarOpacity);

/// Home's navigation, flat across the bottom as the Ink canvas draws it: the
/// page's colour with a hairline along its top, the icons in the text colour,
/// the selected one filled. The pages scroll on behind it.
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
    return DecoratedBox(
      key: const ValueKey('home-navigation-surface'),
      decoration: BoxDecoration(
        color: homeNavigationBarColor(context),
        border: Border(
          top: BorderSide(
            color: tweetDividerColor(context),
            width: kTweetDividerThickness,
          ),
        ),
      ),
      child: SafeArea(
        top: false,
        child: NavigationBarTheme(
          data: _navigationTheme(context),
          child: NavigationBar(
            selectedIndex: selectedIndex,
            height: kHomeNavigationHeight,
            animationDuration: reduceMotion ? Duration.zero : null,
            labelBehavior: showLabels
                ? NavigationDestinationLabelBehavior.alwaysShow
                : NavigationDestinationLabelBehavior.alwaysHide,
            destinations: [
              for (final (index, item) in items.indexed) _holdable(index, item),
            ],
            onDestinationSelected: onSelected,
          ),
        ),
      ),
    );
  }

  Widget _holdable(int index, HomeNavigationItem item) {
    final holds = onLongPress != null && index == longPressIndex;
    return GestureDetector(
      behavior: HitTestBehavior.opaque,
      onLongPress: holds ? () => onLongPress!(index) : null,
      // Below NavigationBar's Material, which resets the default text style:
      // a long label ends in an ellipsis instead of wrapping out of the bar.
      child: DefaultTextStyle.merge(
        maxLines: 1,
        softWrap: false,
        overflow: TextOverflow.ellipsis,
        child: NavigationDestination(
          tooltip: holds ? '' : null,
          icon: item.icon,
          selectedIcon: item.selectedIcon,
          label: item.label,
        ),
      ),
    );
  }

  NavigationBarThemeData _navigationTheme(BuildContext context) {
    final inherited = NavigationBarTheme.of(context);
    final fallbackLabel = Theme.of(context).textTheme.labelMedium;
    final ink = tweetPrimaryColor(context);

    return inherited.copyWith(
      backgroundColor: Colors.transparent,
      elevation: 0,
      shadowColor: Colors.transparent,
      surfaceTintColor: Colors.transparent,
      indicatorColor: Colors.transparent,
      labelPadding: kHomeNavigationLabelPadding,
      labelTextStyle: WidgetStateProperty.resolveWith((states) {
        final style = inherited.labelTextStyle?.resolve(states);
        return (style ?? fallbackLabel)?.copyWith(
          fontSize: kHomeNavigationLabelSize,
          color: ink,
          fontWeight: states.contains(WidgetState.selected)
              ? FontWeight.w700
              : FontWeight.w500,
        );
      }),
      iconTheme: WidgetStateProperty.resolveWith((states) {
        final icon = inherited.iconTheme?.resolve(states);
        return (icon ?? const IconThemeData()).copyWith(
          color: ink,
          size: kHomeNavigationIconSize,
        );
      }),
    );
  }
}

/// The bar's surface: page content blurred and tinted through a rounded pill,
/// edged by a hairline and lifted by a shadow drawn only outside it. Anything
/// else that floats over the pages wears it too, so it reads as the same layer.
class FrostedPill extends StatelessWidget {
  final HomeNavigationPalette palette;
  final Widget child;

  final double height;

  const FrostedPill({
    super.key,
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

/// Moves the bar with the store's motion: off the bottom edge as the reader
/// scrolls down, back as they scroll up, without changing the Scaffold's
/// layout. The pages keep their clearance, so nothing underneath moves, and
/// the translation carries hit testing along, so taps pass through where the
/// bar was. It only translates: fading the pill would drop its backdrop blur
/// the moment it left full opacity.
class HomeNavigationSlide extends StatefulWidget {
  final HomeNavigationVisibilityStore store;
  final Widget child;

  /// Past the edge by a quarter of its height so the outer shadow leaves too.
  static const double travelFactor = 1.25;

  const HomeNavigationSlide({
    super.key,
    required this.store,
    required this.child,
  });

  @override
  State<HomeNavigationSlide> createState() => _HomeNavigationSlideState();
}

class _HomeNavigationSlideState extends State<HomeNavigationSlide>
    with SingleTickerProviderStateMixin
    implements HomeNavigationMotion {
  @override
  late final AnimationController controller = AnimationController(vsync: this);

  late final Animation<Offset> _offset = controller.drive(
    Tween(
      begin: Offset.zero,
      end: const Offset(0, HomeNavigationSlide.travelFactor),
    ),
  );

  double _height = 0;

  @override
  double get travel => _height * HomeNavigationSlide.travelFactor;

  @override
  bool get reduceMotion => mounted && xtaReduceMotion(context);

  @override
  void initState() {
    super.initState();
    widget.store.attach(this);
  }

  @override
  void didUpdateWidget(HomeNavigationSlide oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (identical(oldWidget.store, widget.store)) return;
    oldWidget.store.detach(this);
    widget.store.attach(this);
  }

  @override
  void dispose() {
    widget.store.detach(this);
    controller.dispose();
    super.dispose();
  }

  void _measured(double height) => _height = height;

  @override
  Widget build(BuildContext context) {
    return HeightProbe(
      onHeight: _measured,
      child: SlideTransition(
        position: _offset,
        child: ScopedBuilder<HomeNavigationVisibilityStore, bool>(
          store: widget.store,
          onState: (context, shown) =>
              IgnorePointer(ignoring: !shown, child: widget.child),
        ),
      ),
    );
  }
}
