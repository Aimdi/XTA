import 'package:flutter/material.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:sqflite/sqflite.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/home/home_screen.dart';
import 'package:xta/plugins/plugin.dart';
import 'package:xta/plugins/plugin_account_subscription.dart';
import 'package:xta/plugins/plugin_category.dart';
import 'package:xta/plugins/subscription_source.dart';
import 'package:xta/plugins/websources/web_source_client.dart';
import 'package:xta/plugins/websources/web_source_definition.dart';
import 'package:xta/plugins/websources/web_source_follows_store.dart';
import 'package:xta/plugins/websources/web_source_item_card.dart';
import 'package:xta/plugins/websources/web_source_items.dart';
import 'package:xta/plugins/websources/web_source_screen.dart';
import 'package:xta/plugins/websources/web_sources_screen.dart';
import 'package:xta/plugins/websources/web_sources_settings.dart';
import 'package:xta/plugins/websources/web_sources_store.dart';
import 'package:xta/tweet/interleaved_items.dart';
import 'package:xta/ui/empty_pane.dart';
import 'package:xta/ui/provenance_accent.dart';

const webSourcesBrand = Color(0xFF0E7C86);

/// How many posts of each followed list join a timeline page.
const kWebSourceInterleavedPageSize = 8;

/// Sites added from a published list or by link, read with their source
/// files' rules (docs/specs/web-sources.md).
class WebSourcesPlugin extends XtaPlugin with SubscriptionSource {
  WebSourcesPlugin();

  @override
  String get id => pluginIdWebSources;

  @override
  String get enabledPrefKey => optionPluginWebSourcesEnabled;

  @override
  String? get homeTabPrefKey => optionPluginWebSourcesShowTab;

  @override
  IconData get icon => Icons.travel_explore;

  @override
  PluginCategory get category => PluginCategory.sites;

  @override
  Color get brandColor => webSourcesBrand;

  @override
  String title(BuildContext context) => L10n.of(context).plugin_websources_title;

  @override
  String description(BuildContext context) => L10n.of(context).plugin_websources_description;

  @override
  NavigationPage homePage(BuildContext context) => NavigationPage(
    pluginIdWebSources,
    (c) => L10n.of(c).plugin_websources_title,
    const Icon(Icons.travel_explore_outlined),
    const Icon(Icons.travel_explore),
  );

  @override
  Widget homeScreen({required ScrollController scrollController}) =>
      WebSourcesScreen(scrollController: scrollController);

  @override
  Widget? settingsScreen(BuildContext context) => const WebSourcesSettingsScreen();

  @override
  String get groupMembershipPrefix => '$id:';

  @override
  String get subscriptionTable => optionPluginWebSourcesFollows;

  @override
  String? get subscriptionPreferenceKey => optionPluginWebSourcesFollows;

  @override
  Subscription subscriptionFromMap(Map<String, Object?> row) => PluginAccountSubscription(id, row);

  @override
  Future<List<Subscription>> readSubscriptions(DatabaseExecutor database, {BasePrefService? prefs}) async =>
      prefs == null ? const [] : readWebSourceFollows(prefs);

  @override
  bool owns(Subscription subscription) => subscription is PluginAccountSubscription && subscription.pluginId == id;

  /// The list's name: the subscription's own name is its source's.
  @override
  String subtitleFor(Subscription subscription) => subscription.screenName;

  @override
  Widget Function() destinationFor(Subscription subscription) =>
      () => _FollowedList(key: ValueKey(subscription.id), subscriptionId: subscription.id);

  @override
  Future<void> reloadFromDatabase(BuildContext context) async {
    await context.read<WebSourcesStore>().load();
    if (context.mounted) await context.read<WebSourceFollowsStore>().load();
  }

  @override
  Future<void> unfollow(BuildContext context, Subscription subscription) =>
      context.read<WebSourceFollowsStore>().unfollow(subscription.id);

  @override
  bool inHomeFeed(BuildContext context) {
    final prefs = PrefService.of(context, listen: false);
    return isEnabled(prefs) && prefs.get<bool>(optionPluginWebSourcesInHomeFeed) == true;
  }

