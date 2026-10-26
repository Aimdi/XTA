import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:xta/tweet/tweet_chrome.dart';
import 'package:xta/ui/label_width.dart';
import 'package:xta/ui/x_look_theme.dart';

/// Keeps transparent edge-to-edge system bars legible for the active XTA theme.
class XtaSystemBars extends StatelessWidget {
  final Widget child;

  const XtaSystemBars({super.key, required this.child});

  @override
  Widget build(BuildContext context) {
    final isLight = Theme.of(context).brightness == Brightness.light;
    final iconBrightness = isLight ? Brightness.dark : Brightness.light;
    return AnnotatedRegion<SystemUiOverlayStyle>(
      value: SystemUiOverlayStyle(
        statusBarColor: Colors.transparent,
        statusBarIconBrightness: iconBrightness,
        statusBarBrightness: isLight ? Brightness.light : Brightness.dark,
        systemNavigationBarColor: Colors.transparent,
        systemNavigationBarIconBrightness: iconBrightness,
        systemStatusBarContrastEnforced: false,
        systemNavigationBarContrastEnforced: false,
      ),
      child: child,
    );
  }
}

/// The rounded bar under the selected name in Home's bar and in flat tab bars.
const double kReaderTabMarkerHeight = 3;

/// The space beside each name when a flat bar shares its width out evenly.
const double kReaderEvenTabPadding = kTweetSpace2;

/// A tab's name in Home's bar and in flat tab bars.
TextStyle readerTabNameStyle({required bool selected}) =>
    TextStyle(fontSize: 15, fontWeight: selected ? FontWeight.w700 : FontWeight.w500);

TextStyle _flatTabStyle(BuildContext context) => tweetLabelStyle(context).merge(readerTabNameStyle(selected: true));

/// Whether [names] fit a flat [ReaderTabBar] [width] wide that shares its width
/// out evenly, measured bold, the widest a name gets.
bool readerTabsFitEvenly(BuildContext context, List<String> names, {required double width}) {
  final style = _flatTabStyle(context);
  final widest = names.map((name) => labelsWidth(context, [name], style)).fold(0.0, math.max);
  return names.isNotEmpty && widest + 2 * kReaderEvenTabPadding <= width / names.length;
}

/// Shared content-navigation tabs used by reader surfaces such as Search and
/// Profile. Feature wrappers keep their existing public names and contracts.
class ReaderTabBar extends StatelessWidget implements PreferredSizeWidget {
  final TabController controller;
  final List<Widget> tabs;
  final double height;

  /// Draws the names a size up with a rounded marker under the selected one,
  /// as Home's bar does, in place of a line across the whole tab.
  final bool flat;

  /// Shares the width out evenly instead of scrolling from the start: for
  /// names that [readerTabsFitEvenly].
  final bool even;

  const ReaderTabBar({
    super.key,
    required this.controller,
    required this.tabs,
    this.height = 52,
    this.flat = false,
    this.even = false,
  });

  @override
  Size get preferredSize => Size.fromHeight(height);

  @override
  Widget build(BuildContext context) {
    final background =
        XLookTokens.maybeOf(context)?.background ??
        Theme.of(context).scaffoldBackgroundColor;
    final accent = tweetReadableAccentColor(context);
    final labelStyle = flat ? _flatTabStyle(context) : tweetLabelStyle(context);
    return SizedBox(
      height: height,
      child: DecoratedBox(
        decoration: BoxDecoration(
          color: background,
          border: Border(
            bottom: BorderSide(
              color: tweetDividerColor(context),
              width: kTweetDividerThickness,
            ),
          ),
        ),
        child: TabBar(
          controller: controller,
          isScrollable: !even,
          tabAlignment: even ? TabAlignment.fill : TabAlignment.start,
          labelPadding: even ? const EdgeInsets.symmetric(horizontal: kReaderEvenTabPadding) : null,
          indicatorSize: flat ? TabBarIndicatorSize.label : TabBarIndicatorSize.tab,
          indicator: flat
              ? UnderlineTabIndicator(
                  borderSide: BorderSide(width: kReaderTabMarkerHeight, color: accent),
                  borderRadius: BorderRadius.circular(kReaderTabMarkerHeight),
                )
              : null,
          dividerColor: Colors.transparent,
          indicatorColor: accent,
          labelColor: tweetPrimaryColor(context),
          unselectedLabelColor: tweetSecondaryColor(context),
          labelStyle: labelStyle,
          unselectedLabelStyle: labelStyle.copyWith(fontWeight: FontWeight.w500),
          tabs: tabs,
        ),
      ),
    );
  }
}
