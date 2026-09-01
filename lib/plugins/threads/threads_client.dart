import 'dart:convert';

import 'package:html/parser.dart' as html;
import 'package:http/http.dart' as http;
import 'package:xta/plugins/threads/threads_models.dart';
import 'package:xta/utils/json.dart';

/// Why a Threads read could not be served, in the terms the screen explains it.
enum ThreadsErrorKind {
  /// No RSSHub instance, direct session, or guest path is available.
  notConfigured,

  /// The instance / Meta host did not answer, or did not answer with a feed.
  unreachable,

  /// The instance answered, but not for this account — a wrong handle, or one
  /// its Threads route currently cannot read.
  noSuchFeed,

  /// The instance is refusing for now. Meta rate-limits this route hard, which
  /// is most of why a shared instance is a bad idea.
  throttled,

  /// Cookies or Bearer were rejected (wrong / expired).
  unauthorized,

  /// Meta parked the session (`login_required` / logout_reason).
  sessionSuspended,
}

class ThreadsException implements Exception {
  final ThreadsErrorKind kind;
  final String message;

  ThreadsException(this.kind, this.message);

  @override
  String toString() => 'ThreadsException{$kind: $message}';
}

/// Reads Threads through an RSSHub instance the reader runs.
///
/// Nothing here talks to Meta. RSSHub does that, on a server the reader
/// controls, and hands back a JSON Feed — so the fragile half lives in a
/// project maintained for exactly that purpose, and this file only has to
/// understand a documented feed format.
class ThreadsClient {
  final http.Client httpClient;

  ThreadsClient({http.Client? httpClient}) : httpClient = httpClient ?? http.Client();

  /// Fail a dead RSSHub proxy quickly so guest can answer — does not increase
  /// Meta traffic (guest is the same path as when RSSHub was never set).
  static const _timeout = Duration(seconds: 10);

  /// The feed address for [handle] on [instance].
  ///
  /// JSON rather than the default RSS: RSSHub serves every route as a JSON
  /// Feed, which this app can read with the same non-throwing tools it uses for
  /// every other endpoint, and without an XML parser.
  static Uri feedUri(String instance, String handle) {
    final base = instance.trim().replaceAll(RegExp(r'/+$'), '');
    return Uri.parse('$base/threads/$handle').replace(queryParameters: {'format': 'json'});
  }

  Future<Json> _get(Uri uri) async {
    final http.Response response;
    try {
      response = await httpClient.get(uri).timeout(_timeout);
    } catch (e) {
      throw ThreadsException(ThreadsErrorKind.unreachable, '$uri: $e');
    }

    if (response.statusCode == 404) {
      throw ThreadsException(ThreadsErrorKind.noSuchFeed, '$uri: 404');
    }
    if (response.statusCode == 429) {
      throw ThreadsException(ThreadsErrorKind.throttled, '$uri: 429');
    }
    if (response.statusCode != 200) {
      throw ThreadsException(ThreadsErrorKind.unreachable, '$uri: ${response.statusCode}');
    }

    try {
      return Json(jsonDecode(utf8.decode(response.bodyBytes)));
    } catch (e) {
      // An instance that answers 200 with an error page is unreachable as far
      // as anyone reading is concerned.
      throw ThreadsException(ThreadsErrorKind.unreachable, '$uri: $e');
    }
  }

  /// The account's posts, newest first.
  Future<List<ThreadsPost>> fetchAccount(String instance, String handle) async {
    if (instance.trim().isEmpty) {
      throw ThreadsException(ThreadsErrorKind.notConfigured, 'no instance configured');
    }

    final feed = await _get(feedUri(instance, handle));
    return parseThreadsFeed(feed, handle);
  }

  /// Confirms an instance is an RSSHub that will serve this route.
  ///
  /// Asks for a real feed rather than the instance's front page: a server that
  /// is reachable but has the Threads route disabled would otherwise pass a
  /// test and then fail on everything.
  Future<void> verify(String instance, {String handle = 'zuck'}) async {
    await fetchAccount(instance, handle);
  }
}

/// The display name a feed titles itself with.
///
/// RSSHub writes something like `zuck - Threads`; the handle is already known,
/// so the part before the separator is the only new information.
String _authorFrom(Json feed, String handle) {
  final title = feed['title'].string?.trim();
  if (title == null || title.isEmpty) {
    return handle;
  }
  final name = title.split(RegExp(r'\s+[-–|]\s+')).first.trim();
  return name.isEmpty ? handle : name.replaceFirst(RegExp(r'^@'), '');
}

/// The pictures an item carries: the ones the HTML embeds, then any attachment
/// that declares itself an image, in order and without repeats.
List<String> _imagesOf(Json item, String? contentHtml) {
  final urls = <String>[];

  void add(String? url) {
    if (url != null && url.isNotEmpty && !urls.contains(url)) {
      urls.add(url);
    }
  }

  if (contentHtml != null) {
    for (final image in html.parse(contentHtml).querySelectorAll('img[src]')) {
      add(image.attributes['src']);
    }
  }
  for (final attachment in item['attachments'].list) {
    if (attachment['mime_type'].string?.startsWith('image/') ?? false) {
      add(attachment['url'].string);
    }
  }
  add(item['image'].string);

  return urls;
}

/// The words of a post, with the markup taken off.
///
/// A JSON Feed may carry either form; `content_text` is used as given, and
/// `content_html` is reduced to its text with `<br>` and block ends kept as the
/// line breaks they were.
String _textOf(Json item, String? contentHtml) {
  final plain = item['content_text'].string;
  if (plain != null && plain.trim().isNotEmpty) {
    return plain.trim();
  }
  if (contentHtml == null) {
    return '';
  }

  final document = html.parse(contentHtml.replaceAll(RegExp(r'<br\s*/?>', caseSensitive: false), '\n'));
  for (final block in document.querySelectorAll('p, div')) {
    block.append(html.parseFragment('\n').nodes.first);
  }
  return document.body?.text.replaceAll(RegExp(r'\n{3,}'), '\n\n').trim() ?? '';
}

/// Turns one account's JSON Feed into posts.
///
/// Pure, so the shape can be tested without a server. An item with neither text
/// nor pictures is dropped rather than shown as a blank card.
List<ThreadsPost> parseThreadsFeed(Json feed, String handle) {
  final author = _authorFrom(feed, handle);
  final avatar = feed['icon'].string ?? feed['favicon'].string;

  final posts = <ThreadsPost>[];
  for (final item in feed['items'].list) {
    final contentHtml = item['content_html'].string;
    final text = _textOf(item, contentHtml);
    final images = _imagesOf(item, contentHtml);
    if (text.isEmpty && images.isEmpty) {
      continue;
    }

    final url = item['url'].string ?? item['external_url'].string;
    final id = item['id'].string ?? url;
    if (id == null) {
      continue;
    }

    posts.add(
      ThreadsPost(
        id: id,
        handle: handle,
        authorName: item['authors'][0]['name'].string ?? author,
        avatarUrl: item['authors'][0]['avatar'].string ?? avatar,
        text: text,
        images: images,
        publishedAt: DateTime.tryParse(item['date_published'].string ?? '')?.toLocal(),
        url: url,
      ),
    );
  }

  return posts;
}
