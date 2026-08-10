import 'package:flutter/material.dart';
import 'package:xta/constants.dart';
import 'package:xta/group/custom_feed_rules.dart';
import 'package:xta/group/group_model.dart';
import 'package:xta/group/muted_keyword.dart';
import 'package:xta/subscriptions/group_mark_style.dart';
import 'package:xta/user.dart';
import 'package:intl/intl.dart';

final DateFormat sqliteDateFormat = DateFormat('yyyy-MM-dd hh:mm:ss');

mixin ToMappable {
  Map<String, dynamic> toMap();
}

class SavedTweet with ToMappable {
  final String id;
  final String? user;
  final String? content;
  final String? folderId;
  final String? note;

  SavedTweet({
    required this.id,
    required this.user,
    required this.content,
    this.folderId,
    this.note,
  });

  factory SavedTweet.fromMap(Map<String, Object?> map) {
    return SavedTweet(
      id: map['id'] as String,
      user: map['user_id'] as String?,
      content: map['content'] as String?,
      folderId: map['folder_id'] as String?,
      note: map['note'] as String?,
    );
  }

  // `folderId` is nullable and null is meaningful ("unfiled"), so the sentinel lets
  // callers distinguish "leave unchanged" from "clear the folder".
  static const _unset = Object();

  SavedTweet copyWith({
    String? id,
    String? user,
    String? content,
    Object? folderId = _unset,
    Object? note = _unset,
  }) {
    return SavedTweet(
      id: id ?? this.id,
      user: user ?? this.user,
      content: content ?? this.content,
      folderId: identical(folderId, _unset)
          ? this.folderId
          : folderId as String?,
      note: identical(note, _unset) ? this.note : note as String?,
    );
  }

  @override
  Map<String, dynamic> toMap() {
    return {
      'id': id,
      'content': content,
      'user_id': user,
      'folder_id': folderId,
      'note': note,
    };
  }
}

/// A private note the reader keeps on a profile — never synced anywhere.
class ProfileNote with ToMappable {
  final String id;
  final String note;
  final DateTime updatedAt;

  ProfileNote({required this.id, required this.note, required this.updatedAt});

  factory ProfileNote.fromMap(Map<String, Object?> map) {
    return ProfileNote(
      id: map['id'] as String,
      note: map['note'] as String? ?? '',
      updatedAt:
          DateTime.tryParse((map['updated_at'] as String?) ?? '') ??
          DateTime.now(),
    );
  }

  @override
  Map<String, dynamic> toMap() {
    return {'id': id, 'note': note, 'updated_at': updatedAt.toIso8601String()};
  }
}

/// A saved keyword listener feed (Misskey antenna).
class Antenna with ToMappable {
  final String id;
  final String name;
  final List<String> includeTerms;
  final List<String> excludeTerms;

  /// `search` (all of X search) or `following` (only accounts you follow).
  final String scope;
  final DateTime createdAt;

  Antenna({
    required this.id,
    required this.name,
    required this.includeTerms,
    this.excludeTerms = const [],
    this.scope = 'search',
    required this.createdAt,
  });

  factory Antenna.fromMap(Map<String, Object?> map) {
    return Antenna(
      id: map['id'] as String,
      name: map['name'] as String? ?? '',
      includeTerms: parseMutedKeywordTerms(map['include_terms'] as String?),
      excludeTerms: parseMutedKeywordTerms(map['exclude_terms'] as String?),
      scope: (map['scope'] as String?) == 'following' ? 'following' : 'search',
      createdAt:
          DateTime.tryParse((map['created_at'] as String?) ?? '') ??
          DateTime.now(),
    );
  }

  @override
  Map<String, dynamic> toMap() {
    return {
      'id': id,
      'name': name,
      'include_terms': includeTerms.join(', '),
      'exclude_terms': excludeTerms.isEmpty ? null : excludeTerms.join(', '),
      'scope': scope,
      'created_at': createdAt.toIso8601String(),
    };
  }
}

class LikedTweet with ToMappable {
  final String id;
  final String? user;
  final String? content;

  LikedTweet({required this.id, required this.user, required this.content});

  factory LikedTweet.fromMap(Map<String, Object?> map) {
    return LikedTweet(
      id: map['id'] as String,
      user: map['user_id'] as String?,
      content: map['content'] as String?,
    );
  }

