import 'package:flutter/material.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/utils/browsers.dart';

/// Sentinel for the in-app browser row. Never stored as a package name.
const _inApp = '__in_app__';

/// Where a link goes: in the app, the system default, or a named browser.
///
/// The in-app switch used to sit above a dropdown that turned grey the moment
/// that switch was on — which is the default — so picking "just this browser"
/// looked like it was not there. One tile, always enabled, is the choice.
class BrowserPickerTile extends StatefulWidget {
  const BrowserPickerTile({super.key});

  @override
  State<BrowserPickerTile> createState() => _BrowserPickerTileState();
}

class _BrowserPickerTileState extends State<BrowserPickerTile> {
  List<InstalledBrowser>? _browsers;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    final browsers = await installedBrowsers();
    if (mounted) {
      setState(() => _browsers = browsers);
    }
  }

  String _selected(BasePrefService prefs, List<InstalledBrowser> browsers) {
    if (prefs.get(optionOpenLinksInEmbeddedBrowser) == true) {
      return _inApp;
    }
    final stored = prefs.get<String>(optionExternalBrowser) ?? systemDefaultBrowser;
    if (stored.isEmpty || !browsers.any((b) => b.package == stored)) {
      return systemDefaultBrowser;
    }
    return stored;
  }

  String _labelFor(String value, L10n l10n, List<InstalledBrowser> browsers) {
    if (value == _inApp) {
      return l10n.option_open_links_in_embedded_browser_label;
    }
    if (value.isEmpty) {
      return l10n.option_external_browser_system;
    }
    for (final browser in browsers) {
      if (browser.package == value) return browser.label;
    }
    return value;
  }

  Future<void> _choose(BasePrefService prefs, String value) async {
    if (value == _inApp) {
      await prefs.set(optionOpenLinksInEmbeddedBrowser, true);
    } else {
      await prefs.set(optionOpenLinksInEmbeddedBrowser, false);
      await prefs.set(optionExternalBrowser, value);
    }
    if (mounted) setState(() {});
  }

  Future<void> _openSheet(
    BuildContext context,
    BasePrefService prefs,
    List<InstalledBrowser> browsers,
  ) async {
    final l10n = L10n.of(context);
    final selected = _selected(prefs, browsers);

    await showModalBottomSheet<void>(
      context: context,
      showDragHandle: true,
      isScrollControlled: true,
      builder: (sheetContext) {
        final maxHeight = MediaQuery.sizeOf(sheetContext).height * 0.7;
        void pick(String? value) {
          if (value == null) return;
          Navigator.pop(sheetContext);
          _choose(prefs, value);
        }

        return SafeArea(
          child: ConstrainedBox(
            constraints: BoxConstraints(maxHeight: maxHeight),
            child: ListView(
              shrinkWrap: true,
              children: [
                Padding(
                  padding: const EdgeInsets.fromLTRB(16, 4, 16, 8),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        l10n.option_external_browser_label,
                        style: Theme.of(sheetContext).textTheme.titleMedium,
                      ),
                      const SizedBox(height: 4),
                      Text(
                        l10n.option_external_browser_description,
                        style: Theme.of(sheetContext).textTheme.bodySmall,
                      ),
                    ],
                  ),
                ),
                RadioListTile<String>(
                  value: _inApp,
                  groupValue: selected,
                  title: Text(l10n.option_open_links_in_embedded_browser_label),
                  subtitle: Text(l10n.option_open_links_in_embedded_browser_description),
                  onChanged: pick,
                ),
                RadioListTile<String>(
                  value: systemDefaultBrowser,
                  groupValue: selected,
                  title: Text(l10n.option_external_browser_system),
                  onChanged: pick,
                ),
                for (final browser in browsers)
                  RadioListTile<String>(
                    value: browser.package,
                    groupValue: selected,
                    title: Text(browser.label),
                    onChanged: pick,
                  ),
              ],
            ),
          ),
        );
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final prefs = PrefService.of(context);
    final browsers = _browsers ?? const <InstalledBrowser>[];
    final selected = _selected(prefs, browsers);

    return ListTile(
      title: Text(l10n.option_external_browser_label),
      subtitle: Text(_labelFor(selected, l10n, browsers)),
      trailing: const Icon(Icons.unfold_more),
      onTap: () => _openSheet(context, prefs, browsers),
    );
  }
}
