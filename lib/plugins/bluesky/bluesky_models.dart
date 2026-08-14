import 'dart:convert';

import 'package:xta/plugins/bluesky/bluesky_facets.dart';
import 'package:xta/utils/json.dart';

/// A link card carried by `app.bsky.embed.external`.
class BlueskyLinkCard {
  final String url;
  final String? title;
  final String? description;
  final String? imageUrl;

  const BlueskyLinkCard({
    required this.url,
    this.title,
    this.description,
    this.imageUrl,
  });

  bool get hasImage => imageUrl != null && imageUrl!.isNotEmpty;

  Map<String, dynamic> toJson() => {
    'url': url,
    'title': title,
    'description': description,
    'imageUrl': imageUrl,
  };

  factory BlueskyLinkCard.fromSnapshot(Object? raw) {
    final json = raw is Map
        ? Map<String, dynamic>.from(raw)
        : const <String, dynamic>{};
    return BlueskyLinkCard(
      url: json['url'] as String? ?? '',
      title: json['title'] as String?,
      description: json['description'] as String?,
      imageUrl: json['imageUrl'] as String?,
    );
  }
}

/// One Bluesky post, as much of it as a public feed view carries.
class BlueskyPost {
  final String uri;
  final String cid;
  final String handle;
  final String did;
  final String authorName;
  final String? avatarUrl;
  final String text;
  final List<String> images;
  final List<BlueskyFacet> facets;
  final DateTime? publishedAt;

  /// Where the post lives on bsky.app, for opening it there.
  final String url;

  final int replyCount;
  final int repostCount;
  final int likeCount;
  final int quoteCount;

  /// When this feed item is a repost, the person who reposted it.
  final String? repostedByName;
  final String? repostedByHandle;

  /// Nested quoted post, when the embed is a record (with or without media).
  final BlueskyPost? quotedPost;

  final BlueskyLinkCard? linkCard;

  const BlueskyPost({
    required this.uri,
    required this.cid,
    required this.handle,
    required this.did,
    required this.authorName,
    required this.text,
    required this.url,
    this.avatarUrl,
    this.images = const [],
    this.facets = const [],
    this.publishedAt,
    this.replyCount = 0,
    this.repostCount = 0,
    this.likeCount = 0,
    this.quoteCount = 0,
    this.repostedByName,
    this.repostedByHandle,
    this.quotedPost,
    this.linkCard,
  });

  bool get hasMedia => images.isNotEmpty;
  bool get isRepost => repostedByHandle != null && repostedByHandle!.isNotEmpty;
  bool get hasQuote => quotedPost != null;
  bool get hasLinkCard => linkCard != null;

  Map<String, dynamic> toJson() => {
    'uri': uri,
    'cid': cid,
    'handle': handle,
    'did': did,
    'authorName': authorName,
    'avatarUrl': avatarUrl,
    'text': text,
    'images': images,
    'facets': facets.map((f) => f.toJson()).toList(),
    'publishedAt': publishedAt?.toIso8601String(),
    'url': url,
    'replyCount': replyCount,
    'repostCount': repostCount,
    'likeCount': likeCount,
    'quoteCount': quoteCount,
    'repostedByName': repostedByName,
    'repostedByHandle': repostedByHandle,
    'quotedPost': quotedPost?.toJson(),
    'linkCard': linkCard?.toJson(),
  };