  @override
  Map<String, dynamic> toMap() {
    return {'id': id, 'content': content, 'user_id': user};
  }
}

class SavedTweetFolder with ToMappable {
  final String id;
  final String name;
  final int position;
  final DateTime createdAt;
  // When true, saving a post into this folder also downloads its images.
  final bool autoDownload;
  final bool autoUpload;

  SavedTweetFolder({
    required this.id,
    required this.name,
    this.position = 0,
    required this.createdAt,
    this.autoDownload = false,
    this.autoUpload = false,
  });

  factory SavedTweetFolder.fromMap(Map<String, Object?> map) {
    return SavedTweetFolder(
      id: map['id'] as String,
      name: map['name'] as String,
      position: (map['position'] as int?) ?? 0,
      createdAt: DateTime.parse(map['created_at'] as String),
      autoDownload: (map['auto_download'] as int?) == 1,
      autoUpload: (map['auto_upload'] as int?) == 1,
    );
  }

  SavedTweetFolder copyWith({
    String? name,
    int? position,
    bool? autoDownload,
    bool? autoUpload,
  }) {
    return SavedTweetFolder(
      id: id,
      name: name ?? this.name,
      position: position ?? this.position,
      createdAt: createdAt,
      autoDownload: autoDownload ?? this.autoDownload,
      autoUpload: autoUpload ?? this.autoUpload,
    );
  }

  @override
  Map<String, dynamic> toMap() {
    return {
      'id': id,
      'name': name,
      'position': position,
      'created_at': createdAt.toIso8601String(),
      'auto_download': autoDownload ? 1 : 0,
      'auto_upload': autoUpload ? 1 : 0,
    };
  }
}

abstract class Subscription with ToMappable {
  final String id;
  final String screenName;
  final String name;
  final String? profileImageUrlHttps;
  final bool verified;
  final DateTime createdAt;
  final bool inFeed;

  Subscription({
    required this.id,
    required this.screenName,
    required this.name,
    required this.profileImageUrlHttps,
    required this.verified,
    required this.createdAt,
    required this.inFeed,
  });
}

class SearchSubscription extends Subscription {
  SearchSubscription({required super.id, required super.createdAt})
    : super(
        name: id,
        screenName: id,
        verified: false,
        profileImageUrlHttps: null,
        inFeed: true,
      );

  factory SearchSubscription.fromMap(Map<String, Object?> map) {
    return SearchSubscription(
      id: map['id'] as String,
      createdAt: DateTime.parse(map['created_at'] as String),
    );
  }

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is SearchSubscription &&
          runtimeType == other.runtimeType &&
          id == other.id;

  @override
  int get hashCode => id.hashCode;

  @override
  Map<String, dynamic> toMap() {
    // TODO: Created at date format
    return {'id': id, 'created_at': sqliteDateFormat.format(createdAt)};
  }
}

class UserSubscription extends Subscription {
  /// Max posts from this account kept per feed load; null/0 = uncapped.
  final int? maxPostsPerLoad;

  UserSubscription({
    required super.id,
    required super.screenName,
    required super.name,
    required super.profileImageUrlHttps,
    required super.verified,
    required super.createdAt,
    required super.inFeed,
    this.maxPostsPerLoad,
  });

  factory UserSubscription.fromMap(Map<String, Object?> map) {
    var verified = map['verified'] is int;
    var createdAt = map['created_at'] == null
        ? DateTime.now()
        : DateTime.parse(map['created_at'] as String);
    var inFeed = map['in_feed'] is int;

    return UserSubscription(
      id: map['id'] as String,
      screenName: map['screen_name'] as String,
      name: map['name'] as String,
      profileImageUrlHttps: map['profile_image_url_https'] as String?,
      verified: verified ? map['verified'] == 1 : false,
      createdAt: createdAt,
      inFeed: inFeed ? map['in_feed'] == 1 : false,
      maxPostsPerLoad: map['max_posts_per_load'] as int?,
    );
  }

  factory UserSubscription.fromUser(UserWithExtra user) {
    return UserSubscription(
      id: user.idStr!,
      screenName: user.screenName!,
      name: user.name!,
      profileImageUrlHttps: user.profileImageUrlHttps,
      verified: user.verified!,
      createdAt: user.createdAt!,
      inFeed: true,
    );
  }

