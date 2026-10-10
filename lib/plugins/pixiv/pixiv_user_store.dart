import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/plugins/pixiv/pixiv_client.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_social_api.dart';
import 'package:xta/plugins/pixiv/pixiv_user_profile.dart';

class PixivUserStore extends Store<PixivUserProfile?> {
  final PixivSocialApi api;
  final int userId;
  PixivUserStore(this.api, this.userId) : super(null);

  Future<void> load() => execute(() => api.userProfile(userId));
}

/// Follows the reader changed in this session, and the ones still on their way.
typedef PixivFollows = ({Map<int, bool> followed, Set<int> busy});

/// One follow state for the whole app: every follow button for a creator reads
/// it, so a follow made on a card, a profile or a recycled row shows the same
/// everywhere, whatever the list it came from was told when it loaded.
class PixivFollowStore extends Store<PixivFollows> {
  final PixivClient client;

  PixivFollowStore(this.client) : super(_nothing);

  static const PixivFollows _nothing = (followed: <int, bool>{}, busy: <int>{});

  bool isFollowed(PixivUser user) => state.followed[user.id] ?? user.isFollowed;

  bool isBusy(int userId) => state.busy.contains(userId);

  /// Follows publicly or unfollows, and answers whether the reader now follows.
  /// A failure leaves the follow as it was and rethrows.
  Future<bool> toggle(PixivUser user) => isFollowed(user) ? unfollow(user) : follow(user);

  /// Follows [user], or moves an existing follow to [restrict] (`public` or `private`).
  Future<bool> follow(PixivUser user, {String restrict = 'public'}) =>
      _write(user, followed: true, send: () => client.followUser(user.id, restrict: restrict));

  Future<bool> unfollow(PixivUser user) => _write(user, followed: false, send: () => client.unfollowUser(user.id));

  Future<bool> _write(PixivUser user, {required bool followed, required Future<void> Function() send}) async {
    if (isBusy(user.id)) return isFollowed(user);
    _settle(user.id, busy: true);
    try {
      await send();
      _settle(user.id, followed: followed);
      return followed;
    } catch (_) {
      _settle(user.id);
      rethrow;
    }
  }

  void _settle(int userId, {bool? followed, bool busy = false}) => update((
    followed: followed == null ? state.followed : {...state.followed, userId: followed},
    busy: busy ? {...state.busy, userId} : state.busy.where((id) => id != userId).toSet(),
  ));

  /// Forgets every change, for a sign-out or an uninstall.
  void clear() => update(_nothing);
}
