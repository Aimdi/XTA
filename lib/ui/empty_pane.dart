import 'package:flutter/material.dart';
import 'package:xta/plugins/plugin_feed_insets.dart';
import 'package:xta/plugins/plugin_home_chrome.dart';

/// Icon, sentence, optional way out — the empty shape plugin feeds share.
class EmptyPane extends StatelessWidget {
  final IconData icon;
  final String message;
  final Widget? action;
  final Widget? leading;
  final ScrollController? scrollController;
  final Future<void> Function()? onRefresh;

  const EmptyPane({
    super.key,
    required this.icon,
    required this.message,
    this.action,
    this.leading,
    this.scrollController,
    this.onRefresh,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    // Home-strip plugins sit in NestedScrollView. The requested controller is
    // the *outer* one; attaching it here freezes, then crashes.
    final list = ListView(
      controller: pluginInnerScrollController(context, scrollController),
      primary: PluginEmbedded.maybeOf(context) ? false : null,
      physics: const AlwaysScrollableScrollPhysics(),
      padding: pluginFeedPadding(
        context,
      ).add(EdgeInsets.fromLTRB(32, leading != null ? 16 : 72, 32, 32)),
      children: [
        ?leading,
        Icon(icon, size: 52, color: theme.colorScheme.outline),
        const SizedBox(height: 16),
        Text(
          message,
          textAlign: TextAlign.center,
          style: theme.textTheme.titleMedium!.copyWith(
            fontWeight: FontWeight.w700,
          ),
        ),
        if (action != null) ...[
          const SizedBox(height: 24),
          Center(child: action!),
        ],
      ],
    );

    if (onRefresh == null) return list;
    return RefreshIndicator(onRefresh: onRefresh!, child: list);
  }
}