  factory BlueskyPost.fromSnapshot(Object? raw) {
    final json = raw is Map
        ? Map<String, dynamic>.from(raw)
        : const <String, dynamic>{};
    final handle = json['handle'] as String? ?? '';
    final linkRaw = json['linkCard'];
    final linkCard = linkRaw == null
        ? null
        : BlueskyLinkCard.fromSnapshot(linkRaw);
    final quoteRaw = json['quotedPost'];
    final quoted = quoteRaw == null ? null : BlueskyPost.fromSnapshot(quoteRaw);

    return BlueskyPost(
      uri: json['uri'] as String? ?? '',
      cid: json['cid'] as String? ?? '',
      handle: handle,
      did: json['did'] as String? ?? '',
      authorName: json['authorName'] as String? ?? handle,
      avatarUrl: json['avatarUrl'] as String?,
      text: json['text'] as String? ?? '',
      images:
          (json['images'] as List?)?.whereType<String>().toList(
            growable: false,
          ) ??
          const [],
      facets:
          (json['facets'] as List?)
              ?.map(BlueskyFacet.fromSnapshot)
              .where((f) => f.value.isNotEmpty && f.byteEnd > f.byteStart)
              .toList(growable: false) ??
          const [],
      publishedAt: DateTime.tryParse(
        json['publishedAt'] as String? ?? '',
      )?.toLocal(),
      url: json['url'] as String? ?? '',
      replyCount: _snapshotCount(json['replyCount']),
      repostCount: _snapshotCount(json['repostCount']),
      likeCount: _snapshotCount(json['likeCount']),
      quoteCount: _snapshotCount(json['quoteCount']),
      repostedByName: json['repostedByName'] as String?,
      repostedByHandle: json['repostedByHandle'] as String?,
      quotedPost: quoted == null || quoted.uri.isEmpty ? null : quoted,
      linkCard: linkCard == null || linkCard.url.isEmpty ? null : linkCard,
    );
  }

  static List<BlueskyPost> listFromPrefs(String? raw) {
    if (raw == null || raw.isEmpty) {
      return const [];
    }
    try {
      final decoded = jsonDecode(raw);
      if (decoded is! List) {
        return const [];
      }
      return decoded
          .whereType<Map>()
          .map((e) => BlueskyPost.fromSnapshot(Map<String, dynamic>.from(e)))
          .where((e) => e.uri.isNotEmpty)
          .toList(growable: false);
    } catch (_) {
      return const [];
    }
  }

  static String listToPrefs(List<BlueskyPost> posts) =>
      jsonEncode(posts.map((e) => e.toJson()).toList());
}

int _snapshotCount(Object? value) => value is num ? value.toInt() : 0;

/// Ancestors (root → parent), the focal post, and reply descendants.
class BlueskyThread {
  final BlueskyPost post;
  final List<BlueskyPost> ancestors;
  final List<BlueskyPost> replies;

  const BlueskyThread({
    required this.post,
    this.ancestors = const [],
    this.replies = const [],
  });
}

/// A Bluesky profile, as the public AppView reports it.
class BlueskyProfile {
  final String did;
  final String handle;
  final String displayName;
  final String? avatarUrl;
  final String description;
  final int followersCount;
  final int followsCount;
  final int postsCount;

  const BlueskyProfile({
    required this.did,
    required this.handle,
    required this.displayName,
    required this.description,
    this.avatarUrl,
    this.followersCount = 0,
    this.followsCount = 0,
    this.postsCount = 0,
  });

  factory BlueskyProfile.fromJson(Object? json) {
    final data = Json(json);
    final handle = data['handle'].string?.trim() ?? '';
    final name = data['displayName'].string?.trim();
    final avatar = data['avatar'].string?.trim();

    return BlueskyProfile(
      did: data['did'].string ?? '',
      handle: handle,
      displayName: (name == null || name.isEmpty) ? handle : name,
      avatarUrl: avatar == null || avatar.isEmpty ? null : avatar,
      description: data['description'].string?.trim() ?? '',
      followersCount: data['followersCount'].integer ?? 0,
      followsCount: data['followsCount'].integer ?? 0,
      postsCount: data['postsCount'].integer ?? 0,
    );
  }

  BlueskyAccount toAccount() => BlueskyAccount(
    handle: handle,
    name: displayName,
    avatarUrl: avatarUrl,
    did: did.isEmpty ? null : did,
  );
}

/// An account the reader follows locally — not a Bluesky follow graph edge.
class BlueskyAccount {
  /// Handle without `@`, or a `did:plc:…` when that is what was stored.
  final String handle;
  final String name;
  final String? avatarUrl;
  final String? did;

  const BlueskyAccount({
    required this.handle,
    required this.name,
    this.avatarUrl,
    this.did,
  });

  /// What the AppView wants as `actor`: prefer the DID when we have one.
  String get actor => (did != null && did!.isNotEmpty) ? did! : handle;

