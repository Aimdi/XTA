import 'package:flutter/material.dart';
import 'package:xta/plugins/plugin_brand.dart';
import 'package:xta/plugins/plugin_marks.dart';
import 'package:xta/plugins/plugin_registry.dart';
import 'package:xta/tweet/interleaved_items.dart';
import 'package:xta/tweet/tweet_chrome.dart';
import 'package:xta/ui/contrast.dart';

/// The network's badge on a post's avatar: the disc, its ring and how far it hangs past the
/// avatar's corner (docs/specs/feed-ink.md).
const double kProvenanceBadgeSize = 18;
const double kProvenanceBadgeRing = 2;
const double kProvenanceBadgeOverhang = 4;

/// Brand tint for a plugin's provenance strip in a mixed feed.
Color provenanceAccentColor(BuildContext context, String pluginId) {
  final plugin = pluginById(pluginId);
  final brand = plugin?.brandColor ?? Theme.of(context).colorScheme.primary;
  final readable = readableBrandColor(context, brand);
  final surface = Theme.of(context).colorScheme.surface;
  return ensureContrast(readable, surface, minRatio: 2);
}

/// Misskey-style 2px leading strip marking where an interleaved card came from.
Widget provenanceAccent({required BuildContext context, required Color color, required Widget child}) {
  return DecoratedBox(
    decoration: BoxDecoration(
      border: Border(left: BorderSide(color: color, width: 2)),
    ),
    child: child,
  );
}

/// An [InterleavedItem] that says where its post came from: [avatarBadge] when the card draws a
/// [ProvenanceAvatarBadge] on its avatar, the plugin's strip down its edge when it has no avatar.
InterleavedItem provenanceInterleavedItem({
  required DateTime date,
  required String pluginId,
  required WidgetBuilder build,
  String? id,
  String? linkUrl,
  Map<String, dynamic>? snapshot,
  bool avatarBadge = false,
}) {
  return InterleavedItem(
    date: date,
    id: id, source: pluginId, linkUrl: linkUrl, snapshot: snapshot,
    build: avatarBadge
        ? build
        : (context) =>
              provenanceAccent(context: context, color: provenanceAccentColor(context, pluginId), child: build(context)),
  );
}

/// The network a post came from, on its avatar in a mixed timeline: a disc in the network's colour
/// with its mark, ringed in the card's colour, on the avatar's bottom corner at the end of the line.
/// A screen reader hears the network's name. [show] off, or an unknown plugin, leaves [child] alone.
class ProvenanceAvatarBadge extends StatelessWidget {
  final String pluginId;
  final Widget child;
  final bool show;

  const ProvenanceAvatarBadge({super.key, required this.pluginId, required this.child, this.show = true});

  @override
  Widget build(BuildContext context) {
    final plugin = pluginById(pluginId);
    if (!show || plugin == null) return child;
    final fill = provenanceAccentColor(context, pluginId);
    return Stack(
      clipBehavior: Clip.none,
      children: [
        child,
        PositionedDirectional(
          end: -kProvenanceBadgeOverhang,
          bottom: -kProvenanceBadgeOverhang,
          child: Semantics(
            label: plugin.title(context),
            child: Container(
              key: ValueKey('provenance-badge-$pluginId'),
              width: kProvenanceBadgeSize,
              height: kProvenanceBadgeSize,
              decoration: BoxDecoration(
                color: fill,
                shape: BoxShape.circle,
                border: Border.all(color: tweetSurfaceColor(context), width: kProvenanceBadgeRing),
              ),
              alignment: Alignment.center,
              child: ExcludeSemantics(
                child: pluginMark(
                  plugin,
                  size: kProvenanceBadgeSize - 2 * kProvenanceBadgeRing,
                  color: contrastingForeground(fill),
                ),
              ),
            ),
          ),
        ),
      ],
    );
  }
}
