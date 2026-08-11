import 'dart:ui';

import 'package:flutter/material.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/subscriptions/group_identity.dart';
import 'package:xta/subscriptions/widgets/avatar_mosaic.dart';
import 'package:xta/ui/group_board_tokens.dart';

/// One group on the board: a flat, hairline-bordered card whose identity is a
/// mosaic of its members' pictures, plus the group's colour as a left accent
/// bar and a faint wash.
///
/// Deliberately not a Material [Card] or [InkWell]: no elevation, no ripple —
/// depth comes from the border, feedback from a brief scale/opacity dip.
class GroupTile extends StatefulWidget {
  final SubscriptionGroup group;
  final VoidCallback onTap;
  final VoidCallback? onLongPress;

  /// Suppresses the press animation for the reduce-animations preference.
  final bool animate;

  const GroupTile({
    super.key,
    required this.group,
    required this.onTap,
    this.onLongPress,
    this.animate = true,
  });

  @override
  State<GroupTile> createState() => _GroupTileState();
}

class _GroupTileState extends State<GroupTile> {
  bool _pressed = false;

  void _setPressed(bool value) {
    if (_pressed != value && mounted) {
      setState(() => _pressed = value);
    }
  }

  @override
  Widget build(BuildContext context) {
    final tokens = GroupBoardTokens.resolve(context);
    final group = widget.group;
    // The group's own colour, unharmonised: X does not rotate a user's colour
    // towards a theme accent.
    final accentColor = group.color ?? groupFallbackColor(group.name);
    // A colour the reader chose has to be visible somewhere. Most tiles show a
    // mosaic of member faces, which left a chosen colour with nowhere to appear
    // at all — picking one did nothing. It now tints the tile's border and lays
    // the faintest wash over it. Only an explicit choice does this: tinting
    // every tile with its generated colour would be noise, not identity.
    final chosen = group.color;
    final l10n = L10n.of(context);
    final countLabel = l10n.subscription_group_member_count(
      group.numberOfMembers,
    );

    final tileColor = tokens.tile;
    final titleColor = GroupBoardTokens.ensureContrast(
      tokens.onSurface,
      tileColor,
    );
    final metaColor = GroupBoardTokens.ensureContrast(
      tokens.secondary,
      tileColor,
    );

    final tile = Container(
      decoration: BoxDecoration(
        color: chosen == null
            ? tileColor
            : Color.alphaBlend(chosen.withValues(alpha: 0.07), tileColor),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(
          color: chosen == null
              ? tokens.border
              : chosen.withValues(alpha: 0.55),
          width: 1,
        ),
      ),
      clipBehavior: Clip.antiAlias,
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Expanded(
            child: Padding(
              padding: const EdgeInsets.all(12),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisSize: MainAxisSize.min,
                children: [
                  Flexible(
                    child: LayoutBuilder(
                      builder: (context, constraints) {
                        // Never larger than the space actually left after the
                        // text, so a 2x font scale shrinks the cover instead
                        // of overflowing the tile.
                        final extent = constraints.biggest.shortestSide.clamp(
                          0.0,
                          74.0,
                        );

                        // A mark the user chose themselves outranks the
                        // member faces: they asked for that emoji or icon.
                        final Widget cover = hasExplicitGroupMark(group)
                            ? Center(
                                child: GroupMark.forGroup(
                                  group,
                                  size: extent * 0.78,
                                ),
                              )
                            : AvatarMosaic(
                                extent: extent,
                                members: group.memberPreviews,
                                groupName: group.name,
                                groupColor: accentColor,
                                accent: tokens.accent,
                                ringColor: tokens.tile,
                              );

                        // NSFW groups sit under Censored: blur the mosaic so
                        // the board does not advertise adult avatars at a glance.
                        if (!group.nsfw) {
                          return cover;
                        }
                        return ImageFiltered(
                          imageFilter: ImageFilter.blur(sigmaX: 8, sigmaY: 8),
                          child: cover,
                        );
                      },
                    ),
                  ),
                  const SizedBox(height: 10),
                  Row(
                    children: [
                      if (group.pinned) ...[
                        Icon(Icons.push_pin, size: 13, color: metaColor),
                        const SizedBox(width: 4),
                      ],
                      if (group.nsfw) ...[
                        Icon(
                          Icons.visibility_off_outlined,
                          size: 13,
                          color: metaColor,
                        ),
                        const SizedBox(width: 4),
                      ],
                      Expanded(
                        child: Text(
                          group.name,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: TextStyle(
                            fontSize: 16,
                            fontWeight: FontWeight.w700,
                            letterSpacing: -0.2,
                            color: titleColor,
                          ),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 2),
                  Text(
                    countLabel,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: TextStyle(
                      fontSize: 13,
                      fontWeight: FontWeight.w500,
                      color: metaColor,
                    ),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );

    return Semantics(
      // Composed from the already-translated count string rather than a new
      // compound key: "Name, 3 subscriptions" is grammatical in every locale,
      // and Semantics.button already announces the role.
      label: '${group.name}, $countLabel',
      button: true,
      child: ExcludeSemantics(
        child: RepaintBoundary(
          child: GestureDetector(
            behavior: HitTestBehavior.opaque,
            onTap: widget.onTap,
            onLongPress: widget.onLongPress,
            onTapDown: widget.animate ? (_) => _setPressed(true) : null,
            onTapUp: widget.animate ? (_) => _setPressed(false) : null,
            onTapCancel: widget.animate ? () => _setPressed(false) : null,
            child: AnimatedScale(
              scale: _pressed ? 0.97 : 1.0,
              duration: Duration(milliseconds: _pressed ? 120 : 160),
              curve: Curves.easeOut,
              child: AnimatedOpacity(
                opacity: _pressed ? 0.85 : 1.0,
                duration: Duration(milliseconds: _pressed ? 120 : 160),
                curve: Curves.easeOut,
                child: tile,
              ),
            ),
          ),
        ),
      ),
    );
  }
}
