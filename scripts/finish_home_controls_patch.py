#!/usr/bin/env python3
"""One-time, hash-checked PR304 edits; only UI, tests, and localization."""
import hashlib
import json
from pathlib import Path

EXPECTED = {
    'lib/home/_feed.dart': '1f3f7340877fd1b4e32cd402a57ee0b59acd40e8',
    'lib/plugins/plugin_home_dock.dart': '2bdab664d355275de0d938567d21d1f519de4814',
    'lib/plugins/substack/substack_screen.dart': '15106e6e2c56f8919e2eb1ce8b518a826c484c93',
    'lib/plugins/substack/substack_reading_toolbar.dart': 'd08632f50154b21625d43bdc0e2b2a886e31a981',
}
for name, expected in EXPECTED.items():
    data = Path(name).read_bytes()
    actual = hashlib.sha1(f'blob {len(data)}\0'.encode() + data).hexdigest()
    if actual != expected:
        raise SystemExit(f'Refusing changed source: {name}: {actual}')

def replace(name, before, after):
    path = Path(name)
    source = path.read_text()
    if source.count(before) != 1:
        raise SystemExit(f'Expected one exact match in {name}: {before[:80]}')
    path.write_text(source.replace(before, after, 1))

bar = 'lib/plugins/substack/substack_reading_toolbar.dart'
replace(bar, '  final ValueChanged<SubstackFeedFilter>? onFilter;\n\n  const SubstackReadingToolbar',
    '  final ValueChanged<SubstackFeedFilter>? onFilter;\n  final bool publishToHome;\n\n  const SubstackReadingToolbar')
replace(bar, '    this.onFilter,\n', '    this.onFilter,\n    this.publishToHome = false,\n')
replace(bar, '  Widget build(BuildContext context) {\n    final l10n = L10n.of(context);\n    final options',
    '  Widget build(BuildContext context) {\n    if (PluginHomeDockScope.maybeOf(context) != null && !publishToHome) return const SizedBox.shrink();\n    final l10n = L10n.of(context);\n    final options')
replace(bar, 'class _SheetHeading extends StatelessWidget {', '''/// The published actions outlive recycled list items, but not their active pane.
class SubstackHomeReadingDock extends StatelessWidget {
  final String slot;
  final ValueChanged<SubstackFeedFilter>? onFilter;
  const SubstackHomeReadingDock({super.key, required this.slot, this.onFilter});

  @override
  Widget build(BuildContext context) {
    if (PluginHomeDockScope.maybeOf(context) == null) return const SizedBox.shrink();
    final feed = context.read<SubstackFeedStore>();
    return TripleBuilder<SubstackPublicationsStore, List<SubstackPublication>>(
      store: context.read<SubstackPublicationsStore>(),
      builder: (context, pubs) => pubs.state.isEmpty ? const SizedBox.shrink() :
        TripleBuilder<SubstackFeedStore, SubstackFeedSnapshot>(
          store: feed,
          builder: (context, _) => TripleBuilder<SubstackReadStore, Set<String>>(
            store: context.read<SubstackReadStore>(),
            builder: (context, read) => SubstackReadingToolbar(
              key: ValueKey(slot),
              slot: slot,
              feed: feed,
              publications: pubs.state,
              readIds: read.state,
              onFilter: onFilter,
              publishToHome: true,
            ),
          ),
        ),
    );
  }
}

class _SheetHeading extends StatelessWidget {''')
replace('lib/plugins/substack/substack_screen.dart', '                const Divider(height: 1),\n                Expanded(', '''                if (_tab < 2)
                  SubstackHomeReadingDock(
                    key: ValueKey('substack-reading-$_tab'),
                    slot: _tab == 0 ? 'home' : 'inbox',
                    onFilter: _tab == 0 ? _setFilter : null,
                  ),
                const Divider(height: 1),
                Expanded(''')

dock = 'lib/plugins/plugin_home_dock.dart'
replace(dock, "import 'dart:math' as math;", "import 'dart:math' as math;\nimport 'package:xta/plugins/plugin_home_reading_controls.dart';")
replace(dock, '  bool _closed = false;\n', '  bool _closed = false;\n  final controls = HomeReadingControlsStore();\n')
replace(dock, '    _closed = true;\n    await super.destroy();', '    _closed = true;\n    await controls.destroy();\n    await super.destroy();')
replace(dock, "      onSelected: (value) {\n        if (value == 'xta:open-client') {", """      onOpened: () => scope?.store.controls.reveal(),
      onSelected: (value) async {
        if (!context.mounted || scope?.source != PluginHomeDockScope.maybeOf(context)?.source) return;
        if (value == 'xta:pin-controls') {
          final controls = scope?.store.controls;
          if (controls != null) await controls.setPinned(!controls.state.pinned);
        } else if (value == 'xta:open-client') {""")
