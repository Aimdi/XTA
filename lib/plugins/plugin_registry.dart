import 'package:xta/plugins/bluesky/bluesky_plugin.dart';
import 'package:xta/plugins/deepmarks/deepmarks_plugin.dart';
import 'package:xta/plugins/ehviewer/eh_plugin.dart';
import 'package:xta/plugins/immich/immich_plugin.dart';
import 'package:xta/plugins/karakeep/karakeep_plugin.dart';
import 'package:xta/plugins/mastodon/mastodon_plugin.dart';
import 'package:xta/plugins/pixiv/pixiv_plugin.dart';
import 'package:xta/plugins/plugin.dart';
import 'package:xta/plugins/subscription_source.dart';
import 'package:xta/plugins/reddit/reddit_plugin.dart';
import 'package:xta/plugins/stocks/stocks_plugin.dart';
import 'package:xta/plugins/substack/substack_plugin.dart';
import 'package:xta/plugins/threads/threads_plugin.dart';

/// Built-in plugins shipped with XTA, ordered the way the store groups them.
final List<XtaPlugin> builtInPlugins = [
  ThreadsPlugin(),
  BlueskyPlugin(),
  MastodonPlugin(),
  RedditPlugin(),
  SubstackPlugin(),
  PixivPlugin(),
  EhViewerPlugin(),
  StocksPlugin(),
  KarakeepPlugin(),
  DeepmarksPlugin(),
  ImmichPlugin(),
];

XtaPlugin? pluginById(String id) {
  for (final plugin in builtInPlugins) {
    if (plugin.id == id) return plugin;
  }
  return null;
}

/// The plugins whose followed accounts are subscriptions and whose posts join a
/// timeline. Everything that used to name Reddit, Substack, Threads, Bluesky and
/// the Fediverse one at a time reads this instead.
List<SubscriptionSource> get subscriptionSources =>
    builtInPlugins.whereType<SubscriptionSource>().toList(growable: false);
