import 'package:xta/plugins/mastodon/mastodon_models.dart';
import 'package:xta/plugins/pixiv/pixiv_links.dart';

/// A plugin-owned web URL that XTA can open on a native screen.
sealed class PluginLink {
  const PluginLink();
}

class BlueskyProfileLink extends PluginLink {
  final String actor;

  const BlueskyProfileLink(this.actor);
}

class BlueskyPostLink extends PluginLink {
  final String actor;
  final String rkey;

  const BlueskyPostLink({required this.actor, required this.rkey});

  String get atUri => 'at://$actor/app.bsky.feed.post/$rkey';
}

class ThreadsProfileLink extends PluginLink {
  final String handle;

  const ThreadsProfileLink(this.handle);
}

class ThreadsPostLink extends PluginLink {
  final String url;
  final String handle;

  const ThreadsPostLink({required this.url, required this.handle});
}

class InstagramProfileLink extends PluginLink {
  final String handle;

  const InstagramProfileLink(this.handle);
}

class TikTokProfileLink extends PluginLink {
  final String handle;

  const TikTokProfileLink(this.handle);
}

class TikTokVideoLink extends PluginLink {
  final String id;
  final String? handle;

  const TikTokVideoLink({required this.id, this.handle});
}

class RedditSubredditLink extends PluginLink {
  final String name;

  const RedditSubredditLink(this.name);
}

class RedditUserLink extends PluginLink {
  final String name;

  const RedditUserLink(this.name);
}

class RedditThreadLink extends PluginLink {
  final String id;
  final String subreddit;
  final String permalink;
  final String? title;

  const RedditThreadLink({
    required this.id,
    required this.subreddit,
    required this.permalink,
    this.title,
  });
}

class MastodonProfileLink extends PluginLink {
  final String acct;

  const MastodonProfileLink(this.acct);
}

class MastodonStatusLink extends PluginLink {
  final String acct;
  final String statusId;
  final String url;

  const MastodonStatusLink({
    required this.acct,
    required this.statusId,
    required this.url,
  });
}

class PixivWebLink extends PluginLink {
  final PixivLinkRef ref;

  const PixivWebLink(this.ref);
}

/// Hosts XTA will treat as Mastodon when opening a `/@user` link.
Set<String> mastodonLinkHosts(Iterable<String> instances) => {
  for (final instance in instances)
    if (mastodonInstanceDomain(instance) case final host?) host.toLowerCase(),
};

/// Tries each plugin parser. [mastodonHosts] must be hostnames, not URLs.
PluginLink? parsePluginLink(
  String url, {
  Set<String> mastodonHosts = const {},
}) {
  return parseBlueskyLink(url) ??
      parseThreadsLink(url) ??
      parseInstagramLink(url) ??
      parseTikTokLink(url) ??
      parseRedditLink(url) ??
      parsePixivWebLink(url) ??
      parseMastodonLink(url, knownHosts: mastodonHosts);
}

PluginLink? parseBlueskyLink(String url) {
  final uri = httpUri(url);
  if (uri == null || !_hostIs(uri.host, const {'bsky.app'})) {
    return null;
  }
  final segments = pathSegments(uri);
  if (segments.length < 2 || segments[0] != 'profile') {
    return null;
  }
  final actor = segments[1];
  if (actor.isEmpty) {
    return null;
  }
  if (segments.length >= 4 && segments[2] == 'post' && segments[3].isNotEmpty) {
    return BlueskyPostLink(actor: actor, rkey: segments[3]);
  }
  return BlueskyProfileLink(actor);
}

PluginLink? parseThreadsLink(String url) {
  final uri = httpUri(url);
  if (uri == null || !_hostIs(uri.host, const {'threads.net', 'threads.com'})) {
    return null;
  }
  final segments = pathSegments(uri);
  if (segments.isEmpty) {
    return null;
  }
  if (segments[0].toLowerCase() == 't') {
    return null;
  }
  final handle = _atHandle(segments[0]);
  if (handle == null) {
    return null;
  }
  if (segments.length >= 3 && segments[1] == 'post' && segments[2].isNotEmpty) {
    return ThreadsPostLink(url: uri.toString(), handle: handle);
  }
  return ThreadsProfileLink(handle);
}