replace(dock, "        if (scope != null) ...[\n          const PopupMenuDivider(),\n          PopupMenuItem(", """        if (scope != null) ...[
          const PopupMenuDivider(),
          CheckedPopupMenuItem<String>(
            key: const ValueKey('home-pin-controls'),
            value: 'xta:pin-controls',
            checked: scope.store.controls.state.pinned,
            child: Text(L10n.of(context).home_keep_controls_visible),
          ),
          PopupMenuItem(""")

home = 'lib/home/_feed.dart'
replace(home, "import 'package:xta/plugins/plugin_home_dock.dart';", "import 'package:xta/plugins/plugin_home_dock.dart';\nimport 'package:xta/plugins/plugin_home_reading_controls.dart';")
replace(home, '        bodyBuilder: (context) => Column(\n          children: [\n            if (docked)', '''        bodyBuilder: (context) => HomeReadingViewport(
          store: _dock.controls,
          source: tab.id,
          enabled: docked,
          prefs: prefs,
          controls: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            if (docked)''')
replace(home, '''            Expanded(
              child: NotificationListener<ScrollMetricsNotification>(
                onNotification: (notification) {
                  if (notification.depth == 0 && notification.metrics.axis == Axis.vertical) {
                    _queueControlsUpdate();
                  }
                  return false;
                },
                child: _timelineBody(tab, prefs),
              ),
            ),
          ],
        ),''', '''          ],
          ),
          child: NotificationListener<ScrollMetricsNotification>(
            onNotification: (notification) {
              if (notification.depth == 0 && notification.metrics.axis == Axis.vertical) {
                _queueControlsUpdate();
              }
              return false;
            },
            child: _timelineBody(tab, prefs),
          ),
        ),''')

translations = {
    'en': 'Keep controls visible',
    'de': 'Bedienelemente immer anzeigen',
    'ar': 'إبقاء عناصر التحكم ظاهرة',
    'be': 'Заўсёды паказваць элементы кіравання',
    'be_Latn': 'Zaŭsiody pakazvać elementy kiravannia',
    'ca': 'Mantén els controls visibles',
    'cs': 'Vždy zobrazovat ovládací prvky',
    'eo': 'Teni la regilojn videblaj',
    'es': 'Mantener los controles visibles',
    'et': 'Hoia juhtnupud nähtaval',
    'eu': 'Mantendu kontrolak ikusgai',
    'fr': 'Garder les commandes visibles',
    'hi': 'कंट्रोल हमेशा दिखाएँ',
    'id': 'Selalu tampilkan kontrol',
    'it': 'Mantieni visibili i controlli',
    'ja': '操作ボタンを常に表示',
    'ko': '컨트롤 항상 표시',
    'nb_NO': 'Hold kontrollene synlige',
    'nl': 'Bedieningselementen zichtbaar houden',
    'pl': 'Zawsze pokazuj elementy sterujące',
    'pt': 'Manter os controlos visíveis',
    'pt_BR': 'Manter os controles visíveis',
    'ro': 'Păstrează comenzile vizibile',
    'ru': 'Всегда показывать элементы управления',
    'tr': 'Kontrolleri görünür tut',
    'uk': 'Завжди показувати елементи керування',
    'vi': 'Luôn hiển thị các nút điều khiển',
    'zh_Hans': '始终显示控件',
    'zh_Hant': '一律顯示控制項',
}
paths = sorted(Path('lib/l10n').glob('intl_*.arb'), key=lambda p: (p.stem != 'intl_en', p.name))
if {p.stem[5:] for p in paths} != set(translations):
    raise SystemExit('Supported locales changed; do not leave untranslated UI labels')
for path in paths:
    text = path.read_text()
    if 'home_keep_controls_visible' in json.loads(text):
        raise SystemExit(f'Key already exists: {path}')
    at = text.rfind('}')
    value = json.dumps(translations[path.stem[5:]], ensure_ascii=False)
    path.write_text(text[:at].rstrip() + ',\n  "home_keep_controls_visible": ' + value + '\n' + text[at:])

# The existing verification workflow already runs the full test suite. Do not
# update any retained workflow: its write was refused by the job token.
print('Applied exact-source UI patches and complete pin-label translations. No client/database/dependency or retained workflow changes.')
