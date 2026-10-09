import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/home/_feed.dart';
import 'package:xta/home/feed_strip_store.dart';
import 'package:xta/intro/intro_illustrations.dart';
import 'package:xta/intro/intro_page_frame.dart';
import 'package:xta/plugins/plugin.dart';
import 'package:xta/plugins/plugin_brand.dart';
import 'package:xta/plugins/plugin_marks.dart';
import 'package:xta/plugins/plugin_registry.dart';
import 'package:xta/plugins/rss/rss_models.dart';
import 'package:xta/settings/_plugin_store.dart';
import 'package:xta/tweet/tweet_chrome.dart';
import 'package:xta/ui/motion.dart';
import 'package:xta/ui/x_look_theme.dart';

/// Plugins the intro offers: everything that can sit on Home and that the
/// store lists openly. Private plugins stay behind the store's own switch.
List<XtaPlugin> introSourcePlugins() =>
    builtInPlugins.where((plugin) => plugin.supportsFeedStrip && !plugin.isPrivate).toList(growable: false);

/// Whether a plugin shows nothing until it is configured.
bool pluginNeedsSetup(XtaPlugin plugin, BasePrefService prefs) => switch (plugin.id) {
  pluginIdMastodon => (prefs.get<String>(optionPluginMastodonInstance) ?? '').trim().isEmpty,
  pluginIdRss => RssFeed.listFromPrefs(prefs.get(optionPluginRssFeeds)).isEmpty,
  _ => false,
};

/// Pick your sources: toggling a tile enables the plugin and pins it on Home;
/// off takes it off Home and disables it, keeping whatever it stored.
class IntroSourcesPage extends StatelessWidget {
  final int page;
  final VoidCallback? onFinish;

  const IntroSourcesPage({super.key, required this.page, required this.onFinish});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final prefs = PrefService.of(context);
    return ScopedBuilder<FeedStripStore, List<String>>(
      store: context.read<FeedStripStore>(),
      onState: (context, pinned) => IntroPageFrame(
        page: page,
        title: l10n.intro_sources_title,
        body: l10n.intro_sources_body,
        card: IntroSourcesGrid(prefs: prefs, pinned: pinned),
        cardFitsContent: true,
        footer: IntroButtonRow(
          primary: IntroAction(l10n.intro_start_reading, onFinish, key: const ValueKey('intro-start-reading')),
        ),
      ),
    );
  }
}

/// The tiles, plus a "Set up" button for every chosen plugin that still needs
/// configuring. The grid is laid out in full; the page scrolls around it.
class IntroSourcesGrid extends StatelessWidget {
  final BasePrefService prefs;
  final List<String> pinned;

  const IntroSourcesGrid({super.key, required this.prefs, required this.pinned});

  bool _selected(XtaPlugin plugin) => pinned.contains(plugin.id) && plugin.isEnabled(prefs);

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final plugins = introSourcePlugins();
    final needsSetup = plugins.where((plugin) => _selected(plugin) && pluginNeedsSetup(plugin, prefs));
    return Semantics(
      container: true,
      label: l10n.intro_illustration_sources,
      explicitChildNodes: true,
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          _grid(context, plugins),
          for (final plugin in needsSetup)
            TextButton.icon(
              key: ValueKey('intro-set-up-${plugin.id}'),
              onPressed: () => _setUp(context, plugin),
              style: TextButton.styleFrom(minimumSize: const Size(0, kTweetTouchTarget)),
              icon: const Icon(Icons.tune, size: kTweetActionIconSize),
              label: Text(l10n.intro_set_up_plugin(plugin.title(context))),
            ),
        ],
      ),
    );
  }

  List<Widget> _tiles(BuildContext context, List<XtaPlugin> plugins) {
    final l10n = L10n.of(context);
    final tokens = introTokens(context);
    final on = Theme.of(context).colorScheme.primary;
    return [
      IntroSourceTile(
        id: FeedTab.following.id,
        title: l10n.following,
        mark: markIcon(Icons.people_outline, size: 28, color: tokens.onBackground),
        brand: on,
        selected: true,
      ),
      IntroSourceTile(
        id: coreXPlugin.id,
        title: coreXPlugin.title(context),
        mark: pluginMark(coreXPlugin, size: 28),
        brand: on,
        selected: true,
      ),
      for (final plugin in plugins)
        IntroSourceTile(
          id: plugin.id,
          title: plugin.title(context),
          mark: pluginMark(plugin, size: 28, color: readableBrandColor(context, plugin.brandColor)),
          brand: plugin.brandColor,
          selected: _selected(plugin),
          onTap: () => _toggle(context, plugin),
        ),
      IntroSourceTile(
        id: 'store',
        title: l10n.plugin_store,
        mark: markIcon(Icons.extension_outlined, size: 28, color: tokens.secondary),
        brand: on,
        onTap: () => _openStore(context),
      ),
    ];
  }

  Widget _grid(BuildContext context, List<XtaPlugin> plugins) {
    final tiles = _tiles(context, plugins);
    return LayoutBuilder(
      builder: (context, constraints) {
        final scaler = MediaQuery.textScalerOf(context);
        // Three across at 360 dp, two at 320 dp or once large text would ellipsise every title.
        final columns = constraints.maxWidth / scaler.scale(1) < 260 ? 2 : 3;
        final tileWidth = (constraints.maxWidth - (columns - 1) * kTweetSpace2) / columns;
        final tileHeight = IntroSourceTile.markSpace + scaler.scale(IntroSourceTile.titleSpace);
        return GridView.count(
          crossAxisCount: columns,
          mainAxisSpacing: kTweetSpace2,
          crossAxisSpacing: kTweetSpace2,
          childAspectRatio: tileWidth / tileHeight,
          padding: EdgeInsets.zero,
          shrinkWrap: true,
          physics: const NeverScrollableScrollPhysics(),
          children: tiles,
        );
      },
    );
  }

  Future<void> _toggle(BuildContext context, XtaPlugin plugin) async {
    final strip = context.read<FeedStripStore>();
    if (_selected(plugin)) {
      await strip.forget(plugin.id);
      await plugin.setEnabled(prefs, false);
    } else {
      await plugin.setEnabled(prefs, true);
      await strip.pin(plugin.id);
    }
  }

  Future<void> _setUp(BuildContext context, XtaPlugin plugin) async {
    final screen = plugin.settingsScreen(context);
    if (screen == null) return;
    await Navigator.push(context, MaterialPageRoute<void>(builder: (_) => screen));
  }

  Future<void> _openStore(BuildContext context) =>
      Navigator.push(context, MaterialPageRoute<void>(builder: (_) => const SettingsPluginStoreFragment()));
}

