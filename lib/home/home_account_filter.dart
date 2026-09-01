import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/client/account_fetch_gate.dart';
import 'package:xta/client/accounts.dart';
import 'package:xta/client/client.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/client/login_webview.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/group/future_pool.dart';
import 'package:xta/group/group_model.dart';
import 'package:xta/home/home_group_filter.dart';
import 'package:xta/tweet/paginated_tweet_list.dart';

const int homeTimelineMergeConcurrency = 2;

List<String> homeFeedDisabledIdsFromPrefs(String? raw) {
  if (raw == null || raw.isEmpty) {
    return const [];
  }
  try {
    final decoded = jsonDecode(raw);
    if (decoded is! List) {
      return const [];
    }
    return decoded
        .whereType<String>()
        .where((e) => e.isNotEmpty)
        .toList(growable: false);
  } catch (_) {
    return const [];
  }
}

String homeFeedDisabledIdsToPrefs(Iterable<String> ids) =>
    jsonEncode(ids.toList(growable: false));

/// Accounts that still participate in the merged For you timeline.
List<Account> enabledHomeAccounts(
  List<Account> accounts,
  Set<String> disabledIds,
) {
  if (disabledIds.isEmpty) {
    return accounts;
  }
  final enabled = accounts
      .where((a) => !disabledIds.contains(a.id))
      .toList(growable: false);
  // Never leave For you with zero sources while accounts exist.
  return enabled.isEmpty ? accounts : enabled;
}

bool isHomeAccountEnabled(String accountId, Set<String> disabledIds) =>
    !disabledIds.contains(accountId);

/// False when turning [accountId] off would leave no login contributing.
bool canDisableHomeAccount(
  String accountId,
  List<Account> accounts,
  Set<String> disabledIds,
) {
  final known = accounts.map((a) => a.id).toSet();
  final wouldDisable = {...disabledIds, accountId};
  return known.where((id) => !wouldDisable.contains(id)).isNotEmpty;
}

/// Cache key for the home Following tab. Evict this when the account filter
/// changes, or remounting the tab reuses the old pages.
String homeFollowingCacheKey(String groupId) => 'home-$groupId';

/// Per-account HomeTimeline cursors, encoded as a JSON object for pagination.
Map<String, String> decodeHomeTimelineCursors(String? raw) {
  if (raw == null || raw.isEmpty || !raw.startsWith('{')) {
    return const {};
  }
  try {
    final decoded = jsonDecode(raw);
    if (decoded is! Map) {
      return const {};
    }
    return {
      for (final entry in decoded.entries)
        if (entry.key is String &&
            entry.value is String &&
            (entry.value as String).isNotEmpty)
          entry.key as String: entry.value as String,
    };
  } catch (_) {
    return const {};
  }
}

String? encodeHomeTimelineCursors(Map<String, String> cursors) {
  if (cursors.isEmpty) {
    return null;
  }
  return jsonEncode(cursors);
}

DateTime _chainSortTime(TweetChain chain) {
  for (final tweet in chain.tweets) {
    final created = tweet.createdAt;
    if (created != null) {
      return created;
    }
  }
  return DateTime.fromMillisecondsSinceEpoch(0);
}

/// Newest-first merge with stable dedupe by chain id.
List<TweetChain> mergeHomeTimelineChains(Iterable<List<TweetChain>> batches) {
  final seen = <String>{};
  final merged = <TweetChain>[];
  for (final batch in batches) {
    for (final chain in batch) {
      if (seen.add(chain.id)) {
        merged.add(chain);
      }
    }
  }
  merged.sort((a, b) => _chainSortTime(b).compareTo(_chainSortTime(a)));
  return merged;
}

/// True when any tweet in [chain] was written by one of [authorIds].
bool chainHasAuthor(TweetChain chain, Set<String> authorIds) {
  if (authorIds.isEmpty) {
    return false;
  }
  for (final tweet in chain.tweets) {
    final id = tweet.user?.idStr;
    if (id != null && authorIds.contains(id)) {
      return true;
    }
  }
  return false;
}

/// Drops chains whose author is a login the reader turned off, or a member of
/// a group they hid from Following. For you still fetches Home timelines; this
/// is what actually takes those posts off the screen.
List<TweetChain> dropChainsFromAuthors(
  List<TweetChain> chains,
  Set<String> authorIds,
) {
  if (authorIds.isEmpty) {
    return chains;
  }
  return [
    for (final chain in chains)
      if (!chainHasAuthor(chain, authorIds)) chain,
  ];
}

class _AccountTimelinePage {
  final String accountId;
  final List<TweetChain> chains;
  final String? nextCursor;

  const _AccountTimelinePage({
    required this.accountId,
    required this.chains,
    required this.nextCursor,
  });
}

