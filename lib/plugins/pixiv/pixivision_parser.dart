import 'package:html/dom.dart';
import 'package:html/parser.dart' as html_parser;

/// The pixivision.net language an XTA locale reads best: Pixivision is
/// written in Japanese, English, Korean and both Chinese scripts.
String pixivisionLanguage(String locale) {
  final parts = locale.toLowerCase().split(RegExp('[-_]'));
  return switch (parts.first) {
    'ja' => 'ja',
    'ko' => 'ko',
    'zh' when parts.skip(1).any(const {'hant', 'tw', 'hk', 'mo'}.contains) => 'zh-tw',
    'zh' => 'zh',
    _ => 'en',
  };
}

/// The article's page on pixivision.net in [language].
String pixivisionArticleUrl(int id, String language) => 'https://www.pixivision.net/$language/a/$id';

/// One work an article features, with what is needed to open it.
class PixivisionWork {
  final int artworkId;
  final int userId;
  final String title;
  final String userName;
  final String? imageUrl;
  final String? avatarUrl;

  const PixivisionWork({
    required this.artworkId,
    required this.userId,
    required this.title,
    required this.userName,
    this.imageUrl,
    this.avatarUrl,
  });
}

/// A Pixivision article as its page shows it: title, intro and featured works.
class PixivisionArticle {
  final String title;
  final String intro;
  final String? coverUrl;
  final List<PixivisionWork> works;

  const PixivisionArticle({this.title = '', this.intro = '', this.coverUrl, this.works = const []});

  bool get isEmpty => intro.isEmpty && works.isEmpty;
}

final _artworkHref = RegExp(r'/artworks/(\d+)|[?&]illust_id=(\d+)');
final _userHref = RegExp(r'/users/(\d+)|member\.php\?(?:[^#]*&)?id=(\d+)');

int? _idIn(RegExp pattern, String? href) {
  final match = href == null ? null : pattern.firstMatch(href);
  if (match == null) return null;
  return int.tryParse(match.group(1) ?? match.group(2) ?? '');
}

int? _artworkIdOf(Element link) => _idIn(_artworkHref, link.attributes['href']);

int? _userIdOf(Element link) => _idIn(_userHref, link.attributes['href']);

String _text(Element? element) => (element?.text ?? '').replaceAll(RegExp(r'\s+'), ' ').trim();

/// Lazy-loaded images keep their address in a data attribute until scrolled to.
String? _imageSource(Element? image) {
  final attributes = image?.attributes ?? const {};
  for (final name in const ['data-src', 'data-original', 'src']) {
    final value = attributes[name]?.trim() ?? '';
    if (value.isNotEmpty && !value.startsWith('data:')) {
      return value.startsWith('//') ? 'https:$value' : value;
    }
  }
  return null;
}

/// Pure parse of a pixivision.net article page. Both the English and the
/// Chinese layouts are read the same way: by the links a work block carries
/// rather than by class names, which differ between them.
PixivisionArticle parsePixivisionArticle(String html) {
  final document = html_parser.parse(html);
  final article = document.querySelector('article') ?? document.body;
  final body = article?.querySelector('.am__body') ?? article;
  return PixivisionArticle(
    title: _articleTitle(document, article),
    intro: _intro(article, body),
    coverUrl: document.querySelector('meta[property="og:image"]')?.attributes['content'],
    works: body == null ? const [] : _works(body),
  );
}

String _articleTitle(Document document, Element? article) {
  final heading = _text(article?.querySelector('h1'));
  if (heading.isNotEmpty) return heading;
  return document.querySelector('meta[property="og:title"]')?.attributes['content']?.trim() ?? '';
}

/// The description under the title, else the article's opening paragraphs
/// before its first featured work.
String _intro(Element? article, Element? body) {
  final description = article?.querySelector('[class*="description"]') ?? article?.querySelector('header');
  final paragraphs = description?.querySelectorAll('p') ?? const <Element>[];
  final texts = (paragraphs.isEmpty ? _leadingParagraphs(body) : paragraphs).map(_text).where((t) => t.isNotEmpty);
  return texts.join('\n\n');
}

List<Element> _leadingParagraphs(Element? body) => (body?.querySelectorAll('p, a[href]') ?? const <Element>[])
    .takeWhile((element) => element.localName != 'a' || _artworkIdOf(element) == null)
    .where((element) => element.localName == 'p')
    .toList();

List<PixivisionWork> _works(Element body) {
  final seen = <int>{};
  return [
    for (final link in body.querySelectorAll('a[href]'))
      if (_artworkIdOf(link) case final id? when seen.add(id))
        if (_blockOf(link, id, body) case final block?) ?_workIn(block, id),
  ];
}

/// The smallest element around [link] that names both work [artworkId] and
/// its artist, or null when the search would take in a second work first.
Element? _blockOf(Element link, int artworkId, Element body) {
  for (Element? node = link; node != null && node != body.parent; node = node.parent) {
    final links = node.querySelectorAll('a[href]');
    if ({artworkId, for (final a in links) ?_artworkIdOf(a)}.length > 1) return null;
    if (links.any((a) => _userIdOf(a) != null)) return node;
  }
  return null;
}

PixivisionWork? _workIn(Element block, int artworkId) {
  final links = block.querySelectorAll('a[href]');
  final userLinks = links.where((a) => _userIdOf(a) != null).toList();
  final userId = _userIdOf(userLinks.first);
  if (userId == null) return null;
  final artworkLinks = links.where((a) => _artworkIdOf(a) != null);
  final avatar = userLinks.map((a) => a.querySelector('img')).nonNulls.firstOrNull;
  return PixivisionWork(
    artworkId: artworkId,
    userId: userId,
    title: _firstText([block.querySelector('h3'), block.querySelector('h2'), ...artworkLinks]),
    userName: _firstText([...userLinks, block.querySelector('p')]),
    imageUrl:
        _imageSource(artworkLinks.map((a) => a.querySelector('img')).nonNulls.firstOrNull) ??
        _imageSource(block.querySelectorAll('img').where((img) => img != avatar).firstOrNull),
    avatarUrl: _imageSource(avatar),
  );
}

String _firstText(Iterable<Element?> candidates) =>
    candidates.map(_text).firstWhere((text) => text.isNotEmpty, orElse: () => '');