  @override
  List<String> homeFeedIds(BuildContext context) =>
      inHomeFeed(context) ? [for (final follow in context.read<WebSourceFollowsStore>().state) follow.id] : const [];

  @override
  Future<List<InterleavedItem>> interleavedPosts(BuildContext context, List<String> ids) =>
      loadWebSourceInterleaved(context, ids);

  @override
  Future<void> resetPreferences(BasePrefService prefs) async {
    await prefs.set(optionPluginWebSourcesInstalled, '[]');
    await prefs.set(optionPluginWebSourcesFollows, '[]');
    await prefs.set(optionPluginWebSourcesInHomeFeed, false);
    await prefs.set(optionPluginWebSourcesCheckedAt, '');
  }

  @override
  Future<void> forgetLoadedData(BuildContext context) => reloadFromDatabase(context);
}

/// A followed list's screen, or nothing to read when its source is gone.
class _FollowedList extends StatelessWidget {
  final String subscriptionId;

  const _FollowedList({super.key, required this.subscriptionId});

  @override
  Widget build(BuildContext context) {
    final parts = webSourceFollowParts(subscriptionId.substring('$pluginIdWebSources:'.length));
    final installed = parts == null ? null : context.read<WebSourcesStore>().installed(parts.sourceId);
    if (installed == null) {
      return Scaffold(
        appBar: AppBar(),
        body: EmptyPane(icon: Icons.inbox_outlined, message: L10n.of(context).plugin_websources_list_empty),
      );
    }
    return WebSourceScreen(source: installed.source, listId: parts!.listId);
  }
}

/// The newest posts of the followed lists [ids] names, as dated cards a
/// timeline can slot between its chains. One list that cannot be read does
/// not empty the rest.
Future<List<InterleavedItem>> loadWebSourceInterleaved(BuildContext context, List<String> ids) async {
  final sources = context.read<WebSourcesStore>();
  final client = context.read<WebSourceClient>();
  final lists = [for (final id in ids) ?_followedList(sources, id)];
  Object? failure;
  final pages = await Future.wait(
    lists.map((followed) async {
      try {
        return (source: followed.source, items: await _firstPage(client, followed.source, followed.list));
      } on Exception catch (error) {
        failure ??= error;
        return null;
      }
    }),
  );
  final result = [
    for (final page in pages.nonNulls)
      for (final item in page.items.take(kWebSourceInterleavedPageSize))
        if (item.date case final date?) _interleaved(page.source, item, date),
  ];
  if (failure != null) throw PartialFeedFailure(result, failure!);
  return result;
}

({WebSourceDefinition source, WebSourceList list})? _followedList(WebSourcesStore sources, String id) {
  final prefix = '$pluginIdWebSources:';
  if (!id.startsWith(prefix)) return null;
  final parts = webSourceFollowParts(id.substring(prefix.length));
  final source = parts == null ? null : sources.installed(parts.sourceId)?.source;
  final list = parts == null ? null : source?.list(parts.listId);
  return source == null || list == null ? null : (source: source, list: list);
}

Future<List<WebSourceItem>> _firstPage(WebSourceClient client, WebSourceDefinition source, WebSourceList list) async {
  final url = webSourceListUrl(list);
  if (url == null) return const [];
  return webSourcePage(source, list, await client.page(source, url, list.type), url, page: list.firstPage).items;
}

InterleavedItem _interleaved(WebSourceDefinition source, WebSourceItem item, DateTime date) =>
    provenanceInterleavedItem(
      date: date,
      pluginId: pluginIdWebSources,
      id: '$pluginIdWebSources:${item.key}',
      linkUrl: item.link,
      snapshot: {
        'xtaPlugin': 'link',
        'source': pluginIdWebSources,
        'url': item.link ?? item.discussion ?? source.homepage.toString(),
        'author': item.author ?? source.name,
        'text': [?item.title, ?item.text].join('\n'),
        'images': item.pictures,
      },
      build: (_) => WebSourceItemCard(source: source, item: item, showSource: true),
    );
