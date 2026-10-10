/// Pure readers for the JSON and server-rendered HTML Meta serves Threads in.
///
/// Free of I/O so every shape can be pinned by a test without a network: the
/// payloads are reverse-engineered, and the only defence against Meta reshaping
/// one is reading every field as if it might be gone.
library;

import 'dart:convert';

import 'package:html/parser.dart' as html_parser;
import 'package:xta/plugins/plugin_post_media.dart';
import 'package:xta/plugins/threads/threads_models.dart';
import 'package:xta/utils/json.dart';

const threadsWebBase = 'https://www.threads.com';

final _lsdTokenPattern = RegExp(r'"LSD",\[\],\{"token":"([^"]+)"\}');

/// One page of an account's posts: each thread's root, carrying how many
/// posts its author chained under it.
List<ThreadsPost> parseThreadsApiFeed(Object? json) {
  final root = Json(json);
  final buckets = root['threads'].list.isNotEmpty ? root['threads'].list : root['items'].list;
  return [for (final bucket in buckets) ?_bucketRoot(bucket)];
}

ThreadsPost? _bucketRoot(Json bucket) {
  final items = bucket['thread_items'].list;
  if (items.isEmpty) {
    return threadsPostFromApi(bucket['post']);
  }
  final root = threadsPostFromApi(items.first['post']);
  if (root == null) {
    return null;
  }
  final chain = [for (final item in items.skip(1)) ?threadsPostFromApi(item['post'])];
  return root.withSelfThreadCount(threadsSelfThreadLength(root, chain));
}

/// How many of [following] continue [root]'s author's own thread — the run of
/// posts by the same account straight after it. A reply from anyone else ends
/// the run: a For You bucket may append a popular reply, which is not part of
/// what the author wrote.
int threadsSelfThreadLength(ThreadsPost root, List<ThreadsPost> following) =>
    following.takeWhile((post) => post.handle == root.handle).length;

/// Guest GraphQL profile tab: `data.mediaData.threads` → same post shape as REST.
List<ThreadsPost> parseThreadsGraphqlFeed(Object? json) {
  final root = Json(json);
  final mediaThreads = root['data']['mediaData']['threads'].list;
  if (mediaThreads.isEmpty) {
    return parseThreadsApiFeed(json);
  }
  return parseThreadsApiFeed({
    'threads': [for (final thread in mediaThreads) thread.raw],
  });
}

/// LSD token embedded in Threads HTML for guest GraphQL.
String? extractThreadsLsd(String html) => _lsdTokenPattern.firstMatch(html)?.group(1);

/// Numeric Threads user id for [handle] from a profile page HTML blob.
String? extractThreadsUserIdFromHtml(String html, String handle) {
  final key = handle.trim().toLowerCase();
  if (key.isEmpty) return null;

  // Logged-out profile pages currently embed the owner on
  // BarcelonaProfileThreadsRoot as `props.user_id` — before any pk/username
  // blob. Without this, guest GraphQL never starts.
  final propsId = RegExp(r'"user_id"\s*:\s*"(\d+)"').firstMatch(html)?.group(1);
  if (propsId != null && propsId != '0') {
    return propsId;
  }

  return _userIdNearUsername(html, key) ?? _mostMentionedUserId(html);
}

String? _userIdNearUsername(String html, String key) {
  final escaped = RegExp.escape(key);
  final nearUsername = RegExp(
    '"username"\\s*:\\s*"$escaped".{0,480}?"pk"\\s*:\\s*"(\\d+)"',
    caseSensitive: false,
    dotAll: true,
  ).firstMatch(html);
  if (nearUsername != null) return nearUsername.group(1);

  return RegExp(
    '"pk"\\s*:\\s*"(\\d+)".{0,480}?"username"\\s*:\\s*"$escaped"',
    caseSensitive: false,
    dotAll: true,
  ).firstMatch(html)?.group(1);
}

/// Modal `userID` — ignore the logged-out stub `0`.
String? _mostMentionedUserId(String html) {
  final userIds = RegExp(
    r'"userID"\s*:\s*"(\d+)"',
  ).allMatches(html).map((m) => m.group(1)!).where((id) => id != '0').toList();
  if (userIds.isEmpty) return null;
  final counts = <String, int>{};
  for (final id in userIds) {
    counts[id] = (counts[id] ?? 0) + 1;
  }
  final ranked = counts.entries.toList()..sort((a, b) => b.value.compareTo(a.value));
  return ranked.first.key;
}