  UserSubscription copyWith({
    int? maxPostsPerLoad,
    bool clearMaxPosts = false,
  }) {
    return UserSubscription(
      id: id,
      screenName: screenName,
      name: name,
      profileImageUrlHttps: profileImageUrlHttps,
      verified: verified,
      createdAt: createdAt,
      inFeed: inFeed,
      maxPostsPerLoad: clearMaxPosts
          ? null
          : (maxPostsPerLoad ?? this.maxPostsPerLoad),
    );
  }

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is UserSubscription &&
          runtimeType == other.runtimeType &&
          id == other.id;

  @override
  int get hashCode => id.hashCode;

  @override
  Map<String, dynamic> toMap() {
    return {
      'id': id,
      'screen_name': screenName,
      'name': name,
      'profile_image_url_https': profileImageUrlHttps,
      'verified': verified ? 1 : 0,
      'created_at': sqliteDateFormat.format(createdAt),
      'in_feed': inFeed ? 1 : 0,
      'max_posts_per_load': maxPostsPerLoad,
    };
  }

  UserWithExtra toUser() {
    return UserWithExtra.fromJson({
      'id_str': id,
      'screen_name': screenName,
      'name': name,
      'profile_image_url_https': profileImageUrlHttps,
      'verified': verified,
    });
  }
}

/// A Threads account the reader follows.
///
/// Its id is the handle, without the `@` — that is what the RSSHub route is
/// keyed by, and what the account is called everywhere it is shown.
class ThreadsSubscription extends Subscription {
  ThreadsSubscription({
    required super.id,
    required super.name,
    required String? avatarUrl,
    required super.createdAt,
    required super.inFeed,
  }) : super(screenName: id, verified: false, profileImageUrlHttps: avatarUrl);

  String? get avatarUrl => profileImageUrlHttps;

  factory ThreadsSubscription.fromMap(Map<String, Object?> map) {
    return ThreadsSubscription(
      id: map['id'] as String,
      name: map['name'] as String,
      avatarUrl: map['avatar_url'] as String?,
      createdAt: map['created_at'] == null
          ? DateTime.now()
          : DateTime.parse(map['created_at'] as String),
      inFeed: map['in_feed'] == null || map['in_feed'] == 1,
    );
  }

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is ThreadsSubscription &&
          runtimeType == other.runtimeType &&
          id == other.id;

  @override
  int get hashCode => id.hashCode;

  @override
  Map<String, dynamic> toMap() {
    return {
      'id': id,
      'name': name,
      'avatar_url': avatarUrl,
      'in_feed': inFeed ? 1 : 0,
      'created_at': sqliteDateFormat.format(createdAt),
    };
  }
}

/// A Bluesky account the reader follows locally.
///
/// Its id is the handle (or a DID when that is what was stored) — what the
/// public AppView accepts as `actor`, and what the account is called in the UI.
class BlueskySubscription extends Subscription {
  BlueskySubscription({
    required super.id,
    required super.name,
    required String? avatarUrl,
    required super.createdAt,
    required super.inFeed,
  }) : super(screenName: id, verified: false, profileImageUrlHttps: avatarUrl);

  String? get avatarUrl => profileImageUrlHttps;

  factory BlueskySubscription.fromMap(Map<String, Object?> map) {
    return BlueskySubscription(
      id: map['id'] as String,
      name: map['name'] as String,
      avatarUrl: map['avatar_url'] as String?,
      createdAt: map['created_at'] == null
          ? DateTime.now()
          : DateTime.parse(map['created_at'] as String),
      inFeed: map['in_feed'] == null || map['in_feed'] == 1,
    );
  }

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is BlueskySubscription &&
          runtimeType == other.runtimeType &&
          id == other.id;

  @override
  int get hashCode => id.hashCode;

  @override
  Map<String, dynamic> toMap() {
    return {
      'id': id,
      'name': name,
      'avatar_url': avatarUrl,
      'in_feed': inFeed ? 1 : 0,
      'created_at': sqliteDateFormat.format(createdAt),
    };
  }
}

/// A Mastodon / Fediverse account the reader follows locally.
///
/// Its id is the canonical `user@domain` — portable across home instances,
/// unlike the numeric account id each Mastodon server assigns.
class MastodonSubscription extends Subscription {
  MastodonSubscription({
    required super.id,
    required super.name,
    required String? avatarUrl,
    required super.createdAt,
    required super.inFeed,
  }) : super(screenName: id, verified: false, profileImageUrlHttps: avatarUrl);

