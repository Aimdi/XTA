import 'dart:convert';

import 'package:xta/plugins/plugin_post_media.dart';
import 'package:xta/utils/json.dart';

/// Open Graph–style link preview from Meta's `link_preview_attachment`.
class ThreadsLinkCard {
  final String url;
  final String? title;
  final String? description;
  final String? imageUrl;
  final String? providerName;

  const ThreadsLinkCard({
    required this.url,
    this.title,
    this.description,
    this.imageUrl,
    this.providerName,
  });

  bool get hasImage => imageUrl != null && imageUrl!.isNotEmpty;

  Map<String, dynamic> toJson() => {
    'url': url,
    'title': title,
    'description': description,
    'imageUrl': imageUrl,
    'providerName': providerName,
  };

  factory ThreadsLinkCard.fromSnapshot(Object? raw) {
    final json = raw is Map
        ? Map<String, dynamic>.from(raw)
        : const <String, dynamic>{};
    return ThreadsLinkCard(
      url: json['url'] as String? ?? '',
      title: json['title'] as String?,
      description: json['description'] as String?,
      imageUrl: json['imageUrl'] as String?,
      providerName: json['providerName'] as String?,
    );
  }
}

/// One Threads post, as much of it as a feed carries.
///
/// Meta guest/cookie payloads can also carry likes, reply/repost counts, and a
/// link preview. RSSHub feeds do not — those fields stay null so the card never
/// invents zeroes.
class ThreadsPost {
  /// The entry's own id, unique across accounts — the item id the feed gave,
  /// which for this route is the post's permalink.
  final String id;

  /// The handle that produced it, without the `@`.
  final String handle;

  /// The display name, falling back to the handle when the feed has no better.
  final String authorName;

  final String? avatarUrl;
  final String text;
  final List<String> images;
  final List<double?> imageAspects;
  final DateTime? publishedAt;

  /// Where the post lives on Threads, for opening it there.
  final String? url;

  final int? likeCount;
  final int? replyCount;
  final int? repostCount;
  final ThreadsLinkCard? linkCard;

  /// When set, this row is a repost: [repostedByHandle] shared [handle]'s post.
  final String? repostedByHandle;
  final String? repostedByName;

  /// Meta `user.is_verified` when the payload carried it.
  final bool isVerified;
  final String? replyToHandle;
  final bool isReply;

  const ThreadsPost({
    required this.id,
    required this.handle,
    required this.authorName,
    required this.text,
    this.avatarUrl,
    this.images = const [],
    this.imageAspects = const [],
    this.publishedAt,
    this.url,
    this.likeCount,
    this.replyCount,
    this.repostCount,
    this.linkCard,
    this.repostedByHandle,
    this.repostedByName,
    this.isVerified = false,
    this.replyToHandle,
    this.isReply = false,
  });

  bool get hasMedia => images.isNotEmpty;

  List<PluginMediaItem> get mediaItems =>
      pluginMediaItemsFrom(urls: images, aspects: imageAspects);

  bool get hasEngagement =>
      likeCount != null || replyCount != null || repostCount != null;

  bool get isRepost => repostedByHandle != null && repostedByHandle!.isNotEmpty;

  /// Who to show on a "X reposted" line.
  String get reposterDisplayName {
    final name = repostedByName?.trim() ?? '';
    if (name.isNotEmpty) {
      return name;
    }
    return repostedByHandle ?? '';
  }

  Map<String, dynamic> toJson() => {
    'id': id,
    'handle': handle,
    'authorName': authorName,
    'avatarUrl': avatarUrl,
    'text': text,
    'images': images,
    'imageAspects': imageAspects,
    'publishedAt': publishedAt?.toIso8601String(),
    'url': url,
    'likeCount': likeCount,
    'replyCount': replyCount,
    'repostCount': repostCount,
    'linkCard': linkCard?.toJson(),
    'repostedByHandle': repostedByHandle,
    'repostedByName': repostedByName,
    'isVerified': isVerified,
    'replyToHandle': replyToHandle,
    'isReply': isReply,
  };

