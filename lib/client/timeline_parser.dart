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

import 'package:dart_twitter_api/twitter_api.dart';
import 'package:xta/client/tweet_models.dart';
import 'package:xta/user.dart';
import 'package:xta/utils/iterables.dart';
import 'package:xta/utils/json.dart';

class TimelineParser {
  static PaginatedUsers parseUsersTimeline(dynamic instructions) {
    var users = PaginatedUsers()..users = [];
    if (instructions is! Iterable) {
      return users;
    }
    for (final instruction in instructions) {
      if (instruction is! Map || instruction["type"] != "TimelineAddEntries" || instruction["entries"] == null) {
        continue;
      }
      var entries = List.from(instruction["entries"]);
      users.nextCursorStr = getCursor(entries, [], 'cursor-bottom', 'Bottom');
      users.previousCursorStr = getCursor(entries, [], 'cursor-top', 'Top');
      for (final entry in entries) {
        if (entry is! Map) continue;
        for (final result in _userResultsInEntry(entry)) {
          final user = _userFromGraphqlResult(result);
          if (user != null) users.users!.add(user);
        }
      }
    }
    return users;
  }

  /// User nodes carried by one timeline entry. X has shipped both a single
  /// `itemContent.user_results` on the entry and a `TimelineTimelineModule`
  /// of those items — Retweeters uses both. Unreadable entries yield nothing.
  static Iterable<Map> _userResultsInEntry(Map entry) {
    final content = entry['content'];
    if (content is! Map) return const [];
    final direct = _userResultOf(content['itemContent']);
    if (direct != null) return [direct];
    final items = content['items'];
    if (items is! List) return const [];
    return [
      for (final item in items)
        if (item is Map) _userResultOf(item['item']?['itemContent'] ?? item['itemContent']),
    ].whereType<Map>();
  }

  static Map? _userResultOf(dynamic itemContent) {
    if (itemContent is! Map) return null;
    final result = itemContent['user_results']?['result'] ?? itemContent['userResults']?['result'];
    return result is Map ? result : null;
  }

  static UserWithExtra? _userFromGraphqlResult(Map userResult) {
    final map = _asStringKeyedMap(userResult);
    if (map == null) return null;
    try {
      final user = UserWithExtra.fromNonLegacyJson(map);
      user.idStr ??= map['rest_id'] as String?;
      if (user.idStr == null) return null;
      user.verified = user.verified ?? false;
      user.name ??= '';
      user.screenName ??= '';
      user.createdAt ??= DateTime.fromMillisecondsSinceEpoch(0);
      return user;
    } catch (_) {
      return null;
    }
  }

  /// GraphQL "Retweeters" — people who reposted a tweet, not the quote-tweets.
  /// The timeline lives at `data.retweeters_timeline.timeline.instructions`,
  /// then the same TimelineAddEntries / user_results shape as Followers.
  static dynamic retweetersInstructions(dynamic body) {
    return Json(body)['data']['retweeters_timeline']['timeline']['instructions'].raw;
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
    final map = _asStringKeyedMap(result);
    if (map == null) return null;
    final unwrapped = map['rest_id'] != null ? map : _asStringKeyedMap(map['tweet']);
    if (unwrapped == null || unwrapped['rest_id'] == null) {
      return null;
    }
    return unwrapped;
  }

  /// JSON maps are `Map<String, dynamic>`; hand-built test maps are often
  /// `Map<String, Object>`. Accept either so a type quirk never becomes a crash.
  static Map<String, dynamic>? _asStringKeyedMap(dynamic value) {
    if (value is Map<String, dynamic>) return value;
    if (value is Map) return Map<String, dynamic>.from(value);
    return null;
  }

