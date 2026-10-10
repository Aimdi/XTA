import 'package:pref/pref.dart';
import 'package:xta/plugins/pixiv/pixiv_client.dart';
import 'package:xta/plugins/pixiv/pixiv_comment_models.dart';
import 'package:xta/plugins/pixiv/pixiv_comments_api.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';

PixivUser pixivCommenter(int id, {String name = 'Mika'}) =>
    PixivUser(id: id, name: name, account: name.toLowerCase(), comment: '');

PixivComment pixivTestComment(
  int id, {
  String text = 'Lovely',
  PixivUser? user,
  bool anonymous = false,
  bool hasReplies = false,
  String? stampUrl,
  PixivUser? replyTo,
}) => PixivComment(
  id: id,
  text: text,
  user: anonymous ? null : user ?? pixivCommenter(42),
  hasReplies: hasReplies,
  stampUrl: stampUrl,
  replyTo: replyTo,
  date: DateTime(2026, 7, 1),
);

/// Serves queued pages of comments and replies, recording every request; a
/// missing page answers empty and a null page fails like a dropped connection.
class FakePixivCommentsApi extends PixivCommentsApi {
  final Map<String, List<PixivCommentPage?>> pages;
  final calls = <String>[];

  FakePixivCommentsApi(this.pages) : super(PixivClient(PrefServiceCache()));

  static String commentsKey(PixivCommentTarget target) => '${target.work.name}:${target.id}';

  static String repliesKey(PixivCommentTarget target, int commentId) => '${target.work.name}:${target.id}/$commentId';

  Future<PixivCommentPage> _serve(String key, String? nextUrl) async {
    calls.add('$key@${nextUrl ?? 'first'}');
    final queue = pages[key] ?? const [];
    final index = calls.where((call) => call.startsWith('$key@')).length - 1;
    if (index >= queue.length) return const PixivCommentPage([]);
    return queue[index] ?? (throw PixivException(PixivErrorKind.network, 'offline'));
  }

  @override
  Future<PixivCommentPage> comments(PixivCommentTarget target, {String? nextUrl}) =>
      _serve(commentsKey(target), nextUrl);

  @override
  Future<PixivCommentPage> replies(PixivCommentTarget target, int commentId, {String? nextUrl}) =>
      _serve(repliesKey(target, commentId), nextUrl);
}
