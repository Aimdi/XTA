import 'package:flutter/material.dart';
import 'package:xta/intro/intro_page_frame.dart';
import 'package:xta/plugins/plugin.dart';
import 'package:xta/plugins/plugin_brand.dart';
import 'package:xta/plugins/plugin_marks.dart';
import 'package:xta/plugins/x/x_plugin.dart' show pluginIdX;
import 'package:xta/tweet/tweet_chrome.dart';
import 'package:xta/ui/contrast.dart';
import 'package:xta/ui/x_look_theme.dart';

export 'package:xta/intro/intro_flow_illustration.dart';
export 'package:xta/intro/intro_orbit_illustration.dart';
export 'package:xta/intro/intro_phone_illustration.dart';

/// Placeholder rows need to read on the surface they sit on, and in Lights
/// Out the skeleton colour equals the floating surface, so the bone is lifted
/// from the surface itself instead of taken from the skeleton tokens.
Color introBoneColor(Color onSurface, Color surface) => Color.alphaBlend(onSurface.withValues(alpha: 0.16), surface);

/// A hairline that registers on [surface] in every theme.
Color introHairline(XLookTokens tokens, Color surface) => ensureContrast(tokens.border, surface, minRatio: 1.5);

/// A labelled picture: one node for TalkBack, nothing underneath.
class IntroIllustration extends StatelessWidget {
  final String label;
  final Widget child;

  const IntroIllustration({super.key, required this.label, required this.child});

  @override
  Widget build(BuildContext context) {
    return Semantics(
      label: label,
      image: true,
      child: ExcludeSemantics(child: child),
    );
  }
}

/// A chip holding a plugin's mark on the floating surface, with a hairline.
class IntroSourceChip extends StatelessWidget {
  final XtaPlugin plugin;
  final double size;

  const IntroSourceChip({super.key, required this.plugin, this.size = 44});

  @override
  Widget build(BuildContext context) {
    final tokens = introTokens(context);
    final surface = xLookFloatingSurface(tokens);
    final tint = plugin.id == pluginIdX ? null : readableBrandColor(context, plugin.brandColor);
    return Container(
      width: size,
      height: size,
      decoration: BoxDecoration(
        shape: BoxShape.circle,
        color: surface,
        border: Border.all(color: introHairline(tokens, surface), width: kTweetDividerThickness),
      ),
      child: Center(
        child: pluginMark(plugin, size: size * 0.55, color: tint),
      ),
    );
  }
}

/// A check on a coloured disc: the one "chosen" badge the swatches, the
/// source tiles and the kept-here phone share.
class IntroCheckBadge extends StatelessWidget {
  final double size;
  final Color color;

  const IntroCheckBadge({super.key, required this.size, required this.color});

  @override
  Widget build(BuildContext context) {
    return Container(
      width: size,
      height: size,
      decoration: BoxDecoration(color: color, shape: BoxShape.circle),
      child: Icon(Icons.check, size: size * 0.7, color: contrastingForeground(color)),
    );
  }
}
