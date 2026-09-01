import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:xta/tweet/tweet_chrome.dart';
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

/// Shared content-navigation tabs used by reader surfaces such as Search and
/// Profile. Feature wrappers keep their existing public names and contracts.
class ReaderTabBar extends StatelessWidget implements PreferredSizeWidget {
  final TabController controller;
  final List<Widget> tabs;
  final double height;

  const ReaderTabBar({
    super.key,
    required this.controller,
    required this.tabs,
    this.height = 52,
  });

  @override
  Size get preferredSize => Size.fromHeight(height);

  @override
  Widget build(BuildContext context) {
    final background =
        XLookTokens.maybeOf(context)?.background ??
        Theme.of(context).scaffoldBackgroundColor;
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
          isScrollable: true,
          tabAlignment: TabAlignment.start,
          indicatorSize: TabBarIndicatorSize.tab,
          dividerColor: Colors.transparent,
          indicatorColor: tweetAccentColor(context),
          labelColor: tweetPrimaryColor(context),
          unselectedLabelColor: tweetSecondaryColor(context),
          labelStyle: tweetLabelStyle(context),
          unselectedLabelStyle: tweetLabelStyle(
            context,
          ).copyWith(fontWeight: FontWeight.w500),
          tabs: tabs,
        ),
      ),
    );
  }
}
