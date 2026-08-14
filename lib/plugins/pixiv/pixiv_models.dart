import 'package:html/parser.dart' as html_parser;
import 'package:xta/utils/json.dart';

/// Pixiv captions are HTML fragments — cards and the viewer want plain text.
String pixivCaptionToText(String? caption) {
  final raw = caption?.trim() ?? '';
  if (raw.isEmpty) {
    return '';
  }
  return (html_parser.parseFragment(raw).text ?? raw).trim();
}

/// Headers Pixiv CDN requires before it will serve an image.
const pixivImageHeaders = <String, String>{
  'Referer': 'https://www.pixiv.net/',
  'User-Agent': 'Mozilla/5.0',
};

/// A tag on an illust — translated name when Pixiv sent one.
class PixivTag {
  final String name;
  final String? translatedName;

  const PixivTag({required this.name, this.translatedName});

  String get displayName {
    final translated = translatedName?.trim();
    if (translated != null && translated.isNotEmpty) {
      return translated;
    }
    return name;
  }
}

/// One illustration card / viewer worth of fields from `app-api.pixiv.net`.
class PixivIllust {
  final int id;
  final String title;
  final String caption;
  final String type;
  final String thumbnailUrl;
  final String? largeUrl;
  final List<String> pageUrls;
  final List<PixivTag> tags;
  final int pageCount;
  final int width;
  final int height;
  final int userId;
  final String userName;
  final String userAccount;
  final String? userAvatarUrl;
  final DateTime? createdAt;
  final int totalBookmarks;
  final int totalViews;
  final bool isR18;
  final bool isBookmarked;

  const PixivIllust({
    required this.id,
    required this.title,
    required this.caption,
    required this.type,
    required this.thumbnailUrl,
    required this.pageCount,
    required this.userId,
    required this.userName,
    required this.userAccount,
    this.largeUrl,
    this.pageUrls = const [],
    this.tags = const [],
    this.width = 0,
    this.height = 0,
    this.userAvatarUrl,
    this.createdAt,
    this.totalBookmarks = 0,
    this.totalViews = 0,
    this.isR18 = false,
    this.isBookmarked = false,
  });

  PixivIllust copyWith({bool? isBookmarked, int? totalBookmarks}) =>
      PixivIllust(
        id: id,
        title: title,
        caption: caption,
        type: type,
        thumbnailUrl: thumbnailUrl,
        largeUrl: largeUrl,
        pageUrls: pageUrls,
        tags: tags,
        pageCount: pageCount,
        width: width,
        height: height,
        userId: userId,
        userName: userName,
        userAccount: userAccount,
        userAvatarUrl: userAvatarUrl,
        createdAt: createdAt,
        totalBookmarks: totalBookmarks ?? this.totalBookmarks,
        totalViews: totalViews,
        isR18: isR18,
        isBookmarked: isBookmarked ?? this.isBookmarked,
      );

  String get url => 'https://www.pixiv.net/artworks/$id';
  String get userUrl => 'https://www.pixiv.net/users/$userId';

  bool get isManga => pageCount > 1 || type == 'manga';
  bool get isUgoira => type == 'ugoira';

  /// Aspect ratio for staggered grids — falls back to square when unknown.
  double get aspectRatio {
    if (width > 0 && height > 0) {
      return width / height;
    }
    return 1;
  }

  /// Full-size pages for the in-app viewer (manga page URLs, else large/thumb).
  List<String> get viewerUrls {
    if (pageUrls.isNotEmpty) {
      return pageUrls;
    }
    final large = largeUrl;
    if (large != null && large.isNotEmpty) {
      return [large];
    }
    return [thumbnailUrl];
  }
}

/// Who the refresh token belongs to, as the token response reports it.
class PixivAuthUser {
  final int id;
  final String name;
  final String account;

  const PixivAuthUser({
    required this.id,
    required this.name,
    required this.account,
  });

  String get displayName => name.isEmpty ? account : name;
}

/// A Pixiv user profile from `/v1/user/detail` or search.
class PixivUser {
  final int id;
  final String name;
  final String account;
  final String? avatarUrl;
  final String comment;
  final int illustsCount;
  final int followersCount;