  factory ThreadsPost.fromSnapshot(Object? raw) {
    final json = raw is Map
        ? Map<String, dynamic>.from(raw)
        : const <String, dynamic>{};
    final handle = json['handle'] as String? ?? '';
    final linkCardRaw = json['linkCard'];
    final linkCard = linkCardRaw == null
        ? null
        : ThreadsLinkCard.fromSnapshot(linkCardRaw);

    return ThreadsPost(
      id: json['id'] as String? ?? '',
      handle: handle,
      authorName: json['authorName'] as String? ?? handle,
      avatarUrl: json['avatarUrl'] as String?,
      text: json['text'] as String? ?? '',
      images:
          (json['images'] as List?)?.whereType<String>().toList(
            growable: false,
          ) ??
          const [],
      imageAspects: [
        for (final value in (json['imageAspects'] as List? ?? const []))
          value is num ? value.toDouble() : null,
      ],
      publishedAt: DateTime.tryParse(
        json['publishedAt'] as String? ?? '',
      )?.toLocal(),
      url: json['url'] as String?,
      likeCount: _snapshotCount(json['likeCount']),
      replyCount: _snapshotCount(json['replyCount']),
      repostCount: _snapshotCount(json['repostCount']),
      linkCard: linkCard == null || linkCard.url.isEmpty ? null : linkCard,
      repostedByHandle: json['repostedByHandle'] as String?,
      repostedByName: json['repostedByName'] as String?,
      isVerified: json['isVerified'] as bool? ?? false,
      replyToHandle: json['replyToHandle'] as String?,
      isReply:
          json['isReply'] as bool? ??
          ((json['replyToHandle'] as String?)?.isNotEmpty ?? false),
    );
  }

  static List<ThreadsPost> listFromPrefs(String? raw) {
    if (raw == null || raw.isEmpty) return const [];
    try {
      final decoded = jsonDecode(raw);
      if (decoded is! List) return const [];
      return decoded
          .whereType<Map>()
          .map((e) => ThreadsPost.fromSnapshot(Map<String, dynamic>.from(e)))
          .where((e) => e.id.isNotEmpty)
          .toList(growable: false);
    } catch (_) {
      return const [];
    }
  }

  static String listToPrefs(List<ThreadsPost> posts) =>
      jsonEncode(posts.map((e) => e.toJson()).toList());
}

int? _snapshotCount(Object? value) => value is num ? value.toInt() : null;

/// Minimal profile card from posts when the public page scrape failed.
ThreadsProfile? threadsProfileFromPosts(
  String handle,
  List<ThreadsPost> posts,
) {
  final key = (normaliseThreadsHandle(handle) ?? handle).trim().toLowerCase();
  if (key.isEmpty || posts.isEmpty) {
    return null;
  }
  final sample = posts.firstWhere(
    (p) => p.handle == key,
    orElse: () => posts.first,
  );
  return ThreadsProfile(
    pk: '',
    id: '',
    username: key,
    fullName: sample.authorName,
    isVerified: false,
    isPrivate: false,
    profilePicUrl: sample.avatarUrl ?? '',
    biography: '',
    followerCount: 0,
    followingCount: 0,
    mediaCount: 0,
    externalUrl: 'https://www.threads.com/@$key',
  );
}

/// Unwrap Threads' `l.threads.com/?u=` outbound redirect when present.
String unwrapThreadsOutboundUrl(String url) {
  final uri = Uri.tryParse(url);
  if (uri == null) {
    return url;
  }
  final host = uri.host.toLowerCase();
  if (host != 'l.threads.com' && host != 'l.threads.net') {
    return url;
  }
  final target = uri.queryParameters['u']?.trim();
  if (target == null || target.isEmpty) {
    return url;
  }
  return target;
}

