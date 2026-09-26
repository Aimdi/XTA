import 'package:flutter/material.dart';
import 'package:xta/plugins/plugin_home_chrome.dart';

/// Keeps filter choices scrollable while preserving full-sized touch targets.
class PluginFilterRow extends StatelessWidget {
  final List<Widget> children;

  const PluginFilterRow({super.key, required this.children});

  @override
  Widget build(BuildContext context) {
    if (children.isEmpty) return const SizedBox.shrink();
    final embedded = PluginEmbedded.maybeOf(context);
    return Align(
      alignment: AlignmentDirectional.centerStart,
      child: SingleChildScrollView(
        scrollDirection: Axis.horizontal,
        padding: EdgeInsetsDirectional.fromSTEB(embedded ? 8 : 12, embedded ? 0 : 4, embedded ? 8 : 12, embedded ? 0 : 4),
        child: Row(
          children: [
            for (var index = 0; index < children.length; index++)
              Padding(
                padding: EdgeInsetsDirectional.only(end: index == children.length - 1 ? 0 : (embedded ? 4 : 8)),
                child: ConstrainedBox(
                  constraints: const BoxConstraints(minHeight: 48),
                  child: Center(child: children[index]),
                ),
              ),
          ],
        ),
      ),
    );
  }
}