String? _metaContent(String html, String name) {
  final property = RegExp(
    '<meta[^>]+(?:property|name)="$name"[^>]+content="([^"]*)"',
    caseSensitive: false,
  ).firstMatch(html)?.group(1);
  if (property != null) {
    return property;
  }
  return RegExp(
    '<meta[^>]+content="([^"]*)"[^>]+(?:property|name)="$name"',
    caseSensitive: false,
  ).firstMatch(html)?.group(1);
}

String _decodeHtmlEntities(String value) => html_parser.parseFragment(value).text ?? value;

/// `5.7M` / `1.4K` / `380` → an int the profile card can show.
int? parseThreadsCompactCount(String? raw) {
  if (raw == null) {
    return null;
  }
  final match = RegExp(r'^([\d.,]+)\s*([KMB])?$', caseSensitive: false).firstMatch(raw.trim());
  if (match == null) {
    return null;
  }
  final number = double.tryParse(match.group(1)!.replaceAll(',', ''));
  if (number == null) {
    return null;
  }
  final scale = switch ((match.group(2) ?? '').toUpperCase()) {
    'K' => 1000,
    'M' => 1000000,
    'B' => 1000000000,
    _ => 1,
  };
  return (number * scale).round();
}

/// Public profile card from OG / meta tags on `threads.com/@handle` (no login).
ThreadsProfile? threadsProfileFromGuestHtml(String html, String handle) {
  final key = (normaliseThreadsHandle(handle) ?? handle).trim().toLowerCase();
  if (key.isEmpty) {
    return null;
  }

  final titleRaw = _metaContent(html, 'og:title') ?? _metaContent(html, 'twitter:title');
  final descRaw = _metaContent(html, 'og:description') ?? _metaContent(html, 'description');
  final imageRaw = _metaContent(html, 'og:image') ?? _metaContent(html, 'twitter:image');
  if (titleRaw == null && descRaw == null && imageRaw == null) {
    return null;
  }

  final title = titleRaw == null ? '' : _decodeHtmlEntities(titleRaw);
  final desc = _guestDescription(descRaw == null ? '' : _decodeHtmlEntities(descRaw));
  final image = imageRaw == null ? '' : _decodeHtmlEntities(imageRaw).replaceAll('&amp;', '&');

  final nameMatch = RegExp(r'^(.*?)\s*\(@', caseSensitive: false).firstMatch(title);
  final displayName = (nameMatch?.group(1)?.trim().isNotEmpty ?? false) ? nameMatch!.group(1)!.trim() : key;

  final pk = extractThreadsUserIdFromHtml(html, key) ?? '';
  return ThreadsProfile(
    pk: pk,
    id: pk,
    username: key,
    fullName: displayName,
    isVerified: false,
    isPrivate: false,
    profilePicUrl: image,
    biography: desc.biography,
    followerCount: desc.followers,
    followingCount: 0,
    mediaCount: desc.posts,
    externalUrl: '$threadsWebBase/@$key',
  );
}

/// `5.7M Followers • 380 Threads • Bio text` → its three parts.
({int followers, int posts, String biography}) _guestDescription(String desc) {
  var followers = 0;
  var posts = 0;
  var biography = '';
  for (final part in desc.split(RegExp(r'\s*[•·]\s*')).map((e) => e.trim())) {
    final count = RegExp(r'^([\d.,]+[KMB]?)\s+(Followers?|Threads?)$', caseSensitive: false).firstMatch(part);
    if (count == null) {
      if (part.isNotEmpty && biography.isEmpty) biography = part;
      continue;
    }
    final value = parseThreadsCompactCount(count.group(1));
    if (count.group(2)!.toLowerCase().startsWith('follower')) {
      followers = value ?? followers;
    } else {
      posts = value ?? posts;
    }
  }
  return (followers: followers, posts: posts, biography: biography);
}

/// An id Meta may send as a string or, from the app API, as a number.
String? _idOf(Json value) => value.string ?? (value.raw is num ? value.integer?.toString() : null);

