import 'package:flutter/material.dart';

const double kPluginStoreStackBreakpoint = 420;
const double kPluginStoreTouchTarget = 48;

/// Presentation-only store row that keeps identity readable before actions.
class PluginStoreTile extends StatelessWidget {
  final Widget leading;
  final Widget title;
  final Widget? subtitle;
  final Widget actions;
  final VoidCallback? onTap;

  const PluginStoreTile({
    super.key,
    required this.leading,
    required this.title,
    required this.actions,
    this.subtitle,
    this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: onTap,
      child: Padding(
        padding: const EdgeInsets.fromLTRB(16, 8, 8, 8),
        child: LayoutBuilder(
          builder: (context, constraints) {
            final textScale = MediaQuery.textScalerOf(context).scale(1);
            final stackActions = constraints.maxWidth < kPluginStoreStackBreakpoint || textScale >= 1.3;
            final identity = _PluginStoreIdentity(leading: leading, title: title, subtitle: subtitle);

            if (!stackActions) {
              return Row(
                key: const Key('plugin-store-tile-inline'),
                children: [
                  Expanded(child: identity),
                  const SizedBox(width: 8),
                  actions,
                ],
              );
            }

            return Column(
              key: const Key('plugin-store-tile-stacked'),
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                identity,
                const SizedBox(height: 8),
                Align(alignment: AlignmentDirectional.centerEnd, child: actions),
              ],
            );
          },
        ),
      ),
    );
  }
}

class _PluginStoreIdentity extends StatelessWidget {
  final Widget leading;
  final Widget title;
  final Widget? subtitle;

  const _PluginStoreIdentity({required this.leading, required this.title, required this.subtitle});

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        leading,
        const SizedBox(width: 12),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              title,
              if (subtitle != null) ...[const SizedBox(height: 2), subtitle!],
            ],
          ),
        ),
      ],
    );
  }
}
