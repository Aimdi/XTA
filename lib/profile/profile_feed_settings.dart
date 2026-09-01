import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:quax/client/client.dart';
import 'package:quax/database/repository.dart';
import 'package:quax/generated/l10n.dart';
import 'package:quax/tweet/tweet_chrome.dart';
import 'package:quax/user.dart';
import 'package:sqflite/sqflite.dart';

/// Per-user feed filters ("turn off reposts"): users listed in
/// [tableRetweetFilter] have their retweets hidden from every feed.

Future<bool> isRetweetsHidden(String userId) async {
  var repository = await Repository.readOnly();
  var rows = await repository.query(
    tableRetweetFilter,
    where: 'user_id = ?',
    whereArgs: [userId],
  );
  return rows.isNotEmpty;
}

Future<void> setRetweetsHidden(UserWithExtra user, bool hidden) async {
  var repository = await Repository.writable();
  if (hidden) {
    await repository.insert(tableRetweetFilter, {
      'user_id': user.idStr,
      'screen_name': user.screenName,
    }, conflictAlgorithm: ConflictAlgorithm.replace);
  } else {
    await repository.delete(
      tableRetweetFilter,
      where: 'user_id = ?',
      whereArgs: [user.idStr],
    );
  }
}

/// Lowercased screen names whose retweets are hidden.
Future<Set<String>> hiddenRetweetScreenNames() async {
  var repository = await Repository.readOnly();
  return (await repository.query(
    tableRetweetFilter,
    columns: ['screen_name'],
  )).map((row) => (row['screen_name'] as String).toLowerCase()).toSet();
}

/// Drops chains that are a retweet made by one of the [hidden] users.
List<TweetChain> filterHiddenRetweets(
  List<TweetChain> chains,
  Set<String> hidden,
) {
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
  var rows = await repository.query(
    tableReplyFilter,
    where: 'user_id = ?',
    whereArgs: [userId],
  );
  return rows.isNotEmpty;
}

Future<void> setRepliesHidden(UserWithExtra user, bool hidden) async {
  var repository = await Repository.writable();
  if (hidden) {
    await repository.insert(tableReplyFilter, {
      'user_id': user.idStr,
      'screen_name': user.screenName,
    }, conflictAlgorithm: ConflictAlgorithm.replace);
  } else {
    await repository.delete(
      tableReplyFilter,
      where: 'user_id = ?',
      whereArgs: [user.idStr],
    );
  }
}

/// Lowercased screen names whose replies are hidden.
Future<Set<String>> hiddenReplyScreenNames() async {
  var repository = await Repository.readOnly();
  return (await repository.query(
    tableReplyFilter,
    columns: ['screen_name'],
  )).map((row) => (row['screen_name'] as String).toLowerCase()).toSet();
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
List<TweetChain> filterHiddenReplies(
  List<TweetChain> chains,
  Set<String> hidden,
) {
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

/// The profile tune button: per-user feed filters such as "hide reposts".
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
      icon: const Icon(Icons.tune),
      color: color,
      tooltip: L10n.of(context).filters,
      onPressed: () async {
        final store = ProfileFeedSettingsStore(
          ProfileFeedSettingsState(
            hideRetweets: await isRetweetsHidden(user.idStr!),
            hideReplies: await isRepliesHidden(user.idStr!),
          ),
          user,
        );
        if (!context.mounted) {
          store.destroy();
          return;
        }

        await showModalBottomSheet<void>(
          context: context,
          useSafeArea: true,
          showDragHandle: true,
          builder: (_) => _ProfileFeedSettingsSheet(store: store),
        );
        store.destroy();
      },
    );
  }
}

@immutable
class ProfileFeedSettingsState {
  final bool hideRetweets;
  final bool hideReplies;

  const ProfileFeedSettingsState({
    required this.hideRetweets,
    required this.hideReplies,
  });

  ProfileFeedSettingsState copyWith({bool? hideRetweets, bool? hideReplies}) {
    return ProfileFeedSettingsState(
      hideRetweets: hideRetweets ?? this.hideRetweets,
      hideReplies: hideReplies ?? this.hideReplies,
    );
  }
}

class ProfileFeedSettingsStore extends Store<ProfileFeedSettingsState> {
  final UserWithExtra user;

  ProfileFeedSettingsStore(super.initialState, this.user);

  Future<void> setHideRetweets(bool value) async {
    await setRetweetsHidden(user, value);
    update(state.copyWith(hideRetweets: value));
  }

  Future<void> setHideReplies(bool value) async {
    await setRepliesHidden(user, value);
    update(state.copyWith(hideReplies: value));
  }
}

class _ProfileFeedSettingsSheet extends StatelessWidget {
  final ProfileFeedSettingsStore store;

  const _ProfileFeedSettingsSheet({required this.store});

  @override
  Widget build(BuildContext context) {
    return ScopedBuilder<ProfileFeedSettingsStore, ProfileFeedSettingsState>(
      store: store,
      onState: (context, state) => Padding(
        padding: EdgeInsets.only(bottom: MediaQuery.paddingOf(context).bottom),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(
                kTweetHorizontalPadding,
                0,
                kTweetHorizontalPadding,
                kTweetSpace2,
              ),
              child: Text(
                L10n.of(context).filters,
                style: Theme.of(context).textTheme.titleLarge,
              ),
            ),
            SwitchListTile(
              contentPadding: const EdgeInsets.symmetric(
                horizontal: kTweetHorizontalPadding,
              ),
              title: Text(L10n.of(context).hide_retweets),
              subtitle: Text(L10n.of(context).hide_retweets_description),
              value: state.hideRetweets,
              onChanged: store.setHideRetweets,
            ),
            SwitchListTile(
              contentPadding: const EdgeInsets.symmetric(
                horizontal: kTweetHorizontalPadding,
              ),
              title: Text(L10n.of(context).hide_replies),
              subtitle: Text(L10n.of(context).hide_replies_description),
              value: state.hideReplies,
              onChanged: store.setHideReplies,
            ),
            const SizedBox(height: kTweetSpace2),
          ],
        ),
      ),
    );
  }
}
