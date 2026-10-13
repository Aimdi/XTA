/// URLs for the detail endpoints: popular posts, tag kinds, wiki pages and
/// comments. Pure, so each engine's shape is unit-tested without HTTP.
library;

import 'package:intl/intl.dart';
import 'package:xta/plugins/booru/booru_engines.dart';
import 'package:xta/plugins/booru/booru_popular.dart';

/// The query parameters that carry credentials, on any engine.
const booruCredentialParameters = {'login', 'user_id', 'api_key', 'password_hash'};

final _credentialValues = RegExp('([?&](?:${booruCredentialParameters.join('|')})=)[^&\\s]*');

/// [text] with the value of every credential parameter masked, so an error
/// that names a request URL can be shown.
String redactBooruCredentials(String text) => text.replaceAllMapped(_credentialValues, (match) => '${match[1]}***');

/// Where a request goes and who it is made as.
class BooruSite {
  final BooruEngine engine;
  final String host;
  final String login;
  final String apiKey;

  const BooruSite({required this.engine, required this.host, this.login = '', this.apiKey = ''});

  Uri get _base => Uri.parse(booruRequestHost(host));

  String get _path {
    final path = _base.path;
    if (path.isEmpty || path == '/') return '';
    return path.replaceAll(RegExp(r'/+$'), '');
  }

  /// The credentials each engine expects, named its way. Every engine needs
  /// both halves, so half a login is not sent.
  Map<String, String> get auth => login.isEmpty || apiKey.isEmpty ? const {} : {_loginParam: login, _keyParam: apiKey};

  String get _loginParam => engine == BooruEngine.gelbooruV2 ? 'user_id' : 'login';

  String get _keyParam => engine == BooruEngine.moebooru ? 'password_hash' : 'api_key';

  Uri endpoint(String path, [Map<String, String> query = const {}]) {
    final parameters = {...query, ...auth};
    return _base.replace(path: '$_path$path', queryParameters: parameters.isEmpty ? null : parameters);
  }

  /// Gelbooru's API is one script told what to do by its parameters.
  Uri dapi(String kind, [Map<String, String> query = const {}]) =>
      endpoint('/index.php', {'page': 'dapi', 's': kind, 'q': 'index', 'json': '1', ...query});
}

final _day = DateFormat('yyyy-MM-dd');

/// Popular posts for [query]; null where the engine has no such list.
Uri? booruPopularUri(BooruSite site, BooruPopularQuery query, {required int page, required int limit}) {
  final date = query.date;
  switch (site.engine) {
    case BooruEngine.danbooru:
      return site.endpoint('/explore/posts/popular.json', {
        'date': _day.format(date),
        'scale': query.scale.name,
        'page': '$page',
        'limit': '$limit',
      });
    case BooruEngine.e621:
      return site.endpoint('/popular.json', {'date': _day.format(date), 'scale': query.scale.name});
    case BooruEngine.moebooru:
      return site.endpoint('/post/popular_by_${query.scale.name}.json', {
        if (query.scale != BooruPopularScale.month) 'day': '${date.day}',
        'month': '${date.month}',
        'year': '${date.year}',
      });
    case BooruEngine.gelbooruV2:
      return null;
  }
}

/// Gelbooru keeps no popular list.
bool booruHasPopularList(BooruEngine engine) => engine != BooruEngine.gelbooruV2;

/// Only Danbooru pages through its popular list; the others send one page.
bool booruPopularPages(BooruEngine engine) => engine == BooruEngine.danbooru;

/// Kinds and post counts for [tags] of post [postId], in one request. Null
/// where the host cannot answer for a list of tags.
Uri? booruTagInfoUri(BooruSite site, {required String postId, required List<String> tags}) {
  if (tags.isEmpty) return null;
  final limit = '${tags.length}';
  switch (site.engine) {
    case BooruEngine.danbooru:
      return site.endpoint('/tags.json', {
        'search[name_comma]': tags.join(','),
        'only': 'name,category,post_count',
        'limit': limit,
      });
    case BooruEngine.e621:
      return site.endpoint('/tags.json', {'search[name]': tags.join(','), 'limit': limit});
    case BooruEngine.gelbooruV2:
      return site.dapi('tag', {'names': tags.join(' '), 'limit': limit});
    // Moebooru looks tags up one pattern at a time; its post can name their
    // kinds, though not their counts.
    case BooruEngine.moebooru:
      return site.endpoint('/post.json', {'tags': 'id:$postId', 'api_version': '2', 'include_tags': '1', 'limit': '1'});
  }
}

/// Tags the host finds alongside [tags]: Danbooru for a whole search, e621
/// for one tag. Null where the host keeps no such list.
Uri? booruRelatedTagsUri(BooruSite site, List<String> tags) => switch (site.engine) {
  _ when tags.isEmpty => null,
  BooruEngine.danbooru => site.endpoint('/related_tag.json', {'search[query]': tags.join(' '), 'limit': '25'}),
  BooruEngine.e621 when tags.length == 1 => site.endpoint('/tags.json', {'search[name]': tags.single, 'limit': '1'}),
  _ => null,
};

bool booruSupportsWiki(BooruEngine engine) => engine != BooruEngine.gelbooruV2;

/// Gelbooru only answers comments in XML.
bool booruSupportsComments(BooruEngine engine) => engine != BooruEngine.gelbooruV2;

Uri? booruWikiUri(BooruSite site, String tag) {
  switch (site.engine) {
    case BooruEngine.danbooru:
    case BooruEngine.e621:
      return site.endpoint('/wiki_pages.json', {'search[title]': tag, 'limit': '1'});
    // A title search, not a lookup: many titles holding the tag can sort
    // before it, so ask for as many as one page holds and pick the exact one.
    case BooruEngine.moebooru:
      return site.endpoint('/wiki.json', {'query': tag, 'limit': '100'});
    case BooruEngine.gelbooruV2:
      return null;
  }
}

Uri? booruCommentsUri(BooruSite site, String postId) {
  switch (site.engine) {
    case BooruEngine.danbooru:
      return site.endpoint('/comments.json', {
        'group_by': 'comment',
        'search[post_id]': postId,
        'limit': '100',
        'only': 'id,created_at,score,body,is_deleted,creator[name]',
      });
    case BooruEngine.e621:
      return site.endpoint('/comments.json', {'group_by': 'comment', 'search[post_id]': postId, 'limit': '100'});
    case BooruEngine.moebooru:
      return site.endpoint('/comment.json', {'post_id': postId});
    case BooruEngine.gelbooruV2:
      return null;
  }
}
