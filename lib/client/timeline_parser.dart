/// Turns X's timeline JSON into the app's tweet model.
///
/// Separated from `client.dart` because this is the half that breaks: the
/// transport rarely changes, while X reshapes its timeline entries constantly.
/// Nothing here performs a request, so every function can be exercised against
/// a recorded response — see `test/parser_resilience_test.dart`.
///
/// Per `.claude/skills/parse-api`, an entry whose shape is no longer recognised
/// is skipped rather than thrown on: one unreadable item must not empty a page.
library;

import 'package:dart_twitter_api/src/utils/date_utils.dart';
import 'package:dart_twitter_api/twitter_api.dart';
import 'package:quax/client/tweet_models.dart';
import 'package:quax/user.dart';
import 'package:quax/utils/iterables.dart';

/// Enum for grouping mode when creating tweet chains
/// This replaces the boolean flag for better clarity and type safety
enum GroupingMode {
  /// Group tweets by their conversation thread
  threads,
  /// Keep tweets as individual items (flat list)
  flat,
}

class TimelineParser {
  static PaginatedUsers parseUsersTimeline(dynamic instructions) {
    var users = PaginatedUsers()..users = [];
    if (instructions == null) {
      return users;
    }
    for (final instruction in instructions) {
      if (instruction["type"] != "TimelineAddEntries" || instruction["entries"] == null) continue;
      var entries = instruction["entries"] as List? ?? [];
      users.nextCursorStr = getCursor(entries, [], 'cursor-bottom', 'Bottom');
      users.previousCursorStr = getCursor(entries, [], 'cursor-top', 'Top');
      for (final entry in entries) {
        final userResult = entry["content"]?["itemContent"]?["user_results"]?["result"];
        if (userResult == null) continue;
        var user = UserWithExtra()
          ..screenName = userResult["core"]?["screen_name"]
          ..name = userResult["core"]?["name"]
          ..profileImageUrlHttps = userResult["avatar"]?["image_url"]
          ..verified = userResult["is_blue_verified"]
          ..createdAt = convertTwitterDateTime(userResult["core"]?["created_at"])
          ..idStr = userResult["rest_id"];
        users.users!.add(user);
      }
    }
    return users;
  }

  // GraphQL "ListByRestId" — metadata of an X list. The name is null when the
  // list is deleted or private (data.list absent from the response).

  static bool isNotPromoted(Map<String, dynamic> item) {
    final bool entryIdContainsPromoted = item['entryId']?.contains("promoted") ?? false;
    final bool hasPromotedMetadata = item['item']?['itemContent']?.containsKey("promotedMetadata") ?? false;
    return !(entryIdContainsPromoted || hasPromotedMetadata);
  }

  /// The tweet node inside a `tweet_results.result`, unwrapping the extra layer
  /// that reply-restricted tweets (`TweetWithVisibilityResults`) add. Null when
  /// the result carries no usable tweet — deleted, restricted, or a shape we no
  /// longer recognise.
  static Map<String, dynamic>? _unwrapTweetResult(dynamic result) {
    if (result is! Map<String, dynamic>) {
      return null;
    }
    final unwrapped = result['rest_id'] != null ? result : result['tweet'];
    if (unwrapped is! Map<String, dynamic> || unwrapped['rest_id'] == null) {
      return null;
    }
    return unwrapped;
  }

  /// Tweets carried by a conversation entry. A reply X withheld becomes a
  /// tombstone so the chain keeps its shape, and an item in a shape we do not
  /// recognise is skipped rather than throwing, so one bad reply cannot empty a
  /// thread.
  static List<TweetWithCard> _conversationTweets(dynamic entry, {required bool skipPromoted}) {
    final items = entry?['content']?['items'] as List<dynamic>? ?? const [];

    return items
        .where((item) => !skipPromoted || isNotPromoted(item))
        .where((item) => item?['item']?['itemContent']?['itemType'] == 'TimelineTweet')
        .where(_carriesResult)
        .map(_conversationTweet)
        .toList();
  }

  /// Whether X answered for this position at all. An entry with no `result` is a
  /// shape we do not recognise rather than a reply being withheld, so it stays
  /// skipped — matching how `tweet-` entries are already treated.
  static bool _carriesResult(dynamic item) {
    final results = item?['item']?['itemContent']?['tweet_results'];

    return results is Map<String, dynamic> && results.containsKey('result');
  }

