import 'package:flutter/material.dart';
import 'package:xta/plugins/plugin_home_chrome.dart';

/// Bottom inset so the last card clears the floating home pill.
///
/// Home uses `extendBody: true`, so a list that ends at the scaffold edge sits
/// under the capsule. Standalone plugin routes only need a short gutter.
const double kPluginHomeNavClearance = 88;
const double kPluginStandaloneGutter = 24;

/// Padding for a plugin timeline sitting under the home strip.
EdgeInsets pluginFeedPadding(
  BuildContext context, {
  EdgeInsets extra = EdgeInsets.zero,
}) {
  final bottom = PluginEmbedded.maybeOf(context)
      ? kPluginHomeNavClearance
      : kPluginStandaloneGutter;
  return EdgeInsets.only(bottom: bottom).add(extra) as EdgeInsets;
}

/// Scroll controller a plugin list should attach.
///
/// Embedded readers inherit the shell's [PrimaryScrollController]: Home's
/// fixed header supplies a direct reader controller, while nested hosts supply
/// their inner controller. A nested host must never attach its outer controller
/// to the reader as well. [PluginLazyTabs] mounts one pane at a time so sibling
/// sections cannot attach the same primary controller simultaneously.
ScrollController? pluginInnerScrollController(
  BuildContext context,
  ScrollController? requested,
) {
  if (PluginEmbedded.maybeOf(context)) {
    return PrimaryScrollController.maybeOf(context);
  }
  return requested;
}
