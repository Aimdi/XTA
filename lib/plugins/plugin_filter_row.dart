import 'package:flutter/material.dart';

/// Keeps filter choices scrollable while preserving full-sized touch targets.
class PluginFilterRow extends StatelessWidget {
  final List<Widget> children;

  const PluginFilterRow({super.key, required this.children});

  @override
  Widget build(BuildContext context) => SingleChildScrollView(
    scrollDirection: Axis.horizontal,
    padding: const EdgeInsetsDirectional.fromSTEB(12, 4, 12, 4),
    child: Row(children: [
      for (final child in children)
        Padding(
          padding: const EdgeInsetsDirectional.only(end: 8),
          child: ConstrainedBox(
            constraints: const BoxConstraints(minHeight: 48),
            child: Center(child: child),
          ),
        ),
    ]),
  );
}
