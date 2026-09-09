import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/plugins/pixiv/pixiv_client.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';

class PixivUserStore extends Store<PixivUser?> {
  final PixivClient client;
  final int userId;
  bool followBusy = false;
  PixivUserStore(this.client, this.userId) : super(null);

  Future<void> load() => execute(() => client.userDetail(userId));

  Future<void> toggleFollow() async {
    final user = state;
    if (user == null || followBusy) return;
    followBusy = true;
    update(user, force: true);
    try {
      if (user.isFollowed) {
        await client.unfollowUser(user.id);
      } else {
        await client.followUser(user.id);
      }
      update(
        user.copyWith(
          isFollowed: !user.isFollowed,
          followersCount: (user.followersCount + (user.isFollowed ? -1 : 1)).clamp(0, 1 << 30),
        ),
      );
    } finally {
      followBusy = false;
      update(state, force: true);
    }
  }
}