  String? get avatarUrl => profileImageUrlHttps;

  factory MastodonSubscription.fromMap(Map<String, Object?> map) {
    return MastodonSubscription(
      id: map['id'] as String,
      name: map['name'] as String,
      avatarUrl: map['avatar_url'] as String?,
      createdAt: map['created_at'] == null
          ? DateTime.now()
          : DateTime.parse(map['created_at'] as String),
      inFeed: map['in_feed'] == null || map['in_feed'] == 1,
    );
  }

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is MastodonSubscription &&
          runtimeType == other.runtimeType &&
          id == other.id;

  @override
  int get hashCode => id.hashCode;

  @override
  Map<String, dynamic> toMap() {
    return {
      'id': id,
      'name': name,
      'avatar_url': avatarUrl,
      'in_feed': inFeed ? 1 : 0,
      'created_at': sqliteDateFormat.format(createdAt),
    };
  }
}

/// A followed Substack publication.
///
/// A third kind of subscription beside users and saved searches, so that a
/// group can hold one: group membership joins profile ids against subscription
/// tables, and while publications lived in a preferences blob there was nothing
/// for a group to join to.
class SubstackSubscription extends Subscription {
  /// Where the publication lives, e.g. `https://example.substack.com`.
  final String baseUrl;

  SubstackSubscription({
    required super.id,
    required this.baseUrl,
    required super.name,
    required String? logoUrl,
    required super.createdAt,
    required super.inFeed,
  }) : super(screenName: id, verified: false, profileImageUrlHttps: logoUrl);

  String? get logoUrl => profileImageUrlHttps;

  factory SubstackSubscription.fromMap(Map<String, Object?> map) {
    return SubstackSubscription(
      id: map['id'] as String,
      baseUrl: map['base_url'] as String,
      name: map['name'] as String,
      logoUrl: map['logo_url'] as String?,
      createdAt: map['created_at'] == null
          ? DateTime.now()
          : DateTime.parse(map['created_at'] as String),
      inFeed: map['in_feed'] == null || map['in_feed'] == 1,
    );
  }

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is SubstackSubscription &&
          runtimeType == other.runtimeType &&
          id == other.id;

  @override
  int get hashCode => id.hashCode;

  @override
  Map<String, dynamic> toMap() {
    return {
      'id': id,
      'base_url': baseUrl,
      'name': name,
      'logo_url': logoUrl,
      'in_feed': inFeed ? 1 : 0,
      'created_at': sqliteDateFormat.format(createdAt),
    };
  }
}

/// A followed subreddit.
///
/// A fourth kind of subscription, for the same reason publications became the
/// third: a group joins profile ids against subscription tables, so anything
/// that wants to be a member needs rows.
class RedditSubscription extends Subscription {
  RedditSubscription({
    required super.id,
    required super.name,
    required super.createdAt,
    required super.inFeed,
  }) : super(screenName: id, verified: false, profileImageUrlHttps: null);

  factory RedditSubscription.fromMap(Map<String, Object?> map) {
    return RedditSubscription(
      id: map['id'] as String,
      name: map['name'] as String,
      createdAt: map['created_at'] == null
          ? DateTime.now()
          : DateTime.parse(map['created_at'] as String),
      inFeed: map['in_feed'] == null || map['in_feed'] == 1,
    );
  }

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is RedditSubscription &&
          runtimeType == other.runtimeType &&
          id == other.id;

  @override
  int get hashCode => id.hashCode;

  @override
  Map<String, dynamic> toMap() {
    return {
      'id': id,
      'name': name,
      'in_feed': inFeed ? 1 : 0,
      'created_at': sqliteDateFormat.format(createdAt),
    };
  }
}

/// A watched ticker.
///
/// The symbol carries the display name, so a watchlist entry can be a group
/// member on the same terms as every other subscription kind.
class StockSubscription extends Subscription {
  StockSubscription({
    required super.id,
    required String symbol,
    required super.createdAt,
    required super.inFeed,
  }) : super(
         name: symbol,
         screenName: id,
         verified: false,
         profileImageUrlHttps: null,
       );

  String get symbol => name;

