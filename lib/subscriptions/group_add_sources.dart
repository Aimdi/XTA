/// Reading what a reader typed into the "add to a group" box as a followable
/// thing on one of the networks XTA can read.
///
/// The box used to search X and, as an afterthought, offer a subreddit. Every
/// other source had to be followed on its own tab first and only then ticked
/// into the group, which is why a group of Threads accounts was so much work to
/// build than a group of X accounts. Here one box takes a handle, an address or
/// a bare name and offers whichever networks it could plausibly be on, for the
/// reader to pick from.
///
/// Nothing in this file touches a widget or the database, so what a given
/// string is taken to mean is decided in one place and can be tested there.
library;

import 'package:xta/constants.dart';
import 'package:xta/plugins/bluesky/bluesky_models.dart';
import 'package:xta/plugins/mastodon/mastodon_models.dart';
import 'package:xta/plugins/reddit/reddit_client.dart';
import 'package:xta/plugins/substack/substack_models.dart';
import 'package:xta/plugins/threads/threads_models.dart';

/// A network the add box can follow something on, besides X.
enum GroupAddSource { reddit, threads, substack, bluesky, mastodon }

/// The plugin each source belongs to, for its name, icon and enabled flag.
String pluginIdOfSource(GroupAddSource source) => switch (source) {
  GroupAddSource.reddit => pluginIdReddit,
  GroupAddSource.threads => pluginIdThreads,
  GroupAddSource.substack => pluginIdSubstack,
  GroupAddSource.bluesky => pluginIdBluesky,
  GroupAddSource.mastodon => pluginIdMastodon,
};

/// The preference that says whether the reader turned this source on.
String enabledOptionOfSource(GroupAddSource source) => switch (source) {
  GroupAddSource.reddit => optionPluginRedditEnabled,
  GroupAddSource.threads => optionPluginThreadsEnabled,
  GroupAddSource.substack => optionPluginSubstackEnabled,
  GroupAddSource.bluesky => optionPluginBlueskyEnabled,
  GroupAddSource.mastodon => optionPluginMastodonEnabled,
};

/// Something the reader could follow: which network, the value that network
/// wants, and how to show it in a row.
typedef GroupAddCandidate = ({
  GroupAddSource source,
  String value,
  String label,
});

/// What [raw] could be, on the networks in [enabled], best guess first.
///
/// A pasted address names exactly one network, so it yields one candidate. A
/// bare word could be a subreddit, a Threads handle or a newsletter, so it
/// yields all three and lets the reader say which they meant — guessing one
/// and being wrong costs more than a list of three.
List<GroupAddCandidate> groupAddCandidates(
  String raw, {
  required Set<GroupAddSource> enabled,
}) {
  final query = raw.trim();
  if (query.isEmpty) {
    return const [];
  }

  final found = _hostOf(query) == null
      ? _fromTypedName(query)
      : _fromAddress(query);

  return [
    for (final candidate in found)
      if (enabled.contains(candidate.source)) candidate,
  ];
}

/// The host of a pasted address, or null when this is not one.
///
/// Only a real scheme counts. `alice.bsky.social` parses as a URI with an empty
/// host and `r/flutter` as a path, and treating either as an address would send
/// it to the wrong network.
String? _hostOf(String query) {
  if (!query.contains('://')) {
    return null;
  }
  final uri = Uri.tryParse(query);
  return (uri == null || uri.host.isEmpty) ? null : uri.host.toLowerCase();
}

/// A pasted address, which belongs to whichever site it names.
List<GroupAddCandidate> _fromAddress(String query) {
  final host = _hostOf(query)!;
  final uri = Uri.parse(query);
  final segments = uri.pathSegments
      .where((e) => e.isNotEmpty)
      .toList(growable: false);

  if (host.contains('reddit.')) {
    return _reddit(query);
  }
  if (host.contains('threads.')) {
    return _threads(query);
  }
  if (host == 'bsky.app' ||
      host == 'www.bsky.app' ||
      host.endsWith('.bsky.social')) {
    return _bluesky(query);
  }
  // Substack profiles live at substack.com/@handle — that leading @ is not a
  // Fediverse address, and must not be sent to Mastodon.
  if (isSubstackServiceHost(host) || host.endsWith('.substack.com')) {
    return _substack(query);
  }
  if (isObviousNonSubstackHost(host)) {
    return const [];
  }
  // Every Mastodon-compatible instance has its own domain, so a profile is
  // recognised by its path rather than its host.
  if (segments.isNotEmpty &&
      (segments.first.startsWith('@') || segments.first == 'users')) {
    return _mastodon(query);
  }
  // Anything else with a domain: a newsletter on its own address.
  return _substack(query);
}

/// A handle or a name typed rather than pasted.
List<GroupAddCandidate> _fromTypedName(String query) {
  if (RegExp(r'^/?r/', caseSensitive: false).hasMatch(query)) {
    return _reddit(query);
  }
  if (query.toLowerCase().startsWith('did:plc:')) {
    return _bluesky(query);
  }
  // `alice@example.social` is a Fediverse address and nothing else.
  if (query.replaceFirst(RegExp(r'^@+'), '').contains('@')) {
    return _mastodon(query);
  }
  // A dotted name is a domain: a Bluesky handle, or a newsletter's own site.
  if (query.replaceFirst(RegExp(r'^@+'), '').contains('.')) {
    return [..._bluesky(query), ..._substack(query)];
  }

  return [..._reddit(query), ..._threads(query), ..._substack(query)];
}

List<GroupAddCandidate> _reddit(String query) {
  final name = normaliseSubreddit(query);
  return name == null
      ? const []
      : [(source: GroupAddSource.reddit, value: name, label: 'r/$name')];
}

List<GroupAddCandidate> _threads(String query) {
  final handle = normaliseThreadsHandle(query);
  return handle == null
      ? const []
      : [(source: GroupAddSource.threads, value: handle, label: '@$handle')];
}

List<GroupAddCandidate> _bluesky(String query) {
  final handle = normaliseBlueskyHandle(query);
  return handle == null
      ? const []
      : [(source: GroupAddSource.bluesky, value: handle, label: '@$handle')];
}

List<GroupAddCandidate> _mastodon(String query) {
  final acct = normaliseMastodonAcct(query);
  return acct == null
      ? const []
      : [(source: GroupAddSource.mastodon, value: acct, label: '@$acct')];
}

List<GroupAddCandidate> _substack(String query) {
  final base = resolveSubstackBase(query);
  if (base == null) {
    return const [];
  }
  final name = subdomainOf(base);
  return name.isEmpty
      ? const []
      : [
          (
            source: GroupAddSource.substack,
            value: query.trim(),
            label: base.host,
          ),
        ];
}
