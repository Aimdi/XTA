import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:sqflite/sqflite.dart';
import 'package:xta/client/client.dart';
import 'package:xta/database/repository.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/subscriptions/users_model.dart';
import 'package:xta/tweet/tweet_chrome.dart';
import 'package:xta/user.dart';

Future<bool> isRetweetsHidden(String userId) async {
  final repository = await Repository.readOnly();
  final rows = await repository.query(
    tableRetweetFilter,
    where: 'user_id = ?',
    whereArgs: [userId],
  );
  return rows.isNotEmpty;
}

Future<void> setRetweetsHidden(UserWithExtra user, bool hidden) async {
  final repository = await Repository.writable();
  if (hidden) {
    await repository.insert(
      tableRetweetFilter,
      {'user_id': user.idStr, 'screen_name': user.screenName},
      conflictAlgorithm: ConflictAlgorithm.replace,
    );
    return;
  }
  await repository.delete(
    tableRetweetFilter,
    where: 'user_id = ?',
    whereArgs: [user.idStr],
  );
}

Future<Set<String>> hiddenRetweetScreenNames() async {
  final repository = await Repository.readOnly();
  return (await repository.query(tableRetweetFilter, columns: ['screen_name']))
      .map((row) => (row['screen_name'] as String).toLowerCase())
      .toSet();
}

List<TweetChain> filterHiddenRetweets(
  List<TweetChain> chains,
  Set<String> hidden,
) {
  if (hidden.isEmpty) return chains;
  return chains.where((chain) {
    final tweet = chain.tweets.isEmpty ? null : chain.tweets.first;
    return tweet?.retweetedStatusWithCard == null ||
        !hidden.contains(tweet?.user?.screenName?.toLowerCase());
  }).toList();
}

Future<bool> isRepliesHidden(String userId) async {
  final repository = await Repository.readOnly();
  final rows = await repository.query(
    tableReplyFilter,
    where: 'user_id = ?',
    whereArgs: [userId],
  );
  return rows.isNotEmpty;
}

Future<void> setRepliesHidden(UserWithExtra user, bool hidden) async {
  final repository = await Repository.writable();
  if (hidden) {
    await repository.insert(
      tableReplyFilter,
      {'user_id': user.idStr, 'screen_name': user.screenName},
      conflictAlgorithm: ConflictAlgorithm.replace,
    );
    return;
  }
  await repository.delete(
    tableReplyFilter,
    where: 'user_id = ?',
    whereArgs: [user.idStr],
  );
}

Future<Set<String>> hiddenReplyScreenNames() async {
  final repository = await Repository.readOnly();
  return (await repository.query(tableReplyFilter, columns: ['screen_name']))
      .map((row) => (row['screen_name'] as String).toLowerCase())
      .toSet();
}

bool isReplyToSomeoneElse(TweetWithCard? tweet) {
  if (tweet == null) return false;
  final repliesTo = tweet.inReplyToUserIdStr ?? tweet.inReplyToScreenName;
  if (tweet.inReplyToStatusIdStr == null && repliesTo == null) return false;

  final authorId = tweet.user?.idStr;
  if (tweet.inReplyToUserIdStr != null && authorId != null) {
    return tweet.inReplyToUserIdStr != authorId;
  }

  final authorName = tweet.user?.screenName?.toLowerCase();
  final target = tweet.inReplyToScreenName?.toLowerCase();
  if (target != null && authorName != null) return target != authorName;
  return true;
}

List<TweetChain> filterHiddenReplies(
  List<TweetChain> chains,
  Set<String> hidden,
) {
  if (hidden.isEmpty) return chains;
  return chains.where((chain) {
    final tweet = chain.tweets.isEmpty ? null : chain.tweets.first;
    if (!isReplyToSomeoneElse(tweet)) return true;
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
  return rows.isEmpty ? null : rows.first['max_posts_per_load'] as int?;
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
  return value == null ? L10n.of(context).quiet_account_off : '$value';
}

@immutable
class ProfileFeedSettingsState {
  final bool subscribed;
  final bool retweetsHidden;
  final bool repliesHidden;
  final int? maxPosts;

  const ProfileFeedSettingsState({
    this.subscribed = false,
    this.retweetsHidden = false,
    this.repliesHidden = false,
    this.maxPosts,
  });

  ProfileFeedSettingsState copyWith({
    bool? subscribed,
    bool? retweetsHidden,
    bool? repliesHidden,
    int? maxPosts,
    bool clearMaxPosts = false,
  }) => ProfileFeedSettingsState(
    subscribed: subscribed ?? this.subscribed,
    retweetsHidden: retweetsHidden ?? this.retweetsHidden,
    repliesHidden: repliesHidden ?? this.repliesHidden,
    maxPosts: clearMaxPosts ? null : maxPosts ?? this.maxPosts,
  );
}

class ProfileFeedSettingsStore extends Store<ProfileFeedSettingsState> {
  final UserWithExtra user;
  bool _active = true;

  ProfileFeedSettingsStore(this.user)
    : super(const ProfileFeedSettingsState());

  Future<void> load({required bool subscribed}) async {
    final retweetsHidden = await isRetweetsHidden(user.idStr!);
    final repliesHidden = await isRepliesHidden(user.idStr!);
    final storedMaximum = await loadMaxPostsPerLoad(user.idStr!);
    final maxPosts = quietAccountChoices.contains(storedMaximum)
        ? storedMaximum
        : null;
    if (_active) {
      update(
        ProfileFeedSettingsState(
          subscribed: subscribed,
          retweetsHidden: retweetsHidden,
          repliesHidden: repliesHidden,
          maxPosts: maxPosts,
        ),
      );
    }
  }

  Future<void> setRetweets(bool value) async {
    await setRetweetsHidden(user, value);
    if (_active) update(state.copyWith(retweetsHidden: value));
  }

  Future<void> setReplies(bool value) async {
    await setRepliesHidden(user, value);
    if (_active) update(state.copyWith(repliesHidden: value));
  }

  Future<bool> setMaximum(int? value) async {
    final saved = await setMaxPostsPerLoad(user.idStr!, value);
    if (saved && _active) {
      update(
        state.copyWith(maxPosts: value, clearMaxPosts: value == null),
      );
    }
    return saved;
  }

  @override
  Future<void> destroy() {
    _active = false;
    return super.destroy();
  }
}

class ProfileFeedSettingsButton extends StatelessWidget {
  final UserWithExtra user;
  final Color? color;

  const ProfileFeedSettingsButton({super.key, required this.user, this.color});

  @override
  Widget build(BuildContext context) {
    if (user.idStr == null) return const SizedBox.shrink();
    return IconButton(
      icon: const Icon(Icons.tune_outlined),
      color: color,
      tooltip: L10n.of(context).filters,
      onPressed: () => _open(context),
    );
  }

  Future<void> _open(BuildContext context) async {
    final subscribed = context.read<SubscriptionsModel>().state.any(
      (subscription) => subscription.id == user.idStr,
    );
    final store = ProfileFeedSettingsStore(user);
    try {
      await store.load(subscribed: subscribed);
      if (!context.mounted) return;
      await showModalBottomSheet<void>(
        context: context,
        useSafeArea: true,
        isScrollControlled: true,
        showDragHandle: true,
        builder: (sheetContext) => ProfileFeedSettingsSheet(store: store),
      );
    } catch (_) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(L10n.of(context).oops_something_went_wrong)),
        );
      }
    } finally {
      store.destroy();
    }
  }
}

