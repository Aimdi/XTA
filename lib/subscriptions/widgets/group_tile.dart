import 'dart:ui';

import 'package:flutter/material.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/subscriptions/group_board_layout.dart';
import 'package:xta/subscriptions/group_identity.dart';
import 'package:xta/subscriptions/widgets/avatar_mosaic.dart';
import 'package:xta/subscriptions/widgets/group_unread_badge.dart';
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
  final VoidCallback? onDiscover;

  /// Suppresses the press animation for the reduce-animations preference.
  final bool animate;

  /// Newer cached posts than the last-read mark. Tests omit this.
  final bool unread;

  /// Lays the tile out sideways, the faces beside the name and count: the
  /// board's Kompakt choice.
  final bool compact;

  const GroupTile({
    super.key,
    required this.group,
    required this.onTap,
    this.onLongPress,
    this.onDiscover,
    this.animate = true,
    this.unread = false,
    this.compact = false,
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
    final metaColor = GroupBoardTokens.ensureContrast(
      tokens.secondary,
      tileColor,
    );
    final name = _name(
      GroupBoardTokens.ensureContrast(tokens.onSurface, tileColor),
      metaColor,
    );
    final count = Text(
      countLabel,
      maxLines: 1,
      overflow: TextOverflow.ellipsis,
      style: TextStyle(
        fontSize: 13,
        fontWeight: FontWeight.w500,
        color: metaColor,
      ),
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
      child: widget.compact
          ? _compactContent(tokens, name, count)
          : _coverContent(tokens, name, count),
    );

    final accessibleTile = Semantics(
      // Composed from the already-translated count string rather than a new
      // compound key: "Name, 3 subscriptions" is grammatical in every locale,
      // and Semantics.button already announces the role.
      label: widget.unread
          ? '${group.name}, $countLabel, ${l10n.group_has_unread}'
          : '${group.name}, $countLabel',
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
                child: GroupUnreadBadge(
                  unread: widget.unread,
                  inset: 8,
                  child: tile,
                ),
              ),
            ),
          ),
        ),
      ),
    );
    if (widget.onDiscover == null) return accessibleTile;
    final discover = IconButton(
      tooltip: l10n.group_discovery_ai,
      onPressed: widget.onDiscover,
      icon: const Icon(Icons.auto_awesome, size: 20),
    );
    return Stack(
      children: [
        Positioned.fill(child: accessibleTile),
        if (widget.compact)
          PositionedDirectional(
            top: 0,
            bottom: 0,
            end: 2,
            child: Center(child: discover),
          )
        else
          PositionedDirectional(top: 2, end: 2, child: discover),
      ],
    );
  }

  /// The large tile: the cover above the name and count.
  Widget _coverContent(GroupBoardTokens tokens, Widget name, Widget count) =>
      Row(
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
                      // Never larger than the space actually left after the
                      // text, so a 2x font scale shrinks the cover instead of
                      // overflowing the tile.
                      builder: (context, constraints) => _cover(
                        tokens,
                        constraints.biggest.shortestSide.clamp(0.0, 74.0),
                      ),
                    ),
                  ),
                  const SizedBox(height: 10),
                  name,
                  const SizedBox(height: 2),
                  count,
                ],
              ),
            ),
          ),
        ],
      );

  /// The Kompakt tile: the cover at the start, the name and count beside it,
  /// clear of the discovery button when there is one.
  Widget _compactContent(GroupBoardTokens tokens, Widget name, Widget count) =>
      Padding(
        padding: EdgeInsetsDirectional.fromSTEB(
          10,
          6,
          widget.onDiscover == null ? 10 : 48,
          6,
        ),
        child: Row(
          children: [
            _cover(tokens, kCompactGroupCoverExtent),
            const SizedBox(width: 10),
            Expanded(
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [name, const SizedBox(height: 1), count],
              ),
            ),
          ],
        ),
      );

  /// The member faces, or the mark the reader chose, at [extent].
  Widget _cover(GroupBoardTokens tokens, double extent) {
    final group = widget.group;
    // A mark the user chose themselves outranks the member faces: they asked
    // for that emoji or icon.
    final Widget cover = hasExplicitGroupMark(group)
        ? Center(child: GroupMark.forGroup(group, size: extent * 0.78))
        : AvatarMosaic(
            extent: extent,
            members: group.memberPreviews,
            groupName: group.name,
            // The group's own colour, unharmonised: X does not rotate a user's
            // colour towards a theme accent.
            groupColor: group.color ?? groupFallbackColor(group.name),
            accent: tokens.accent,
            ringColor: tokens.tile,
          );

    // NSFW groups sit under Censored: blur the mosaic so the board does not
    // advertise adult avatars at a glance.
    if (!group.nsfw) {
      return cover;
    }
    return ImageFiltered(
      imageFilter: ImageFilter.blur(sigmaX: 8, sigmaY: 8),
      child: cover,
    );
  }

  /// The name, after the pinned and NSFW marks.
  Widget _name(Color titleColor, Color metaColor) {
    final group = widget.group;
    return Row(
      children: [
        if (group.pinned) ...[
          Icon(Icons.push_pin, size: 13, color: metaColor),
          const SizedBox(width: 4),
        ],
        if (group.nsfw) ...[
          Icon(Icons.visibility_off_outlined, size: 13, color: metaColor),
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
    );
  }
}
