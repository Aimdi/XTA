import 'package:flutter/material.dart';
import 'package:xta/client/client.dart';
import 'package:xta/database/repository.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/subscriptions/users_model.dart';
import 'package:xta/user.dart';
import 'package:provider/provider.dart';
import 'package:sqflite/sqflite.dart';

/// Per-user feed filters ("turn off reposts"): users listed in
/// [tableRetweetFilter] have their retweets hidden from every feed.

Future<bool> isRetweetsHidden(String userId) async {
  var repository = await Repository.readOnly();
  var rows = await repository.query(tableRetweetFilter, where: 'user_id = ?', whereArgs: [userId]);
  return rows.isNotEmpty;
}

Future<void> setRetweetsHidden(UserWithExtra user, bool hidden) async {
  var repository = await Repository.writable();
  if (hidden) {
    await repository.insert(tableRetweetFilter, {'user_id': user.idStr, 'screen_name': user.screenName},
        conflictAlgorithm: ConflictAlgorithm.replace);
  } else {
    await repository.delete(tableRetweetFilter, where: 'user_id = ?', whereArgs: [user.idStr]);
  }
}

/// Lowercased screen names whose retweets are hidden.
Future<Set<String>> hiddenRetweetScreenNames() async {
  var repository = await Repository.readOnly();
  return (await repository.query(tableRetweetFilter, columns: ['screen_name']))
      .map((row) => (row['screen_name'] as String).toLowerCase())
      .toSet();
}

/// Drops chains that are a retweet made by one of the [hidden] users.
List<TweetChain> filterHiddenRetweets(List<TweetChain> chains, Set<String> hidden) {
  if (hidden.isEmpty) {
    return chains;
  }
  return chains.where((chain) {
    var tweet = chain.tweets.isEmpty ? null : chain.tweets.first;
    return tweet?.retweetedStatusWithCard == null ||
        !hidden.contains(tweet?.user?.screenName?.toLowerCase());
  }).toList();
}

Future<bool> isRepliesHidden(String userId) async {
  var repository = await Repository.readOnly();
  var rows = await repository.query(tableReplyFilter, where: 'user_id = ?', whereArgs: [userId]);
  return rows.isNotEmpty;
}

Future<void> setRepliesHidden(UserWithExtra user, bool hidden) async {
  var repository = await Repository.writable();
  if (hidden) {
    await repository.insert(tableReplyFilter, {'user_id': user.idStr, 'screen_name': user.screenName},
        conflictAlgorithm: ConflictAlgorithm.replace);
  } else {
    await repository.delete(tableReplyFilter, where: 'user_id = ?', whereArgs: [user.idStr]);
  }
}

/// Lowercased screen names whose replies are hidden.
Future<Set<String>> hiddenReplyScreenNames() async {
  var repository = await Repository.readOnly();
  return (await repository.query(tableReplyFilter, columns: ['screen_name']))
      .map((row) => (row['screen_name'] as String).toLowerCase())
      .toSet();
}

/// Whether [tweet] answers somebody other than its own author.
///
/// A self-reply is how a thread is built, so it is not treated as a reply here:
/// hiding someone's replies should quiet their conversations with others, not
/// cut their own threads out of the feed.
bool isReplyToSomeoneElse(TweetWithCard? tweet) {
  if (tweet == null) {
    return false;
  }
  final repliesTo = tweet.inReplyToUserIdStr ?? tweet.inReplyToScreenName;
  if (tweet.inReplyToStatusIdStr == null && repliesTo == null) {
    return false;
  }

  final authorId = tweet.user?.idStr;
  if (tweet.inReplyToUserIdStr != null && authorId != null) {
    return tweet.inReplyToUserIdStr != authorId;
  }

  final authorName = tweet.user?.screenName?.toLowerCase();
  final target = tweet.inReplyToScreenName?.toLowerCase();
  if (target != null && authorName != null) {
    return target != authorName;
  }

  // A reply whose target cannot be identified: treat it as a reply, since the
  // alternative is showing what the reader asked to hide.
  return true;
}

