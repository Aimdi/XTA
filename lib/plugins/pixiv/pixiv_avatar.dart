import 'package:extended_image/extended_image.dart';
import 'package:flutter/material.dart';
import 'package:xta/plugins/pixiv/pixiv_image.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/subscriptions/widgets/fallback_avatar.dart';

/// A Pixiv creator's round avatar, decoded at the size it is painted, or their
/// initials when Pixiv sends no picture.
class PixivAvatar extends StatelessWidget {
  final int userId;
  final String name;
  final String? url;
  final double size;

  const PixivAvatar({super.key, required this.userId, required this.name, required this.url, this.size = 40});

  PixivAvatar.user(PixivUser user, {super.key, this.size = 40})
    : userId = user.id,
      name = user.name,
      url = user.avatarUrl;

  @override
  Widget build(BuildContext context) {
    final image = url;
    final pixels = (size * MediaQuery.devicePixelRatioOf(context)).ceil();
    return ClipOval(
      child: image == null || image.isEmpty
          ? _initials(context)
          : SizedBox.square(
              dimension: size,
              child: PixivNetworkImage(
                url: image,
                fit: BoxFit.cover,
                cacheWidth: pixels,
                cacheHeight: pixels,
                // A picture that will not load falls back to the initials too.
                loadStateChanged: (state) =>
                    state.extendedImageLoadState == LoadState.failed ? _initials(context) : null,
              ),
            ),
    );
  }

  Widget _initials(BuildContext context) =>
      FallbackAvatar(seed: '$userId', displayName: name, size: size, accent: Theme.of(context).colorScheme.primary);
}
