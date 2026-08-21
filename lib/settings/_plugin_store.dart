import 'package:flutter/material.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/home/feed_strip_store.dart';
import 'package:xta/home/home_model.dart';
import 'package:xta/home/network_recents_store.dart';
import 'package:xta/plugins/plugin.dart';
import 'package:xta/plugins/plugin_catalogue.dart';
import 'package:xta/plugins/plugin_brand.dart';
import 'package:xta/plugins/plugin_category.dart';
import 'package:xta/plugins/plugin_registry.dart';
import 'package:xta/settings/_plugin_row.dart';

/// What the reader has installed, then what is on offer grouped by purpose.
///
/// The offer comes from a document published in the app's repository, so a
/// plugin can be added or withdrawn without a release. It can only ever narrow
/// what this build already contains, and it never withdraws something already
/// installed.
class SettingsPluginStoreFragment extends StatefulWidget {
  const SettingsPluginStoreFragment({super.key});

  @override
  State<SettingsPluginStoreFragment> createState() =>
      _SettingsPluginStoreFragmentState();
}

class _SettingsPluginStoreFragmentState
    extends State<SettingsPluginStoreFragment> {
  late final PluginCatalogue _catalogue = PluginCatalogue(
    PrefService.of(context, listen: false),
  );

  List<String> _offered = const [];
  bool _loading = true;
  bool _unreachable = false;
  String _query = '';

  @override
  void initState() {
    super.initState();
    _offered = _offeredFromCache();
    WidgetsBinding.instance.addPostFrameCallback((_) => _refresh());
  }

  List<String> _offeredFromCache() {
    final builtIn = builtInPlugins.map((p) => p.id);
    if (!_catalogue.hasCache) {
      return builtIn.toList();
    }
    final body =
        PrefService.of(
          context,
          listen: false,
        ).get<String>(optionPluginCatalogueCache) ??
        '';
    return offeredPluginIds(
      builtInIds: builtIn,
      catalogueOffered: _catalogue.cached(),
      catalogueMentioned: parsePluginCatalogueMentioned(body),
    );
  }

  Future<void> _refresh() async {
    if (!mounted) return;
    setState(() => _loading = true);
    final fetched = await _catalogue.fetch();
    if (!mounted) return;

    setState(() {
      _loading = false;
      _unreachable = fetched == null;
      if (fetched != null) {
        _offered = _offeredFromCache();
      }
    });
  }

  /// Visible in the store: installed always, plus anything the catalogue still
  /// offers. Installed-but-withdrawn plugins stay so the reader can uninstall.
  /// Private plugins only appear when show-private is on (or already installed).
  List<XtaPlugin> get _listed {
    final prefs = PrefService.of(context, listen: false);
    final showPrivate = prefs.get<bool>(optionPluginStoreShowPrivate) == true;
    return builtInPlugins
        .where(
          (plugin) =>
              plugin.isEnabled(prefs) ||
              _offered.contains(plugin.id) ||
              (plugin.isPrivate && showPrivate),
        )
        .where(
          (plugin) => pluginMatchesStoreQuery(
            query: _query,
            id: plugin.id,
            title: plugin.title(context),
            description: plugin.description(context),
            category: plugin.category.label(context),
          ),
        )
        .toList();
  }

  Future<void> _install(XtaPlugin plugin) async {
    final prefs = PrefService.of(context, listen: false);
    await plugin.setEnabled(prefs, true);
    if (!mounted) return;
    await pinPluginOnFeedStripIn(context, plugin.id);
    if (!mounted) return;
    await context.read<HomeModel>().loadPages();
    if (!mounted) return;
    await context.read<FeedStripStore>().seedEnabled();
    if (mounted) setState(() {});
  }

  Future<void> _uninstall(XtaPlugin plugin) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) {
        final l10n = L10n.of(dialogContext);
        return AlertDialog(
          title: Text(
            l10n.plugin_uninstall_confirm(plugin.title(dialogContext)),
          ),
          content: Text(l10n.plugin_uninstall_confirm_detail),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(dialogContext, false),
              child: Text(l10n.cancel),
            ),
            TextButton(
              onPressed: () => Navigator.pop(dialogContext, true),
              child: Text(l10n.plugin_uninstall),
            ),
          ],
        );
      },
    );

    if (confirmed != true || !mounted) return;

    final strip = context.read<FeedStripStore>();
    final home = context.read<HomeModel>();
    NetworkRecentsStore? recents;
    try {
      recents = context.read<NetworkRecentsStore>();
    } on ProviderNotFoundException {
      recents = null;
    }

    await plugin.uninstall(context);
    if (!mounted) return;
    await strip.forget(plugin.id);
    await recents?.forget(plugin.id);
    if (!mounted) return;
    await home.loadPages();
    if (mounted) setState(() {});
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final prefs = PrefService.of(context, listen: false);
    final sections = pluginStoreSections(
      _listed,
      isInstalled: (plugin) => plugin.isEnabled(prefs),
    );

    return Scaffold(
      appBar: AppBar(
        title: Text(l10n.plugin_store),
        actions: [
          PopupMenuButton<String>(
            onSelected: (value) async {
              if (value == 'private') {
                final next =
                    !(prefs.get<bool>(optionPluginStoreShowPrivate) == true);
                await prefs.set(optionPluginStoreShowPrivate, next);
                if (mounted) setState(() {});
              }
            },
            itemBuilder: (context) => [
              CheckedPopupMenuItem(
                value: 'private',
                checked: prefs.get<bool>(optionPluginStoreShowPrivate) == true,
                child: Text(l10n.plugin_store_show_private),
              ),
            ],
          ),
          IconButton(
            tooltip: l10n.retry,
            icon: const Icon(Icons.refresh),
            onPressed: _loading ? null : _refresh,
          ),
        ],
      ),
      body: RefreshIndicator(
        onRefresh: _refresh,
        child: ListView(
          physics: const AlwaysScrollableScrollPhysics(),
          padding: const EdgeInsets.only(bottom: 24),
          children: [
            if (_loading) const LinearProgressIndicator(minHeight: 2),
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 8, 16, 4),
              child: SearchBar(
                hintText: l10n.plugin_store_search,
                leading: const Icon(Icons.search),
                onChanged: (value) => setState(() => _query = value),
              ),
            ),
            if (_unreachable)
              ListTile(
                dense: true,
                leading: const Icon(Icons.cloud_off),
                title: Text(l10n.plugin_catalogue_unavailable),
                subtitle: _catalogue.hasCache
                    ? Text(l10n.plugin_catalogue_cached)
                    : null,
              ),
            if (sections.installed.isNotEmpty) ...[
              _header(context, l10n.plugin_installed),
              for (final plugin in sections.installed)
                InstalledPluginRow(
                  plugin: plugin,
                  onUninstall: () => _uninstall(plugin),
                  onChanged: () => setState(() {}),
                ),
            ],
            if (sections.availableByCategory.isNotEmpty)
              PluginAvailableSection(
                groups: sections.availableByCategory,
                onInstall: _install,
              ),
          ],
        ),
      ),
    );
  }

  Widget _header(BuildContext context, String text, {bool nested = false}) {
    final theme = Theme.of(context);
    return Padding(
      padding: nested
          ? const EdgeInsets.fromLTRB(16, 10, 16, 2)
          : const EdgeInsets.fromLTRB(16, 16, 16, 4),
      child: Text(
        text,
        style: nested
            ? theme.textTheme.labelLarge!.copyWith(
                color: theme.colorScheme.onSurfaceVariant,
                fontWeight: FontWeight.w600,
              )
            : theme.textTheme.titleSmall!.copyWith(
                color: theme.colorScheme.primary,
                fontWeight: FontWeight.w700,
              ),
      ),
    );
  }
}

