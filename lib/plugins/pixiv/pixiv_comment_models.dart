import 'package:flutter/foundation.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/utils/json.dart';

/// The kind of work a comment thread hangs off; artworks and novels answer on
/// parallel paths with their own id parameter.
enum PixivCommentWork {
  illust(listPath: '/v3/illust/comments', idField: 'illust_id', repliesPath: '/v2/illust/comment/replies'),
  novel(listPath: '/v3/novel/comments', idField: 'novel_id', repliesPath: '/v2/novel/comment/replies');

  final String listPath;
  final String idField;
  final String repliesPath;

  const PixivCommentWork({required this.listPath, required this.idField, required this.repliesPath});
}

/// The work whose comments are read: an artwork or a novel.
@immutable
class PixivCommentTarget {
  final PixivCommentWork work;
  final int id;

  const PixivCommentTarget.illust(this.id) : work = PixivCommentWork.illust;

  const PixivCommentTarget.novel(this.id) : work = PixivCommentWork.novel;

  @override
  bool operator ==(Object other) => other is PixivCommentTarget && other.work == work && other.id == id;

  @override
  int get hashCode => Object.hash(work, id);
}

/// One comment as `/v3/illust/comments`, `/v3/novel/comments` and the reply
/// endpoints send it.
@immutable
class PixivComment {
  final int id;
  final String text;
  final DateTime? date;

  /// Null when Pixiv sent no usable author, as for a withdrawn account.
  final PixivUser? user;

  /// Whom this comment answers, when it is a reply.
  final PixivUser? replyTo;
  final bool hasReplies;

  /// The sticker a comment is made of instead of text.
  final String? stampUrl;

  const PixivComment({
    required this.id,
    required this.text,
    this.date,
    this.user,
    this.replyTo,
    this.hasReplies = false,
    this.stampUrl,
  });

  /// Links away from Pixiv are how comment spam reaches readers.
  bool get linksOutsidePixiv => pixivTextLinksOutside(text);
}

/// The name a comment author goes by: their name, else their account.
String? pixivCommentAuthorName(PixivUser? user) => switch (user) {
  PixivUser(:final name) when name.isNotEmpty => name,
  PixivUser(:final account) when account.isNotEmpty => '@$account',
  _ => null,
};

PixivUser? _commentUser(Json user) => switch (PixivUser.fromUserJson(user.raw)) {
  final parsed when parsed.id > 0 => parsed,
  _ => null,
};

/// One comment object, or null when it has no id to tell it apart.
PixivComment? pixivCommentFromJson(Object? json) {
  final data = Json(json);
  final id = data['id'].integer;
  if (id == null) {
    return null;
  }
  final stamp = data['stamp']['stamp_url'].string?.trim() ?? '';
  return PixivComment(
    id: id,
    text: data['comment'].string ?? '',
    date: DateTime.tryParse(data['date'].string ?? '')?.toLocal(),
    user: _commentUser(data['user']),
    replyTo: _commentUser(data['parent_comment']['user']),
    hasReplies: data['has_replies'].boolean == true,
    stampUrl: stamp.isEmpty ? null : stamp,
  );
}

/// One page of comments and where the next one is.
class PixivCommentPage extends PixivPage<PixivComment> {
  /// How many comments the work has; only a work's first page says.
  final int? total;

  const PixivCommentPage(super.items, {super.nextUrl, this.total});
}

PixivCommentPage parsePixivCommentPage(Object? json) {
  final root = Json(json);
  return PixivCommentPage(
    [for (final item in root['comments'].list) ?pixivCommentFromJson(item.raw)],
    nextUrl: root['next_url'].string,
    total: root['total_comments'].integer,
  );
}

/// Pixiv's own sites; a link to any of them is a reader pointing at a work.
const _pixivHosts = {'pixiv.net', 'pixiv.me', 'pximg.net', 'pixivision.net', 'fanbox.cc', 'booth.pm'};

/// A link written with its scheme, or a bare `www.` address. The bare form
/// needs a dotted host after it: "www" is also how Japanese comments laugh.
final _link = RegExp(r'https?://([^\s/?#<>"\\]*)|\bwww\.([a-z0-9\-]+(?:\.[a-z0-9\-]+)+)', caseSensitive: false);
final _asciiHost = RegExp(r'^[a-z0-9.\-]*');

/// Whether [text] links anywhere other than Pixiv's own sites. Collapsing
/// those is the spam guard: no list of bad domains to keep current.
bool pixivTextLinksOutside(String text) =>
    _link.allMatches(text).any((match) => !_isPixivHost(match.group(1) ?? match.group(2) ?? ''));

bool _isPixivHost(String raw) {
  final host = (_asciiHost.stringMatch(raw.toLowerCase()) ?? '').replaceFirst(RegExp(r'^www\.'), '');
  final bare = host.replaceFirst(RegExp(r'\.+$'), '');
  return bare.isNotEmpty && _pixivHosts.any((own) => bare == own || bare.endsWith('.$own'));
}