DateTime? _takenAt(Json post) {
  final taken = post['taken_at'].integer;
  return taken == null ? null : DateTime.fromMillisecondsSinceEpoch(taken * 1000, isUtc: true).toLocal();
}

/// One Meta post object → [ThreadsPost], including pure reposts.
///
/// A repost often has an empty outer caption; the original lives under
/// `text_post_app_info.share_info.reposted_post`. Skipping those empty shells
/// is why followed accounts' reposts never showed up.
ThreadsPost? threadsPostFromApi(Json post) {
  if (!post.exists) return null;

  final reposted = post['text_post_app_info']['share_info']['reposted_post'];
  if (reposted.exists) {
    return _threadsRepostFromApi(outer: post, inner: reposted);
  }
  return _threadsOriginalFromApi(post);
}

ThreadsPost? _threadsRepostFromApi({required Json outer, required Json inner}) {
  final original = _threadsOriginalFromApi(inner);
  if (original == null) {
    return null;
  }

  final user = outer['user'];
  final reposter = (user['username'].string ?? '').trim().toLowerCase();
  if (reposter.isEmpty) {
    return null;
  }
  final reposterName = (user['full_name'].string ?? '').trim();
  return original.repostedBy(
    id: _idOf(outer['pk']) ?? outer['id'].string ?? original.id,
    handle: reposter,
    name: reposterName.isEmpty ? reposter : reposterName,
    at: _takenAt(outer),
  );
}

ThreadsPost? _threadsOriginalFromApi(Json post, {bool withQuote = true}) {
  if (!post.exists) return null;
  final user = post['user'];
  final handle = (user['username'].string ?? '').trim().toLowerCase();
  final text = (post['caption']['text'].string ?? '').trim();
  final media = threadsMediaOf(post);
  final linkCard = threadsLinkCardOf(post);
  final quoted = withQuote ? threadsQuotedPostOf(post) : null;
  if (handle.isEmpty || (text.isEmpty && media.isEmpty && linkCard == null && quoted == null)) {
    return null;
  }

  final code = post['code'].string;
  final pk = _idOf(post['pk']) ?? post['id'].string ?? code;
  if (pk == null || pk.isEmpty) return null;

  final tpi = post['text_post_app_info'];
  final replyTo = _threadsReplyToHandleOf(post);
  return ThreadsPost(
    id: pk,
    handle: handle,
    authorName: _authorNameOf(user, handle),
    avatarUrl: user['profile_pic_url'].string ?? user['hd_profile_pic_url_info']['url'].string,
    text: text,
    images: [for (final item in media) item.url],
    imageAspects: [for (final item in media) item.aspectRatio],
    imageAlts: [for (final item in media) item.alt],
    videoUrls: [for (final item in media) item.videoUrl],
    publishedAt: _takenAt(post),
    url: code == null ? null : '$threadsWebBase/@$handle/post/$code',
    likeCount: post['like_count'].integer,
    replyCount: tpi['direct_reply_count'].integer,
    repostCount: tpi['repost_count'].integer,
    quoteCount: tpi['quote_count'].integer,
    linkCard: linkCard,
    quoted: quoted,
    topicTag: threadsTopicTagOf(post),
    fragments: threadsFragmentsOf(post, text),
    isVerified: user['is_verified'].boolean ?? false,
    replyToHandle: replyTo,
    isReply: replyTo != null || _threadsIsReply(post),
  );
}

String _authorNameOf(Json user, String handle) {
  final name = (user['full_name'].string ?? '').trim();
  return name.isEmpty ? handle : name;
}

/// The post a quote embeds, one level deep.
///
/// Meta has carried it as `quoted_post` and, for a pasted Threads link, as
/// `quoted_attachment_post`; either is the same post shape.
ThreadsPost? threadsQuotedPostOf(Json post) {
  final share = post['text_post_app_info']['share_info'];
  for (final key in const ['quoted_post', 'quoted_attachment_post']) {
    final quoted = _threadsOriginalFromApi(share[key], withQuote: false);
    if (quoted != null) {
      return quoted;
    }
  }
  return null;
}

/// The topic a post was filed under, e.g. "Books".
String? threadsTopicTagOf(Json post) {
  final header = post['text_post_app_info']['tag_header'];
  final name = (header['display_name'].string ?? header['name'].string ?? '').trim();
  return name.isEmpty ? null : name.replaceFirst(RegExp(r'^#'), '');
}