const _instagramReserved = {
  'p',
  'reel',
  'reels',
  'stories',
  'explore',
  'accounts',
  'about',
  'legal',
  'direct',
  'tv',
  'share',
  'emails',
  'web',
  'lite',
  'developer',
  'directory',
  'privacy',
  'terms',
};

PluginLink? parseInstagramLink(String url) {
  final uri = httpUri(url);
  if (uri == null || !_hostIs(uri.host, const {'instagram.com'})) {
    return null;
  }
  final segments = pathSegments(uri);
  if (segments.isEmpty) {
    return null;
  }
  final first = segments[0].toLowerCase();
  if (_instagramReserved.contains(first)) {
    return null;
  }
  if (!RegExp(r'^[A-Za-z0-9._]{1,30}$').hasMatch(first)) {
    return null;
  }
  return InstagramProfileLink(first);
}

const _tiktokReserved = {
  'foryou',
  'following',
  'live',
  'music',
  'tag',
  'search',
  'explore',
  'discover',
  'upload',
  'messages',
  'activity',
  'about',
  'legal',
};

PluginLink? parseTikTokLink(String url) {
  final uri = httpUri(url);
  if (uri == null || !_isTikTokHost(uri.host)) {
    return null;
  }
  final segments = pathSegments(uri);
  if (segments.isEmpty) {
    return null;
  }
  final handle = _atHandle(segments[0]);
  if (handle == null || _tiktokReserved.contains(handle)) {
    return null;
  }
  if (segments.length >= 3 &&
      segments[1] == 'video' &&
      RegExp(r'^\d+$').hasMatch(segments[2])) {
    return TikTokVideoLink(id: segments[2], handle: handle);
  }
  return TikTokProfileLink(handle);
}

PluginLink? parseRedditLink(String url) {
  final uri = httpUri(url);
  if (uri == null) {
    return null;
  }
  final host = uri.host.toLowerCase();
  if (_isRedditMediaHost(host)) {
    return null;
  }
  if (host == 'redd.it' || host == 'www.redd.it') {
    return _redditShortLink(pathSegments(uri));
  }
  if (!_isRedditSiteHost(host)) {
    return null;
  }
  return _redditSiteLink(pathSegments(uri));
}

PluginLink? parsePixivWebLink(String url) {
  final uri = httpUri(url);
  if (uri == null || !_isPixivHost(uri.host)) {
    return null;
  }
  final ref = parsePixivLink(url);
  return ref == null ? null : PixivWebLink(ref);
}

PluginLink? parseMastodonLink(String url, {required Set<String> knownHosts}) {
  final uri = httpUri(url);
  if (uri == null || knownHosts.isEmpty) {
    return null;
  }
  final host = uri.host.toLowerCase();
  if (!knownHosts.contains(host)) {
    return null;
  }
  final segments = pathSegments(uri);
  if (segments.isEmpty) {
    return null;
  }
  return _mastodonFromSegments(uri, host, segments);
}

Uri? httpUri(String input) {
  final text = input.trim();
  if (text.isEmpty) {
    return null;
  }
  final parsed = Uri.tryParse(text);
  if (parsed != null &&
      (parsed.scheme == 'http' || parsed.scheme == 'https') &&
      parsed.host.isNotEmpty) {
    return parsed;
  }
  if (parsed != null && parsed.hasScheme) {
    return null;
  }
  final withScheme = Uri.tryParse('https://$text');
  if (withScheme == null || withScheme.host.isEmpty) {
    return null;
  }
  return withScheme;
}

List<String> pathSegments(Uri uri) =>
    uri.pathSegments.where((segment) => segment.isNotEmpty).toList();

