import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/plugin_profile_tabs.dart';
import 'package:xta/plugins/threads/threads_api.dart';
import 'package:xta/plugins/threads/threads_client.dart';
import 'package:xta/plugins/threads/threads_direct_client.dart';
import 'package:xta/plugins/threads/threads_models.dart';
import 'package:xta/plugins/threads/threads_store.dart';

/// One Threads profile as far as it has been read.
class ThreadsProfileState {
  final ThreadsProfile? profile;
  final List<ThreadsPost> posts;

  /// What the profile's replies page gave; null until the Replies tab asks.
  final List<ThreadsPost>? replies;
  final bool loadingReplies;

  const ThreadsProfileState({this.profile, this.posts = const [], this.replies, this.loadingReplies = false});

  bool get isEmpty => profile == null && posts.isEmpty;

  ThreadsProfileState copy({
    ThreadsProfile? profile,
    List<ThreadsPost>? posts,
    List<ThreadsPost>? replies,
    bool? loadingReplies,
  }) => ThreadsProfileState(
    profile: profile ?? this.profile,
    posts: posts ?? this.posts,
    replies: replies ?? this.replies,
    loadingReplies: loadingReplies ?? this.loadingReplies,
  );

  /// The posts one tab shows. [liked] is the reader's own hearts, for Saved.
  List<ThreadsPost> forTab(PluginProfileFeedTab tab, {List<ThreadsPost> liked = const []}) => switch (tab) {
    PluginProfileFeedTab.posts => [
      for (final post in posts)
        if (!post.isReply) post,
    ],
    PluginProfileFeedTab.replies => _newestFirst([...?replies, ...posts.where((post) => post.isReply)]),
    PluginProfileFeedTab.media => _newestFirst([...posts, ...?replies].where((post) => post.hasMedia)),
    PluginProfileFeedTab.saved => liked,
  };
}

/// Without repeats — a reply can be both on the profile and on its replies
/// page — and newest first.
List<ThreadsPost> _newestFirst(Iterable<ThreadsPost> posts) {
  final seen = <String>{};
  final unique = [
    for (final post in posts)
      if (seen.add(post.id)) post,
  ];
  return unique..sort((a, b) => (b.publishedAt ?? DateTime(0)).compareTo(a.publishedAt ?? DateTime(0)));
}

/// Reads one profile: its card from the public page (else a session, else
/// Xy), its posts through the same source the feed uses, and — only when
/// the reader opens that tab — its replies page.
class ThreadsProfileStore extends Store<ThreadsProfileState> {
  final String handle;
  final ThreadsDirectClient direct;
  final ThreadsApi api;
  final ThreadsFeedStore feed;
  final BasePrefService prefs;

  ThreadsProfileStore({
    required this.handle,
    required this.direct,
    required this.api,
    required this.feed,
    required this.prefs,
  }) : super(const ThreadsProfileState());

  var _repliesAsked = false;

  /// Header and posts together. A failure keeps whatever was already shown;
  /// only a profile that never had anything to show surfaces the error.
  Future<void> load({bool force = false}) async {
    if (handle.isEmpty) {
      setError(ThreadsException(ThreadsErrorKind.noSuchFeed, 'empty handle'));
      return;
    }
    await execute(() async {
      // Guest HTML is single-flight in the client, so asking for the header
      // and the posts at once is not two paced page loads. Both are awaited
      // together: a posts failure while the header is still loading must not
      // escape as an unhandled error.
      final (profile, read) = await (_resolveProfile(), _readPosts(force)).wait;
      final (:posts, :error) = read;
      final resolved = profile ?? threadsProfileFromPosts(handle, posts);
      if (resolved == null && posts.isEmpty) {
        if (!state.isEmpty) {
          return state;
        }
        throw error ?? ThreadsException(ThreadsErrorKind.noSuchFeed, 'profile missing');
      }
      return state.copy(profile: resolved, posts: posts);
    });
  }

  Future<({List<ThreadsPost> posts, Object? error})> _readPosts(bool force) async {
    try {
      return (posts: await feed.postsFor([handle], forceRefresh: force), error: null);
    } catch (e) {
      return (posts: const <ThreadsPost>[], error: e);
    }
  }

  Future<ThreadsProfile?> _resolveProfile() async {
    try {
      return await direct.fetchGuestProfile(handle);
    } catch (_) {
      // A session, then Xy, may still know the account.
    }
    if (direct.hasCookies) {
      try {
        return await direct.fetchProfile(handle);
      } catch (_) {
        // Xy below.
      }
    }
    try {
      final base = prefs.get<String>(optionPluginThreadsApiBase) ?? kThreadsApiDefaultBase;
      return await api.profile(base, prefs.get<String>(optionPluginThreadsApiToken) ?? '', handle);
    } catch (_) {
      return null;
    }
  }

  /// The replies page, asked for once per visit. When Meta does not embed it
  /// for a guest, the tab still shows the replies the posts already carried.
  Future<void> loadReplies() async {
    if (_repliesAsked) {
      return;
    }
    _repliesAsked = true;
    update(state.copy(loadingReplies: true));
    var replies = const <ThreadsPost>[];
    try {
      replies = await direct.fetchGuestReplies(handle);
    } catch (_) {
      // Shown from the posts alone.
    }
    update(state.copy(replies: replies, loadingReplies: false));
  }
}