/// Meta's own split of the caption into text, mentions, links and tags.
///
/// Only trusted when the pieces spell [caption] exactly: a split that does not
/// is a reshaped payload, and the pattern-based caption is the safer read.
List<ThreadsTextFragment> threadsFragmentsOf(Json post, String caption) {
  final raw = post['text_post_app_info']['text_fragments']['fragments'].list;
  if (raw.isEmpty) {
    return const [];
  }
  final fragments = [for (final fragment in raw) ?_fragmentOf(fragment)];
  final spelled = fragments.map((f) => f.text).join().trim();
  final meaningful = fragments.any((f) => f.kind != ThreadsFragmentKind.text);
  return spelled == caption && meaningful ? fragments : const [];
}

ThreadsTextFragment? _fragmentOf(Json fragment) {
  final text = fragment['plaintext'].string;
  if (text == null) {
    return null;
  }
  final target = switch (fragment['fragment_type'].string) {
    'mention' => (ThreadsFragmentKind.mention, fragment['mention_fragment']['mentioned_user']['username'].string),
    'link' => (ThreadsFragmentKind.link, _linkTarget(fragment['link_fragment']['uri'].string)),
    'tag' => (ThreadsFragmentKind.tag, _tagTarget(fragment, text)),
    _ => (ThreadsFragmentKind.text, null),
  };
  final (kind, value) = target;
  if (kind == ThreadsFragmentKind.text || value == null || value.isEmpty) {
    return ThreadsTextFragment(ThreadsFragmentKind.text, text);
  }
  return ThreadsTextFragment(kind, text, value);
}

String? _linkTarget(String? uri) {
  final value = uri?.trim();
  if (value == null || value.isEmpty) {
    return null;
  }
  final unwrapped = unwrapThreadsOutboundUrl(value);
  return Uri.tryParse(unwrapped)?.hasScheme == true ? unwrapped : null;
}

String? _tagTarget(Json fragment, String text) {
  final tag = fragment['tag_fragment'];
  final name = tag['display_name'].string ?? tag['tag_name'].string ?? text;
  final value = name.trim().replaceFirst(RegExp(r'^#'), '');
  return value.isEmpty ? null : value;
}

String? _threadsReplyToHandleOf(Json post) {
  final handle = post['text_post_app_info']['reply_to_author']['username'].string?.trim();
  if (handle == null || handle.isEmpty) {
    return null;
  }
  return handle.toLowerCase();
}

bool _threadsIsReply(Json post) {
  final tpi = post['text_post_app_info'];
  return tpi['is_reply'].boolean == true || tpi['reply_to_author'].exists;
}

({String? url, double? aspect}) _bestCandidate(Json versions) {
  String? best;
  var bestArea = -1;
  double? aspect;
  for (final candidate in versions['candidates'].list) {
    final url = candidate['url'].string;
    if (url == null || url.isEmpty) {
      continue;
    }
    final w = candidate['width'].integer ?? 0;
    final h = candidate['height'].integer ?? 0;
    final area = w * h;
    if (area >= bestArea) {
      bestArea = area;
      best = url;
      if (w > 0 && h > 0) {
        aspect = w / h;
      }
    }
  }
  return (url: best, aspect: aspect);
}

/// The largest video rendition, or the first when none says how big it is.
String? _bestVideo(Json media) {
  String? best;
  var bestArea = -1;
  for (final version in media['video_versions'].list) {
    final url = version['url'].string;
    if (url == null || url.isEmpty) {
      continue;
    }
    final area = (version['width'].integer ?? 0) * (version['height'].integer ?? 0);
    if (area > bestArea) {
      bestArea = area;
      best = url;
    }
  }
  return best;
}

