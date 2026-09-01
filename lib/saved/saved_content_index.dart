/// The parsed form of what the saved and liked tables store.
///
/// Both tables keep a post as its raw JSON blob, commonly tens of kilobytes.
/// Decoding one is expensive enough that it must not happen per build or per
/// keystroke, so it happens once per blob and is kept beside the store that
/// emitted it.
library;

import 'dart:convert';

import 'package:xta/client/client.dart';
import 'package:xta/plugins/reddit/reddit_archive.dart';
import 'package:xta/plugins/reddit/reddit_client.dart';

/// One stored post, parsed: the model its tile renders and the lowercased text
/// the saved-screen search matches against.
class SavedContent {
  /// Null when the row carries no blob (the post was too large to store) or
  /// when the blob no longer parses.
  final TweetWithCard? tweet;

  /// A Reddit post filed in Archiv. Null for X posts.
  final RedditPost? reddit;

  final String haystack;

  const SavedContent({this.tweet, this.reddit, required this.haystack});

  static const empty = SavedContent(haystack: '');

  /// [needle] must already be lowercased.
  bool matches(String needle) => haystack.contains(needle);
}

/// Post text (including a long post's note text) plus author name and handle,
/// lowercased once so searching does not re-case every post per keystroke.
String _haystackOf(TweetWithCard tweet) {
  var parts = [tweet.fullText, tweet.text, tweet.noteText, tweet.user?.name, tweet.user?.screenName];

  return parts.whereType<String>().join('\n').toLowerCase();
}

SavedContent parseSavedContent(String? blob) {
  if (blob == null) {
    return SavedContent.empty;
  }

  try {
    final decoded = jsonDecode(blob);
    final reddit = redditPostFromArchive(decoded);
    if (reddit != null) {
      return SavedContent(reddit: reddit, haystack: redditArchiveHaystack(reddit));
    }
    var tweet = TweetWithCard.fromJson(decoded);

    return SavedContent(tweet: tweet, haystack: _haystackOf(tweet));
  } catch (_) {
    return SavedContent.empty;
  }
}

/// The parsed posts of a store, keyed by id.
///
/// Rebuilt whenever the store emits: an entry whose blob is unchanged keeps the
/// parse it already had, so a reload only pays for the rows that changed.
/// Membership is a map lookup rather than a scan of the whole table.
class SavedContentIndex {
  var _entries = <String, _Entry>{};

  bool contains(String id) => _entries.containsKey(id);

  SavedContent? operator [](String id) => _entries[id]?.content;

  void rebuild<T>(List<T> items, {required String Function(T) idOf, required String? Function(T) blobOf}) {
    var previous = _entries;

    _entries = Map.fromEntries(
      items.map((item) {
        var id = idOf(item);
        var blob = blobOf(item);
        var cached = previous[id];
        var entry = cached != null && cached.blob == blob ? cached : _Entry(blob);

        return MapEntry(id, entry);
      }),
    );
  }
}

/// Parsed on first read, not on rebuild. Membership is answered for every post
/// in the feed, and answering it must not decode the whole saved table.
class _Entry {
  final String? blob;
  SavedContent? _content;

  _Entry(this.blob);

  SavedContent get content => _content ??= parseSavedContent(blob);
}