  /// Whether the signed-in account already follows this user.
  final bool isFollowed;

  const PixivUser({
    required this.id,
    required this.name,
    required this.account,
    required this.comment,
    this.avatarUrl,
    this.illustsCount = 0,
    this.followersCount = 0,
    this.isFollowed = false,
  });

  PixivUser copyWith({bool? isFollowed, int? followersCount}) => PixivUser(
    id: id,
    name: name,
    account: account,
    comment: comment,
    avatarUrl: avatarUrl,
    illustsCount: illustsCount,
    followersCount: followersCount ?? this.followersCount,
    isFollowed: isFollowed ?? this.isFollowed,
  );

  factory PixivUser.fromDetailJson(Object? json) {
    final root = Json(json);
    final user = root['user'];
    final profile = root['profile'];
    return _userFromJson(user, profile: profile);
  }

  factory PixivUser.fromUserJson(Object? json) => _userFromJson(Json(json));
}

PixivUser _userFromJson(Json user, {Json? profile}) {
  final avatar = user['profile_image_urls']['medium'].string;
  return PixivUser(
    id: user['id'].integer ?? 0,
    name: user['name'].string?.trim() ?? '',
    account: user['account'].string?.trim() ?? '',
    avatarUrl: avatar == null || avatar.isEmpty ? null : avatar,
    comment: user['comment'].string?.trim() ?? '',
    illustsCount:
        profile?['total_illusts'].integer ??
        profile?['total_illust_series'].integer ??
        0,
    followersCount: profile?['total_follower'].integer ?? 0,
    isFollowed: user['is_followed'].raw == true,
  );
}

bool pixivIsR18(Json illust) {
  final xRestrict = illust['x_restrict'].integer ?? 0;
  final sanity = illust['sanity_level'].integer ?? 0;
  return xRestrict > 0 || sanity >= 6;
}

/// Whether [url] is Pixiv's stand-in for a deleted / restricted work.
///
/// Those PNGs load fine (so the grid does not show a broken-image icon) and
/// carry Japanese copy like "削除済み もしくは 非公開" — treating them as
/// real thumbnails filled bookmarks with blank placeholders.
bool pixivIsLimitPlaceholderUrl(String? url) {
  if (url == null || url.isEmpty) {
    return false;
  }
  final lower = url.toLowerCase();
  return lower.contains('limit_unknown') ||
      lower.contains('limit_r18') ||
      lower.contains('limit_sanity') ||
      lower.contains('/common/images/limit');
}

/// Whether the listing entry is a real, viewable work for this account.
bool pixivIllustIsAccessible(Json illust) {
  if (illust['visible'].boolean == false) {
    return false;
  }
  return !pixivIsLimitPlaceholderUrl(_firstImageUrl(illust));
}

/// Waterfall thumb — prefer aspect-preserving `medium` (Pixez-style), not the
/// cropped `square_medium` Pixiv also sends.
String? _firstImageUrl(Json illust) {
  final urls = illust['image_urls'];
  return urls['medium'].string ??
      urls['square_medium'].string ??
      urls['large'].string ??
      illust['meta_single_page']['original_image_url'].string;
}

String? _largeImageUrl(Json illust) {
  return illust['image_urls']['large'].string ??
      illust['meta_single_page']['original_image_url'].string ??
      _firstImageUrl(illust);
}

/// Viewer page — prefer `large` over multi‑MB `original` for browse speed.
String? _pageImageUrl(Json page) {
  final urls = page['image_urls'];
  return urls['large'].string ??
      urls['medium'].string ??
      urls['original'].string ??
      urls['square_medium'].string;
}

List<String> _pageUrlsOf(Json illust) {
  final pages = illust['meta_pages'].list;
  if (pages.isNotEmpty) {
    return [for (final page in pages) ?_pageImageUrl(page)];
  }

  final single =
      _largeImageUrl(illust) ??
      illust['meta_single_page']['original_image_url'].string;
  return single == null || single.isEmpty ? const [] : [single];
}

