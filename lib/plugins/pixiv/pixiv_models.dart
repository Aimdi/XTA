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

/// The series a work belongs to, as its `series` object names it.
class PixivSeriesRef {
  final int id;
  final String title;

  const PixivSeriesRef({required this.id, required this.title});
}

/// One illustration card / viewer worth of fields from `app-api.pixiv.net`.
class PixivIllust {
  final int id;
  final String title;
  final String caption;

  /// The caption as Pixiv sent it, links and line breaks intact.
  final String captionHtml;
  final String type;
  final String thumbnailUrl;
  final String? largeUrl;
  final List<String> pageUrls;

  /// Per-page full-resolution files, index-aligned with [viewerUrls].
  final List<String> originalUrls;

  /// Per-page small previews for the page overview, index-aligned with [viewerUrls].
  final List<String> pageThumbUrls;
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
  final int totalComments;
  final bool isR18;

  /// Pixiv's 0-8 content rating; 6 and above counts as R-18 with [isR18].
  final int sanityLevel;

  /// The creator marked the work as AI-generated.
  final bool isAi;
  final bool isBookmarked;
  final bool userIsFollowed;
  final PixivSeriesRef? series;

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
    this.originalUrls = const [],
    this.pageThumbUrls = const [],
    this.tags = const [],
    this.width = 0,
    this.height = 0,
    this.userAvatarUrl,
    this.createdAt,
    this.totalBookmarks = 0,
    this.totalViews = 0,
    this.totalComments = 0,
    this.isR18 = false,
    this.sanityLevel = 0,
    this.isAi = false,
    this.isBookmarked = false,
    this.userIsFollowed = false,
    this.captionHtml = '',
    this.series,
  });

  PixivIllust copyWith({bool? isBookmarked, int? totalBookmarks, bool? userIsFollowed}) =>
      PixivIllust(
        id: id,
        title: title,
        caption: caption,
        captionHtml: captionHtml,
        type: type,
        thumbnailUrl: thumbnailUrl,
        largeUrl: largeUrl,
        pageUrls: pageUrls,
        originalUrls: originalUrls,
        pageThumbUrls: pageThumbUrls,
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
        totalComments: totalComments,
        isR18: isR18,
        sanityLevel: sanityLevel,
        isAi: isAi,
        isBookmarked: isBookmarked ?? this.isBookmarked,
        userIsFollowed: userIsFollowed ?? this.userIsFollowed,
        series: series,
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

  /// The file to save for [page]: the original when Pixiv sent one.
  String downloadUrlAt(int page) => _alignedOr(originalUrls, page);

  /// A light preview of [page] for thumbnails.
  String thumbUrlAt(int page) => _alignedOr(pageThumbUrls, page);

  String _alignedOr(List<String> urls, int page) {
    final pages = viewerUrls;
    final index = page.clamp(0, pages.length - 1);
    return urls.length == pages.length && urls[index].isNotEmpty ? urls[index] : pages[index];
  }
}

/// Who the refresh token belongs to, as the token response reports it.
class PixivAuthUser {
  final int id;
  final String name;
  final String account;
  final bool isPremium;
  final String? avatarUrl;

  const PixivAuthUser({
    required this.id,
    required this.name,
    required this.account,
    this.isPremium = false,
    this.avatarUrl,
  });

  /// The token response's `user` object.
  factory PixivAuthUser.fromJson(Object? json) {
    final user = Json(json);
    final images = user['profile_image_urls'];
    final avatar = images['px_170x170'].string ?? images['px_50x50'].string ?? images['medium'].string;
    return PixivAuthUser(
      id: user['id'].integer ?? 0,
      name: user['name'].string?.trim() ?? '',
      account: user['account'].string?.trim() ?? '',
      isPremium: user['is_premium'].boolean == true,
      avatarUrl: avatar == null || avatar.isEmpty ? null : avatar,
    );
  }

  String get displayName => name.isEmpty ? account : name;
}