/// Catalogue offers, folded so Installed stays the first thing you see.
class PluginAvailableSection extends StatelessWidget {
  final List<({PluginCategory category, List<XtaPlugin> plugins})> groups;
  final Future<void> Function(XtaPlugin plugin) onInstall;

  const PluginAvailableSection({
    super.key,
    required this.groups,
    required this.onInstall,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final l10n = L10n.of(context);
    return ExpansionTile(
      key: const Key('plugin-available'),
      initiallyExpanded: true,
      tilePadding: const EdgeInsets.symmetric(horizontal: 16),
      title: Text(
        l10n.plugin_available,
        style: theme.textTheme.titleSmall!.copyWith(
          color: theme.colorScheme.primary,
          fontWeight: FontWeight.w700,
        ),
      ),
      children: [
        for (final group in groups) ...[
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 10, 16, 2),
            child: Align(
              alignment: Alignment.centerLeft,
              child: Text(
                group.category.label(context),
                style: theme.textTheme.labelLarge!.copyWith(
                  color: theme.colorScheme.onSurfaceVariant,
                  fontWeight: FontWeight.w600,
                ),
              ),
            ),
          ),
          for (final plugin in group.plugins)
            AvailablePluginRow(
              plugin: plugin,
              onInstall: () => onInstall(plugin),
            ),
        ],
      ],
    );
  }
}