/// Loads and merges HomeTimeline pages from every enabled login account.
Future<TweetPageResult> loadMergedForYouPage({
  required List<Account> accounts,
  required Set<String> disabledIds,
  required String? cursor,
  required int count,
  required bool includeReplies,
  required int Function() getTweetsCounter,
  required void Function() incrementTweetsCounter,
  Set<String> excludedAuthorIds = const {},
}) async {
  final sources = enabledHomeAccounts(accounts, disabledIds);
  if (sources.isEmpty) {
    return (chains: const <TweetChain>[], nextCursor: null);
  }

  // Single account: keep a plain X cursor so behaviour matches the old path.
  if (sources.length == 1) {
    final account = sources.first;
    final result = await Twitter.getTimelineTweetsForAccount(
      account,
      cursor: cursor,
      count: count,
      includeReplies: includeReplies,
      getTweetsCounter: getTweetsCounter,
      incrementTweetsCounter: incrementTweetsCounter,
    );
    return (
      chains: dropChainsFromAuthors(result.chains, {
        ...disabledIds,
        ...excludedAuthorIds,
      }),
      nextCursor: result.cursorBottom,
    );
  }

  final previous = cursor == null
      ? const <String, String>{}
      : decodeHomeTimelineCursors(cursor);
  Object? lastError;

  final pages = await mapWithConcurrency(
    sources,
    homeTimelineMergeConcurrency,
    (account) async {
      final accountCursor = cursor == null ? null : previous[account.id];
      if (cursor != null && accountCursor == null) {
        return _AccountTimelinePage(
          accountId: account.id,
          chains: const [],
          nextCursor: null,
        );
      }
      try {
        final result = await Twitter.getTimelineTweetsForAccount(
          account,
          cursor: accountCursor,
          count: count,
          includeReplies: includeReplies,
          getTweetsCounter: getTweetsCounter,
          incrementTweetsCounter: incrementTweetsCounter,
        );
        return _AccountTimelinePage(
          accountId: account.id,
          chains: result.chains,
          nextCursor: result.cursorBottom,
        );
      } catch (e) {
        lastError = e;
        return _AccountTimelinePage(
          accountId: account.id,
          chains: const [],
          nextCursor: accountCursor,
        );
      }
    },
  );

  final chains = dropChainsFromAuthors(
    mergeHomeTimelineChains(pages.map((p) => p.chains)),
    {...disabledIds, ...excludedAuthorIds},
  );
  if (chains.isEmpty && lastError != null) {
    throw lastError!;
  }

  final next = <String, String>{
    for (final page in pages)
      if (page.nextCursor != null && page.nextCursor!.isNotEmpty)
        page.accountId: page.nextCursor!,
  };
  return (chains: chains, nextCursor: encodeHomeTimelineCursors(next));
}

/// Which login accounts are excluded from home-feed content.
///
/// For you merges HomeTimeline only from accounts left on. Following still
/// builds from local subscriptions, but [AccountFetchGate] also skips disabled
/// accounts when fetching those chunks — so a spare rate-limit account is not
/// spent on the home feeds. If every account is disabled, fetch falls back to
/// the full pool so comments / quotes / profiles still have a credential.
class HomeAccountFilterStore extends Store<Set<String>> {
  final BasePrefService prefs;

  HomeAccountFilterStore(this.prefs)
    : super(
        homeFeedDisabledIdsFromPrefs(
          prefs.get(optionHomeFeedDisabledAccountIds),
        ).toSet(),
      ) {
    _publish(state);
  }

  void _publish(Set<String> disabled) {
    AccountFetchGate.disabledIds = Set<String>.from(disabled);
  }

  Future<void> reload() async {
    await execute(() async {
      final next = homeFeedDisabledIdsFromPrefs(
        prefs.get(optionHomeFeedDisabledAccountIds),
      ).toSet();
      _publish(next);
      return next;
    });
  }

  Future<void> setEnabled(
    String accountId,
    bool enabled, {
    required List<Account> accounts,
  }) async {
    await execute(() async {
      final next = Set<String>.from(state);
      if (enabled) {
        next.remove(accountId);
      } else {
        if (!canDisableHomeAccount(accountId, accounts, next)) {
          return state;
        }
        next.add(accountId);
      }
      await prefs.set(
        optionHomeFeedDisabledAccountIds,
        homeFeedDisabledIdsToPrefs(next),
      );
      _publish(next);
      return next;
    });
  }
}