  /// A reply X refused to give us still occupies a position in the thread, so it
  /// becomes a tombstone rather than disappearing and breaking the chain.
  static TweetWithCard _conversationTweet(dynamic item) {
    final rawResult = item?['item']?['itemContent']?['tweet_results']?['result'];

    final result = _unwrapTweetResult(rawResult);
    if (result != null) {
      return TweetWithCard.fromGraphqlJson(result);
    }

    // `conversation.dart` sorts a chain by id, so a tombstone left with the
    // empty default id would jump to the top of the thread instead of sitting
    // where the missing reply belongs.
    return TweetWithCard.tombstoneFor(rawResult)..idStr = _itemTweetId(item) ?? '';
  }

  /// `conversationthread-1-tweet-2` → `2`; the id of a reply X would not return.
  static String? _itemTweetId(dynamic item) {
    const marker = '-tweet-';

    final entryId = item?['entryId'] as String?;
    final start = entryId?.lastIndexOf(marker) ?? -1;

    return start < 0 ? null : entryId!.substring(start + marker.length);
  }

  static List<TweetChain> createTweetChains(List<dynamic> addEntries) {
    List<TweetChain> replies = [];

    for (var entry in addEntries) {
      var entryId = entry?['entryId'] as String?;
      if (entryId == null) {
        continue;
      }
      if (entryId.startsWith('tweet-')) {
        final tweetResults = entry['content']?['itemContent']?['tweet_results'] as Map<String, dynamic>?;

        // This may happen for tweets that x.com cannot open neither
        if (tweetResults == null || !tweetResults.containsKey('result')) continue;

        final result = _unwrapTweetResult(tweetResults['result']);
        if (result != null) {
          replies.add(
            TweetChain(id: result['rest_id'], tweets: [TweetWithCard.fromGraphqlJson(result)], isPinned: false),
          );
        } else {
          replies.add(TweetChain(id: entryId.substring(6), tweets: [TweetWithCard.tombstone({})], isPinned: false));
        }
      }

      if (entryId.startsWith('conversationthread')) {
        replies.add(
          TweetChain(
            id: entryId.replaceFirst('conversationthread-', ''),
            tweets: _conversationTweets(entry, skipPromoted: true),
            isPinned: false,
          ),
        );
      }
    }

    return replies;
  }

  static List<TweetChain> createTweets(List<dynamic> addEntries, [bool isPinned = false]) {
    List<TweetChain> replies = [];

    // Deleted or restricted posts come back without a usable result; they must
    // be skipped so one bad entry cannot break the whole page.
    Map<String, dynamic>? usableResult(dynamic container) =>
        _unwrapTweetResult(container?['itemContent']?['tweet_results']?['result']);

    for (var entry in addEntries) {
      var entryId = entry?['entryId'] as String?;
      if (entryId == null) {
        continue;
      }
      if (entryId.startsWith('tweet-')) {
        var result = usableResult(entry['content']);
        if (result != null) {
          replies.add(
            TweetChain(id: result['rest_id'], tweets: [TweetWithCard.fromGraphqlJson(result)], isPinned: isPinned),
          );
        }
      } else if (entryId.startsWith('profile-grid-')) {
        // We got a tweet queried from the media tab
        for (var mediaTweet in entry['content']?['items'] as List? ?? []) {
          var result = usableResult(mediaTweet['item']);
          if (result != null) {
            replies.add(
              TweetChain(id: result['rest_id'], tweets: [TweetWithCard.fromGraphqlJson(result)], isPinned: isPinned),
            );
          }
        }
      }

      if (entryId.startsWith('profile-conversation')) {
        replies.add(
          TweetChain(
            id: entryId.replaceFirst('profile-conversation-', ''),
            tweets: _conversationTweets(entry, skipPromoted: false),
            isPinned: false,
          ),
        );
      }
    }
    return replies;
  }

