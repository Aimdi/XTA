import 'package:flutter/material.dart';
import 'package:xta/plugins/pixiv/pixiv_avatar.dart';
import 'package:xta/plugins/pixiv/pixiv_link_open.dart';

/// A creator's avatar and one-line name, a full-height touch target that
/// opens their profile; inert when the id is unknown.
class PixivUserLink extends StatelessWidget {
  final int userId;
  final String name;
  final String? avatarUrl;
  final double avatarSize;
  final TextStyle? style;
  final EdgeInsetsGeometry padding;
  final BorderRadius? borderRadius;

  const PixivUserLink({
    super.key,
    required this.userId,
    required this.name,
    required this.avatarUrl,
    this.avatarSize = 32,
    this.style,
    this.padding = EdgeInsets.zero,
    this.borderRadius,
  });

  @override
  Widget build(BuildContext context) => InkWell(
    borderRadius: borderRadius,
    onTap: userId == 0 ? null : () => openPixivUser(context, userId),
    child: ConstrainedBox(
      constraints: const BoxConstraints(minHeight: kMinInteractiveDimension),
      child: Padding(
        padding: padding,
        child: Row(
          spacing: 10,
          children: [
            PixivAvatar(userId: userId, name: name, url: avatarUrl, size: avatarSize),
            Flexible(
              child: Text(name, maxLines: 1, overflow: TextOverflow.ellipsis, style: style),
            ),
          ],
        ),
      ),
    ),
  );
}
