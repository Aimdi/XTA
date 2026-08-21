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
/// [GroupFeedShell] already owns [requested] as the NestedScrollView *outer*
/// controller. Giving that same object to an inner ListView paints the first
/// card under the pinned tab strip. When embedded, the list uses the inner
/// [PrimaryScrollController] NestedScrollView injects.
ScrollController? pluginInnerScrollController(
  BuildContext context,
  ScrollController? requested,
) {
  if (PluginEmbedded.maybeOf(context)) {
    return PrimaryScrollController.maybeOf(context);
  }
  return requested;
}