  static TweetStatus createChainsFromGridModule(Map<String, dynamic> timeline) {
    var instructions = timeline['timeline']?['instructions'] as List? ?? [];
    var addEntries = instructions.firstWhereOrNull((e) => e['type'] == 'TimelineAddEntries')?['entries'] as List? ?? [];
    
    var addModItems = instructions.firstWhereOrNull((e) => e['type'] == 'TimelineAddToModule')?['moduleItems'] as List? ?? [];
    
    var repEntries = instructions.where((e) => e['type'] == 'TimelineReplaceEntry').toList();

    String? cursorBottom = getCursor(addEntries, repEntries, 'cursor-bottom', 'Bottom');
    String? cursorTop = getCursor(addEntries, repEntries, 'cursor-top', 'Top');

    var moduleItems = [
      ...addEntries
          .where((e) => e['content']?['entryType'] == 'TimelineTimelineModule')
          .expand((e) => e['content']?['items'] as List? ?? []),
      ...addModItems,
    ];

    List<TweetChain> chains = [];
    for (var item in moduleItems) {
      var result =
          item['item']?['itemContent']?['tweet_results']?['result'] ??
          item['item']?['content']?['tweetResult']?['result'] ??
          item['item']?['content']?['tweet_results']?['result'];
      result = result?['rest_id'] != null ? result : result?['tweet'];
      if (result?['rest_id'] == null) continue;
      chains.add(TweetChain(id: result['rest_id'], tweets: [TweetWithCard.fromGraphqlJson(result)], isPinned: false));
    }

    return TweetStatus(chains: chains, cursorBottom: cursorBottom, cursorTop: cursorTop);
  }

  /// The cursor behind X's "Show additional replies" prompt — the replies it
  /// hides by default because it judged them low quality or offensive.
  ///
  /// Kept apart from [getCursor] deliberately: `cursor-bottom` is automatic
  /// paging, whereas this is content the reader has to ask for, so the caller
  /// must decide whether to follow it rather than have it fetched for them.
  ///
  /// The entry id is matched case-insensitively on `showmore` because X has
  /// spelled this cursor several ways (`cursor-showMore`,
  /// `cursor-showmorethreads`, `…prompt`). An id we do not recognise yields
  /// null, which simply leaves the prompt unoffered.
  static String? getShowMoreCursor(List<dynamic> addEntries) {
    final entry = addEntries.firstWhereOrNull(
      (e) => (e?['entryId'] as String?)?.toLowerCase().contains('showmore') ?? false,
    );

    return entry == null ? null : _cursorValue(entry['content']);
  }

  /// The three shapes a cursor entry's `content` has taken across X's revisions.
  static String? _cursorValue(dynamic content) {
    if (content is! Map<String, dynamic>) {
      return null;
    }

    final value = content['value'] ?? content['operation']?['cursor']?['value'] ?? content['itemContent']?['value'];

    // Checked rather than cast: a cast would throw on the day X sends a number.
    return value is String ? value : null;
  }

  static String? getCursor(List<dynamic> addEntries, List<dynamic> repEntries, String legacyType, String type) {
    String? cursor;

    Map<String, dynamic>? cursorEntry;

    var isLegacyCursor = addEntries.any((element) => element['entryId'].startsWith('cursor'));
    if (isLegacyCursor) {
      cursorEntry = addEntries.firstWhere((e) => e['entryId'].contains(legacyType), orElse: () => null);
    } else {
      cursorEntry = addEntries
          .where((e) => e['entryId'].startsWith('sq-C'))
          .firstWhere((e) => e['content']['operation']['cursor']['cursorType'] == type, orElse: () => null);
    }

    if (cursorEntry != null) {
      var content = cursorEntry['content'];
      if (content.containsKey('value')) {
        cursor = content['value'];
      } else if (content.containsKey('operation')) {
        cursor = content['operation']['cursor']['value'];
      } else {
        cursor = content['itemContent']['value'];
      }
    } else {
      // Look for a "replaceEntry" with the cursor
      var cursorReplaceEntry = repEntries.firstWhere(
        (e) => e.containsKey('replaceEntry')
            ? e['replaceEntry']['entryIdToReplace'].contains(type)
            : e['entry']['content']['cursorType'].contains(type),
        orElse: () => null,
      );

      if (cursorReplaceEntry != null) {
        cursor = cursorReplaceEntry.containsKey('replaceEntry')
            ? cursorReplaceEntry['replaceEntry']['entry']['content']['operation']['cursor']['value']
            : cursorReplaceEntry['entry']['content']['value'];
      }
    }

    return cursor;
  }