bool _hostIs(String host, Set<String> names) {
  final normalised = host.toLowerCase();
  final bare = normalised.startsWith('www.')
      ? normalised.substring(4)
      : normalised;
  return names.contains(normalised) || names.contains(bare);
}

bool _isTikTokHost(String host) {
  final normalised = host.toLowerCase();
  if (normalised == 'vm.tiktok.com' || normalised == 'vt.tiktok.com') {
    return false;
  }
  return normalised == 'tiktok.com' || normalised.endsWith('.tiktok.com');
}

bool _isPixivHost(String host) {
  final normalised = host.toLowerCase();
  return normalised == 'pixiv.net' ||
      normalised.endsWith('.pixiv.net') ||
      normalised == 'pixiv.me' ||
      normalised.endsWith('.pixiv.me');
}

bool _isRedditSiteHost(String host) {
  return host == 'reddit.com' || host.endsWith('.reddit.com');
}

bool _isRedditMediaHost(String host) {
  return host == 'i.redd.it' ||
      host == 'v.redd.it' ||
      host.startsWith('preview.') ||
      host.startsWith('external-preview.');
}

String? _atHandle(String segment) {
  final value = segment.startsWith('@') ? segment.substring(1) : segment;
  if (value.isEmpty || !RegExp(r'^[A-Za-z0-9._]+$').hasMatch(value)) {
    return null;
  }
  return value.toLowerCase();
}

PluginLink? _redditShortLink(List<String> segments) {
  if (segments.length != 1 || !_isRedditId(segments[0])) {
    return null;
  }
  final id = segments[0].toLowerCase();
  return RedditThreadLink(id: id, subreddit: '', permalink: '/comments/$id/');
}

PluginLink? _redditSiteLink(List<String> segments) {
  if (segments.length >= 2 && _isRedditCommunity(segments[0], segments[1])) {
    return _redditCommunityLink(segments);
  }
  if (segments.length >= 2 && _isRedditUser(segments[0])) {
    return RedditUserLink(segments[1]);
  }
  return null;
}

PluginLink? _redditCommunityLink(List<String> segments) {
  final subreddit = segments[1];
  if (segments.length >= 4 &&
      segments[2] == 'comments' &&
      _isRedditId(segments[3])) {
    final id = segments[3].toLowerCase();
    final title = segments.length >= 5
        ? segments[4].replaceAll('_', ' ')
        : null;
    return RedditThreadLink(
      id: id,
      subreddit: subreddit,
      permalink: '/r/$subreddit/comments/$id/',
      title: title,
    );
  }
  return RedditSubredditLink(subreddit);
}

bool _isRedditCommunity(String first, String name) =>
    (first == 'r' || first == 'R') &&
    RegExp(r'^[A-Za-z0-9_]{2,50}$').hasMatch(name);

bool _isRedditUser(String first) => first == 'u' || first == 'user';

bool _isRedditId(String value) =>
    RegExp(r'^[A-Za-z0-9]{5,10}$').hasMatch(value);

PluginLink? _mastodonFromSegments(Uri uri, String host, List<String> segments) {
  if (segments.length >= 4 &&
      segments[0].toLowerCase() == 'users' &&
      segments[2].toLowerCase() == 'statuses' &&
      RegExp(r'^\d+$').hasMatch(segments[3])) {
    return MastodonStatusLink(
      acct: '${segments[1]}@$host',
      statusId: segments[3],
      url: uri.toString(),
    );
  }
  if (segments.length >= 2 && segments[0].toLowerCase() == 'users') {
    return MastodonProfileLink('${segments[1]}@$host');
  }
  final user = _atHandle(segments[0]);
  if (user == null || !segments[0].startsWith('@')) {
    return null;
  }
  if (segments.length >= 2 && RegExp(r'^\d+$').hasMatch(segments[1])) {
    return MastodonStatusLink(
      acct: '$user@$host',
      statusId: segments[1],
      url: uri.toString(),
    );
  }
  return MastodonProfileLink('$user@$host');
}