/// A Pixiv account signed in on this device, kept so the reader can switch
/// back to it. Its refresh token makes this a credential.
class PixivAccount {
  final int userId;
  final String name;
  final String account;
  final String? avatarUrl;
  final bool isPremium;
  final String refreshToken;

  const PixivAccount({
    required this.userId,
    required this.name,
    required this.account,
    required this.refreshToken,
    this.avatarUrl,
    this.isPremium = false,
  });

  factory PixivAccount.of(PixivAuthUser user, String refreshToken) => PixivAccount(
    userId: user.id,
    name: user.name,
    account: user.account,
    avatarUrl: user.avatarUrl,
    isPremium: user.isPremium,
    refreshToken: refreshToken,
  );

  /// A stored account, or null without the id or token needed to use it.
  static PixivAccount? fromJson(Json json) {
    final id = json['userId'].integer ?? 0;
    final token = json['refreshToken'].string?.trim() ?? '';
    if (id <= 0 || token.isEmpty) return null;
    final avatar = json['avatar'].string;
    return PixivAccount(
      userId: id,
      name: json['name'].string ?? '',
      account: json['account'].string ?? '',
      avatarUrl: avatar == null || avatar.isEmpty ? null : avatar,
      isPremium: json['isPremium'].boolean == true,
      refreshToken: token,
    );
  }

  Map<String, Object> toJson() => {
    'userId': userId,
    'name': name,
    'account': account,
    'avatar': avatarUrl ?? '',
    'isPremium': isPremium,
    'refreshToken': refreshToken,
  };

  PixivAccount withRefreshToken(String token) => PixivAccount(
    userId: userId,
    name: name,
    account: account,
    avatarUrl: avatarUrl,
    isPremium: isPremium,
    refreshToken: token,
  );

  String get displayName => name.isEmpty ? (account.isEmpty ? '$userId' : account) : name;
}

/// A Pixiv user profile from `/v1/user/detail` or search.
///
/// The counts come only with the detail's `profile`; Pixiv's app API has no
/// follower count, so the profile shows who they follow and their My pixiv.
class PixivUser {
  final int id;
  final String name;
  final String account;
  final String? avatarUrl;
  final String comment;

  /// Illustrations and manga together.
  final int worksCount;
  final int followingCount;
  final int mypixivCount;

  /// Whether the signed-in account already follows this user.
  final bool isFollowed;

  const PixivUser({
    required this.id,
    required this.name,
    required this.account,
    required this.comment,
    this.avatarUrl,
    this.worksCount = 0,
    this.followingCount = 0,
    this.mypixivCount = 0,
    this.isFollowed = false,
  });

  PixivUser copyWith({bool? isFollowed}) => PixivUser(
    id: id,
    name: name,
    account: account,
    comment: comment,
    avatarUrl: avatarUrl,
    worksCount: worksCount,
    followingCount: followingCount,
    mypixivCount: mypixivCount,
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
    worksCount: (profile?['total_illusts'].integer ?? 0) + (profile?['total_manga'].integer ?? 0),
    followingCount: profile?['total_follow_users'].integer ?? 0,
    mypixivCount: profile?['total_mypixiv_users'].integer ?? 0,
    isFollowed: user['is_followed'].boolean == true,
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

String? _pageOriginalUrl(Json page) =>
    page['image_urls']['original'].string ?? _pageImageUrl(page);

/// Page preview — the aspect-preserving `medium`, since overview tiles are page-shaped.
String? _pageThumbUrl(Json page) {
  final urls = page['image_urls'];
  return urls['medium'].string ?? urls['square_medium'].string ?? _pageImageUrl(page);
}

/// Every page through [pick], or the single-page fallback when it yields none.
List<String> _perPage(Json illust, String? Function(Json page) pick, String? single) {
  final pages = illust['meta_pages'].list;
  if (pages.isNotEmpty) {
    return [for (final page in pages) ?pick(page)];
  }
  return single == null || single.isEmpty ? const [] : [single];
}

List<String> _originalUrlsOf(Json illust) => _perPage(
  illust,
  _pageOriginalUrl,
  illust['meta_single_page']['original_image_url'].string ?? _largeImageUrl(illust),
);

List<String> _pageThumbUrlsOf(Json illust) =>
    _perPage(illust, _pageThumbUrl, _pageThumbUrl(illust) ?? _firstImageUrl(illust));

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
    captionHtml: data['caption'].string?.trim() ?? '',
    type: data['type'].string ?? 'illust',
    thumbnailUrl: thumb,
    largeUrl: _largeImageUrl(data),
    pageUrls: _pageUrlsOf(data),
    originalUrls: _originalUrlsOf(data),
    pageThumbUrls: _pageThumbUrlsOf(data),
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
    totalComments: data['total_comments'].integer ?? 0,
    isR18: pixivIsR18(data),
    sanityLevel: data['sanity_level'].integer ?? 0,
    isAi: data['illust_ai_type'].integer == 2,
    isBookmarked: data['is_bookmarked'].boolean == true,
    userIsFollowed: user['is_followed'].boolean == true,
    series: _seriesOf(data['series']),
  );
}