  static TweetStatus createUnconversationedChainsGraphql(
    Map<String, dynamic> result,
    String tweetIndicator,
    List<String> pinnedTweets,
    bool mapToThreads,
    bool includeReplies,
  ) {
    var instructions = result['timeline']['instructions'] as List;
    if (instructions.isEmpty || !instructions.any((e) => e['type'] == 'TimelineAddEntries')) {
      return TweetStatus(chains: [], cursorBottom: null, cursorTop: null);
    }

    var addEntries = instructions.firstWhere((e) => e['type'] == 'TimelineAddEntries')['entries'] as List;
    var repEntries = instructions.where((e) => e['type'] == 'TimelineReplaceEntry').toList();

    String? cursorBottom = getCursor(addEntries, repEntries, 'cursor-bottom', 'Bottom');
    String? cursorTop = getCursor(addEntries, repEntries, 'cursor-top', 'Top');

    var tweets = _createTweetsGraphql(tweetIndicator, addEntries, includeReplies);

    // First, get all the IDs of the tweets we need to display.
    String? entryRestId(dynamic e) {
      var result = e['content']?['itemContent']?['tweet_results']?['result'];
      return result?['rest_id'] ?? result?['tweet']?['rest_id'];
    }

    var tweetEntries = addEntries
        .where((e) => e['entryId'].contains(tweetIndicator) && entryRestId(e) != null)
        .sorted((a, b) => b['sortIndex'].compareTo(a['sortIndex']))
        .map(entryRestId)
        .cast<String?>()
        .toList();

    Map<String, List<TweetWithCard>> conversations = tweets.values.where((e) => tweetEntries.contains(e.idStr)).groupBy(
      (e) {
        // Group by conversation ID when in threads mode, otherwise by tweet ID
        final groupingMode = mapToThreads ? GroupingMode.threads : GroupingMode.flat;
        return groupingMode == GroupingMode.threads ? e.conversationIdStr : e.idStr;
      },
    ).cast<String, List<TweetWithCard>>();

    List<TweetChain> chains = [];

    // Order all the conversations by newest first (assuming the ID is an incrementing key), and create a chain from them
    for (var conversation in conversations.entries.sorted((a, b) => b.key.compareTo(a.key))) {
      var chainTweets = conversation.value.sorted((a, b) => a.idStr!.compareTo(b.idStr!));

      chains.add(TweetChain(id: conversation.key, tweets: chainTweets, isPinned: false));
    }

    // If we want to show pinned tweets, add them before the chains that we already have
    if (pinnedTweets.isNotEmpty) {
      for (var id in pinnedTweets) {
        // It's possible for the pinned tweet to either not exist, or not be returned, so handle that
        if (tweets.containsKey(id)) {
          chains.insert(0, TweetChain(id: id, tweets: [tweets[id]!], isPinned: true));
        }
      }
    }

    return TweetStatus(chains: chains, cursorBottom: cursorBottom, cursorTop: cursorTop);
  }

  static TweetStatus createUnconversationedChains(
    Map<String, dynamic> result,
    String tweetIndicator,
    List<String> pinnedTweets,
    bool mapToThreads,
    bool includeReplies,
    bool showPinnedTweet,
    int Function() getTweetsCounter,
    void Function() increaseTweetCounter,
  ) {
    final timeline =
        result["data"]?["user"]?["result"]?["timeline_v2"] ?? result["data"]?["user"]?["result"]?["timeline"];
    var instructions = timeline?['timeline']?['instructions'] as List? ?? [];
    var addEntriesInstructions = instructions.firstWhereOrNull((e) => e['type'] == 'TimelineAddEntries');
    var addModEntriesInstructions = instructions.firstWhereOrNull((e) => e['type'] == 'TimelineAddToModule');
    List addModEntries = addModEntriesInstructions?['moduleItems'] as List? ?? [];

    if (addEntriesInstructions == null && addModEntries.isEmpty) {
      return TweetStatus(chains: [], cursorBottom: null, cursorTop: null);
    }

    var addPinnedTweetsInstructions = instructions.firstWhereOrNull((e) => e['type'] == 'TimelinePinEntry');
    var addEntries = addEntriesInstructions?['entries'] as List? ?? [];
    var repEntries = instructions.where((e) => e['type'] == 'TimelineReplaceEntry').toList();
    List addPinnedEntries = List<dynamic>.empty(growable: true);
    if (addPinnedTweetsInstructions != null) {
      addPinnedEntries.add(addPinnedTweetsInstructions['entry']);
    }

    String? cursorBottom = getCursor(addEntries, repEntries, 'cursor-bottom', 'Bottom');
    String? cursorTop = getCursor(addEntries, repEntries, 'cursor-top', 'Top');

    var chains = createTweets(addEntries);
    var pinnedChains = createTweets(addPinnedEntries, true);

    for (final addModEntry in addModEntries) {
      final entryId = addModEntry['entryId'] as String? ?? addModEntry['entry_id'] as String? ?? '';
      if (entryId.startsWith('profile-grid-')) {
        Map<String, dynamic>? result = addModEntry['item']?['content']?['tweetResult']?['result'];
        result ??= addModEntry['item']?['itemContent']?['tweet_results']?['result'];
        result ??= addModEntry['item']?['content']?['tweet_results']?['result'];
        if (result != null) {
          result = result['rest_id'] != null ? result : result['tweet'];
          if (result != null) {
            chains.add(
              TweetChain(id: result['rest_id'], tweets: [TweetWithCard.fromGraphqlJson(result)], isPinned: false),
            );
          }
        }
      }
    }

    //If we want to show pinned tweets, add them before the others that we already have
    if (pinnedTweets.isNotEmpty & showPinnedTweet) {
      chains.insertAll(0, pinnedChains);
    }

    if (chains.length < 5) {
      increaseTweetCounter();
      if (getTweetsCounter() > 5) {
        cursorBottom = null;
      }
    }
    return TweetStatus(chains: chains, cursorBottom: cursorBottom, cursorTop: cursorTop);
  }