  /// Tweets carried by a conversation entry. A reply X withheld becomes a
  /// tombstone so the chain keeps its shape, and an item in a shape we do not
  /// recognise is skipped rather than throwing, so one bad reply cannot empty a
  /// thread.
  static List<TweetWithCard> _conversationTweets(dynamic entry, {required bool skipPromoted}) {
    final items = entry?['content']?['items'] as List<dynamic>? ?? const [];

    return items
        .where((item) => !skipPromoted || (item is Map && isNotPromoted(Map<String, dynamic>.from(item))))
        .where(_isConversationTweetItem)
        .where(_carriesResult)
        .map(_conversationTweet)
        .toList();
  }

  /// TimelineTweet items, or items that omitted `itemType` but still carry a
  /// tweet result — X has shipped both shapes inside conversation modules.
  static bool _isConversationTweetItem(dynamic item) {
    if (item is! Map) {
      return false;
    }
    final itemType = item['item']?['itemContent']?['itemType'] as String?;
    if (itemType == null) {
      return _carriesResult(item);
    }
    return itemType == 'TimelineTweet';
  }

  /// Whether X answered for this position at all. An entry with no `result` is a
  /// shape we do not recognise rather than a reply being withheld, so it stays
  /// skipped — matching how `tweet-` entries are already treated.
  static bool _carriesResult(dynamic item) {
    if (item is! Map) {
      return false;
    }
    final map = _asStringKeyedMap(item['item']?['itemContent']?['tweet_results']);
    return map != null && map.containsKey('result');
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

      final entryIdLower = entryId.toLowerCase();
      if (entryIdLower.startsWith('cursor-bottom') || entryIdLower.contains('showmore')) {
        // Handled by getCursor / getShowMoreCursor — not chains.
      }

      if (entryIdLower.startsWith('conversationthread')) {
        final tweets = _conversationTweets(entry, skipPromoted: true);
        // Modules that only carry a nested show-more cursor (no TimelineTweet
        // items) used to become empty chains and hide the prompt — skip them.
        if (tweets.isEmpty) {
          continue;
        }
        replies.add(
          TweetChain(
            id: entryId.replaceFirst(RegExp(r'^conversationthread-', caseSensitive: false), ''),
            tweets: tweets,
            isPinned: false,
          ),
        );
      }
    }

