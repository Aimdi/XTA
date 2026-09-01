/// Following what the add box offered, on whichever network it belongs to.
///
/// Each plugin already knows how to follow one thing; what was missing was a
/// single way to ask, so the sheet does not have to grow a branch — and a
/// screenful of plugin stores — for every network it accepts.
library;

import 'package:flutter/widgets.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/plugins/bluesky/bluesky_client.dart';
import 'package:xta/plugins/bluesky/bluesky_store.dart';
import 'package:xta/plugins/mastodon/mastodon_client.dart';
import 'package:xta/plugins/mastodon/mastodon_models.dart';
import 'package:xta/plugins/mastodon/mastodon_store.dart';
import 'package:xta/plugins/reddit/reddit_store.dart';
import 'package:xta/plugins/rss/rss_client.dart';
import 'package:xta/plugins/rss/rss_store.dart';
import 'package:xta/plugins/substack/substack_store.dart';
import 'package:xta/plugins/threads/threads_models.dart';
import 'package:xta/plugins/threads/threads_store.dart';
import 'package:xta/subscriptions/group_add_sources.dart';

/// Follows [candidate] and returns the subscription id a group ticks.
///
/// Throws whatever the network threw, for the caller to show: a handle that
/// does not exist has to say so rather than appear followed.
Future<String> followGroupAddCandidate(
  BuildContext context,
  GroupAddCandidate candidate,
) async {
  switch (candidate.source) {
    case GroupAddSource.reddit:
      await context.read<RedditSubredditsStore>().add(candidate.value);
      // The store keys a subreddit by its lowercased name, which is the id a
      // group member row carries.
      return candidate.value.toLowerCase();

    case GroupAddSource.threads:
      // No lookup: a Threads profile costs a request against the reader's own
      // session, and the handle is the id either way. The name fills in when
      // the account's posts first arrive.
      await context.read<ThreadsAccountsStore>().add(
        ThreadsAccount(handle: candidate.value, name: candidate.value),
      );
      return candidate.value;

    case GroupAddSource.substack:
      return _followSubstack(
        context.read<SubstackAddPublicationStore>(),
        context.read<SubstackPublicationsStore>(),
        candidate.value,
      );

    case GroupAddSource.bluesky:
      return _followBluesky(
        context.read<BlueskyClient>(),
        context.read<BlueskyAccountsStore>(),
        candidate.value,
      );

    case GroupAddSource.rss:
      return _followRss(
        context.read<RssClient>(),
        context.read<RssFeedsStore>(),
        candidate.value,
      );

    case GroupAddSource.mastodon:
      return _followMastodon(
        context.read<MastodonClient>(),
        context.read<MastodonAccountsStore>(),
        mastodonInstanceCandidates(
          candidate.value,
          configured: mastodonConfiguredInstances(
            PrefService.of(context, listen: false),
          ),
        ),
        candidate.value,
      );
  }
}

Future<String> _followRss(
  RssClient client,
  RssFeedsStore feeds,
  String input,
) async {
  final feed = await client.lookup(input);
  await feeds.add(feed);
  return feed.id;
}

Future<String> _followSubstack(
  SubstackAddPublicationStore lookup,
  SubstackPublicationsStore publications,
  String input,
) async {
  final publication = await lookup.lookup(input);
  await publications.add(publication);
  return publication.id;
}

Future<String> _followBluesky(
  BlueskyClient client,
  BlueskyAccountsStore accounts,
  String actor,
) async {
  final account = (await client.getProfile(actor)).toAccount();
  await accounts.add(account);
  return account.handle;
}

Future<String> _followMastodon(
  MastodonClient client,
  MastodonAccountsStore accounts,
  List<String> instances,
  String acct,
) async {
  final account = (await client.lookupAnywhere(instances, acct)).toAccount();
  await accounts.add(account);
  return account.acct;
}
