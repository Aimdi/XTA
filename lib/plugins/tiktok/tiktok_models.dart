/// Guest TikTok models. Vertical video stays in this plugin — not a tweet.
library;

class TikTokProfile {
  final String id;
  final String secUid;
  final String uniqueId;
  final String nickname;
  final String? signature;
  final String? avatarUrl;
  final bool privateAccount;
  final bool verified;
  final int followerCount;
  final int followingCount;
  final int videoCount;
  final int heartCount;

  const TikTokProfile({
    required this.id,
    required this.secUid,
    required this.uniqueId,
    required this.nickname,
    this.signature,
    this.avatarUrl,
    this.privateAccount = false,
    this.verified = false,
    this.followerCount = 0,
    this.followingCount = 0,
    this.videoCount = 0,
    this.heartCount = 0,
  });

  String get handle => uniqueId;

  String get displayName => nickname.trim().isEmpty ? uniqueId : nickname;

  Uri profileUri() => Uri.parse('https://www.tiktok.com/@$uniqueId');
}

class TikTokAuthor {
  final String uniqueId;
  final String nickname;
  final String? secUid;
  final String? avatarUrl;
  final bool verified;

  const TikTokAuthor({
    required this.uniqueId,
    required this.nickname,
    this.secUid,
    this.avatarUrl,
    this.verified = false,
  });

  String get displayName => nickname.trim().isEmpty ? uniqueId : nickname;
}

class TikTokVideoSource {
  final String url;
  final String? label;

  const TikTokVideoSource(this.url, {this.label});
}

class TikTokPost {
  final String id;
  final String desc;
  final DateTime createdAt;
  final TikTokAuthor author;
  final String? coverUrl;
  final int durationSeconds;
  final int width;
  final int height;
  final int diggCount;
  final int commentCount;
  final int shareCount;
  final int playCount;
  final List<TikTokVideoSource> sources;

  const TikTokPost({
    required this.id,
    required this.desc,
    required this.createdAt,
    required this.author,
    this.coverUrl,
    this.durationSeconds = 0,
    this.width = 0,
    this.height = 0,
    this.diggCount = 0,
    this.commentCount = 0,
    this.shareCount = 0,
    this.playCount = 0,
    this.sources = const [],
  });

  double get aspectRatio {
    if (width > 0 && height > 0) return width / height;
    return 9 / 16;
  }

  String? get playUrl => sources.isEmpty ? null : sources.first.url;

  Uri webUri() =>
      Uri.parse('https://www.tiktok.com/@${author.uniqueId}/video/$id');

  Uri embedUri() => Uri.parse('https://www.tiktok.com/embed/v3/$id');
}

class TikTokItemPage {
  final List<TikTokPost> posts;
  final String? cursor;
  final bool hasMore;
  final int? statusCode;

  const TikTokItemPage({
    required this.posts,
    this.cursor,
    required this.hasMore,
    this.statusCode,
  });
}
