import 'package:extended_image/extended_image.dart';
import 'package:flutter/material.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/plugins/reddit/reddit_subreddit_avatar.dart';
import 'package:xta/subscriptions/widgets/fallback_avatar.dart';

/// A group's members rendered as its cover art.
///
/// The layout adapts to how many members there are, so a small group never
/// looks like a broken large one: 4+ tile into a 2x2, 3 sit 2-over-1, 2 overlap,
/// 1 fills the block, and an empty group falls back to the group's own initial.
class AvatarMosaic extends StatelessWidget {
  /// Edge length of the whole mosaic block.
  final double extent;

  final List<GroupMemberPreview> members;

  /// Group name and colour, used for the empty-group placeholder.
  final String groupName;
  final Color groupColor;

  /// Theme accent, used to harmonise the fallback palette.
  final Color accent;

  /// Painted between overlapping circles so their edges stay separate.
  final Color ringColor;

  const AvatarMosaic({
    super.key,
    required this.extent,
    required this.members,
    required this.groupName,
    required this.groupColor,
    required this.accent,
    required this.ringColor,
  });

  static const _gap = 2.0;
  static const _ring = 1.5;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: extent,
      height: extent,
      child: switch (members.length) {
        0 => _empty(),
        1 => Center(child: _avatar(context, members[0], extent * 0.78)),
        2 => _pair(context),
        3 => _triple(context),
        _ => _quad(context),
      },
    );
  }

  /// A group with no members still gets a deliberate cover: its own colour and
  /// initial, in the same circular language as a member avatar.
  Widget _empty() {
    final diameter = extent * 0.78;
    final onColor =
        ThemeData.estimateBrightnessForColor(groupColor) == Brightness.dark ? Colors.white : Colors.black87;
    return Center(
      child: Container(
        width: diameter,
        height: diameter,
        decoration: BoxDecoration(color: groupColor, shape: BoxShape.circle),
        alignment: Alignment.center,
        child: Text(
          fallbackAvatarInitial(groupName, groupName),
          textScaler: TextScaler.noScaling,
          style: TextStyle(fontWeight: FontWeight.w700, fontSize: diameter * 0.46, color: onColor, height: 1.0),
        ),
      ),
    );
  }

  Widget _pair(BuildContext context) {
    final diameter = extent * 0.58;
    return Center(
      child: SizedBox(
        height: diameter + 2 * _ring,
        width: diameter * 2 - 8 + 2 * _ring,
        child: Stack(
          children: [
            Positioned(left: 0, child: _ringed(context, members[0], diameter)),
            Positioned(left: diameter - 8, child: _ringed(context, members[1], diameter)),
          ],
        ),
      ),
    );
  }

  Widget _triple(BuildContext context) {
    final diameter = (extent - _gap) / 2;
    return Column(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            _avatar(context, members[0], diameter),
            const SizedBox(width: _gap),
            _avatar(context, members[1], diameter),
          ],
        ),
        const SizedBox(height: _gap),
        _avatar(context, members[2], diameter),
      ],
    );
  }

  Widget _quad(BuildContext context) {
    final diameter = (extent - _gap) / 2;
    return Column(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        Row(children: [
          _avatar(context, members[0], diameter),
          const SizedBox(width: _gap),
          _avatar(context, members[1], diameter),
        ]),
        const SizedBox(height: _gap),
        Row(children: [
          _avatar(context, members[2], diameter),
          const SizedBox(width: _gap),
          _avatar(context, members[3], diameter),
        ]),
      ],
    );
  }

  Widget _ringed(BuildContext context, GroupMemberPreview member, double diameter) => Container(
        decoration: BoxDecoration(
          shape: BoxShape.circle,
          border: Border.all(color: ringColor, width: _ring),
        ),
        child: _avatar(context, member, diameter),
      );

  Widget _avatar(BuildContext context, GroupMemberPreview member, double diameter) {
    // A subreddit's picture is not a URL this app holds: it is fetched once and
    // cached, and falls back to a drawn tile. Its own widget knows all of that.
    final subreddit = member.subreddit;
    if (subreddit != null) {
      return ClipOval(child: RedditSubredditAvatar(subreddit: subreddit, size: diameter));
    }

    final url = member.avatarUrl;
    final fallback = FallbackAvatar(
      seed: member.id,
      displayName: member.name,
      size: diameter,
      accent: accent,
    );

    if (url == null || url.isEmpty) {
      return fallback;
    }

    // Decode at display size, not the ~400px source: a screen of tiles holds
    // dozens of avatars at once and full-size decodes would thrash the cache.
    final cachePx = (diameter * MediaQuery.devicePixelRatioOf(context)).round();

    return ClipOval(
      child: ExtendedImage.network(
        url,
        width: diameter,
        height: diameter,
        fit: BoxFit.cover,
        cache: true,
        retries: 2,
        cacheWidth: cachePx,
        cacheHeight: cachePx,
        loadStateChanged: (state) => switch (state.extendedImageLoadState) {
          // Showing the monogram while loading keeps the mosaic's geometry
          // stable — no blank hole, no layout shift when the image lands.
          LoadState.loading || LoadState.failed => fallback,
          LoadState.completed => null,
        },
      ),
    );
  }
}