List<PixivTag> _tagsOf(Json illust) {
  return [
    for (final tag in illust['tags'].list)
      if ((tag['name'].string ?? '').trim() case final name
          when name.isNotEmpty)
        PixivTag(
          name: name,
          translatedName: tag['translated_name'].string?.trim(),
        ),
  ];
}

/// One illust object → [PixivIllust], or null when unusable.
PixivIllust? pixivIllustFromJson(Object? json) {
  final data = Json(json);
  final id = data['id'].integer;
  final thumb = _firstImageUrl(data);
  if (id == null ||
      thumb == null ||
      thumb.isEmpty ||
      !pixivIllustIsAccessible(data)) {
    return null;
  }

  final user = data['user'];
  final avatar = user['profile_image_urls']['medium'].string;

  return PixivIllust(
    id: id,
    title: data['title'].string?.trim() ?? '',
    caption: pixivCaptionToText(data['caption'].string),
    type: data['type'].string ?? 'illust',
    thumbnailUrl: thumb,
    largeUrl: _largeImageUrl(data),
    pageUrls: _pageUrlsOf(data),
    tags: _tagsOf(data),
    pageCount: data['page_count'].integer ?? 1,
    width: data['width'].integer ?? 0,
    height: data['height'].integer ?? 0,
    userId: user['id'].integer ?? 0,
    userName: user['name'].string?.trim() ?? '',
    userAccount: user['account'].string?.trim() ?? '',
    userAvatarUrl: avatar == null || avatar.isEmpty ? null : avatar,
    createdAt: DateTime.tryParse(data['create_date'].string ?? '')?.toLocal(),
    totalBookmarks: data['total_bookmarks'].integer ?? 0,
    totalViews: data['total_view'].integer ?? 0,
    isR18: pixivIsR18(data),
    isBookmarked: data['is_bookmarked'].boolean == true,
  );
}

/// A trending tag with the illust Pixiv picked to represent it — every OSS
/// client renders these as a tappable image grid for the search landing page.
class PixivTrendTag {
  final String name;
  final String? translatedName;
  final PixivIllust? illust;

  const PixivTrendTag({required this.name, this.translatedName, this.illust});
}

/// Related works for an R-18 seed are themselves R-18. Hiding them left one
/// SFW leftover under "Similar works" — the reader opened the R-18 illust
/// on purpose (bookmarks keep those even when the home feed hides them).
bool pixivRelatedIncludeR18({required bool seedIsR18, required bool showR18}) =>
    showR18 || seedIsR18;

/// Viewer frame for an illust: follow the art, not a fixed 55% of the screen.
///
/// A short landscape in a tall box left a black slab between the image and
/// the caption, which also pushed similar works off the first screen.
double pixivDetailViewerHeight({
  required double screenWidth,
  required double screenHeight,
  required int width,
  required int height,
}) {
  final ratio = (width > 0 && height > 0) ? width / height : 1.0;
  return (screenWidth / ratio).clamp(160.0, screenHeight * 0.70);
}

/// Pure parse of a following / ranking / bookmarks / search list payload.
List<PixivIllust> parsePixivIllustList(
  Object? json, {
  bool includeR18 = false,
}) {
  final root = Json(json);
  final list = root['illusts'].list;
  return [
    for (final item in list)
      if (pixivIllustFromJson(item.raw) case final illust?)
        if (includeR18 || !illust.isR18) illust,
  ];
}

/// Pure parse of `/v1/search/user` → user list.
List<PixivUser> parsePixivUserList(Object? json) {
  final root = Json(json);
  final list = root['user_previews'].list.isNotEmpty
      ? root['user_previews'].list
      : root['users'].list;
  return [
    for (final item in list)
      if (_previewUser(item) case final user? when user.id != 0) user,
  ];
}

PixivUser? _previewUser(Json item) {
  if (item['user'].exists) {
    return PixivUser.fromUserJson(item['user'].raw);
  }
  if (item['id'].exists) {
    return PixivUser.fromUserJson(item.raw);
  }
  return null;
}
