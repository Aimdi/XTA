import 'package:xta/plugins/threads/threads_models.dart';

/// A Threads post page, arranged the way the app reads it: what the post
/// answers, the post, the rest of its author's thread, then each reply thread.
class ThreadsConversation {
  /// The posts above [focus], oldest first.
  final List<ThreadsPost> ancestors;

  final ThreadsPost focus;

  /// The author's own posts chained under [focus].
  final List<ThreadsPost> continuation;

  /// Each reply thread: a reply, then the replies Meta chained under it.
  final List<List<ThreadsPost>> replies;

  /// False until a page has been read — [focus] is then only the card the
  /// reader tapped, or the stub a link produced.
  final bool loaded;

  const ThreadsConversation({
    required this.focus,
    this.ancestors = const [],
    this.continuation = const [],
    this.replies = const [],
    this.loaded = false,
  });

  /// What the screen shows before Meta answers.
  const ThreadsConversation.seed(this.focus)
    : ancestors = const [],
      continuation = const [],
      replies = const [],
      loaded = false;

  int get replyCount => replies.fold(0, (sum, chain) => sum + chain.length);

  /// A link stub carries no words or pictures; there is nothing to draw yet.
  bool get focusIsStub => focus.text.isEmpty && !focus.hasMedia && focus.linkCard == null && focus.quoted == null;
}

/// Whether [post] is the one [seed] stands for — by id, else by short code.
///
/// A link stub's id is its URL, and the same post reached from a feed and from
/// a share has different hosts and query strings; the short code is the part
/// they all agree on.
bool threadsSamePost(ThreadsPost post, ThreadsPost seed) {
  if (post.id == seed.id) {
    return true;
  }
  final code = seed.shortcode ?? threadsShortcodeOf(seed.id);
  return code != null && post.shortcode == code;
}

/// Arranges a page's [chains] around [seed].
///
/// The chain holding the post gives what came before it and its author's
/// continuation; any other post in that chain, and every other chain, is a
/// reply thread. When no chain holds the post — Meta answered a short link
/// with a different id — the first chain's root is the post the page is about.
ThreadsConversation threadsConversationFrom(List<List<ThreadsPost>> chains, ThreadsPost seed) {
  final usable = chains.where((chain) => chain.isNotEmpty).toList(growable: false);
  if (usable.isEmpty) {
    return ThreadsConversation(focus: seed, loaded: true);
  }

  var chainIndex = usable.indexWhere((chain) => chain.any((post) => threadsSamePost(post, seed)));
  var focusIndex = chainIndex < 0 ? 0 : usable[chainIndex].indexWhere((post) => threadsSamePost(post, seed));
  if (chainIndex < 0) {
    chainIndex = 0;
    focusIndex = 0;
  }

  final chain = usable[chainIndex];
  final focus = _keepRepostContext(chain[focusIndex], seed);
  final after = chain.sublist(focusIndex + 1);
  final continuation = after.takeWhile((post) => post.handle == focus.handle).toList(growable: false);
  final rest = after.sublist(continuation.length);

  return ThreadsConversation(
    ancestors: chain.sublist(0, focusIndex),
    focus: focus,
    continuation: continuation,
    replies: [
      if (rest.isNotEmpty) rest,
      for (final (index, other) in usable.indexed)
        if (index != chainIndex) other,
    ],
    loaded: true,
  );
}

/// A repost opened from the feed is read back as the original; keep saying
/// who reposted it, which is why it was on the reader's screen at all.
ThreadsPost _keepRepostContext(ThreadsPost fresh, ThreadsPost seed) {
  if (!seed.isRepost || fresh.isRepost) {
    return fresh;
  }
  return fresh.repostedBy(
    id: fresh.id,
    handle: seed.repostedByHandle!,
    name: seed.reposterDisplayName,
    at: fresh.publishedAt,
  );
}

/// Reply threads that involve the post's author — the "author's replies" view.
List<List<ThreadsPost>> threadsAuthorReplies(ThreadsConversation conversation) => [
  for (final chain in conversation.replies)
    if (chain.any((post) => post.handle == conversation.focus.handle)) chain,
];

/// How many posts of a reply thread show before "continue thread".
const kThreadsReplyChainPreview = 3;
