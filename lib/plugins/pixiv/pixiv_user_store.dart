import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/plugins/pixiv/pixiv_client.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';

class PixivUserStore extends Store<PixivUser?> {
  final PixivClient client;
  final int userId;
  PixivUserStore(this.client, this.userId) : super(null);

  Future<void> load() => execute(() => client.userDetail(userId));

  /// Keeps the profile in step after its follow button changed the follow.
  void setFollowed(bool followed) {
    final user = state;
    if (user != null) update(user.copyWith(isFollowed: followed));
  }
}
