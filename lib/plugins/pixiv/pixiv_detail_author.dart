import 'package:flutter/material.dart';
import 'package:xta/plugins/pixiv/pixiv_avatar.dart';
import 'package:xta/plugins/pixiv/pixiv_link_open.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_user_card.dart';

/// The creator of [illust] as a follow button sees them.
PixivUser pixivIllustAuthor(PixivIllust illust) => PixivUser(
  id: illust.userId,
  name: illust.userName,
  account: illust.userAccount,
  comment: '',
  avatarUrl: illust.userAvatarUrl,
  isFollowed: illust.userIsFollowed,
);

/// The work's author — avatar, name and @account opening their profile — with
/// the follow button beside them.
class PixivDetailAuthor extends StatelessWidget {
  final PixivIllust illust;

  const PixivDetailAuthor({super.key, required this.illust});

  @override
  Widget build(BuildContext context) => PixivFollowHeader(
    user: pixivIllustAuthor(illust),
    identityChrome: 40 + 10 + 8,
    identity: InkWell(
      key: const ValueKey('pixiv-detail-author'),
      onTap: () => openPixivUser(context, illust.userId),
      child: ConstrainedBox(
        constraints: const BoxConstraints(minHeight: kMinInteractiveDimension),
        child: Row(
          children: [
            PixivAvatar(userId: illust.userId, name: illust.userName, url: illust.userAvatarUrl),
            const SizedBox(width: 10),
            Expanded(
              child: PixivUserNames(name: illust.userName, account: illust.userAccount),
            ),
          ],
        ),
      ),
    ),
  );
}
