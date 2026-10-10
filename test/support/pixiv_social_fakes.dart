import 'dart:math';

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
  int novels = 0,
  String comment = '',
  String? background,
  bool premium = false,
  String webpage = 'https://example.com/mika',
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
  totalNovels: novels,
  publicBookmarks: 12,
  webpage: webpage,
  twitterAccount: 'mika_draws',
  isPremium: premium,
);

/// Answers every profile call from fixtures and records what was asked. Lists
/// come in pages of [pageSize]; a later page is recorded as `<call>@next<n>`.
class FakePixivSocialApi extends PixivSocialApi {
  PixivUserProfile profile;
  final Map<PixivWorkType, List<PixivIllust>> works;
  final List<PixivIllust> bookmarks;
  final Map<String, List<PixivUserPreview>> following;
  final List<PixivUserPreview> followers;
  PixivFollowDetail followDetailResult;
  final int ownId;
  final int pageSize;

  /// Holds every answer asked for from now on back until completed, to leave a screen mid-load.
  Future<void>? gate;
  final calls = <String>[];

  FakePixivSocialApi({
    PixivUserProfile? profile,
    this.works = const {},
    this.bookmarks = const [],
    this.following = const {},
    this.followers = const [],
    this.followDetailResult = const PixivFollowDetail(isFollowed: false),
    this.ownId = 7,
    this.pageSize = 30,
    this.gate,
  }) : profile = profile ?? pixivProfileOf(),
       super(PixivClient(PrefServiceCache()));

  SingleChildWidget get provider => Provider<PixivSocialApi>.value(value: this);

  Future<T> _answer<T>(String call, T Function() answer, {String? nextUrl}) async {
    calls.add(nextUrl == null ? call : '$call@$nextUrl');
    await gate;
    return answer();
  }

  PixivPage<T> _page<T>(List<T> items, String? nextUrl) {
    final index = nextUrl == null ? 0 : int.parse(nextUrl.substring('next'.length));
    final end = min((index + 1) * pageSize, items.length);
    final start = min(index * pageSize, end);
    return PixivPage(items.sublist(start, end), nextUrl: end < items.length ? 'next${index + 1}' : null);
  }

  PixivIllustPage _illustPage(List<PixivIllust> illusts, String? nextUrl) {
    final page = _page(illusts, nextUrl);
    return PixivIllustPage(illusts: page.items, nextUrl: page.nextUrl);
  }

  @override
  Future<int> ownUserId() async => ownId;

  @override
  Future<PixivUserProfile> userProfile(int userId) => _answer('profile:$userId', () => profile);

  @override
  Future<PixivIllustPage> userWorks(int userId, PixivWorkType type, {String? nextUrl}) =>
      _answer('works:$userId:${type.name}', () => _illustPage(works[type] ?? const [], nextUrl), nextUrl: nextUrl);

  @override
  Future<PixivIllustPage> userBookmarks(int userId, {String restrict = 'public', String? tag, String? nextUrl}) =>
      _answer('bookmarks:$userId:$restrict', () => _illustPage(bookmarks, nextUrl), nextUrl: nextUrl);

  @override
  Future<PixivPage<PixivUserPreview>> userFollowing(int userId, {String restrict = 'public', String? nextUrl}) =>
      _answer('following:$userId:$restrict', () => _page(following[restrict] ?? const [], nextUrl), nextUrl: nextUrl);

  @override
  Future<PixivPage<PixivUserPreview>> userFollowers(int userId, {String? nextUrl}) =>
      _answer('followers:$userId', () => _page(followers, nextUrl), nextUrl: nextUrl);

  @override
  Future<PixivFollowDetail> followDetail(int userId) => _answer('followDetail:$userId', () => followDetailResult);
}