  BlueskyAccount copyWith({String? name, String? avatarUrl, String? did}) =>
      BlueskyAccount(
        handle: handle,
        name: name ?? this.name,
        avatarUrl: avatarUrl ?? this.avatarUrl,
        did: did ?? this.did,
      );
}

/// Official public AppView — read-only xrpc without a Bluesky login.
const kBlueskyDefaultAppView = 'https://public.api.bsky.app';

/// Strip trailing slash; require http(s) and a hostname. Null when unusable.
String? normaliseBlueskyAppView(String input) {
  var value = input.trim();
  if (value.isEmpty) {
    return null;
  }
  if (!value.contains('://')) {
    value = 'https://$value';
  }
  final uri = Uri.tryParse(value);
  final host = uri?.host.trim() ?? '';
  if (uri == null ||
      (uri.scheme != 'https' && uri.scheme != 'http') ||
      host.isEmpty ||
      host.contains(' ') ||
      host.contains('%20')) {
    return null;
  }
  final path = uri.path.replaceAll(RegExp(r'/+$'), '');
  return Uri(
    scheme: uri.scheme,
    host: host,
    port: uri.hasPort ? uri.port : null,
    path: path,
  ).toString();
}

/// Resolved AppView URL from prefs, always falling back to the working default.
String blueskyAppViewFromPrefs(String? raw) =>
    normaliseBlueskyAppView(raw ?? '') ?? kBlueskyDefaultAppView;

/// A handle, profile URL, or DID as the plugin wants it.
///
/// Accepts `@alice.bsky.social`, bare handles with dots, `bsky.app/profile/…`
/// URLs, and `did:plc:…`. Returns null when nothing usable is left.
String? normaliseBlueskyHandle(String input) {
  var value = input.trim();
  if (value.isEmpty) {
    return null;
  }

  final uri = Uri.tryParse(value);
  if (uri != null && (uri.host == 'bsky.app' || uri.host == 'www.bsky.app')) {
    final segments = uri.pathSegments.where((e) => e.isNotEmpty).toList();
    if (segments.length >= 2 && segments.first == 'profile') {
      value = segments[1];
    }
  }

  value = value.replaceFirst(RegExp(r'^@+'), '').trim();
  if (value.isEmpty) {
    return null;
  }

  final lower = value.toLowerCase();
  if (RegExp(r'^did:plc:[a-z2-7]+$').hasMatch(lower)) {
    return lower;
  }

  // Handles are DNS-like: letters, digits, hyphens, dots; at least one dot.
  if (!RegExp(
    r'^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$',
  ).hasMatch(lower)) {
    return null;
  }
  return lower;
}

/// Web URL for a post: handle + rkey of an `at://` URI.
String? blueskyWebUrl({required String handle, required String atUri}) {
  if (handle.isEmpty) {
    return null;
  }
  final rkey = blueskyRkeyOf(atUri);
  if (rkey == null) {
    return null;
  }
  return 'https://bsky.app/profile/$handle/post/$rkey';
}

/// The record key of an `at://did/…/collection/rkey` URI.
String? blueskyRkeyOf(String atUri) {
  if (!atUri.startsWith('at://')) {
    return null;
  }
  final parts = atUri.substring('at://'.length).split('/');
  if (parts.length < 3) {
    return null;
  }
  final rkey = parts.last.trim();
  return rkey.isEmpty ? null : rkey;
}

/// Images from a feed post's view embed (thumb preferred, else fullsize).
List<String> blueskyImagesOf(Json post) {
  final urls = <String>[];

  void addFrom(Json images) {
    for (final image in images.list) {
      final url = image['thumb'].string ?? image['fullsize'].string;
      if (url != null && url.isNotEmpty && !urls.contains(url)) {
        urls.add(url);
      }
    }
  }

  final embed = post['embed'];
  addFrom(embed['images']);
  addFrom(embed['media']['images']);

  return urls;
}

BlueskyLinkCard? blueskyLinkCardOf(Json post) {
  final external = post['embed']['external'];
  if (!external.exists) {
    // recordWithMedia may nest the external under media.
    final nested = post['embed']['media']['external'];
    if (!nested.exists) {
      return null;
    }
    return _linkCardFrom(nested);
  }
  return _linkCardFrom(external);
}

