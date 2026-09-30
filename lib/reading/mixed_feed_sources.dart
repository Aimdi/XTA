import 'package:flutter/widgets.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/plugins/bluesky/bluesky_mixed_sources.dart';
import 'package:xta/plugins/hackernews/hn_mixed_sources.dart';
import 'package:xta/plugins/mastodon/mastodon_mixed_sources.dart';
import 'package:xta/plugins/plugin_registry.dart';
import 'package:xta/plugins/reddit/reddit_mixed_sources.dart';
import 'package:xta/plugins/rss/rss_mixed_sources.dart';
import 'package:xta/plugins/substack/substack_mixed_sources.dart';
import 'package:xta/plugins/threads/threads_mixed_sources.dart';
import 'package:xta/plugins/x/x_mixed_sources.dart';
import 'package:xta/reading/mixed_feed_definition.dart';
import 'package:xta/reading/mixed_feed_source.dart';

/// Every kind of source a mix can hold, in the order the source picker offers them.
const mixedSourceKinds = <MixedSourceKind>[
  ...xMixedSourceKinds,
  ...mastodonMixedSourceKinds,
  ...blueskyMixedSourceKinds,
  ...threadsMixedSourceKinds,
  ...redditMixedSourceKinds,
  ...rssMixedSourceKinds,
  ...substackMixedSourceKinds,
  ...hackerNewsMixedSourceKinds,
];

MixedSourceKind? mixedSourceKindById(String id, {List<MixedSourceKind>? kinds}) =>
    (kinds ?? mixedSourceKinds).where((kind) => kind.id == id).firstOrNull;

/// Whether [kind]'s plugin is on; a kind whose plugin is not registered counts as on.
bool mixedSourceKindEnabled(MixedSourceKind kind, BasePrefService prefs) =>
    pluginById(kind.pluginId)?.isEnabled(prefs) ?? true;

/// What [source] depends on, or an empty signature when its services are missing.
String mixedSourceSignature(BuildContext context, MixedSourceKind kind, MixedFeedSource source) {
  try {
    return kind.signature(context, source);
  } on ProviderNotFoundException {
    return '';
  }
}

/// A reader for [source], or null when its kind is unknown, its plugin is off or its services are missing.
MixedSourceReader? mixedSourceReader(BuildContext context, MixedFeedSource source, {List<MixedSourceKind>? kinds}) {
  final kind = mixedSourceKindById(source.kind, kinds: kinds);
  if (kind == null || !mixedSourceKindEnabled(kind, PrefService.of(context, listen: false))) return null;
  try {
    return kind.reader(context, source);
  } on ProviderNotFoundException {
    return null;
  }
}