/// Drops chains that open with one of the [hidden] users replying to someone else.
List<TweetChain> filterHiddenReplies(List<TweetChain> chains, Set<String> hidden) {
  if (hidden.isEmpty) {
    return chains;
  }
  return chains.where((chain) {
    final tweet = chain.tweets.isEmpty ? null : chain.tweets.first;
    if (!isReplyToSomeoneElse(tweet)) {
      return true;
    }
    return !hidden.contains(tweet?.user?.screenName?.toLowerCase());
  }).toList();
}

const quietAccountChoices = <int?>[null, 1, 2, 3, 5, 10];

Future<int?> loadMaxPostsPerLoad(String userId) async {
  final repository = await Repository.readOnly();
  final rows = await repository.query(
    tableSubscription,
    columns: ['max_posts_per_load'],
    where: 'id = ?',
    whereArgs: [userId],
    limit: 1,
  );
  if (rows.isEmpty) {
    return null;
  }
  return rows.first['max_posts_per_load'] as int?;
}

Future<bool> setMaxPostsPerLoad(String userId, int? value) async {
  final repository = await Repository.writable();
  final updated = await repository.update(
    tableSubscription,
    {'max_posts_per_load': value},
    where: 'id = ?',
    whereArgs: [userId],
  );
  return updated > 0;
}

String quietAccountLabel(BuildContext context, int? value) {
  final l10n = L10n.of(context);
  return value == null ? l10n.quiet_account_off : '$value';
}

/// The wrench button on a profile: per-user feed filters, like X's
/// "turn off reposts".
class ProfileFeedSettingsButton extends StatelessWidget {
  final UserWithExtra user;
  final Color? color;

  const ProfileFeedSettingsButton({super.key, required this.user, this.color});

  @override
  Widget build(BuildContext context) {
    if (user.idStr == null) {
      return const SizedBox.shrink();
    }

    return IconButton(
      icon: const Icon(Icons.build_outlined),
      color: color,
      tooltip: L10n.of(context).filters,
      onPressed: () async {
        final subscribed = context.read<SubscriptionsModel>().state.any((e) => e.id == user.idStr);
        var hidden = await isRetweetsHidden(user.idStr!);
        var repliesHidden = await isRepliesHidden(user.idStr!);
        var maxPosts = await loadMaxPostsPerLoad(user.idStr!);
        if (!context.mounted) {
          return;
        }

        showModalBottomSheet(
            context: context,
            builder: (sheetContext) {
              return SafeArea(
                child: StatefulBuilder(
                  builder: (sheetContext, setSheetState) => Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      SwitchListTile(
                        title: Text(L10n.of(sheetContext).hide_retweets),
                        subtitle: Text(L10n.of(sheetContext).hide_retweets_description),
                        value: hidden,
                        onChanged: (value) async {
                          await setRetweetsHidden(user, value);
                          setSheetState(() => hidden = value);
                        },
                      ),
                      SwitchListTile(
                        title: Text(L10n.of(sheetContext).hide_replies),
                        subtitle: Text(L10n.of(sheetContext).hide_replies_description),
                        value: repliesHidden,
                        onChanged: (value) async {
                          await setRepliesHidden(user, value);
                          setSheetState(() => repliesHidden = value);
                        },
                      ),
                      if (subscribed)
                        ListTile(
                          title: Text(L10n.of(sheetContext).quiet_account),
                          subtitle: Text(L10n.of(sheetContext).quiet_account_description),
                          trailing: DropdownButton<int?>(
                            value: quietAccountChoices.contains(maxPosts) ? maxPosts : null,
                            items: [
                              for (final choice in quietAccountChoices)
                                DropdownMenuItem(
                                  value: choice,
                                  child: Text(quietAccountLabel(sheetContext, choice)),
                                ),
                            ],
                            onChanged: (value) async {
                              final ok = await setMaxPostsPerLoad(user.idStr!, value);
                              if (!ok && sheetContext.mounted) {
                                ScaffoldMessenger.of(sheetContext).showSnackBar(
                                  SnackBar(content: Text(L10n.of(sheetContext).unable_to_load_the_profile)),
                                );
                                return;
                              }
                              setSheetState(() => maxPosts = value);
                            },
                          ),
                        ),
                    ],
                  ),
                ),
              );
            });
      },
    );
  }
}
