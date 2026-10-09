import 'dart:convert';

import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/threads/threads_models.dart';

/// Which posts of the merged Threads feed to show.
enum ThreadsFeedContent { all, media, links }

/// What the reader chose to see in the Threads tab. Applied to what is
/// already loaded — nothing here changes what is asked of Meta.
class ThreadsFeedOptions {
  final ThreadsFeedContent content;
  final bool hideReplies;
  final bool hideReposts;

  const ThreadsFeedOptions({this.content = ThreadsFeedContent.all, this.hideReplies = false, this.hideReposts = false});

  /// How many choices differ from showing everything, for the filter badge.
  int get activeCount => (content == ThreadsFeedContent.all ? 0 : 1) + (hideReplies ? 1 : 0) + (hideReposts ? 1 : 0);

  bool get filtered => activeCount > 0;

  ThreadsFeedOptions copy({ThreadsFeedContent? content, bool? hideReplies, bool? hideReposts}) => ThreadsFeedOptions(
    content: content ?? this.content,
    hideReplies: hideReplies ?? this.hideReplies,
    hideReposts: hideReposts ?? this.hideReposts,
  );

  Map<String, Object> toJson() => {'content': content.name, 'hideReplies': hideReplies, 'hideReposts': hideReposts};

  static ThreadsFeedOptions fromJson(String? raw) {
    try {
      final json = jsonDecode(raw ?? '');
      if (json is! Map) {
        return const ThreadsFeedOptions();
      }
      return ThreadsFeedOptions(
        content: ThreadsFeedContent.values.asNameMap()[json['content']] ?? ThreadsFeedContent.all,
        hideReplies: json['hideReplies'] == true,
        hideReposts: json['hideReposts'] == true,
      );
    } catch (_) {
      return const ThreadsFeedOptions();
    }
  }
}

/// [posts] as [options] would have them shown.
List<ThreadsPost> filterThreadsFeed(List<ThreadsPost> posts, ThreadsFeedOptions options) {
  if (!options.filtered) {
    return posts;
  }
  return [
    for (final post in posts)
      if (!(options.hideReplies && post.isReply) &&
          !(options.hideReposts && post.isRepost) &&
          _matchesContent(post, options.content))
        post,
  ];
}

bool _matchesContent(ThreadsPost post, ThreadsFeedContent content) => switch (content) {
  ThreadsFeedContent.all => true,
  ThreadsFeedContent.media => post.hasMedia || (post.quoted?.hasMedia ?? false),
  ThreadsFeedContent.links => threadsPostHasLink(post),
};

/// Whether a post points somewhere off Threads: a preview card, a link Meta
/// resolved in the caption, or a bare address in the text.
bool threadsPostHasLink(ThreadsPost post) =>
    post.linkCard != null ||
    post.fragments.any((f) => f.kind == ThreadsFragmentKind.link) ||
    RegExp(r'https?://', caseSensitive: false).hasMatch(post.text);

/// The Threads tab's filter choices, remembered across restarts.
class ThreadsFeedOptionsStore extends Store<ThreadsFeedOptions> {
  final BasePrefService prefs;

  ThreadsFeedOptionsStore(this.prefs)
    : super(ThreadsFeedOptions.fromJson(prefs.get<String>(optionPluginThreadsFeedOptions)));

  Future<void> set(ThreadsFeedOptions options) async {
    update(options);
    await prefs.set(optionPluginThreadsFeedOptions, jsonEncode(options.toJson()));
  }

  Future<void> reset() => set(const ThreadsFeedOptions());
}