PixivSeriesRef? _seriesOf(Json series) => switch (series['id'].integer) {
  final id? when id > 0 => PixivSeriesRef(id: id, title: series['title'].string?.trim() ?? ''),
  _ => null,
};

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

/// Whether the reader's Show R-18 and Hide AI choices let [illust] through.
bool pixivContentAllowed(PixivIllust illust, {required bool includeR18, required bool includeAi}) =>
    (includeR18 || !illust.isR18) && (includeAi || !illust.isAi);

/// Pure parse of a following / ranking / bookmarks / search list payload.
List<PixivIllust> parsePixivIllustList(
  Object? json, {
  bool includeR18 = false,
  bool includeAi = true,
}) {
  final root = Json(json);
  final list = root['illusts'].list;
  return [
    for (final item in list)
      if (pixivIllustFromJson(item.raw) case final illust?)
        if (pixivContentAllowed(illust, includeR18: includeR18, includeAi: includeAi)) illust,
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

/// A creator together with the works Pixiv previews beside them.
class PixivUserPreview {
  final PixivUser user;
  final List<PixivIllust> illusts;

  /// The preview novels exactly as sent, until the novel side parses them.
  final List<Json> novels;

  /// The account muted this creator on Pixiv itself.
  final bool isMuted;

  const PixivUserPreview({required this.user, this.illusts = const [], this.novels = const [], this.isMuted = false});
}

/// Pure parse of `user_previews` (recommended, related, search, follow lists)
/// → creators with their preview works, R-18 and AI previews left out as the
/// feeds leave them out.
List<PixivUserPreview> parsePixivUserPreviews(Object? json, {bool includeR18 = false, bool includeAi = true}) => [
  for (final item in Json(json)['user_previews'].list)
    if (_previewUser(item) case final user? when user.id != 0)
      PixivUserPreview(
        user: user,
        illusts: parsePixivIllustList(item.raw, includeR18: includeR18, includeAi: includeAi),
        novels: item['novels'].list,
        isMuted: item['is_muted'].boolean == true,
      ),
];

PixivUser? _previewUser(Json item) {
  if (item['user'].exists) {
    return PixivUser.fromUserJson(item['user'].raw);
  }
  if (item['id'].exists) {
    return PixivUser.fromUserJson(item.raw);
  }
  return null;
}

/// One page of any Pixiv list and where the next one is.
class PixivPage<T> {
  final List<T> items;
  final String? nextUrl;

  const PixivPage(this.items, {this.nextUrl});
}

class PixivUserPage extends PixivPage<PixivUser> {
  const PixivUserPage({required List<PixivUser> users, super.nextUrl}) : super(users);

  List<PixivUser> get users => items;

  factory PixivUserPage.fromJson(Object? json) {
    final root = Json(json);
    return PixivUserPage(
      users: [for (final preview in root['user_previews'].list)
        if (PixivUser.fromUserJson(preview['user'].raw) case final user when user.id > 0) user],
      nextUrl: root['next_url'].string,
    );
  }
}
