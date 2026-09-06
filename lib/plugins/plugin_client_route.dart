import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:xta/plugins/plugin_session.dart';
import 'package:xta/plugins/plugin.dart';
import 'package:xta/ui/reader_chrome.dart';

/// Opens the existing full client without changing pins or navigation settings.
Future<void> openPluginClient(BuildContext context, XtaPlugin plugin) {
  final session = context.read<PluginSessionStore?>();
  return Navigator.push<void>(
    context,
    MaterialPageRoute(
      builder: (_) => Provider<PluginSessionStore?>.value(
        value: session,
        child: _PluginClientPage(plugin: plugin),
      ),
    ),
  );
}

class _PluginClientPage extends StatefulWidget {
  final XtaPlugin plugin;

  const _PluginClientPage({required this.plugin});

  @override
  State<_PluginClientPage> createState() => _PluginClientPageState();
}

class _PluginClientPageState extends State<_PluginClientPage> {
  final _scroll = ScrollController();

  @override
  void dispose() {
    _scroll.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) =>
      XtaSystemBars(child: widget.plugin.homeScreen(scrollController: _scroll) ?? const SizedBox.shrink());
}
