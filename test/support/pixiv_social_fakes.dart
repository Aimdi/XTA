import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:provider/single_child_widget.dart';
import 'package:xta/plugins/pixiv/pixiv_client.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_social_api.dart';
import 'package:xta/plugins/pixiv/pixiv_user_profile.dart';

import 'pixiv_reader_harness.dart';

PixivUserPreview pixivPreviewOf(int id, {String? name, bool followed = false}) => PixivUserPreview(
  user: PixivUser(id: id, name: name ?? 'Painter $id', account: 'painter$id', comment: '', isFollowed: followed),
  illusts: [pixivWork(id: id * 100, pages: 1)],
);

PixivUserProfile pixivProfileOf({
  int id = 9,
  int illusts = 3,
  int manga = 0,
  String comment = '',
  String? background,
  bool premium = false,
}) => PixivUserProfile(
  user: PixivUser(
    id: id,
    name: 'Mika',
    account: 'mika',
    comment: comment,
    avatarUrl: 'https://i.pximg.net/user-profile/img/$id.jpg',
    worksCount: illusts + manga,
    followingCount: 1500,
    mypixivCount: 2,
  ),
  backgroundUrl: background,
  totalIllusts: illusts,
  totalManga: manga,
  publicBookmarks: 12,
  webpage: 'https://example.com/mika',
  twitterAccount: 'mika_draws',
  isPremium: premium,
);

/// Answers every profile call from fixtures and records what was asked.
class FakePixivSocialApi extends PixivSocialApi {
  PixivUserProfile profile;
  final Map<PixivWorkType, List<PixivIllust>> works;
  final List<PixivIllust> bookmarks;
  final Map<String, List<PixivUserPreview>> following;
  final List<PixivUserPreview> followers;
  PixivFollowDetail followDetailResult;
  final int ownId;
  final calls = <String>[];

  FakePixivSocialApi({
    PixivUserProfile? profile,
    this.works = const {},
    this.bookmarks = const [],
    this.following = const {},
    this.followers = const [],
    this.followDetailResult = const PixivFollowDetail(isFollowed: false),
    this.ownId = 7,
  }) : profile = profile ?? pixivProfileOf(),
       super(PixivClient(PrefServiceCache()));

  SingleChildWidget get provider => Provider<PixivSocialApi>.value(value: this);

  @override
  Future<int> ownUserId() async => ownId;

  @override
  Future<PixivUserProfile> userProfile(int userId) async {
    calls.add('profile:$userId');
    return profile;
  }

  @override
  Future<PixivIllustPage> userWorks(int userId, PixivWorkType type, {String? nextUrl}) async {
    calls.add('works:$userId:${type.name}');
    return PixivIllustPage(illusts: works[type] ?? const []);
  }

  @override
  Future<PixivIllustPage> userBookmarks(int userId, {String restrict = 'public', String? tag, String? nextUrl}) async {
    calls.add('bookmarks:$userId:$restrict');
    return PixivIllustPage(illusts: bookmarks);
  }

  @override
  Future<PixivPage<PixivUserPreview>> userFollowing(int userId, {String restrict = 'public', String? nextUrl}) async {
    calls.add('following:$userId:$restrict');
    return PixivPage(following[restrict] ?? const []);
  }

  @override
  Future<PixivPage<PixivUserPreview>> userFollowers(int userId, {String? nextUrl}) async {
    calls.add('followers:$userId');
    return PixivPage(followers);
  }

  @override
  Future<PixivFollowDetail> followDetail(int userId) async {
    calls.add('followDetail:$userId');
    return followDetailResult;
  }
}