    return replies;
  }

  /// Replies delivered on a later TweetDetail page via `TimelineAddToModule`.
  ///
  /// Follow-up pages often omit a fresh `TimelineAddEntries` list and only append
  /// module items (`conversationthread-…-tweet-…`). Ignoring those left the
  /// status screen stuck on the focal post after the first page.
  static List<TweetChain> chainsFromModuleItems(List<dynamic> moduleItems) {
    final byThread = <String, List<TweetWithCard>>{};

    for (final item in moduleItems) {
      if (!_isConversationTweetItem(item) || !_carriesResult(item)) {
        continue;
      }
      final entryId = (item is Map ? item['entryId'] as String? : null) ?? '';
      final entryIdLower = entryId.toLowerCase();
      // Prefer the conversation-thread id when X uses the usual module shape;
      // fall back to the tweet id so a bare `tweet-…` module item is not dropped.
      final threadId = entryIdLower.startsWith('conversationthread') && entryIdLower.contains('-tweet-')
          ? _moduleThreadId(entryId)
          : (_itemTweetId(item) ?? entryId);
      if (threadId.isEmpty) {
        continue;
      }
      byThread.putIfAbsent(threadId, () => []).add(_conversationTweet(item));
    }

    return [
      for (final entry in byThread.entries) TweetChain(id: entry.key, tweets: entry.value, isPinned: false),
    ];
  }

  /// `conversationthread-{thread}-tweet-{id}` → `{thread}`.
  static String _moduleThreadId(String entryId) {
    final match = RegExp(r'^conversationthread-(.+?)-tweet-', caseSensitive: false).firstMatch(entryId);
    return match?.group(1) ?? entryId;
  }

  /// Show-more cursors nested in `TimelineAddToModule` items (same shapes as
  /// [getShowMoreCursor]'s nested scan).
  static String? getShowMoreCursorFromModuleItems(List<dynamic> moduleItems) {
    return _moduleItemCursor(moduleItems, (id) => id.toLowerCase().contains('showmore'));
  }

  /// Bottom / next-page cursors nested in `TimelineAddToModule` (later pages
  /// sometimes advance the cursor here instead of via `TimelineAddEntries`).
  static String? getBottomCursorFromModuleItems(List<dynamic> moduleItems) {
    return _moduleItemCursor(
      moduleItems,
      (id) {
        final lower = id.toLowerCase();
        return lower.contains('cursor-bottom') || lower.endsWith('-bottom') || lower.contains('cursorbottom');
      },
    );
  }

  static String? _moduleItemCursor(List<dynamic> moduleItems, bool Function(String entryId) matchId) {
    for (final item in moduleItems) {
      final itemMap = _asStringKeyedMap(item);
      final id = itemMap?['entryId'] as String?;
      if (id == null || !matchId(id)) {
        continue;
      }
      final itemInner = _asStringKeyedMap(itemMap?['item']);
      final nested =
          _cursorValue(itemInner?['itemContent']) ?? _cursorValue(itemInner) ?? _cursorValue(itemMap?['content']);
      if (nested != null) {
        return nested;
      }
    }
    return null;
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
        for (var mediaTweet in List.from(entry['content']?['items'] ?? [])) {
          var result = usableResult(mediaTweet['item']);
          if (result != null) {
            replies.add(
              TweetChain(id: result['rest_id'], tweets: [TweetWithCard.fromGraphqlJson(result)], isPinned: isPinned),
            );
          }
        }
      }

      if (entryId.startsWith('cursor-bottom') || entryId.startsWith('cursor-showMore')) {
        // TODO: Use as the "next page" cursor
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
    var instructions = List.from(timeline['timeline']?['instructions'] ?? []);
    var addEntries = List.from(
      instructions.firstWhereOrNull((e) => e['type'] == 'TimelineAddEntries')?['entries'] ?? [],
    );
    var addModItems = List.from(
      instructions.firstWhereOrNull((e) => e['type'] == 'TimelineAddToModule')?['moduleItems'] ?? [],
    );
    var repEntries = List.from(instructions.where((e) => e['type'] == 'TimelineReplaceEntry'));

    String? cursorBottom = getCursor(addEntries, repEntries, 'cursor-bottom', 'Bottom');
    String? cursorTop = getCursor(addEntries, repEntries, 'cursor-top', 'Top');

    var moduleItems = [
      ...addEntries
          .where((e) => e['content']?['entryType'] == 'TimelineTimelineModule')
          .expand((e) => List.from(e['content']?['items'] ?? [])),
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
  ///
  /// Top-level entries are preferred (thread-end prompt). When every reply is
  /// withheld, X often nests the same cursor inside a `conversationthread`
  /// module instead — those are scanned second.
  static String? getShowMoreCursor(List<dynamic> addEntries) {
    final topLevel = addEntries.firstWhereOrNull(
      (e) => (e?['entryId'] as String?)?.toLowerCase().contains('showmore') ?? false,
    );
    final topValue = topLevel == null ? null : _cursorValue(topLevel['content']);
    if (topValue != null) {
      return topValue;
    }

    for (final entry in addEntries) {
      final content = _asStringKeyedMap(entry is Map ? entry['content'] : null);
      final items = content?['items'];
      if (items is! List) {
        continue;
      }
      for (final item in items) {
        final itemMap = _asStringKeyedMap(item);
        final id = itemMap?['entryId'] as String?;
        if (id == null || !id.toLowerCase().contains('showmore')) {
          continue;
        }
        final itemInner = _asStringKeyedMap(itemMap?['item']);
        final nested =
            _cursorValue(itemInner?['itemContent']) ?? _cursorValue(itemInner) ?? _cursorValue(itemMap?['content']);
        if (nested != null) {
          return nested;
        }
      }
    }

    return null;
  }

  /// Whether [status] already shows replies under [focalTweetId].
  ///
  /// Counts tweets that appear after the focal post — either later in the same
  /// chain (self-thread / stacked replies) or in subsequent chains. Ancestors
  /// above the focal post do not count.
  static bool hasVisibleReplies(TweetStatus status, String focalTweetId) {
    var seenFocal = false;
    for (final chain in status.chains) {
      for (final tweet in chain.tweets) {
        if (!seenFocal) {
          if (tweet.idStr == focalTweetId) {
            seenFocal = true;
          }
          continue;
        }
        if (tweet.idStr != null && tweet.idStr!.isNotEmpty) {
          return true;
        }
      }
    }
    return false;
  }

  /// Whether [status] already shows replies under [focalTweetId], or still has
  /// a cursor (show-more or bottom) that can load them. Used to avoid caching a
  /// focal-only page that would hide replies on the next open — and to decide
  /// whether the status screen should offer retry.
  static bool hasRepliesOrShowMore(TweetStatus status, String focalTweetId) {
    if (status.cursorShowMore != null || status.cursorBottom != null) {
      return true;
    }
    return hasVisibleReplies(status, focalTweetId);
  }

  /// The three shapes a cursor entry's `content` has taken across X's revisions.
  static String? _cursorValue(dynamic content) {
    final map = _asStringKeyedMap(content);
    if (map == null) return null;

    final value = map['value'] ?? map['operation']?['cursor']?['value'] ?? map['itemContent']?['value'];

    // Checked rather than cast: a cast would throw on the day X sends a number.
    return value is String ? value : null;
  }

  /// Finds a paging cursor among timeline entries. Missing, null, or reshaped
  /// cursor fields yield null rather than throwing — a broken cursor must not
  /// wipe the page that still has tweets.
  static String? getCursor(List<dynamic> addEntries, List<dynamic> repEntries, String legacyType, String type) {
    String? entryIdOf(dynamic e) => e is Map ? e['entryId'] as String? : null;

    final isLegacyCursor = addEntries.any((e) => entryIdOf(e)?.startsWith('cursor') ?? false);

    Map<String, dynamic>? cursorEntry;
    if (isLegacyCursor) {
      cursorEntry = _asStringKeyedMap(
        addEntries.firstWhereOrNull((e) => entryIdOf(e)?.contains(legacyType) ?? false),
      );
    } else {
      cursorEntry = _asStringKeyedMap(
        addEntries
            .where((e) => entryIdOf(e)?.startsWith('sq-C') ?? false)
            .firstWhereOrNull(
              (e) => e is Map && (e['content']?['operation']?['cursor']?['cursorType'] as String?) == type,
            ),
      );
    }

    if (cursorEntry != null) {
      return _cursorValue(cursorEntry['content']);
    }

    final cursorReplaceEntry = _asStringKeyedMap(
      repEntries.firstWhereOrNull((e) {
        if (e is! Map) return false;
        if (e.containsKey('replaceEntry')) {
          final id = e['replaceEntry']?['entryIdToReplace'] as String?;
          // X has used both `cursor-bottom-0` and ids that embed `Bottom`/`Top`.
          return id != null && (id.contains(legacyType) || id.contains(type));
        }
        final cursorType = e['entry']?['content']?['cursorType'] as String?;
        return cursorType?.contains(type) ?? false;
      }),
    );

    if (cursorReplaceEntry == null) return null;

    if (cursorReplaceEntry.containsKey('replaceEntry')) {
      return _cursorValue(cursorReplaceEntry['replaceEntry']?['entry']?['content']);
    }
    return _cursorValue(cursorReplaceEntry['entry']?['content']);
  }

  static TweetStatus createUnconversationedChainsGraphql(
    Map<String, dynamic> result,
    String tweetIndicator,
    List<String> pinnedTweets,
    bool mapToThreads,
    bool includeReplies,
  ) {
    var instructions = List.from(result['timeline']?['instructions'] ?? []);
    if (instructions.isEmpty || !instructions.any((e) => e is Map && e['type'] == 'TimelineAddEntries')) {
      return TweetStatus(chains: [], cursorBottom: null, cursorTop: null);
    }

    final addInstruction = instructions.firstWhere((e) => e is Map && e['type'] == 'TimelineAddEntries');
    var addEntries = List.from(addInstruction is Map ? (addInstruction['entries'] ?? []) : []);
    var repEntries = List.from(instructions.where((e) => e is Map && e['type'] == 'TimelineReplaceEntry'));

    String? cursorBottom = getCursor(addEntries, repEntries, 'cursor-bottom', 'Bottom');
    String? cursorTop = getCursor(addEntries, repEntries, 'cursor-top', 'Top');

    var tweets = _createTweetsGraphql(tweetIndicator, addEntries, includeReplies);

    // First, get all the IDs of the tweets we need to display.
    String? entryRestId(dynamic e) {
      if (e is! Map) return null;
      var result = e['content']?['itemContent']?['tweet_results']?['result'];
      return result?['rest_id'] ?? result?['tweet']?['rest_id'];
    }

    int sortIndexOf(dynamic e) {
      final raw = e is Map ? e['sortIndex'] : null;
      if (raw is int) return raw;
      if (raw is String) return int.tryParse(raw) ?? 0;
      return 0;
    }

    var tweetEntries = addEntries
        .where((e) {
          final entryId = e is Map ? e['entryId'] as String? : null;
          return entryId != null && entryId.contains(tweetIndicator) && entryRestId(e) != null;
        })
        .sorted((a, b) => sortIndexOf(b).compareTo(sortIndexOf(a)))
        .map(entryRestId)
        .cast<String?>()
        .toList();

    Map<String, List<TweetWithCard>> conversations = tweets.values.where((e) => tweetEntries.contains(e.idStr)).groupBy(
      (e) {
        // TODO: I don't think a flag is the right way to handle this
        if (mapToThreads) {
          // Then group the tweets-to-display by their conversation ID
          return e.conversationIdStr;
        }

        return e.idStr;
      },
    ).cast<String, List<TweetWithCard>>();

    List<TweetChain> chains = [];

    // Order all the conversations by newest first (assuming the ID is an incrementing key), and create a chain from them
    for (var conversation in conversations.entries.sorted((a, b) => b.key.compareTo(a.key))) {
      var chainTweets = conversation.value.sorted((a, b) => a.idStr!.compareTo(b.idStr!)).toList();

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
    var instructions = List.from(timeline?['timeline']?['instructions'] ?? []);
    var addEntriesInstructions = instructions.firstWhereOrNull((e) => e['type'] == 'TimelineAddEntries');
    var addModEntriesInstructions = instructions.firstWhereOrNull((e) => e['type'] == 'TimelineAddToModule');
    List addModEntries = List.from(addModEntriesInstructions?['moduleItems'] ?? []);

    if (addEntriesInstructions == null && addModEntries.isEmpty) {
      return TweetStatus(chains: [], cursorBottom: null, cursorTop: null);
    }

    var addPinnedTweetsInstructions = instructions.firstWhereOrNull((e) => e['type'] == 'TimelinePinEntry');
    var addEntries = List.from(addEntriesInstructions?['entries'] ?? []);
    var repEntries = List.from(instructions.where((e) => e['type'] == 'TimelineReplaceEntry'));
    List addPinnedEntries = List<dynamic>.empty(growable: true);
    if (addPinnedTweetsInstructions != null) {
      addPinnedEntries.add(addPinnedTweetsInstructions['entry']);
    }

    String? cursorBottom = getCursor(addEntries, repEntries, 'cursor-bottom', 'Bottom');
    String? cursorTop = getCursor(addEntries, repEntries, 'cursor-top', 'Top');

    var chains = createTweets(addEntries);
    // var debugTweets = json.encode(chains);
    //var debugTweets2 = json.encode(addEntries);
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
    //To prevent infinte loading of tweets while filtering via regex , we have to count added tweets.
    //(infinite loading originating in paged_silver_builder.dart at line 246)
    //As soon as there is no tweet left that passes regex critera and we also reached maximum attemps
    //to find them, than stop loading more.
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
    var instructions = List.from(result["data"]?["home"]?["home_timeline_urt"]?['instructions'] ?? []);
    var addEntriesInstructions = instructions.firstWhereOrNull((e) => e is Map && e['type'] == 'TimelineAddEntries');
    if (addEntriesInstructions == null) {
      return TweetStatus(chains: [], cursorBottom: null, cursorTop: null);
    }
    var addPinnedTweetsInstructions = instructions.firstWhereOrNull((e) => e is Map && e['type'] == 'TimelinePinEntry');
    var addEntries = List.from(addEntriesInstructions['entries'] ?? []);
    var repEntries = List.from(instructions.where((e) => e is Map && e['type'] == 'TimelineReplaceEntry'));
    List addPinnedEntries = List<dynamic>.empty(growable: true);
    if (addPinnedTweetsInstructions != null) {
      addPinnedEntries.add(addPinnedTweetsInstructions['entry']);
    }

    String? cursorBottom = getCursor(addEntries, repEntries, 'cursor-bottom', 'Bottom');
    String? cursorTop = getCursor(addEntries, repEntries, 'cursor-top', 'Top');
    var chains = createTweets(addEntries);
    // var debugTweets = json.encode(chains);
    //var debugTweets2 = json.encode(addEntries);
    var pinnedChains = createTweets(addPinnedEntries, true);

    //If we want to show pinned tweets, add them before the others that we already have
    if (pinnedTweets.isNotEmpty & showPinnedTweet) {
      chains.insertAll(0, pinnedChains);
    }
    //To prevent infinte loading of tweets while filtering via regex , we have to count added tweets.
    //(infinite loading originating in paged_silver_builder.dart at line 246)
    //As soon as there is no tweet left that passes regex critera and we also reached maximum attemps
    //to find them, than stop loading more.
    if (chains.length < 5) {
      increaseTweetCounter();
      if (getTweetsCounter() > 5) {
        cursorBottom = null;
      }
    }

    return TweetStatus(chains: chains, cursorBottom: cursorBottom, cursorTop: cursorTop);
  }

  /// Builds the tweet map for a GraphQL timeline page. Entries whose shape is
  /// unrecognised — missing `entryId`/`content`, no usable result, or a
  /// `fromGraphqlJson` failure — are skipped so one bad item cannot empty the
  /// whole page.
  static Map<String, TweetWithCard> _createTweetsGraphql(
    String entryPrefix,
    List<dynamic> allTweets,
    bool includeReplies,
  ) {
    bool includeTweet(dynamic t) {
      if (t is! Map) return false;

      final entryId = t['entryId'] as String?;
      if (entryId == null || !entryId.startsWith(entryPrefix)) {
        return false;
      }

      final itemContent = _asStringKeyedMap(t['content']?['itemContent']);
      if (itemContent == null) return false;
      if (itemContent['promotedMetadata'] != null) return false;
      if (itemContent['tweet_results']?['result'] == null) return false;

      return true;
    }

    Map<String, dynamic>? unwrapResult(dynamic entry) {
      if (entry is! Map) return null;
      return _unwrapTweetResult(entry['content']?['itemContent']?['tweet_results']?['result']);
    }

    TweetWithCard? parseTweet(Map<String, dynamic> result) {
      try {
        return TweetWithCard.fromGraphqlJson(result);
      } catch (_) {
        // One unparseable tweet must not wipe the page.
        return null;
      }
    }

    final tweets = allTweets
        .where(includeTweet)
        .map(unwrapResult)
        .whereType<Map<String, dynamic>>()
        .map(parseTweet)
        .whereType<TweetWithCard>()
        .where((tweet) {
          if (!includeReplies && (tweet.inReplyToStatusIdStr != null || tweet.inReplyToUserIdStr != null)) {
            return false;
          }
          return true;
        })
        .toList();

    return {for (final e in tweets) if (e.idStr != null) e.idStr!: e};
  }
}
