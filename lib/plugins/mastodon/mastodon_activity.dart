import 'package:flutter/material.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/mastodon/mastodon_archive.dart';
import 'package:xta/plugins/mastodon/mastodon_client.dart';
import 'package:xta/plugins/mastodon/mastodon_models.dart';
import 'package:xta/plugins/mastodon/mastodon_post_card.dart';
import 'package:xta/plugins/mastodon/mastodon_profile_screen.dart';
import 'package:xta/plugins/mastodon/mastodon_store.dart';
import 'package:xta/plugins/plugin_activity.dart';
import 'package:xta/plugins/plugin_post_actions.dart';
import 'package:xta/subscriptions/widgets/fallback_avatar.dart';

Future<void> showMastodonPostActions(BuildContext context, MastodonPost post) => showPluginPostActions(
  context, post: PluginPostArchive(id: mastodonArchiveId(post), userId: post.acct, content: mastodonArchiveBlob(post)), url: post.url,
  onReposts: () => openMastodonReposts(context, post), onQuotes: () => openMastodonQuotes(context, post),
);

List<String> _instances(BuildContext context, MastodonPost post) => mastodonInstanceCandidates(post.acct,
  configured: mastodonConfiguredInstances(PrefService.of(context, listen: false)));

String _activityError(L10n l10n, Object error) => error is MastodonException &&
    (error.kind == MastodonErrorKind.unauthorized || error.kind == MastodonErrorKind.notFound)
    ? l10n.plugin_post_activity_unavailable : mastodonErrorMessage(l10n, error);

Future<void> openMastodonReposts(BuildContext context, MastodonPost post) {
  final client = context.read<MastodonClient>();
  final instances = _instances(context, post);
  return openPluginActivity<MastodonProfile>(context,
    title: L10n.of(context).plugin_post_reposted_by, postUrl: post.url,
    loader: (cursor) => client.getRepostedBy(instances, post, cursor: cursor), idOf: (person) => person.acct,
    errorLabel: _activityError,
    itemBuilder: (context, person) => ListTile(
      leading: FallbackAvatar(seed: person.acct, displayName: person.displayName, size: 40, accent: Theme.of(context).colorScheme.primary),
      title: Text(person.displayName), subtitle: Text('@${person.acct}'), trailing: const Icon(Icons.chevron_right),
      onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => MastodonProfileScreen(acct: person.acct))),
    ),
  );
}

Future<void> openMastodonQuotes(BuildContext context, MastodonPost post) {
  final client = context.read<MastodonClient>();
  final instances = _instances(context, post);
  return openPluginActivity<MastodonPost>(context, title: L10n.of(context).quotes, postUrl: post.url,
    loader: (cursor) => client.getQuotes(instances, post, cursor: cursor), idOf: (post) => post.url,
    errorLabel: _activityError, itemBuilder: (context, quote) => MastodonPostCard(post: quote),
  );
}