BlueskyLinkCard? _linkCardFrom(Json external) {
  final url = external['uri'].string?.trim();
  if (url == null || url.isEmpty) {
    return null;
  }
  final title = external['title'].string?.trim();
  final description = external['description'].string?.trim();
  final thumb = external['thumb'].string?.trim();
  return BlueskyLinkCard(
    url: url,
    title: title == null || title.isEmpty ? null : title,
    description: description == null || description.isEmpty
        ? null
        : description,
    imageUrl: thumb == null || thumb.isEmpty ? null : thumb,
  );
}

/// Quoted post from `embed.record` / `embed.recordWithMedia.record`.
BlueskyPost? blueskyQuotedPostOf(Json post) {
  var record = post['embed']['record'];
  if (!record.exists) {
    return null;
  }
  // recordWithMedia wraps the quote as embed.record.record.
  if (record['record'].exists) {
    record = record['record'];
  }
  final type = record['\$type'].string ?? '';
  if (type.contains('viewNotFound') ||
      type.contains('viewBlocked') ||
      type.contains('viewDetached')) {
    return null;
  }
  return blueskyPostFromView(record.raw, allowEmpty: true);
}

/// Turns a PostView (or embed viewRecord) into a [BlueskyPost].
BlueskyPost? blueskyPostFromView(
  Object? json, {
  String? repostedByName,
  String? repostedByHandle,
  bool allowEmpty = false,
  bool parseQuote = true,
}) {
  final post = Json(json);
  final uri = post['uri'].string;
  if (uri == null || uri.isEmpty) {
    return null;
  }

  final author = post['author'];
  final handle = author['handle'].string?.trim() ?? '';
  final did = author['did'].string ?? '';
  final name = author['displayName'].string?.trim();

  // viewRecord carries text under `value`; PostView under `record`.
  final record = post['record'].exists ? post['record'] : post['value'];
  final text = record['text'].string?.trim() ?? '';
  final facets = blueskyFacetsOf(record);
  final images = blueskyImagesOf(post);
  final linkCard = blueskyLinkCardOf(post);
  final quoted = parseQuote ? blueskyQuotedPostOf(post) : null;

  if (!allowEmpty &&
      text.isEmpty &&
      images.isEmpty &&
      quoted == null &&
      linkCard == null) {
    return null;
  }

  final url = blueskyWebUrl(handle: handle, atUri: uri);
  if (url == null) {
    return null;
  }

  final avatar = author['avatar'].string?.trim();
  final created = record['createdAt'].string ?? post['indexedAt'].string;

  return BlueskyPost(
    uri: uri,
    cid: post['cid'].string ?? '',
    handle: handle,
    did: did,
    authorName: (name == null || name.isEmpty) ? handle : name,
    avatarUrl: avatar == null || avatar.isEmpty ? null : avatar,
    text: text,
    images: images,
    facets: facets,
    publishedAt: DateTime.tryParse(created ?? '')?.toLocal(),
    url: url,
    replyCount: post['replyCount'].integer ?? 0,
    repostCount: post['repostCount'].integer ?? 0,
    likeCount: post['likeCount'].integer ?? 0,
    quoteCount: post['quoteCount'].integer ?? 0,
    repostedByName: repostedByName,
    repostedByHandle: repostedByHandle,
    quotedPost: quoted,
    linkCard: linkCard,
  );
}

/// Turns one feed item's `post` (+ optional repost reason) into a [BlueskyPost].
BlueskyPost? blueskyPostFromFeedItem(Object? item) {
  final root = Json(item);
  final post = root['post'].exists ? root['post'] : root;

  String? repostName;
  String? repostHandle;
  final reason = root['reason'];
  final reasonType = reason['\$type'].string ?? '';
  if (reasonType.contains('reasonRepost')) {
    final by = reason['by'];
    repostHandle = by['handle'].string?.trim();
    final name = by['displayName'].string?.trim();
    repostName = (name == null || name.isEmpty) ? repostHandle : name;
  }

  return blueskyPostFromView(
    post.raw,
    repostedByName: repostName,
    repostedByHandle: repostHandle,
  );
}