/// Mark over title. A chosen tile fills with the brand tint, rings itself in
/// the brand and wears a check; one that is always on ([selected] without
/// [onTap]) keeps the tint and the check but only a hairline, so it reads as
/// included rather than as a choice; [selected] null is a plain button, like
/// the store tile.
class IntroSourceTile extends StatelessWidget {
  /// Height not taken by the title: the mark, its gap and the padding.
  static const double markSpace = 36;

  /// Two lines of title at 1.0× text, so "Hacker News" and the longer
  /// locales wrap instead of ellipsising; it grows with the reader's text size.
  static const double titleSpace = 40;
  static const double titleInset = 2;

  final String id;
  final String title;
  final Widget mark;
  final Color brand;
  final bool? selected;
  final VoidCallback? onTap;

  const IntroSourceTile({
    super.key,
    required this.id,
    required this.title,
    required this.mark,
    required this.brand,
    this.selected,
    this.onTap,
  });

  bool get _chosen => selected == true;

  bool get _fixed => _chosen && onTap == null;

  @override
  Widget build(BuildContext context) {
    final tokens = introTokens(context);
    final dark = Theme.of(context).brightness == Brightness.dark;
    final ring = readableBrandColor(context, brand);
    final fill = _chosen ? brand.withValues(alpha: dark ? 0.22 : 0.14) : xLookFloatingSurface(tokens);
    final hairline = introHairline(tokens, fill);
    return Semantics(
      button: onTap != null,
      selected: selected,
      label: title,
      child: ExcludeSemantics(
        child: AnimatedContainer(
          duration: xtaMotionDuration(context, kXtaMotionFast),
          decoration: BoxDecoration(
            color: fill,
            borderRadius: BorderRadius.circular(kTweetSpace3),
            border: Border.all(color: _chosen && !_fixed ? ring : hairline, width: _chosen && !_fixed ? 2 : 1),
          ),
          child: Material(
            type: MaterialType.transparency,
            child: InkWell(
              key: ValueKey('intro-source-$id'),
              onTap: onTap,
              borderRadius: BorderRadius.circular(kTweetSpace3),
              child: Stack(
                children: [
                  Center(child: _content(context, tokens)),
                  if (_chosen)
                    PositionedDirectional(
                      top: kTweetSpace1,
                      end: kTweetSpace1,
                      child: IntroCheckBadge(size: 16, color: ring),
                    ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _content(BuildContext context, XLookTokens tokens) => Padding(
    padding: const EdgeInsets.symmetric(horizontal: titleInset),
    child: Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        mark,
        const SizedBox(height: kTweetSpace1),
        Text(
          title,
          maxLines: 2,
          overflow: TextOverflow.ellipsis,
          textAlign: TextAlign.center,
          style: Theme.of(context).textTheme.bodySmall!.copyWith(color: tokens.onBackground),
        ),
      ],
    ),
  );
}
