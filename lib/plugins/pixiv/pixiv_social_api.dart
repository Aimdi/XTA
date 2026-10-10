import 'package:flutter/widgets.dart';
import 'package:provider/provider.dart';
import 'package:xta/plugins/pixiv/pixiv_client.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_user_profile.dart';
import 'package:xta/utils/json.dart';

/// The two people lists a profile opens.
enum PixivUserListKind { following, followers }

/// Profiles, their works and bookmarks, follow lists and follow details.
class PixivSocialApi {
  final PixivClient client;

  const PixivSocialApi(this.client);

  /// A test's fake when one is provided, else the app's client.
  static PixivSocialApi of(BuildContext context) =>
      context.read<PixivSocialApi?>() ?? PixivSocialApi(context.read<PixivClient>());

  bool _isSelf(int userId) => userId == client.storedUserId;

  /// The signed-in reader's id, for their own lists.
  Future<int> ownUserId() => client.ensureUserId();

  Future<Object?> _firstOrNext(String path, Map<String, String> query, String? nextUrl) =>
      nextUrl == null ? client.getJson(path, query: query) : client.getNextJson(nextUrl);

  Future<PixivUserProfile> userProfile(int userId) async {
    final json = await client.getJson('/v1/user/detail', query: {'user_id': '$userId', 'filter': 'for_android'});
    final profile = PixivUserProfile.fromJson(json);
    if (profile.id == 0) {
      throw PixivException(PixivErrorKind.badResponse, 'empty user $userId');
    }
    return profile;
  }

  /// One kind of [userId]'s works; the reader's own keep everything they posted.
  Future<PixivIllustPage> userWorks(int userId, PixivWorkType type, {String? nextUrl}) =>
      client.userIllusts(userId, type: type, ownList: _isSelf(userId), nextUrl: nextUrl);

  /// [userId]'s bookmarks; someone else's follow the reader's R-18 and AI choices.
  Future<PixivIllustPage> userBookmarks(int userId, {String restrict = 'public', String? tag, String? nextUrl}) async {
    final json = await _firstOrNext('/v1/user/bookmarks/illust', {
      'user_id': '$userId',
      'restrict': restrict,
      if (tag != null && tag.isNotEmpty) 'tag': tag,
      'filter': 'for_android',
    }, nextUrl);
    return client.illustPageFrom(json, ownList: _isSelf(userId));
  }

  /// Who [userId] follows; [restrict] `private` is only answered for the reader.
  Future<PixivPage<PixivUserPreview>> userFollowing(int userId, {String restrict = 'public', String? nextUrl}) async {
    final json = await _firstOrNext('/v1/user/following', {
      'user_id': '$userId',
      'restrict': restrict,
      'filter': 'for_android',
    }, nextUrl);
    return _previewPage(json);
  }

  Future<PixivPage<PixivUserPreview>> userFollowers(int userId, {String? nextUrl}) async {
    final json = await _firstOrNext('/v1/user/follower', {
      'user_id': '$userId',
      'restrict': 'public',
      'filter': 'for_android',
    }, nextUrl);
    return _previewPage(json);
  }

  /// One page of the [kind] list for [userId].
  Future<PixivPage<PixivUserPreview>> userList(
    PixivUserListKind kind,
    int userId, {
    String restrict = 'public',
    String? nextUrl,
  }) => switch (kind) {
    PixivUserListKind.following => userFollowing(userId, restrict: restrict, nextUrl: nextUrl),
    PixivUserListKind.followers => userFollowers(userId, nextUrl: nextUrl),
  };

  Future<PixivFollowDetail> followDetail(int userId) async {
    final json = await client.getJson('/v1/user/follow/detail', query: {'user_id': '$userId'});
    return PixivFollowDetail.fromJson(json);
  }

  PixivPage<PixivUserPreview> _previewPage(Object? json) => PixivPage(
    parsePixivUserPreviews(json, includeR18: client.showR18, includeAi: !client.hideAi),
    nextUrl: Json(json)['next_url'].string,
  );
}