/// Pure parse of `getAuthorFeed` JSON into posts (cursor ignored).
List<BlueskyPost> parseBlueskyFeed(Object? json) {
  final feed = Json(json)['feed'];
  return [for (final item in feed.list) ?blueskyPostFromFeedItem(item)];
}

/// Pure parse of `searchPosts` JSON (`posts` is a list of PostView).
List<BlueskyPost> parseBlueskySearchPosts(Object? json) {
  final posts = Json(json)['posts'];
  return [for (final post in posts.list) ?blueskyPostFromView(post.raw)];
}

/// Flattens `getPostThread` into ancestors, focal post, and replies.
BlueskyThread? parseBlueskyThread(Object? json) {
  final thread = Json(json)['thread'];
  if (!thread.exists) {
    return null;
  }

  final focal = _threadViewPost(thread);
  if (focal == null) {
    return null;
  }

  final ancestors = <BlueskyPost>[];
  var parent = thread['parent'];
  while (parent.exists) {
    final post = _threadViewPost(parent);
    if (post != null) {
      ancestors.insert(0, post);
    }
    parent = parent['parent'];
  }

  final replies = <BlueskyPost>[];
  _collectReplies(thread['replies'], replies, depth: 0);

  return BlueskyThread(post: focal, ancestors: ancestors, replies: replies);
}

BlueskyPost? _threadViewPost(Json node) {
  final type = node['\$type'].string ?? '';
  if (type.contains('notFoundPost') || type.contains('blockedPost')) {
    return null;
  }
  if (!node['post'].exists) {
    return null;
  }
  return blueskyPostFromView(node['post'].raw);
}

void _collectReplies(
  Json replies,
  List<BlueskyPost> out, {
  required int depth,
}) {
  if (depth > 8) {
    return;
  }
  for (final reply in replies.list) {
    final post = _threadViewPost(reply);
    if (post != null) {
      out.add(post);
    }
    _collectReplies(reply['replies'], out, depth: depth + 1);
  }
}

/// One page of `app.bsky.graph.getFollows`.
class BlueskyFollowsPage {
  final List<BlueskyProfile> follows;
  final String? cursor;

  const BlueskyFollowsPage({required this.follows, this.cursor});
}

/// One page of `app.bsky.graph.getFollowers`.
class BlueskyFollowersPage {
  final List<BlueskyProfile> followers;
  final String? cursor;

  const BlueskyFollowersPage({required this.followers, this.cursor});
}

/// Metadata for a public Bluesky list (`app.bsky.graph.defs#listView`).
class BlueskyListInfo {
  final String uri;
  final String name;
  final int itemCount;

  const BlueskyListInfo({
    required this.uri,
    required this.name,
    this.itemCount = 0,
  });

  factory BlueskyListInfo.fromJson(Object? json) {
    final data = Json(json);
    return BlueskyListInfo(
      uri: data['uri'].string?.trim() ?? '',
      name: data['name'].string?.trim() ?? '',
      itemCount: data['listItemCount'].integer ?? 0,
    );
  }
}

/// One page of lists created by an actor.
class BlueskyListsPage {
  final List<BlueskyListInfo> lists;
  final String? cursor;

  const BlueskyListsPage({required this.lists, this.cursor});
}

/// One page of list members from `app.bsky.graph.getList`.
class BlueskyListMembersPage {
  final BlueskyListInfo? list;
  final List<BlueskyProfile> members;
  final String? cursor;

  const BlueskyListMembersPage({required this.members, this.list, this.cursor});
}

/// A list identified by AT-URI, or by profile + rkey from a bsky.app URL.
class BlueskyListRef {
  final String? atUri;
  final String? actor;
  final String? rkey;

  const BlueskyListRef.atUri(this.atUri) : actor = null, rkey = null;

  const BlueskyListRef.web({required this.actor, required this.rkey})
    : atUri = null;
}