  factory StockSubscription.fromMap(Map<String, Object?> map) {
    return StockSubscription(
      id: map['id'] as String,
      symbol: map['symbol'] as String,
      createdAt: map['created_at'] == null
          ? DateTime.now()
          : DateTime.parse(map['created_at'] as String),
      inFeed: map['in_feed'] == null || map['in_feed'] == 1,
    );
  }

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is StockSubscription &&
          runtimeType == other.runtimeType &&
          id == other.id;

  @override
  int get hashCode => id.hashCode;

  @override
  Map<String, dynamic> toMap() {
    return {
      'id': id,
      'symbol': symbol,
      'in_feed': inFeed ? 1 : 0,
      'created_at': sqliteDateFormat.format(createdAt),
    };
  }
}

/// One member shown in a group's avatar preview. [avatarUrl] is nullable: a
/// member with no picture still appears, as a deterministic monogram keyed by
/// [id].
@immutable
class GroupMemberPreview {
  final String id;
  final String name;
  final String? avatarUrl;

  /// Set when the member is a subreddit, whose picture is not a URL this app
  /// holds — it is fetched and cached separately, and drawn from the name.
  final String? subreddit;

  const GroupMemberPreview({
    required this.id,
    required this.name,
    this.avatarUrl,
    this.subreddit,
  });
}

class SubscriptionGroup with ToMappable {
  final String id;
  final String name;
  final String icon;
  final Color? color;
  final int numberOfMembers;
  final DateTime createdAt;
  final bool pinned;

  /// Reader-marked adult group: board sinks it under Censored. Not the same as
  /// [SubscriptionGroupGet.contentFilter], which filters posts in a custom feed.
  final bool nsfw;
  final String? emoji;
  final int markStyle;

  /// The group this one sits inside, or null when it stands on its own.
  final String? parentId;

  // Not persisted: a few members for the tile's avatar preview, populated by
  // GroupsModel.reloadGroups.
  final List<GroupMemberPreview> memberPreviews;

  IconData get iconData => deserializeIconData(icon);

  SubscriptionGroup({
    required this.id,
    required this.name,
    required this.icon,
    required this.color,
    required this.numberOfMembers,
    required this.createdAt,
    this.pinned = false,
    this.nsfw = false,
    this.emoji,
    this.markStyle = 0,
    this.parentId,
    this.memberPreviews = const [],
  });

  SubscriptionGroup withMemberPreviews(List<GroupMemberPreview> previews) =>
      SubscriptionGroup(
        id: id,
        name: name,
        icon: icon,
        color: color,
        numberOfMembers: numberOfMembers,
        createdAt: createdAt,
        pinned: pinned,
        nsfw: nsfw,
        emoji: emoji,
        markStyle: markStyle,
        parentId: parentId,
        memberPreviews: previews,
      );

  factory SubscriptionGroup.fromMap(Map<String, Object?> json) {
    // This is here to handle imports of data from before v2.15.0
    var icon = json['icon'] as String?;
    if (icon == null || icon == 'rss' || icon == '') {
      icon = defaultGroupIcon;
    }

    return SubscriptionGroup(
      id: json['id'] as String,
      name: json['name'] as String,
      icon: icon,
      color: json['color'] == null ? null : Color(json['color'] as int),
      numberOfMembers: json['number_of_members'] == null
          ? 0
          : json['number_of_members'] as int,
      createdAt: DateTime.parse(json['created_at'] as String),
      pinned: json['pinned'] == 1,
      nsfw: json['nsfw'] == 1,
      emoji: json['emoji'] as String?,
      markStyle: GroupMarkStyle.coerce(json['mark_style']),
      parentId: json['parent_id'] as String?,
    );
  }

  @override
  Map<String, dynamic> toMap() {
    return {
      'id': id,
      'name': name,
      'icon': icon,
      'color': color?.toARGB32(),
      'created_at': createdAt.toIso8601String(),
      'pinned': pinned ? 1 : 0,
      'nsfw': nsfw ? 1 : 0,
      'emoji': emoji,
      'mark_style': markStyle,
      'parent_id': parentId,
    };
  }
}

// Sentinel distinguishing "argument omitted" from "explicitly set to null" in
// copyWith, needed for the nullable include-replies/retweets overrides.
const Object _unset = Object();

class SubscriptionGroupGet {
  final String id;
  final String name;
  final String icon;
  final List<Subscription> subscriptions;
  // null means "follow the global default" (optionGlobalIncludeReplies /
  // optionGlobalIncludeRetweets); resolved to a concrete bool where the feed
  // is built.
  bool? includeReplies;
  bool? includeRetweets;
  bool popular;
  bool custom;
  String contentFilter;

