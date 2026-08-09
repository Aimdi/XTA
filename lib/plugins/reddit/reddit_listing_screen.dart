import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:pref/pref.dart';
import 'package:xta/plugins/reddit/reddit_client.dart';
import 'package:xta/plugins/reddit/reddit_listing_body.dart';
import 'package:xta/plugins/reddit/reddit_read_session.dart';
import 'package:xta/plugins/reddit/reddit_screen.dart' show redditErrorMessage;
import 'package:xta/plugins/reddit/reddit_search_screen.dart';
import 'package:xta/plugins/reddit/reddit_store.dart';
import 'package:intl/intl.dart';
import 'package:xta/subscriptions/users_model.dart';

/// A list of Reddit posts under a title.
///
/// A subreddit and an account differ only in where the posts come from and
/// whether the title can be followed, so they are one screen rather than two
/// that would drift apart.
class RedditListingScreen extends StatelessWidget {
  final String? subreddit;
  final String? user;

  const RedditListingScreen.subreddit(String name, {super.key}) : subreddit = name, user = null;

  const RedditListingScreen.user(String name, {super.key}) : user = name, subreddit = null;

  String get title => subreddit != null ? 'r/$subreddit' : 'u/$user';

  @override
  Widget build(BuildContext context) {
    final subreddit = this.subreddit;

    return Scaffold(
      appBar: AppBar(
        title: Text(title),
        actions: [
          if (subreddit != null) ...[
            IconButton(
              icon: const Icon(Icons.search),
              tooltip: L10n.of(context).search,
              onPressed: () =>
                  Navigator.push(context, MaterialPageRoute(builder: (_) => RedditSearchScreen(subreddit: subreddit))),
            ),
            IconButton(
              icon: const Icon(Icons.info_outline),
              tooltip: L10n.of(context).plugin_reddit_about_community,
              onPressed: () => showRedditAboutSheet(context, subreddit),
            ),
            RedditFollowButton(subreddit: subreddit),
          ],
        ],
      ),
      body: subreddit == null ? RedditListingBody.user(user!) : RedditListingBody.subreddit(subreddit),
    );
  }
}

/// Follows or unfollows a subreddit, reflecting whichever it currently is.
///
/// Observes the store rather than reading it once, so the label flips the
/// moment the list changes — including when it is changed from somewhere else.
class RedditFollowButton extends StatelessWidget {
  final String subreddit;

  const RedditFollowButton({super.key, required this.subreddit});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);

    return ScopedBuilder<RedditSubredditsStore, List<String>>(
      store: context.read<RedditSubredditsStore>(),
      onState: (context, names) {
        final followed = isFollowedSubreddit(names, subreddit);

        return TextButton.icon(
          icon: Icon(followed ? Icons.check : Icons.add, size: 18),
          label: Text(followed ? l10n.unsubscribe : l10n.subscribe),
          onPressed: () => toggleRedditFollow(context, subreddit, followed: followed),
        );
      },
    );
  }
}

/// Subreddit names are stored as the reader typed them but compared as Reddit
/// does, which is case-insensitively.
bool isFollowedSubreddit(List<String> names, String subreddit) =>
    names.any((e) => e.toLowerCase() == subreddit.toLowerCase());

/// Adds or removes a subreddit, and tells the subscription list about it so the
/// group editor sees the change without a restart.
Future<void> toggleRedditFollow(BuildContext context, String subreddit, {required bool followed}) async {
  final store = context.read<RedditSubredditsStore>();
  final subscriptions = context.read<SubscriptionsModel>();

  followed ? await store.remove(subreddit) : await store.add(subreddit);
  await subscriptions.reloadSubscriptions();
}

/// The sidebar as a sheet: what the community is, how many read it, and its
/// own description — the "is this worth following" answer, one tap away.
final NumberFormat _compact = NumberFormat.compact(locale: 'en_US');

Future<void> showRedditAboutSheet(BuildContext context, String subreddit) {
  final client = context.read<RedditClient>();
  final prefs = PrefService.of(context, listen: false);

  return showModalBottomSheet<void>(
    context: context,
    showDragHandle: true,
    isScrollControlled: true,
    builder: (context) => DraggableScrollableSheet(
      expand: false,
      initialChildSize: 0.5,
      builder: (context, controller) => FutureBuilder<RedditSubredditAbout>(
        future: RedditReadSession.resolve(
          prefs: prefs,
        ).then((session) => session.fetchSubredditAbout(client, subreddit)),
        builder: (context, snapshot) {
          final l10n = L10n.of(context);
          final theme = Theme.of(context);
          final about = snapshot.data;

          if (snapshot.hasError) {
            return Padding(
              padding: const EdgeInsets.all(24),
              child: Text(redditErrorMessage(l10n, snapshot.error!), textAlign: TextAlign.center),
            );
          }
          if (about == null) {
            return const Center(child: CircularProgressIndicator());
          }

          return ListView(
            controller: controller,
            padding: const EdgeInsets.fromLTRB(24, 8, 24, 24),
            children: [
              Text('r/${about.name}', style: theme.textTheme.titleLarge!.copyWith(fontWeight: FontWeight.w700)),
              if (about.title != null && about.title != about.name) ...[
                const SizedBox(height: 4),
                Text(about.title!, style: theme.textTheme.titleSmall),
              ],
              const SizedBox(height: 12),
              DefaultTextStyle.merge(
                style: theme.textTheme.bodySmall!.copyWith(color: theme.colorScheme.onSurfaceVariant),
                child: Row(
                  children: [
                    if (about.subscribers != null) ...[
                      const Icon(Icons.people_outline, size: 16),
                      const SizedBox(width: 4),
                      Text('${_compact.format(about.subscribers)} ${l10n.followers.toLowerCase()}'),
                    ],
                    if (about.activeUsers != null) ...[
                      const SizedBox(width: 12),
                      Icon(Icons.circle, size: 8, color: theme.colorScheme.primary),
                      const SizedBox(width: 4),
                      Text('${_compact.format(about.activeUsers)} ${l10n.plugin_reddit_online_now}'),
                    ],
                  ],
                ),
              ),
              if (about.description != null) ...[
                const SizedBox(height: 16),
                Text(about.description!, style: theme.textTheme.bodyMedium),
              ],
            ],
          );
        },
      ),
    ),
  );
}
