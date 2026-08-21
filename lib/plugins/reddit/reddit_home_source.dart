import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/reddit/reddit_client.dart';
import 'package:xta/plugins/reddit/reddit_sort_sheet.dart';

/// What the Reddit tab is showing: a discovery rail, or one followed community.
class RedditHomeSource {
  final RedditFeedMode mode;
  final String? subreddit;

  const RedditHomeSource({
    this.mode = RedditFeedMode.following,
    this.subreddit,
  });

  bool get viewingSubreddit {
    final name = subreddit;
    return name != null && name.isNotEmpty;
  }

  @override
  bool operator ==(Object other) =>
      other is RedditHomeSource &&
      other.mode == mode &&
      other.subreddit?.toLowerCase() == subreddit?.toLowerCase();

  @override
  int get hashCode => Object.hash(mode, subreddit?.toLowerCase());
}

/// Stable id of the open feed. A followed community is `r/name`, never a rail.
String redditHomeFeedKey(RedditHomeSource source) {
  if (source.viewingSubreddit) {
    return 'r/${source.subreddit!.toLowerCase()}';
  }
  return source.mode.name;
}

bool isSelectedRedditCommunity(String? selected, String name) =>
    selected != null && selected.toLowerCase() == name.toLowerCase();

bool redditHomeRailSelected(RedditHomeSource source, RedditFeedMode mode) =>
    !source.viewingSubreddit && source.mode == mode;

String? storedRedditSelectedSubreddit(BasePrefService prefs) {
  final raw = prefs.get<String>(optionPluginRedditSelectedSubreddit) ?? '';
  return raw.isEmpty ? null : normaliseSubreddit(raw);
}

/// The Reddit tab's selected rail or community. Replaces setState on the screen.
class RedditHomeStore extends Store<RedditHomeSource> {
  final BasePrefService prefs;

  RedditHomeStore(this.prefs) : super(_fromPrefs(prefs));

  static RedditHomeSource _fromPrefs(BasePrefService prefs) => RedditHomeSource(
    mode: storedRedditFeedMode(prefs),
    subreddit: storedRedditSelectedSubreddit(prefs),
  );

  Future<void> selectMode(RedditFeedMode mode) async {
    await prefs.set(optionPluginRedditFeedMode, mode.name);
    await prefs.set(optionPluginRedditSelectedSubreddit, '');
    update(RedditHomeSource(mode: mode));
  }

  Future<void> selectSubreddit(String name) async {
    final normalised = normaliseSubreddit(name);
    if (normalised == null) {
      return;
    }
    await prefs.set(optionPluginRedditSelectedSubreddit, normalised);
    update(RedditHomeSource(mode: state.mode, subreddit: normalised));
  }

  /// A persisted community that is no longer followed should not reopen.
  Future<void> reconcileFollowed(Iterable<String> followed) async {
    final current = state.subreddit;
    if (current == null) {
      return;
    }
    if (followed.any((name) => name.toLowerCase() == current.toLowerCase())) {
      return;
    }
    await prefs.set(optionPluginRedditSelectedSubreddit, '');
    update(RedditHomeSource(mode: state.mode));
  }
}
