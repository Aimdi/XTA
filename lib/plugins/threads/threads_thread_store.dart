import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/plugins/threads/threads_conversation.dart';
import 'package:xta/plugins/threads/threads_direct_client.dart';
import 'package:xta/plugins/threads/threads_models.dart';

/// One Threads conversation, read from the post's public page.
///
/// Starts from the card the reader tapped, so the post is on screen at once;
/// a failed read leaves that card (and anything read before) in place.
class ThreadsThreadStore extends Store<ThreadsConversation> {
  final ThreadsDirectClient direct;

  ThreadsThreadStore(this.direct, ThreadsPost seed) : super(ThreadsConversation.seed(seed));

  ThreadsPost get _seed => state.focus;

  Future<void> load({bool force = false}) async {
    final url = _seed.url?.trim();
    if (url == null || url.isEmpty) {
      update(ThreadsConversation(focus: _seed, loaded: true));
      return;
    }
    await execute(() async {
      final chains = await direct.fetchGuestPostChains(url, force: force);
      return threadsConversationFrom(chains, _seed);
    });
  }
}

/// How the reader has chosen to look at a conversation.
class ThreadsThreadView {
  /// Only reply threads the post's author took part in.
  final bool authorOnly;

  /// Reply threads opened past their first few posts, by their first post's id.
  final Set<String> expanded;

  const ThreadsThreadView({this.authorOnly = false, this.expanded = const {}});

  bool isExpanded(List<ThreadsPost> chain) => chain.isNotEmpty && expanded.contains(chain.first.id);
}

class ThreadsThreadViewStore extends Store<ThreadsThreadView> {
  ThreadsThreadViewStore() : super(const ThreadsThreadView());

  void toggleAuthorOnly() => update(ThreadsThreadView(authorOnly: !state.authorOnly, expanded: state.expanded));

  void expand(List<ThreadsPost> chain) {
    if (chain.isEmpty) {
      return;
    }
    update(ThreadsThreadView(authorOnly: state.authorOnly, expanded: {...state.expanded, chain.first.id}));
  }
}

/// One row of the reply list: a post, where it sits in its reply thread, or
/// the marker that opens the rest of a long one.
sealed class ThreadsReplyRow {
  const ThreadsReplyRow();
}

class ThreadsReplyPostRow extends ThreadsReplyRow {
  final ThreadsPost post;

  /// Rails join a reply thread's posts, the way the Threads app draws them.
  final bool connectTop;
  final bool connectBottom;

  const ThreadsReplyPostRow(this.post, {this.connectTop = false, this.connectBottom = false});
}

class ThreadsReplyMoreRow extends ThreadsReplyRow {
  final List<ThreadsPost> chain;
  final int hidden;

  const ThreadsReplyMoreRow(this.chain, this.hidden);
}

/// The reply list as rows, each long thread cut to its first few posts until
/// the reader opens it.
List<ThreadsReplyRow> threadsReplyRows(ThreadsConversation conversation, ThreadsThreadView view) {
  final chains = view.authorOnly ? threadsAuthorReplies(conversation) : conversation.replies;
  return [for (final chain in chains) ..._chainRows(chain, view.isExpanded(chain))];
}

List<ThreadsReplyRow> _chainRows(List<ThreadsPost> chain, bool expanded) {
  final shown = expanded ? chain : chain.take(kThreadsReplyChainPreview).toList(growable: false);
  final hidden = chain.length - shown.length;
  return [
    for (final (index, post) in shown.indexed)
      ThreadsReplyPostRow(post, connectTop: index > 0, connectBottom: index < shown.length - 1 || hidden > 0),
    if (hidden > 0) ThreadsReplyMoreRow(chain, hidden),
  ];
}
