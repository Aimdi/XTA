import 'package:intl/intl.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/utils/json.dart';

/// The two kinds of work a creator's Works tab switches between, named as
/// `/v1/user/illusts` takes them.
enum PixivWorkType { illust, manga }

/// A creator's full profile from `/v1/user/detail`.
///
/// Fields the creator marked private in `profile_publicity` are left empty
/// even when Pixiv sends them, so nothing they chose to hide is shown.
class PixivUserProfile {
  final PixivUser user;
  final String? backgroundUrl;
  final int totalIllusts;
  final int totalManga;
  final int totalNovels;
  final int publicBookmarks;
  final String webpage;
  final String twitterAccount;
  final String twitterUrl;
  final String pawooUrl;
  final String gender;
  final String region;
  final String job;

  /// `MM-DD`, empty when unknown or private.
  final String birthDay;
  final int? birthYear;
  final bool isPremium;

  const PixivUserProfile({
    required this.user,
    this.backgroundUrl,
    this.totalIllusts = 0,
    this.totalManga = 0,
    this.totalNovels = 0,
    this.publicBookmarks = 0,
    this.webpage = '',
    this.twitterAccount = '',
    this.twitterUrl = '',
    this.pawooUrl = '',
    this.gender = '',
    this.region = '',
    this.job = '',
    this.birthDay = '',
    this.birthYear,
    this.isPremium = false,
  });

  factory PixivUserProfile.fromJson(Object? json) {
    final root = Json(json);
    final profile = root['profile'];
    final publicity = root['profile_publicity'];
    String shown(String field, {String? publicityKey}) =>
        _isPrivate(publicity[publicityKey ?? field]) ? '' : _text(profile[field]);
    return PixivUserProfile(
      user: PixivUser.fromDetailJson(json),
      backgroundUrl: _url(profile['background_image_url']),
      totalIllusts: profile['total_illusts'].integer ?? 0,
      totalManga: profile['total_manga'].integer ?? 0,
      totalNovels: profile['total_novels'].integer ?? 0,
      publicBookmarks: profile['total_illust_bookmarks_public'].integer ?? 0,
      webpage: _url(profile['webpage']) ?? '',
      twitterAccount: _text(profile['twitter_account']),
      twitterUrl: _url(profile['twitter_url']) ?? '',
      pawooUrl: publicity['pawoo'].boolean == false ? '' : _url(profile['pawoo_url']) ?? '',
      gender: shown('gender'),
      region: shown('region'),
      job: shown('job'),
      birthDay: shown('birth_day'),
      birthYear: _isPrivate(publicity['birth_year']) ? null : _year(profile['birth_year']),
      isPremium: profile['is_premium'].boolean == true,
    );
  }

  int get id => user.id;

  String get url => 'https://www.pixiv.net/users/$id';

  /// Name, @account and link, as Copy info puts them on the clipboard.
  String get infoText => '${user.name}\n@${user.account}\n$url';

  /// The tab a reader most likely came for: manga when there is more of it.
  PixivWorkType get defaultWorkType => totalManga > totalIllusts ? PixivWorkType.manga : PixivWorkType.illust;

  /// Whether both kinds have works, so the Works tab offers a switch.
  bool get hasBothWorkTypes => totalIllusts > 0 && totalManga > 0;

  /// The X link, built from the account when Pixiv sent no URL.
  String get twitterLink => twitterUrl.isNotEmpty || twitterAccount.isEmpty
      ? twitterUrl
      : 'https://x.com/${twitterAccount.replaceFirst('@', '')}';
}

/// What `profile_publicity` says for one field: only `private` hides it, since
/// Pixiv sends a My pixiv-only field only to readers allowed to see it.
bool _isPrivate(Json publicity) => publicity.string == 'private';

String _text(Json value) => value.string?.trim() ?? '';

String? _url(Json value) {
  final url = _text(value);
  return url.startsWith('https://') || url.startsWith('http://') ? url : null;
}

int? _year(Json value) => switch (value.integer) {
  final year? when year > 0 => year,
  _ => null,
};

/// The profile's birthday as the reader's language writes it, with only the
/// parts the creator made public; empty when neither part is.
String pixivBirthdayLabel(PixivUserProfile profile, String locale) {
  final parts = RegExp(r'^(\d{1,2})-(\d{1,2})$').firstMatch(profile.birthDay);
  final year = profile.birthYear;
  if (parts == null) return year == null ? '' : '$year';
  final month = int.parse(parts.group(1)!);
  final day = int.parse(parts.group(2)!);
  if (month < 1 || month > 12 || day < 1 || day > 31) return year == null ? '' : '$year';
  final date = DateTime(year ?? 2000, month, day);
  return year == null ? DateFormat.MMMMd(locale).format(date) : DateFormat.yMMMMd(locale).format(date);
}

/// Whether and how the reader follows a creator, from `/v1/user/follow/detail`.
class PixivFollowDetail {
  final bool isFollowed;

  /// `public` or `private`; empty when not followed.
  final String restrict;

  const PixivFollowDetail({required this.isFollowed, this.restrict = ''});

  factory PixivFollowDetail.fromJson(Object? json) {
    final detail = Json(json)['follow_detail'];
    return PixivFollowDetail(
      isFollowed: detail['is_followed'].boolean == true,
      restrict: detail['restrict'].string?.trim() ?? '',
    );
  }

  bool get isPrivate => restrict == 'private';
}

/// A profile as its screen shows it: whose it is, and whether the reader muted them.
class PixivProfileScope {
  final PixivUserProfile profile;

  /// The profile is the signed-in reader's own.
  final bool own;
  final bool muted;

  const PixivProfileScope({required this.profile, this.own = false, this.muted = false});
}
