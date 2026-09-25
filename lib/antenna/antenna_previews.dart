import 'package:flutter/material.dart';
import 'package:flutter/widget_previews.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:xta/antenna/antenna_widgets.dart';
import 'package:xta/generated/l10n.dart';

@Preview(name: 'Antenna tile - light', group: 'Discover', size: Size(720, 180), brightness: Brightness.light)
@Preview(name: 'Antenna tile - dark', group: 'Discover', size: Size(720, 180), brightness: Brightness.dark)
Widget antennaTilePreview() => const _AntennaPreviewApp(child: _AntennaTilePreview());

@Preview(name: 'Antenna editor - 200% text', group: 'Discover', size: Size(360, 640))
Widget antennaEditorPreview() => const _AntennaPreviewApp(textScale: 2, child: _AntennaEditorPreview());

class _AntennaPreviewApp extends StatelessWidget {
  final Widget child;
  final double textScale;

  const _AntennaPreviewApp({required this.child, this.textScale = 1});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      localizationsDelegates: const [
        L10n.delegate,
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
      ],
      supportedLocales: L10n.delegate.supportedLocales,
      theme: ThemeData(colorSchemeSeed: Colors.blue),
      darkTheme: ThemeData(brightness: Brightness.dark, colorSchemeSeed: Colors.blue),
      themeMode: ThemeMode.system,
      home: Builder(
        builder: (context) => MediaQuery(
          data: MediaQuery.of(context).copyWith(textScaler: TextScaler.linear(textScale)),
          child: Scaffold(body: child),
        ),
      ),
    );
  }
}

class _AntennaTilePreview extends StatelessWidget {
  const _AntennaTilePreview();

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return AntennaTile(
      name: l10n.antenna_title,
      terms: l10n.antenna_include,
      scopeLabel: l10n.antenna_scope_following,
      settingsLabel: l10n.settings,
      deleteLabel: l10n.delete,
      onOpen: () {},
      onSettings: () {},
      onDelete: () {},
    );
  }
}

class _AntennaEditorPreview extends StatefulWidget {
  const _AntennaEditorPreview();

  @override
  State<_AntennaEditorPreview> createState() => _AntennaEditorPreviewState();
}

class _AntennaEditorPreviewState extends State<_AntennaEditorPreview> {
  final _name = TextEditingController();
  final _include = TextEditingController();
  final _exclude = TextEditingController();
  String _scope = 'search';

  @override
  void dispose() {
    _name.dispose();
    _include.dispose();
    _exclude.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return AntennaEditorContent(
      title: l10n.antenna_new,
      nameLabel: l10n.antenna_name,
      includeLabel: l10n.antenna_include,
      excludeLabel: l10n.antenna_exclude,
      searchScopeLabel: l10n.antenna_scope_search,
      followingScopeLabel: l10n.antenna_scope_following,
      saveLabel: l10n.ok,
      nameController: _name,
      includeController: _include,
      excludeController: _exclude,
      scope: _scope,
      onScopeChanged: (value) => setState(() => _scope = value),
      onSave: null,
    );
  }
}
