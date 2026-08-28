import 'package:flutter/widgets.dart';
import 'package:http/http.dart' as http;
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/profile/profile.dart' show profileTabs;
import 'package:url_launcher/url_launcher_string.dart';

import 'package:android_intent_plus/android_intent.dart';
import 'package:xta/utils/browsers.dart';

const _trackingParams = {
  'fbclid',
  'gclid',
  'dclid',
  'msclkid',
  'twclid',
  'ttclid',
  'yclid',
  'igshid',
  'igsh',
  'mc_eid',
  'mc_cid',
  'mkt_tok',
  'li_fat_id',
  'gad_source',
  'gclsrc',
  'gbraid',
  'wbraid',
  'srsltid',
  'ocid',
  '_hsenc',
  '_hsmi',
  'wickedid',
};
const _trackingPrefixes = {'utm_', 'mtm_', 'pk_', 'hsa_'};
// Share identifiers X appends to copied links; only meaningful on X hosts,
// where stripping them cannot change what the link points to.
const _xTrackingParams = {'s', 't', 'ref_src', 'ref_url'};
const _xHosts = {'x.com', 'www.x.com', 'twitter.com', 'www.twitter.com', 'mobile.twitter.com'};

/// The article id in an `x.com/i/article/…` link, or null if it is not one.
///
/// A link to a long-form X post carries no title, no author and no thumbnail —
/// nothing a preview could be built from — so it used to render as a truncated
/// blue URL and nothing else. Recognising it at least lets the post say what
/// the link is.
String? articleIdIn(String? url) {
  if (url == null || url.isEmpty) {
    return null;
  }

  final uri = Uri.tryParse(url);
  if (uri == null || !_xHosts.contains(uri.host)) {
    return null;
  }

  final parts = uri.pathSegments.where((e) => e.isNotEmpty).toList(growable: false);
  if (parts.length < 3 || parts[0] != 'i' || parts[1] != 'article') {
    return null;
  }

  return parts[2];
}

bool _isTrackingParam(String key, bool isXHost) =>
    _trackingPrefixes.any(key.startsWith) ||
    _trackingParams.contains(key) ||
    (isXHost && _xTrackingParams.contains(key));

/// Removes known tracking query parameters from a URL before it leaves the
/// app (opened externally or shared).
String cleanUrl(String url) {
  final uri = Uri.tryParse(url);
  if (uri == null || uri.query.isEmpty) {
    return url;
  }

  final isXHost = _xHosts.contains(uri.host);
  final kept = <String, List<String>>{};
  uri.queryParametersAll.forEach((key, values) {
    if (!_isTrackingParam(key, isXHost)) {
      kept[key] = values;
    }
  });
  if (kept.length == uri.queryParametersAll.length) {
    return url;
  }

  final cleaned = uri.replace(queryParameters: kept).toString();
  // An empty parameter map leaves a dangling '?' behind.
  return kept.isEmpty ? cleaned.replaceFirst('?', '') : cleaned;
}

/// Whether outbound URLs should have tracking parameters stripped.
///
/// Unset is on: that is the behaviour this fork always had, and a missing
/// key must not start leaking click-ids.
bool cleanLinksEnabled(BasePrefService prefs) => prefs.get<bool>(optionCleanLinks) != false;

/// [cleanUrl] when the reader left that switch on, otherwise [url] as written.
String prepareUrl(BasePrefService prefs, String url) => cleanLinksEnabled(prefs) ? cleanUrl(url) : url;

/// Hands a link to the system browser, bypassing the reader's choice.
///
/// Kept for a caller that genuinely needs to leave the app; nothing does today,
/// and [openUri] is what a link in the feed should go through.
Future<void> openInDefaultBrowser(String url) async {
  final packageName = await browserChannel.invokeMethod<String>('getDefaultBrowser');
  final intent = AndroidIntent(
    action: 'android.intent.action.VIEW',
    data: cleanUrl(url),
    package: packageName,
  );
  await intent.launch();
}

/// Opens [uri] outside the feed: in an in-app browser view when the reader
/// asked for that in settings, otherwise in the browser they named — or the
/// system default, if they named none.
Future<void> openUri(BuildContext context, String uri) async {
  final prefs = PrefService.of(context, listen: false);
  final url = prepareUrl(prefs, uri);

  if (prefs.get(optionOpenLinksInEmbeddedBrowser) == true) {
    await launchUrlString(url, mode: LaunchMode.inAppBrowserView);
    return;
  }

  await openExternally(url, package: prefs.get<String>(optionExternalBrowser) ?? systemDefaultBrowser);
}

sealed class UriParseResult {}

enum ProfileTabs { posts, postsAndReplies, media, saved }

class ProfileUriInfo extends UriParseResult {
  String screenName;
  int? profileTabIndex;