class ProfileFeedSettingsSheet extends StatelessWidget {
  final ProfileFeedSettingsStore store;

  const ProfileFeedSettingsSheet({super.key, required this.store});

  @override
  Widget build(BuildContext context) {
    return ScopedBuilder<ProfileFeedSettingsStore, ProfileFeedSettingsState>(
      store: store,
      onState: (context, state) => SingleChildScrollView(
        padding: EdgeInsets.fromLTRB(
          kTweetHorizontalPadding,
          0,
          kTweetHorizontalPadding,
          kTweetSpace4 + MediaQuery.viewPaddingOf(context).bottom,
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(
              L10n.of(context).filters,
              style: Theme.of(context).textTheme.titleLarge?.copyWith(
                fontWeight: FontWeight.w800,
              ),
            ),
            const SizedBox(height: kTweetSpace2),
            SwitchListTile(
              contentPadding: EdgeInsets.zero,
              title: Text(L10n.of(context).hide_retweets),
              subtitle: Text(L10n.of(context).hide_retweets_description),
              value: state.retweetsHidden,
              onChanged: (value) => _apply(
                context,
                () => store.setRetweets(value),
              ),
            ),
            SwitchListTile(
              contentPadding: EdgeInsets.zero,
              title: Text(L10n.of(context).hide_replies),
              subtitle: Text(L10n.of(context).hide_replies_description),
              value: state.repliesHidden,
              onChanged: (value) => _apply(
                context,
                () => store.setReplies(value),
              ),
            ),
            if (state.subscribed) ...[
              const SizedBox(height: kTweetSpace2),
              Text(
                L10n.of(context).quiet_account,
                style: tweetLabelStyle(context),
              ),
              const SizedBox(height: kTweetSpace1),
              Text(
                L10n.of(context).quiet_account_description,
                style: tweetMetadataStyle(context),
              ),
              const SizedBox(height: kTweetSpace3),
              Wrap(
                spacing: kTweetSpace2,
                runSpacing: kTweetSpace2,
                children: [
                  for (final choice in quietAccountChoices)
                    Semantics(
                      selected: choice == state.maxPosts,
                      child: ConstrainedBox(
                        constraints: const BoxConstraints(
                          minHeight: kTweetTouchTarget,
                        ),
                        child: ChoiceChip(
                          label: Text(quietAccountLabel(context, choice)),
                          labelStyle: tweetMetadataStyle(context).copyWith(
                            color: tweetPrimaryColor(context),
                            fontWeight: choice == state.maxPosts
                                ? FontWeight.w700
                                : FontWeight.w500,
                          ),
                          side: BorderSide(
                            color: choice == state.maxPosts
                                ? tweetReadableAccentColor(context)
                                : tweetDividerColor(context),
                          ),
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(12),
                          ),
                          backgroundColor: Colors.transparent,
                          selectedColor: tweetAccentColor(
                            context,
                          ).withValues(alpha: 0.12),
                          selected: choice == state.maxPosts,
                          onSelected: (_) => _setMaximum(context, choice),
                        ),
                      ),
                    ),
                ],
              ),
            ],
          ],
        ),
      ),
    );
  }

  Future<void> _setMaximum(BuildContext context, int? value) async {
    try {
      final saved = await store.setMaximum(value);
      if (!saved && context.mounted) _showError(context);
    } catch (_) {
      if (context.mounted) _showError(context);
    }
  }

  Future<void> _apply(
    BuildContext context,
    Future<void> Function() update,
  ) async {
    try {
      await update();
    } catch (_) {
      if (context.mounted) _showError(context);
    }
  }

  void _showError(BuildContext context) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(L10n.of(context).oops_something_went_wrong)),
    );
  }
}
