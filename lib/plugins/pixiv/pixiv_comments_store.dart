import 'package:xta/plugins/pixiv/pixiv_comment_models.dart';
import 'package:xta/plugins/pixiv/pixiv_comments_api.dart';
import 'package:xta/plugins/pixiv/pixiv_mute_store.dart';
import 'package:xta/plugins/pixiv/pixiv_store.dart';

/// Whether [comment] survives the reader's mutes: neither it nor its author is muted.
bool pixivCommentVisible(PixivComment comment, PixivMuteState mutes) =>
    !mutes.isCommentMuted(comment.id) && !mutes.authorIds.contains(comment.user?.id);

List<PixivComment> pixivVisibleComments(List<PixivComment> comments, PixivMuteState mutes) => [
  for (final comment in comments)
    if (pixivCommentVisible(comment, mutes)) comment,
];

/// The pages of [target]'s comments, or of the replies under [parent].
PixivPageLoader<PixivComment> pixivCommentLoader(
  PixivCommentsApi api,
  PixivCommentTarget target, {
  PixivComment? parent,
}) => switch (parent) {
  null => ({nextUrl}) => api.comments(target, nextUrl: nextUrl),
  final parent => ({nextUrl}) => api.replies(target, parent.id, nextUrl: nextUrl),
};

int _commentId(PixivComment comment) => comment.id;

/// A work's comments, or one comment's replies, paged through `next_url`
/// without what the reader muted. [mutes] is read at each page, so a mute
/// made on this screen also holds for the pages still to come.
class PixivCommentsStore extends PixivPagedListStore<PixivComment> {
  final PixivCommentTarget target;

  /// Set in thread mode: the comment whose replies these are.
  final PixivComment? parent;

  PixivCommentsStore(PixivCommentsApi api, this.target, {this.parent, PixivMuteState Function()? mutes})
    : super(
        pixivCommentLoader(api, target, parent: parent),
        keyOf: _commentId,
        filter: mutes == null ? null : (comments) => pixivVisibleComments(comments, mutes()),
      );

  bool get isThread => parent != null;

  /// A last page the mutes emptied leaves the same list behind, which the
  /// store would not announce; announcing it lets the list drop its loading tail.
  @override
  Future<void> loadMore() async {
    await super.loadMore();
    if (!hasMore) update(state, force: true);
  }
}
