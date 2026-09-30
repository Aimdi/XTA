import 'package:flutter/material.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:quiver/iterables.dart' show partition;
import 'package:xta/catcher/exceptions.dart';
import 'package:xta/client/accounts.dart';
import 'package:xta/client/client.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/group/feed_chunk_hash.dart';
import 'package:xta/group/future_pool.dart';
import 'package:xta/group/group_members.dart';
import 'package:xta/group/group_model.dart';
import 'package:xta/group/group_search_query.dart';
import 'package:xta/home/home_account_filter.dart';
import 'package:xta/plugins/x/x_plugin.dart';
import 'package:xta/reading/mixed_feed_definition.dart';
import 'package:xta/reading/mixed_feed_merge.dart';
import 'package:xta/reading/mixed_feed_source.dart';
import 'package:xta/tweet/conversation.dart';
import 'package:xta/tweet/interleaved_items.dart';
import 'package:xta/tweet/tweet_filtering.dart';
import 'package:xta/utils/urls.dart';

const _forYouPageSize = 20;
const _searchConcurrency = 3;
const _maxMemberPages = 20;

/// List members are read in the list's order; nothing sorts them by when they were followed.
final _listJoined = DateTime.utc(1970);

MixedEntry xMixedEntry(TweetChain chain) =>
    MixedEntry(identity: 'x:${chain.id}', item: chain, date: newestDateOf(chain));

String? _nextCursor(String? next, Object? current) =>
    next == null || next.isEmpty || next == '0' || next == current ? null : next;

abstract class _XMixedSource extends MixedSourceKind {
  const _XMixedSource();

  @override
  String get pluginId => pluginIdX;

  /// Needs a [TweetContextScope] above it, which a mix provides for all its X posts.
  @override
  Widget card(BuildContext context, MixedEntry entry) {
    final chain = entry.item as TweetChain;
    return TweetConversation(
      key: ValueKey(chain.id),
      id: chain.id,
      username: null,
      isPinned: chain.isPinned,
      tweets: chain.tweets,
    );
  }

  @override
  String filterText(MixedEntry entry) => sharedFilterChainText(entry.item as TweetChain);
}

/// For you, merged over the accounts turned on for Home, as the For you tab reads it.
class XForYouMixedSource extends _XMixedSource {
  const XForYouMixedSource();

  @override
  String get id => 'x.foryou';

  @override
  IconData get icon => Icons.auto_awesome_outlined;

  @override
  String title(BuildContext context) => L10n.of(context).foryou;

  @override
  String signature(BuildContext context, MixedFeedSource source) =>
      (context.read<HomeAccountFilterStore>().state.toList()..sort()).join('\n');

  @override
  MixedSourceReader reader(BuildContext context, MixedFeedSource source) {
    final filter = context.read<HomeAccountFilterStore>();
    var loads = 0;
    return MixedFunctionReader((cursor) async {
      final page = await loadMergedForYouPage(
        accounts: await getAccounts(),
        disabledIds: filter.state,
        cursor: cursor as String?,
        count: _forYouPageSize,
        includeReplies: false,
        getTweetsCounter: () => loads,
        incrementTweetsCounter: () => loads++,
      );
      return MixedPage([
        for (final chain in page.chains) xMixedEntry(chain),
      ], next: _nextCursor(page.nextCursor, cursor));
    });
  }
}

/// The searches that read a set of accounts, and whether they include replies.
typedef _Searches = ({List<String> queries, bool includeReplies});

/// Where a chunked search has got to: each query that still has posts, with its next cursor.
class _SearchCursor {
  final _Searches searches;
  final Map<int, String?> cursors;
  const _SearchCursor(this.searches, this.cursors);
}

List<String> _queries(List<Subscription> members, {required bool includeReplies, required bool includeRetweets}) => [
  for (final chunk in partition(members, feedChunkSize))
    groupSearchQuery(chunk, includeReplies: includeReplies, includeRetweets: includeRetweets),
];

/// Reads many accounts through X search, sixteen per query, each query paged on its own. What comes back is their
/// posts newest first, never a list's or group's own ranking. A query that fails is tried again on the next page.
MixedSourceReader _chunkedSearch(Future<_Searches> Function() searches) => MixedFunctionReader((cursor) async {
  final at = switch (cursor) {
    final _SearchCursor at => at,
    _ => await searches().then(
      (found) => _SearchCursor(found, {for (var i = 0; i < found.queries.length; i++) i: null}),
    ),
  };
  final pages = await mapWithConcurrency(at.cursors.entries, _searchConcurrency, (entry) async {
    try {
      final status = await withRateLimitOperations([
        'SearchTimeline',
      ], () => Twitter.searchTweets(at.searches.queries[entry.key], at.searches.includeReplies, cursor: entry.value));
      return (index: entry.key, status: status, error: null);
    } catch (error) {
      return (index: entry.key, status: null, error: error);
    }
  });
  if (pages.isNotEmpty && pages.every((page) => page.error != null)) throw pages.first.error!;
  final next = <int, String?>{
    for (final page in pages)
      if (page.status == null)
        page.index: at.cursors[page.index]
      else if (page.status!.chains.isNotEmpty)
        page.index: ?_nextCursor(page.status!.cursorBottom, at.cursors[page.index]),
  };
  final chains = mergeHomeTimelineChains([for (final page in pages) ?page.status?.chains]);
  return MixedPage([
    for (final chain in chains) xMixedEntry(chain),
  ], next: next.isEmpty ? null : _SearchCursor(at.searches, next));
});

