/// Guest / session Instagram models. Photos stay in this plugin — not a tweet.
library;

class InstagramProfile {
  final String id;
  final String username;
  final String fullName;
  final String? biography;
  final String? avatarUrl;
  final bool isPrivate;
  final bool isVerified;
  final int followerCount;
  final int followingCount;
  final int mediaCount;

  const InstagramProfile({
    required this.id,
    required this.username,
    required this.fullName,
    this.biography,
    this.avatarUrl,
    this.isPrivate = false,
    this.isVerified = false,
    this.followerCount = 0,
    this.followingCount = 0,
    this.mediaCount = 0,
  });

  String get handle => username;

  String get displayName => fullName.trim().isEmpty ? username : fullName;

  Uri profileUri() => Uri.parse('https://www.instagram.com/$username/');
}

class InstagramAuthor {
  final String username;
  final String fullName;
  final String? avatarUrl;
  final bool isVerified;

  const InstagramAuthor({
    required this.username,
    required this.fullName,
    this.avatarUrl,
    this.isVerified = false,
  });

  String get displayName => fullName.trim().isEmpty ? username : fullName;
}

class InstagramPost {
  final String id;
  final String shortcode;
  final String caption;
  final DateTime createdAt;
  final InstagramAuthor author;
  final String? coverUrl;
  final bool isVideo;
  final int likeCount;
  final int commentCount;
  final List<String> carouselUrls;

  const InstagramPost({
    required this.id,
    required this.shortcode,
    required this.caption,
    required this.createdAt,
    required this.author,
    this.coverUrl,
    this.isVideo = false,
    this.likeCount = 0,
    this.commentCount = 0,
    this.carouselUrls = const [],
  });

  Uri webUri() => Uri.parse(
    'https://www.instagram.com/${isVideo ? 'reel' : 'p'}/$shortcode/',
  );
}

class InstagramItemPage {
  final List<InstagramPost> posts;
  final String? cursor;
  final bool hasMore;

  const InstagramItemPage({
    required this.posts,
    this.cursor,
    required this.hasMore,
  });
}

class InstagramSearchUser {
  final String id;
  final String username;
  final String fullName;
  final String? avatarUrl;
  final bool isPrivate;
  final bool isVerified;

  const InstagramSearchUser({
    required this.id,
    required this.username,
    required this.fullName,
    this.avatarUrl,
    this.isPrivate = false,
    this.isVerified = false,
  });

  String get displayName => fullName.trim().isEmpty ? username : fullName;
}
