import 'package:flutter/widgets.dart';
import 'package:provider/provider.dart';
import 'package:xta/plugins/pixiv/pixiv_client.dart';
import 'package:xta/plugins/pixiv/pixiv_comment_models.dart';

/// Reading comments and replies on artworks and novels. There is deliberately
/// no way to post: XTA is a reader.
class PixivCommentsApi {
  final PixivClient client;

  const PixivCommentsApi(this.client);

  /// A test's fake when one is provided, else one over the app's client.
  static PixivCommentsApi of(BuildContext context) =>
      context.read<PixivCommentsApi?>() ?? PixivCommentsApi(context.read<PixivClient>());

  Future<Object?> _page(String path, Map<String, String> query, String? nextUrl) =>
      nextUrl == null || nextUrl.isEmpty ? client.getJson(path, query: query) : client.getNextJson(nextUrl);

  /// The comments on [target], in the order Pixiv lists them.
  Future<PixivCommentPage> comments(PixivCommentTarget target, {String? nextUrl}) async =>
      parsePixivCommentPage(await _page(target.work.listPath, {target.work.idField: '${target.id}'}, nextUrl));

  /// The replies under comment [commentId] on [target].
  Future<PixivCommentPage> replies(PixivCommentTarget target, int commentId, {String? nextUrl}) async =>
      parsePixivCommentPage(await _page(target.work.repliesPath, {'comment_id': '$commentId'}, nextUrl));
}