/// A group's X members, read the way the group feed reads them; plugin members are left to their own sources.
class XGroupMixedSource extends _XMixedSource {
  const XGroupMixedSource();

  @override
  String get id => 'x.group';

  @override
  IconData get icon => Icons.group_outlined;

  @override
  MixedSourceInput get input => MixedSourceInput.choice;

  @override
  String title(BuildContext context) => L10n.of(context).mixed_feed_group;

  @override
  String signature(BuildContext context, MixedFeedSource source) =>
      '${context.read<GroupsModel>().state.where((group) => group.id == source.value).firstOrNull?.numberOfMembers}';

  @override
  Future<List<MixedFeedSource>> choices(BuildContext context) async => [
    for (final group in context.read<GroupsModel>().state)
      if (group.id != '-1') MixedFeedSource(kind: id, value: group.id, label: group.name),
  ];

  @override
  MixedSourceReader reader(BuildContext context, MixedFeedSource source) {
    final prefs = PrefService.of(context, listen: false);
    return _chunkedSearch(() => _groupSearches(source.value, prefs));
  }
}

Future<_Searches> _groupSearches(String id, BasePrefService prefs) async {
  final model = GroupModel(id, prefs: prefs);
  try {
    await model.loadGroup(showLoading: false);
    final group = model.state;
    if (group.id != id) throw StateError('The group could not be read');
    final includeReplies = group.includeReplies ?? prefs.get<bool>(optionGlobalIncludeReplies) ?? true;
    final includeRetweets = group.includeRetweets ?? prefs.get<bool>(optionGlobalIncludeRetweets) ?? true;
    final members = splitGroupMembers(
      [...group.subscriptions]..sort((a, b) => a.createdAt.compareTo(b.createdAt)),
    ).xMembers;
    return (
      queries: _queries(members, includeReplies: includeReplies, includeRetweets: includeRetweets),
      includeReplies: includeReplies,
    );
  } finally {
    await model.destroy();
  }
}

/// Posts by the members of an X list. X offers no list timeline here, so these are the members' own posts through
/// search, newest first, not the list's ranking.
class XListMixedSource extends _XMixedSource {
  const XListMixedSource();

  @override
  String get id => 'x.list';

  @override
  IconData get icon => Icons.list_alt;

  @override
  MixedSourceInput get input => MixedSourceInput.text;

  @override
  String title(BuildContext context) => L10n.of(context).mixed_feed_x_list;

  @override
  String? inputHint(BuildContext context) => L10n.of(context).mixed_feed_x_list_hint;

  @override
  Future<MixedFeedSource?> fromText(BuildContext context, String text) async {
    final listId = extractListId(text);
    if (listId == null) return null;
    try {
      final details = await Twitter.getListDetails(listId);
      return MixedFeedSource(kind: id, value: listId, label: details.name ?? listId);
    } catch (_) {
      return null;
    }
  }

  @override
  MixedSourceReader reader(BuildContext context, MixedFeedSource source) => _chunkedSearch(
    () async => (
      queries: _queries(await _listMembers(source.value), includeReplies: false, includeRetweets: false),
      includeReplies: false,
    ),
  );
}

Future<List<Subscription>> _listMembers(String listId) async {
  final members = <String, Subscription>{};
  String? cursor;
  for (var page = 0; page < _maxMemberPages; page++) {
    final response = await Twitter.getListMembers(listId, cursor: cursor);
    var fresh = 0;
    for (final user in response.users) {
      final (id, screenName) = (user.idStr, user.screenName);
      if (id == null || screenName == null || members.containsKey(id)) continue;
      members[id] = UserSubscription(
        id: id,
        screenName: screenName,
        name: user.name ?? screenName,
        profileImageUrlHttps: user.profileImageUrlHttps,
        verified: user.verified ?? false,
        createdAt: _listJoined,
        inFeed: true,
      );
      fresh++;
    }
    final next = _nextCursor(response.cursorBottom, cursor);
    if (next == null || fresh == 0) break;
    cursor = next;
  }
  return members.values.toList();
}

const xMixedSourceKinds = <MixedSourceKind>[XForYouMixedSource(), XGroupMixedSource(), XListMixedSource()];
