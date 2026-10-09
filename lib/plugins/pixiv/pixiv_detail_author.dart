import 'package:flutter/material.dart';
import 'package:xta/plugins/pixiv/pixiv_image.dart';
import 'package:xta/plugins/pixiv/pixiv_link_open.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/subscriptions/widgets/fallback_avatar.dart';

/// The work's author — avatar, name and @account — opening their profile.
class PixivDetailAuthor extends StatelessWidget {
  final PixivIllust illust;

  const PixivDetailAuthor({super.key, required this.illust});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return InkWell(
      onTap: () => openPixivUser(context, illust.userId),
      child: Row(
        children: [
          ClipOval(child: _avatar(context)),
          const SizedBox(width: 10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(illust.userName, style: theme.textTheme.titleSmall!.copyWith(fontWeight: FontWeight.w700)),
                Text(
                  '@${illust.userAccount}',
                  style: theme.textTheme.bodySmall!.copyWith(color: theme.colorScheme.onSurfaceVariant),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _avatar(BuildContext context) {
    const size = 40.0;
    final avatar = illust.userAvatarUrl;
    if (avatar == null) {
      return FallbackAvatar(
        seed: '${illust.userId}',
        displayName: illust.userName,
        size: size,
        accent: Theme.of(context).colorScheme.primary,
      );
    }
    final pixels = (size * MediaQuery.devicePixelRatioOf(context)).ceil();
    return SizedBox.square(
      dimension: size,
      child: PixivNetworkImage(url: avatar, fit: BoxFit.cover, cacheWidth: pixels, cacheHeight: pixels),
    );
  }
}
