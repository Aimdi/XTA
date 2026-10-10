import 'package:xta/plugins/pixiv/pixiv_comment_models.dart';
import 'package:xta/plugins/pixiv/pixiv_comments_api.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
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

/// Runs a page loader and keeps why the latest later page failed until a page
/// arrives: the paged store keeps the list on such a failure and records nothing.
class _LaterPageFailure {
  final PixivPageLoader<PixivComment> _load;
  Object? latest;

  _LaterPageFailure(this._load);

  Future<PixivPage<PixivComment>> call({String? nextUrl}) async {
    try {
      final page = await _load(nextUrl: nextUrl);
      latest = null;
      return page;
    } catch (error) {
      if (nextUrl != null) latest = error;
      rethrow;
    }
  }
}

/// A work's comments, or one comment's replies, paged through `next_url`
/// without what the reader muted. [mutes] is read at each page, so a mute
/// made on this screen also holds for the pages still to come.
class PixivCommentsStore extends PixivPagedListStore<PixivComment> {
  final PixivCommentTarget target;

  /// Set in thread mode: the comment whose replies these are.
  final PixivComment? parent;

  final _LaterPageFailure _pages;

  PixivCommentsStore(
    PixivCommentsApi api,
    PixivCommentTarget target, {
    PixivComment? parent,
    PixivMuteState Function()? mutes,
  }) : this._(_LaterPageFailure(pixivCommentLoader(api, target, parent: parent)), target, parent, mutes);

  PixivCommentsStore._(this._pages, this.target, this.parent, PixivMuteState Function()? mutes)
    : super(
        _pages.call,
        keyOf: _commentId,
        filter: mutes == null ? null : (comments) => pixivVisibleComments(comments, mutes()),
      );

  bool get isThread => parent != null;

  /// Why the next page failed, while no other attempt is on its way.
  Object? get moreError => loadingMore ? null : _pages.latest;

  /// flutter_triple marks a load as running only after a short delay; until
  /// then the empty list would read as "No comments yet".
  @override
  Future<void> refresh() {
    if (state.isEmpty) setLoading(true);
    return super.refresh();
  }

  /// A page starting, failing, or arriving empty after the mutes leaves the
  /// same list behind, which the store would not announce; the list's tail
  /// follows all three.
  @override
  Future<void> loadMore() async {
    if (loadingMore || !hasMore) return;
    final loading = super.loadMore();
    _announce();
    await loading;
    _announce();
  }

  void _announce() {
    if (error == null && !isLoading) update(state, force: true);
  }
}
