import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/bluesky/bluesky_archive.dart';
import 'package:xta/plugins/bluesky/bluesky_client.dart';
import 'package:xta/plugins/bluesky/bluesky_models.dart';
import 'package:xta/plugins/bluesky/bluesky_post_card.dart';
import 'package:xta/plugins/bluesky/bluesky_profile_screen.dart';
import 'package:xta/plugins/plugin_activity.dart';
import 'package:xta/plugins/plugin_post_actions.dart';
import 'package:xta/subscriptions/widgets/fallback_avatar.dart';

Future<void> showBlueskyPostActions(BuildContext context, BlueskyPost post) => showPluginPostActions(
  context,
  post: PluginPostArchive(id: blueskyArchiveId(post), userId: post.did, content: blueskyArchiveBlob(post)),
  url: post.url,
  onReposts: () => openBlueskyReposts(context, post),
  onQuotes: () => openBlueskyQuotes(context, post),
);

Future<void> openBlueskyReposts(BuildContext context, BlueskyPost post) {
  final client = context.read<BlueskyClient>();
  return openPluginActivity<BlueskyProfile>(
    context,
    title: L10n.of(context).plugin_post_reposted_by,
    postUrl: post.url,
    loader: (cursor) => client.getRepostedBy(post.uri, cursor: cursor),
    idOf: (person) => person.did.isEmpty ? person.handle : person.did,
    errorLabel: blueskyErrorMessage,
    itemBuilder: (context, person) => ListTile(
      leading: FallbackAvatar(
        seed: person.did,
        displayName: person.displayName,
        size: 40,
        accent: Theme.of(context).colorScheme.primary,
      ),
      title: Text(person.displayName),
      subtitle: Text('@${person.handle}'),
      trailing: const Icon(Icons.chevron_right),
      onTap: () => Navigator.push(
        context,
        MaterialPageRoute(builder: (_) => BlueskyProfileScreen(actor: person.did.isEmpty ? person.handle : person.did)),
      ),
    ),
  );
}

Future<void> openBlueskyQuotes(BuildContext context, BlueskyPost post) {
  final client = context.read<BlueskyClient>();
  return openPluginActivity<BlueskyPost>(
    context,
    title: L10n.of(context).quotes,
    postUrl: post.url,
    loader: (cursor) => client.getQuotes(post.uri, cursor: cursor),
    idOf: (post) => post.uri,
    errorLabel: blueskyErrorMessage,
    itemBuilder: (context, quote) => BlueskyPostCard(post: quote),
  );
}