/// Preview attachment on a Meta post JSON, or null when nothing useful is there.
ThreadsLinkCard? threadsLinkCardOf(Json post) {
  final card = post['text_post_app_info']['link_preview_attachment'];
  if (!card.exists) {
    return null;
  }

  final rawUrl = card['url'].string?.trim() ?? '';
  final title = card['title'].string?.trim();
  final description = card['description'].string?.trim();
  final image = card['image_url'].string?.trim();
  final display = card['display_url'].string?.trim();

  if (rawUrl.isEmpty &&
      (title == null || title.isEmpty) &&
      (image == null || image.isEmpty)) {
    return null;
  }

  final url = rawUrl.isEmpty
      ? (display == null || display.isEmpty
            ? ''
            : (display.startsWith('http') ? display : 'https://$display'))
      : unwrapThreadsOutboundUrl(rawUrl);
  if (url.isEmpty) {
    return null;
  }

  return ThreadsLinkCard(
    url: url,
    title: title == null || title.isEmpty ? null : title,
    description: description == null || description.isEmpty
        ? null
        : description,
    imageUrl: image == null || image.isEmpty ? null : image,
    providerName: display == null || display.isEmpty
        ? Uri.tryParse(url)?.host
        : display,
  );
}

/// A Threads profile, as the Xy server reports it.
///
/// Every field is read defensively: the upstream shape is Meta's, relayed, and
/// a field that stops being sent should leave a blank on the card rather than
/// take the screen down. Only [externalUrl] is documented as nullable, but none
/// of them are trusted to be there.
class ThreadsProfile {
  final String pk;
  final String id;
  final String username;
  final String fullName;
  final bool isVerified;
  final bool isPrivate;
  final String profilePicUrl;
  final String biography;
  final int followerCount;
  final int followingCount;
  final int mediaCount;
  final String? externalUrl;

  const ThreadsProfile({
    required this.pk,
    required this.id,
    required this.username,
    required this.fullName,
    required this.isVerified,
    required this.isPrivate,
    required this.profilePicUrl,
    required this.biography,
    required this.followerCount,
    required this.followingCount,
    required this.mediaCount,
    this.externalUrl,
  });

  factory ThreadsProfile.fromJson(Object? json) {
    final data = Json(json);
    final url = data['external_url'].string?.trim();

    return ThreadsProfile(
      pk: data['pk'].string ?? '',
      id: data['id'].string ?? '',
      username: data['username'].string ?? '',
      fullName: data['full_name'].string ?? '',
      isVerified: data['is_verified'].boolean ?? false,
      isPrivate: data['is_private'].boolean ?? false,
      profilePicUrl: data['profile_pic_url'].string ?? '',
      biography: data['biography'].string ?? '',
      followerCount: data['follower_count'].integer ?? 0,
      followingCount: data['following_count'].integer ?? 0,
      mediaCount: data['media_count'].integer ?? 0,
      externalUrl: url == null || url.isEmpty ? null : url,
    );
  }

  /// What to show as the name: the display name, or the handle when the
  /// profile has none.
  String get displayName =>
      fullName.trim().isEmpty ? username : fullName.trim();

  ThreadsAccount toAccount() => ThreadsAccount(
    handle: username,
    name: displayName,
    avatarUrl: profilePicUrl.isEmpty ? null : profilePicUrl,
  );
}

/// An account the reader follows, as the plugin thinks of it.
class ThreadsAccount {
  /// The handle, without the `@`.
  final String handle;
  final String name;
  final String? avatarUrl;

  const ThreadsAccount({
    required this.handle,
    required this.name,
    this.avatarUrl,
  });

  ThreadsAccount copyWith({String? name, String? avatarUrl}) => ThreadsAccount(
    handle: handle,
    name: name ?? this.name,
    avatarUrl: avatarUrl ?? this.avatarUrl,
  );
}

/// A handle as the route wants it: no `@`, no URL around it, lower case.
///
/// Readers paste all three — `@zuck`, `zuck`, and the address bar's
/// `https://www.threads.com/@zuck` — and every one of them means the same
/// account. Returns null when nothing usable is left.
String? normaliseThreadsHandle(String input) {
  var value = input.trim();
  if (value.isEmpty) {
    return null;
  }

  final uri = Uri.tryParse(value);
  if (uri != null && uri.host.contains('threads.')) {
    final segment = uri.pathSegments.where((e) => e.isNotEmpty).firstOrNull;
    value = segment ?? '';
  }

  value = value.replaceFirst(RegExp(r'^@+'), '').trim().toLowerCase();
  // Handles are letters, digits, underscores and dots — anything else came
  // from a paste that was not a handle at all.
  if (value.isEmpty || !RegExp(r'^[a-z0-9._]+$').hasMatch(value)) {
    return null;
  }
  return value;
}
