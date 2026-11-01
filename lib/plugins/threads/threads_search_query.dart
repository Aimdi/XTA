import 'package:xta/plugins/plugin_url.dart';
import 'package:xta/plugins/threads/threads_models.dart';

/// A search the results page shows: [query] the words or the tag (without its `#`), and [profile] a handle the
/// words also spell.
typedef ThreadsSearchTerms = ({String query, bool tag, String? profile});

/// What a query typed into Threads search names, by the rules the search sheet and the results page share
/// (docs/specs/threads-x-parity-3.md, Search).
sealed class ThreadsQuery {
  const ThreadsQuery();

  /// What the search history keeps of it; a post link is not kept.
  String? get remembered;

  /// The search it runs on the results page; null when it opens a page of its own instead.
  ThreadsSearchTerms? get terms;
}

/// A pasted post link: that post opens.
final class ThreadsPostQuery extends ThreadsQuery {
  final ThreadsPostLink link;

  const ThreadsPostQuery(this.link);

  @override
  String? get remembered => null;

  @override
  ThreadsSearchTerms? get terms => null;
}

/// A #tag: the posts filed under it.
final class ThreadsTagQuery extends ThreadsQuery {
  final String tag;

  const ThreadsTagQuery(this.tag);

  @override
  String get remembered => '#$tag';

  @override
  ThreadsSearchTerms get terms => (query: tag, tag: true, profile: null);
}

/// An @handle or a profile link: that profile.
final class ThreadsProfileQuery extends ThreadsQuery {
  final String handle;

  const ThreadsProfileQuery(this.handle);

  @override
  String get remembered => handle;

  @override
  ThreadsSearchTerms? get terms => null;
}

/// Words: the posts that say them, with [profile] the handle they also spell.
final class ThreadsWordsQuery extends ThreadsQuery {
  final String words;
  final String? profile;

  const ThreadsWordsQuery(this.words, {this.profile});

  @override
  String get remembered => words;

  @override
  ThreadsSearchTerms get terms => (query: words, tag: false, profile: profile);
}

/// What [text] names, or null when it is blank.
ThreadsQuery? threadsQueryOf(String text) {
  final query = text.trim();
  if (query.isEmpty) return null;
  if (parseThreadsLink(query) case final ThreadsPostLink link) return ThreadsPostQuery(link);
  final tag = query.startsWith('#') ? query.substring(1).trim() : '';
  if (tag.isNotEmpty) return ThreadsTagQuery(tag);
  final handle = normaliseThreadsHandle(query);
  final named = query.startsWith('@') || (Uri.tryParse(query)?.host.contains('threads.') ?? false);
  if (named && handle != null) return ThreadsProfileQuery(handle);
  return ThreadsWordsQuery(query, profile: handle);
}

/// [terms] as the search field shows them: a tag with its `#`.
String threadsSearchText(ThreadsSearchTerms terms) => terms.tag ? '#${terms.query}' : terms.query;