void showHomeAccountFilterSheet(
  BuildContext context, {
  VoidCallback? onChanged,
}) {
  final filter = context.read<HomeAccountFilterStore>();
  HomeGroupFilterStore? groupFilter;
  List<SubscriptionGroup> groups = const [];
  try {
    groupFilter = context.read<HomeGroupFilterStore>();
    groups = context.read<GroupsModel>().state;
  } on ProviderNotFoundException {
    groupFilter = null;
  }
  showModalBottomSheet(
    context: context,
    isScrollControlled: true,
    builder: (sheetContext) {
      return SafeArea(
        child: FutureBuilder<List<Account>>(
          future: getAccounts(),
          builder: (context, snapshot) {
            if (snapshot.connectionState == ConnectionState.waiting) {
              return const Padding(
                padding: EdgeInsets.all(24),
                child: LinearProgressIndicator(),
              );
            }
            final accounts = snapshot.data ?? const <Account>[];
            return ScopedBuilder<HomeAccountFilterStore, Set<String>>(
              store: filter,
              onState: (_, disabled) {
                return SingleChildScrollView(
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      ListTile(
                        leading: IconButton(
                          icon: const Icon(Icons.arrow_back),
                          onPressed: () => Navigator.of(sheetContext).pop(),
                        ),
                        title: Text(
                          L10n.of(context).home_feed_accounts,
                          style: Theme.of(context).textTheme.titleLarge,
                        ),
                      ),
                      Padding(
                        padding: const EdgeInsets.only(
                          left: 16,
                          right: 16,
                          bottom: 8,
                        ),
                        child: Text(
                          L10n.of(context).home_feed_accounts_description,
                          style: TextStyle(
                            color: Theme.of(context).disabledColor,
                          ),
                        ),
                      ),
                      if (accounts.isEmpty)
                        ListTile(
                          title: Text(
                            L10n.of(context).home_feed_accounts_empty,
                          ),
                          trailing: TextButton(
                            onPressed: () {
                              Navigator.of(sheetContext).pop();
                              Navigator.push(
                                context,
                                MaterialPageRoute(
                                  builder: (_) => const TwitterLoginWebview(),
                                ),
                              );
                            },
                            child: Text(L10n.of(context).add_account),
                          ),
                        )
                      else
                        ...accounts.map(
                          (account) => HomeAccountToggleTile(
                            account: account,
                            disabled: disabled,
                            accounts: accounts,
                            onChanged: (value) async {
                              await filter.setEnabled(
                                account.id,
                                value,
                                accounts: accounts,
                              );
                              onChanged?.call();
                            },
                          ),
                        ),
                      if (groupFilter != null && groups.isNotEmpty)
                        ScopedBuilder<HomeGroupFilterStore, Set<String>>(
                          store: groupFilter,
                          onState: (_, disabledGroups) {
                            return Column(
                              mainAxisSize: MainAxisSize.min,
                              children: [
                                const Divider(),
                                ListTile(
                                  title: Text(
                                    L10n.of(context).home_feed_groups,
                                    style: Theme.of(
                                      context,
                                    ).textTheme.titleMedium,
                                  ),
                                  subtitle: Padding(
                                    padding: const EdgeInsets.only(top: 4),
                                    child: Text(
                                      L10n.of(
                                        context,
                                      ).home_feed_groups_description,
                                    ),
                                  ),
                                ),
                                ...groups.map(
                                  (group) => HomeGroupToggleTile(
                                    group: group,
                                    disabled: disabledGroups,
                                    onChanged: (value) async {
                                      await groupFilter!.setEnabled(
                                        group.id,
                                        value,
                                      );
                                      onChanged?.call();
                                    },
                                  ),
                                ),
                              ],
                            );
                          },
                        ),
                    ],
                  ),
                );
              },
            );
          },
        ),
      );
    },
  );
}

/// One login on the home-feed filter. The last account left on cannot be
/// turned off — the switch stays on so it does not look broken.
class HomeAccountToggleTile extends StatelessWidget {
  final Account account;
  final Set<String> disabled;
  final List<Account> accounts;
  final Future<void> Function(bool enabled) onChanged;

  const HomeAccountToggleTile({
    super.key,
    required this.account,
    required this.disabled,
    required this.accounts,
    required this.onChanged,
  });

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final enabled = isHomeAccountEnabled(account.id, disabled);
    final canDisable = canDisableHomeAccount(account.id, accounts, disabled);
    return SwitchListTile(
      secondary: const Icon(Icons.account_circle),
      title: Text(account.screenName ?? l10n.unknown_username),
      subtitle: Text(
        enabled && !canDisable
            ? l10n.home_feed_keep_one_account
            : l10n.home_feed_include_in_for_you,
      ),
      value: enabled,
      onChanged: !enabled || canDisable ? onChanged : null,
    );
  }
}

/// One group on the home-feed filter. Off hides its members from Following.
class HomeGroupToggleTile extends StatelessWidget {
  final SubscriptionGroup group;
  final Set<String> disabled;
  final Future<void> Function(bool enabled) onChanged;

  const HomeGroupToggleTile({
    super.key,
    required this.group,
    required this.disabled,
    required this.onChanged,
  });

  @override
  Widget build(BuildContext context) {
    final enabled = !disabled.contains(group.id);
    return SwitchListTile(
      secondary: Icon(group.iconData),
      title: Text(group.name),
      subtitle: Text(L10n.of(context).home_feed_include_in_following),
      value: enabled,
      onChanged: onChanged,
    );
  }
}
