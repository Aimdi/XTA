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
  Future<bool> toggle(PixivUser user) async {
    final was = isFollowed(user);
    if (isBusy(user.id)) return was;
    _settle(user.id, busy: true);
    try {
      await (was ? client.unfollowUser(user.id) : client.followUser(user.id));
      _settle(user.id, followed: !was);
      return !was;
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