  /// Custom-feed thresholds; 0 means the threshold is off.
  int minLikes;
  int minRetweets;

  /// Terms whose posts this feed hides or folds.
  List<MutedKeyword> mutedKeywords;

  SubscriptionGroupGet({
    required this.id,
    required this.name,
    required this.icon,
    required this.subscriptions,
    required this.includeReplies,
    required this.includeRetweets,
    required this.popular,
    this.custom = false,
    this.contentFilter = contentFilterDefault,
    this.minLikes = 0,
    this.minRetweets = 0,
    this.mutedKeywords = const [],
  });

  CustomFeedRules get customRules => custom
      ? CustomFeedRules(
          contentFilter: contentFilter,
          minLikes: minLikes,
          minRetweets: minRetweets,
          mutedKeywords: mutedKeywords,
        )
      : const CustomFeedRules();

  // Store updates must emit a new instance, otherwise listeners never see the
  // change (the store skips identical states) and dependent widgets go stale.
  SubscriptionGroupGet copyWith({
    Object? includeReplies = _unset,
    Object? includeRetweets = _unset,
    bool? popular,
    bool? custom,
    String? contentFilter,
    int? minLikes,
    int? minRetweets,
    List<MutedKeyword>? mutedKeywords,
  }) {
    return SubscriptionGroupGet(
      id: id,
      name: name,
      icon: icon,
      subscriptions: subscriptions,
      includeReplies: identical(includeReplies, _unset)
          ? this.includeReplies
          : includeReplies as bool?,
      includeRetweets: identical(includeRetweets, _unset)
          ? this.includeRetweets
          : includeRetweets as bool?,
      popular: popular ?? this.popular,
      custom: custom ?? this.custom,
      contentFilter: contentFilter ?? this.contentFilter,
      minLikes: minLikes ?? this.minLikes,
      minRetweets: minRetweets ?? this.minRetweets,
      mutedKeywords: mutedKeywords ?? this.mutedKeywords,
    );
  }
}

class SubscriptionGroupEdit {
  final String? id;
  String name;
  String icon;
  Color? color;
  Set<String> members;
  String? emoji;
  int markStyle;

  SubscriptionGroupEdit({
    required this.id,
    required this.name,
    required this.icon,
    required this.color,
    required this.members,
    this.emoji,
    this.markStyle = GroupMarkStyle.auto,
  });
}

class SubscriptionGroupMember with ToMappable {
  final String group;
  final String profile;

  SubscriptionGroupMember({required this.group, required this.profile});

  factory SubscriptionGroupMember.fromMap(Map<String, Object?> json) {
    return SubscriptionGroupMember(
      group: json['group_id'] as String,
      profile: json['profile_id'] as String,
    );
  }

  @override
  Map<String, dynamic> toMap() {
    return {'group_id': group, 'profile_id': profile};
  }
}

class Account with ToMappable {
  final String id;
  final dynamic authHeader;
  final String? screenName;
  final DateTime? lastNotFoundAt;
  final int consecutiveNotFound;

  Account({
    required this.id,
    required this.authHeader,
    required this.screenName,
    this.lastNotFoundAt,
    this.consecutiveNotFound = 0,
  });

  static DateTime? _date(Object? value) =>
      value == null ? null : DateTime.parse(value as String);

  /// No flag set, so a successful response needs no database write (hot-path guard).
  bool get isClean => consecutiveNotFound == 0 && lastNotFoundAt == null;

  factory Account.fromMap(Map<String, Object?> map) {
    return Account(
      id: map['id'] as String,
      authHeader: map['auth_header'],
      screenName: map['screen_name'] as String?,
      lastNotFoundAt: _date(map['last_not_found_at']),
      consecutiveNotFound: (map['consecutive_not_found'] as int?) ?? 0,
    );
  }

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is Account && runtimeType == other.runtimeType && id == other.id;

  @override
  int get hashCode => id.hashCode;

  @override
  Map<String, dynamic> toMap() {
    return {
      'id': id,
      'auth_header': authHeader,
      'screen_name': screenName,
      'last_not_found_at': lastNotFoundAt?.toIso8601String(),
      'consecutive_not_found': consecutiveNotFound,
    };
  }
}