/// Parses a public list URL or `at://…/app.bsky.graph.list/…` URI.
BlueskyListRef? parseBlueskyListRef(String input) {
  final value = input.trim();
  if (value.isEmpty) {
    return null;
  }

  if (value.startsWith('at://') && value.contains('/app.bsky.graph.list/')) {
    return BlueskyListRef.atUri(value);
  }

  final uri = Uri.tryParse(value);
  if (uri == null || (uri.host != 'bsky.app' && uri.host != 'www.bsky.app')) {
    return null;
  }

  final segments = uri.pathSegments.where((e) => e.isNotEmpty).toList();
  if (segments.length >= 4 &&
      segments[0] == 'profile' &&
      segments[2] == 'lists') {
    final actor = segments[1].trim();
    final rkey = segments[3].trim();
    if (actor.isEmpty || rkey.isEmpty) {
      return null;
    }
    return BlueskyListRef.web(actor: actor, rkey: rkey);
  }

  return null;
}

/// A starter pack identified by AT-URI, or by handle + rkey from a bsky.app URL.
class BlueskyStarterPackRef {
  final String? atUri;
  final String? actor;
  final String? rkey;

  const BlueskyStarterPackRef.atUri(this.atUri) : actor = null, rkey = null;

  const BlueskyStarterPackRef.web({required this.actor, required this.rkey})
    : atUri = null;
}

/// Parses a public starter-pack URL or `at://…/app.bsky.graph.starterpack/…`.
///
/// Short `go.bsky.app` links are skipped — they need a redirect we do not follow.
BlueskyStarterPackRef? parseBlueskyStarterPackRef(String input) {
  final value = input.trim();
  if (value.isEmpty) {
    return null;
  }

  if (value.startsWith('at://') &&
      value.contains('/app.bsky.graph.starterpack/')) {
    return BlueskyStarterPackRef.atUri(value);
  }

  final uri = Uri.tryParse(value);
  if (uri == null || (uri.host != 'bsky.app' && uri.host != 'www.bsky.app')) {
    return null;
  }

  final segments = uri.pathSegments.where((e) => e.isNotEmpty).toList();
  if (segments.length >= 3 && segments[0] == 'starter-pack') {
    final actor = segments[1].trim();
    final rkey = segments[2].trim();
    if (actor.isEmpty || rkey.isEmpty) {
      return null;
    }
    return BlueskyStarterPackRef.web(actor: actor, rkey: rkey);
  }
  return null;
}

/// List AT-URI a starter pack points at, if the AppView included one.
String? starterPackListUri(Object? json) {
  final pack = Json(json)['starterPack'];
  final list = pack['list'];
  return list.string ?? list['uri'].string ?? pack['record']['list'].string;
}

BlueskyFollowsPage parseBlueskyFollowsPage(Object? json) {
  final root = Json(json);
  return BlueskyFollowsPage(
    follows:
        [
              for (final follow in root['follows'].list)
                BlueskyProfile.fromJson(follow.raw),
            ]
            .where((p) => p.handle.isNotEmpty || p.did.isNotEmpty)
            .toList(growable: false),
    cursor: root['cursor'].string,
  );
}

BlueskyFollowersPage parseBlueskyFollowersPage(Object? json) {
  final root = Json(json);
  return BlueskyFollowersPage(
    followers:
        [
              for (final follower in root['followers'].list)
                BlueskyProfile.fromJson(follower.raw),
            ]
            .where((p) => p.handle.isNotEmpty || p.did.isNotEmpty)
            .toList(growable: false),
    cursor: root['cursor'].string,
  );
}

BlueskyListsPage parseBlueskyListsPage(Object? json) {
  final root = Json(json);
  return BlueskyListsPage(
    lists: [
      for (final list in root['lists'].list)
        if (BlueskyListInfo.fromJson(list.raw).uri.isNotEmpty)
          BlueskyListInfo.fromJson(list.raw),
    ],
    cursor: root['cursor'].string,
  );
}

BlueskyListMembersPage parseBlueskyListMembersPage(Object? json) {
  final root = Json(json);
  final listRaw = root['list'].raw;
  return BlueskyListMembersPage(
    list: listRaw == null ? null : BlueskyListInfo.fromJson(listRaw),
    members:
        [
              for (final item in root['items'].list)
                BlueskyProfile.fromJson(item['subject'].raw),
            ]
            .where((p) => p.handle.isNotEmpty || p.did.isNotEmpty)
            .toList(growable: false),
    cursor: root['cursor'].string,
  );
}
