import 'package:flutter/material.dart';
import 'package:xta/plugins/plugin.dart';
import 'package:xta/plugins/plugin_compact_header.dart';
import 'package:xta/plugins/plugin_home_chrome.dart';
import 'package:xta/plugins/plugin_home_dock.dart';

/// Microblogs use the same single row in Home and in their full reader.
/// Only the header listens to dock changes, leaving feed state and scroll intact.
class MicroblogReaderShell extends StatefulWidget {
  final XtaPlugin plugin;
  final WidgetBuilder builder;

  const MicroblogReaderShell({super.key, required this.plugin, required this.builder});

  @override
  State<MicroblogReaderShell> createState() => _MicroblogReaderShellState();
}

class _MicroblogReaderShellState extends State<MicroblogReaderShell> {
  final _dock = PluginHomeDockStore();

  @override
  void dispose() {
    _dock.destroy();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    if (PluginEmbedded.maybeOf(context) || PluginHomeDockScope.maybeOf(context) != null) {
      return widget.builder(context);
    }
    return PluginHomeDockScope(
      store: _dock,
      source: widget.plugin.id,
      enabled: true,
      compact: true,
      openClientLabel: '',
      onOpenClient: null,
      child: Material(
        color: Theme.of(context).scaffoldBackgroundColor,
        child: SafeArea(
          bottom: false,
          child: Column(
            children: [
              PluginCompactHeader(
                key: ValueKey('microblog-header-${widget.plugin.id}'),
                plugin: widget.plugin,
                store: _dock,
                showBack: Navigator.canPop(context),
              ),
              Expanded(child: Builder(builder: widget.builder)),
            ],
          ),
        ),
      ),
    );
  }
}