/// Every picture or video a post shows, posters standing in for videos.
List<PluginMediaItem> threadsMediaOf(Json post) {
  final items = <PluginMediaItem>[];
  final seen = <String>{};

  void add(Json media) {
    final picked = _bestCandidate(media['image_versions2']);
    final url = picked.url;
    if (url == null || url.isEmpty || !seen.add(url)) {
      return;
    }
    final fallback = pluginMediaAspectFrom({
      'width': media['original_width'].integer ?? post['original_width'].integer,
      'height': media['original_height'].integer ?? post['original_height'].integer,
    });
    final alt = media['accessibility_caption'].string?.trim();
    final video = _bestVideo(media);
    items.add(
      PluginMediaItem(
        url: url,
        aspectRatio: picked.aspect ?? fallback,
        alt: alt == null || alt.isEmpty ? null : alt,
        isVideo: video != null,
        videoUrl: video,
      ),
    );
  }

  final carousel = post['carousel_media'].list;
  for (final media in carousel.isNotEmpty ? carousel : [post]) {
    add(media);
  }
  return items;
}

ThreadsProfile? threadsProfileFromUserJson(Json user) {
  if (!user.exists) return null;
  final username = (user['username'].string ?? '').trim();
  if (username.isEmpty) return null;
  final pk = _idOf(user['pk']) ?? user['id'].string ?? user['pk_id'].string ?? '';
  final url = user['external_url'].string?.trim();
  return ThreadsProfile(
    pk: pk,
    id: user['id'].string ?? pk,
    username: username,
    fullName: user['full_name'].string ?? '',
    isVerified: user['is_verified'].boolean ?? false,
    isPrivate: user['is_private'].boolean ?? false,
    profilePicUrl: user['profile_pic_url'].string ?? user['hd_profile_pic_url_info']['url'].string ?? '',
    biography: user['biography'].string ?? '',
    followerCount: user['follower_count'].integer ?? 0,
    followingCount: user['following_count'].integer ?? 0,
    mediaCount: user['media_count'].integer ?? 0,
    externalUrl: url == null || url.isEmpty ? null : url,
  );
}

/// Every `thread_items` list a server-rendered page embeds, in page order.
///
/// Each list is one chain: a thread's root and its author's continuation on a
/// profile, or the conversation leading to a post and each reply thread under
/// it on a post page. A post repeated across blobs is kept where it first
/// appeared, so a chain is never shown twice.
List<List<ThreadsPost>> threadsSsrChains(String body) {
  final chains = <List<ThreadsPost>>[];
  final seen = <String>{};
  for (final script in html_parser.parse(body).querySelectorAll('script[data-sjs]')) {
    final text = script.text.trim();
    if (text.isEmpty || !text.contains('thread_items')) continue;
    Object? decoded;
    try {
      decoded = jsonDecode(text);
    } catch (_) {
      continue;
    }
    _collectSsrChains(decoded, chains, seen);
  }
  return chains;
}

void _collectSsrChains(Object? node, List<List<ThreadsPost>> out, Set<String> seen) {
  if (node is List) {
    for (final value in node) {
      _collectSsrChains(value, out, seen);
    }
    return;
  }
  if (node is! Map) {
    return;
  }
  final items = node['thread_items'];
  if (items is List && items.isNotEmpty) {
    final chain = [
      for (final item in Json(items).list)
        if (threadsPostFromApi(item['post']) case final post? when seen.add(post.id)) post,
    ];
    if (chain.isNotEmpty) out.add(chain);
  }
  for (final value in node.values) {
    _collectSsrChains(value, out, seen);
  }
}

/// A profile page's threads: one card per chain root, keeping the chain's
/// length. Reposts keep the original author on [ThreadsPost.handle]; the
/// profile owner is [ThreadsPost.repostedByHandle], so either one matches.
List<ThreadsPost> parseThreadsSsrHtml(String body, String handle) => [
  for (final chain in threadsSsrChains(body))
    if (handle.isEmpty || chain.first.handle == handle || chain.first.repostedByHandle == handle)
      chain.first.withSelfThreadCount(threadsSelfThreadLength(chain.first, chain.sublist(1))),
];

/// Every post embedded in a Threads post page (root + replies), flattened.
List<ThreadsPost> parseThreadsSsrThread(String body) =>
    threadsSsrChains(body).expand((chain) => chain).toList(growable: false);

/// A profile's replies page: each of [handle]'s replies, without the posts
/// from other people the page shows them under.
List<ThreadsPost> parseThreadsSsrReplies(String body, String handle) => [
  for (final chain in threadsSsrChains(body))
    for (final (index, post) in chain.indexed)
      if (post.handle == handle && (index > 0 || post.isReply)) post,
];