  static TweetStatus createTimelineChains(
    Map<String, dynamic> result,
    String tweetIndicator,
    List<String> pinnedTweets,
    bool mapToThreads,
    bool includeReplies,
    bool showPinnedTweet,
    int Function() getTweetsCounter,
    void Function() increaseTweetCounter,
  ) {
    var instructions = result["data"]["home"]["home_timeline_urt"]['instructions'] as List;
    var addEntriesInstructions = instructions.firstWhereOrNull((e) => e['type'] == 'TimelineAddEntries');
    if (addEntriesInstructions == null) {
      return TweetStatus(chains: [], cursorBottom: null, cursorTop: null);
    }
    var addPinnedTweetsInstructions = instructions.firstWhereOrNull((e) => e['type'] == 'TimelinePinEntry');
    var addEntries = addEntriesInstructions['entries'] as List;
    var repEntries = instructions.where((e) => e['type'] == 'TimelineReplaceEntry').toList();
    List addPinnedEntries = List<dynamic>.empty(growable: true);
    if (addPinnedTweetsInstructions != null) {
      addPinnedEntries.add(addPinnedTweetsInstructions['entry']);
    }

    String? cursorBottom = getCursor(addEntries, repEntries, 'cursor-bottom', 'Bottom');
    String? cursorTop = getCursor(addEntries, repEntries, 'cursor-top', 'Top');
    var chains = createTweets(addEntries);
    var pinnedChains = createTweets(addPinnedEntries, true);

    //If we want to show pinned tweets, add them before the others that we already have
    if (pinnedTweets.isNotEmpty & showPinnedTweet) {
      chains.insertAll(0, pinnedChains);
    }

    if (chains.length < 5) {
      increaseTweetCounter();
      if (getTweetsCounter() > 5) {
        cursorBottom = null;
      }
    }

    return TweetStatus(chains: chains, cursorBottom: cursorBottom, cursorTop: cursorTop);
  }

  static Map<String, TweetWithCard> _createTweetsGraphql(
    String entryPrefix,
    List<dynamic> allTweets,
    bool includeReplies,
  ) {
    bool includeTweet(dynamic t) {
      // Exclude any items that aren't tweets
      if (!t['entryId'].startsWith(entryPrefix)) {
        return false;
      }

      if (t['content']['itemContent']['promotedMetadata'] != null) {
        return false;
      }

      if (t['content']?['itemContent']?['tweet_results']?['result'] == null) {
        return false;
      }

      return true;
    }

    var filteredTweets = allTweets.where(includeTweet);

    var globalTweets = filteredTweets.map((e) {
      var elm = e['content']['itemContent']['tweet_results']['result'];
      if (elm['rest_id'] == null && elm['tweet'] != null) {
        elm = elm['tweet'];
      }

      return elm;
    }).toList();

    var tweets = [];
    try {
      tweets = globalTweets.map((e) => TweetWithCard.fromGraphqlJson(e)).toList();
    } catch (exc) {
      rethrow;
    }

    // include replies only if we should
    tweets = tweets.where((tweet) {
      if (!includeReplies && (tweet.inReplyToStatusIdStr != null || tweet.inReplyToUserIdStr != null)) {
        return false;
      }
      return true;
    }).toList();

    return {for (var e in tweets) e.idStr: e};
  }
}
