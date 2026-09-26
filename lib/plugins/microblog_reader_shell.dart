import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/plugins/plugin.dart';
import 'package:xta/plugins/plugin_home_chrome.dart';
import 'package:xta/plugins/plugin_home_dock.dart';
import 'package:xta/plugins/plugin_marks.dart';
import 'package:xta/ui/contrast.dart';

/// Keep normal and 200% text compact; allow extra height at larger accessibility sizes.
double microblogToolbarHeight(BuildContext context) {
  final style = Theme.of(context).textTheme.titleMedium;
  final lineHeight = MediaQuery.textScalerOf(context).scale(style?.fontSize ?? 16) * (style?.height ?? 1.5);
  return (lineHeight + 4).clamp(52, double.infinity);
}

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
              SizedBox(
                key: ValueKey('microblog-header-${widget.plugin.id}'),
                height: microblogToolbarHeight(context),
                child: ScopedBuilder<PluginHomeDockStore, Map<String, PluginDockEntry>>(
                  store: _dock,
                  onState: (context, _) {
                    final tabs = _dock.content(widget.plugin.id, 'navigation')?.tabs ?? const <PluginHomeTab>[];
                    return Row(
                      children: [
                        if (Navigator.canPop(context)) const SizedBox.square(dimension: 48, child: BackButton()),
                        Padding(
                          padding: const EdgeInsets.symmetric(horizontal: 8),
                          child: Tooltip(
                            message: widget.plugin.title(context),
                            child: Semantics(
                              label: widget.plugin.title(context),
                              image: true,
                              child: pluginMark(
                                widget.plugin,
                                size: 24,
                                color: ensureContrast(
                                  widget.plugin.brandColor,
                                  Theme.of(context).scaffoldBackgroundColor,
                                ),
                              ),
                            ),
                          ),
                        ),
                        Expanded(
                          child: tabs.isEmpty
                              ? const SizedBox.shrink()
                              : PluginSectionPicker(tabs: tabs, accent: widget.plugin.brandColor, verticalPadding: 4),
                        ),
                        PluginDockActions(store: _dock, source: widget.plugin.id, unified: true),
                      ],
                    );
                  },
                ),
              ),
              Expanded(child: Builder(builder: widget.builder)),
            ],
          ),
        ),
      ),
    );
  }
}
