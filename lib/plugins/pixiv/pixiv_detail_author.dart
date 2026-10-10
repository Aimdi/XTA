import 'package:flutter/material.dart';
import 'package:xta/plugins/pixiv/pixiv_avatar.dart';
import 'package:xta/plugins/pixiv/pixiv_link_open.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';

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
          PixivAvatar(userId: illust.userId, name: illust.userName, url: illust.userAvatarUrl),
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
}