  ProfileUriInfo(this.screenName, ProfileTabs? tab) {
    if (tab != null) {
      profileTabIndex = profileTabs.indexWhere((e) => e.id == tab);
    }
  }
}

ProfileUriInfo? _parseAsProfileLink(List<String> parts) {
  if (parts.isEmpty) return null;

  // "i" is X's reserved path segment (/i/lists/…, /i/topics/…), never a
  // screen name — without this guard those links parse as a profile "i".
  if (parts.first == 'i') return null;

  // https://x.com/DogsTrust
  if (parts.length == 1) {
    return ProfileUriInfo(parts.first, null);
  }

  const Map<String, ProfileTabs?> supportedProfileSubpaths = {
    "with_replies": ProfileTabs.postsAndReplies, // https://x.com/DogsTrust/with_replies
    "media": ProfileTabs.media, // https://x.com/DogsTrust/media
    // All following sublinks are not supported by XTA, but remain valid for an account link
    "highlights": null, // https://x.com/DogsTrust/highlights
    "affiliates": null, // https://x.com/DogsTrust/affiliates
    "about": null, // https://x.com/DogsTrust/about
    "topics": null, // https://x.com/DogsTrust/topics
    "lists": null, // https://x.com/DogsTrust/lists
  };

  if (supportedProfileSubpaths.containsKey(parts[1])) {
    return ProfileUriInfo(parts.first, supportedProfileSubpaths[parts[1]]);
  }

  // The URI is not an account link
  return null;
}

class ListUriInfo extends UriParseResult {
  final String id;

  ListUriInfo(this.id);
}

ListUriInfo? _parseAsListLink(List<String> parts) {
  // https://x.com/i/lists/1234567890123456789
  if (parts.length == 3 && parts[0] == 'i' && parts[1] == 'lists' && RegExp(r'^\d+$').hasMatch(parts[2])) {
    return ListUriInfo(parts[2]);
  }
  return null;
}

/// Extracts an X list id from user input: either a bare numeric id or a
/// list URL. Returns null when the input is neither.
String? extractListId(String input) {
  final trimmed = input.trim();
  if (RegExp(r'^\d+$').hasMatch(trimmed)) {
    return trimmed;
  }
  final uri = Uri.tryParse(trimmed);
  if (uri == null) {
    return null;
  }
  return _parseAsListLink(uri.pathSegments.where((e) => e.isNotEmpty).toList())?.id;
}

class PostUriInfo extends UriParseResult {
  String? screenName;
  String id;
  bool direct = false;
  int? photoNumber;

  PostUriInfo(this.screenName, this.id, {this.photoNumber}) {
    direct = id.endsWith(".jpg") || id.endsWith(".mp4");
    // In case of FX Twitter links
    // https://github.com/FxEmbed/FxEmbed?tab=readme-ov-file#direct-media-links
    id = id.replaceAll(RegExp(r'\.(?:jpg|mp4)$'), '');
  }
}

int? extractPhotoNumber(List<String> parts, int index) {
  if (parts.length < index + 2) return null;
  if (parts[index] != "photo") return null;
  return int.tryParse(parts[index + 1]);
}

PostUriInfo? _parseAsPostLink(List<String> parts) {
  if (parts.length < 3) return null;

  if (parts[1] == "status") {
    return PostUriInfo(parts[0], parts[2], photoNumber: extractPhotoNumber(parts, 3));
  }

  if (parts.length < 4) return null;

  if (parts[0] == "i" && parts[1] == "topics" && parts[2] == "tweet") {
    return PostUriInfo(null, parts[3], photoNumber: extractPhotoNumber(parts, 4));
  }

  // The URI is not a post link
  return null;
}

Future<String?> _resolveShortUrl(Uri shortUrl) async {
  final request = http.Request('GET', shortUrl)
    ..followRedirects = false;

  final response = await request.send();
  if (response.isRedirect || response.statusCode == 301 || response.statusCode == 302) {
    return response.headers['location'];
  }
  return response.request?.url.toString();
}

class UnknownResult extends UriParseResult {}

Future<UriParseResult> parseUri(Uri link) async {
  if (link.host == 't.co') {
    String? lnk = await _resolveShortUrl(link);
    if (lnk == null) return UnknownResult();
    Uri parsed = Uri.parse(lnk);
    if (parsed.host != 't.co') {
      return parseUri(parsed);
    }
    return UnknownResult();
  }
  link = link.replace(path: link.path.replaceAll(RegExp(r'/$'), ''));
  final parts = link.pathSegments;

  final listInfo = _parseAsListLink(parts);
  if (listInfo != null) {
    return listInfo;
  }
  final profileInfo = _parseAsProfileLink(parts);
  if (profileInfo != null) {
    return profileInfo;
  }
  final postInfo = _parseAsPostLink(parts);
  if (postInfo != null) {
    return postInfo;
  }
  return UnknownResult();
}
