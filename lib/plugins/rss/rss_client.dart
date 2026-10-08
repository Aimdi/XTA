import 'package:http/http.dart' as http;
import 'package:xta/plugins/rss/rss_models.dart';
import 'package:xta/plugins/rss/rss_parser.dart';

/// Public RSS / Atom reads. No login, no write.
class RssClient {
  static const userAgent = 'XTA RSS plugin';

  final http.Client httpClient;
  final Duration timeout;

  RssClient({
    http.Client? httpClient,
    this.timeout = const Duration(seconds: 15),
  }) : httpClient = httpClient ?? http.Client();

  /// Fetches [feedUrl] and parses whatever RSS or Atom comes back.
  Future<RssChannel> fetchChannel(String feedUrl) async {
    final body = await _get(feedUrl);
    return parseRss(body, feedUrl: feedUrl);
  }

  /// Resolves a pasted site or feed URL into a followable channel.
  Future<RssFeed> lookup(String input) async {
    final feedUrl = await resolveFeedUrl(input);
    final channel = await fetchChannel(feedUrl);
    final title = channel.title?.trim();
    return RssFeed(
      id: rssFeedId(feedUrl),
      feedUrl: feedUrl,
      name: (title != null && title.isNotEmpty)
          ? title
          : Uri.tryParse(feedUrl)?.host ?? feedUrl,
      siteUrl: channel.link,
      iconUrl: channel.imageUrl,
      description: channel.description,
    );
  }

  Future<List<RssItem>> fetchItems(RssFeed feed) async {
    final channel = await fetchChannel(feed.feedUrl);
    return [
      for (final item in channel.items)
        RssItem(
          id: item.id,
          title: item.title,
          link: item.link,
          excerpt: item.excerpt,
          bodyHtml: item.bodyHtml,
          publishedAt: item.publishedAt,
          author: item.author,
          imageUrl: item.imageUrl ?? feed.iconUrl,
          feedId: feed.id,
          feedTitle: feed.name,
          categories: item.categories,
        ),
    ];
  }

  /// A pasted feed URL is used as-is; a site URL is probed for a feed.
  Future<String> resolveFeedUrl(String input) async {
    final trimmed = input.trim();
    final uri = Uri.tryParse(
      trimmed.contains('://') ? trimmed : 'https://$trimmed',
    );
    if (uri == null || uri.host.isEmpty) {
      throw const FormatException('Not a URL');
    }
    final start = uri
        .replace(scheme: uri.scheme.isEmpty ? 'https' : uri.scheme)
        .toString();
    if (looksLikeRssUrl(start) && await _isFeed(start)) {
      return start;
    }

    final body = await _get(start);
    if (_looksLikeFeed(body)) {
      return start;
    }

    for (final found in discoverFeedUrls(body, page: uri)) {
      if (await _isFeed(found)) return found;
    }
    for (final candidate in commonFeedPaths(uri)) {
      if (await _isFeed(candidate)) return candidate;
    }
    throw const FormatException('No RSS or Atom feed');
  }

  Future<bool> _isFeed(String url) async {
    try {
      final body = await _get(url);
      return _looksLikeFeed(body);
    } catch (_) {
      return false;
    }
  }

  bool _looksLikeFeed(String body) {
    final head = body.trimLeft();
    if (head.startsWith('<!DOCTYPE html') || head.startsWith('<html')) {
      return false;
    }
    return RegExp(
      r'<(?:rss|feed|rdf:RDF)\b',
      caseSensitive: false,
    ).hasMatch(head.substring(0, head.length < 800 ? head.length : 800));
  }

  Future<String> _get(String url) async {
    final response = await httpClient
        .get(
          Uri.parse(url),
          headers: {
            'User-Agent': userAgent,
            'Accept':
                'application/rss+xml, application/atom+xml, application/xml, text/xml, text/html;q=0.8, */*;q=0.5',
          },
        )
        .timeout(timeout);
    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw http.ClientException('HTTP ${response.statusCode}', Uri.parse(url));
    }
    return response.body;
  }
}
