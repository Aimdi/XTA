import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_mute_store.dart';
import 'package:xta/plugins/pixiv/pixiv_settings_mute.dart';
import 'package:xta/plugins/plugin_view_store.dart';

/// Why a work is hidden: the work itself, its author, or one of its tags.
sealed class PixivMuteReason {
  const PixivMuteReason();
}

class PixivMutedWork extends PixivMuteReason {
  const PixivMutedWork();
}

class PixivMutedAuthor extends PixivMuteReason {
  final String name;

  const PixivMutedAuthor(this.name);
}

class PixivMutedTag extends PixivMuteReason {
  /// The muted entry that matched: a tag name or an `r'pattern'`.
  final String entry;

  const PixivMutedTag(this.entry);
}

/// What in [state] hides [illust], or null when nothing does.
PixivMuteReason? pixivMuteReason(PixivMuteState state, PixivIllust illust) {
  if (state.illustIds.contains(illust.id)) {
    return const PixivMutedWork();
  }
  if (state.authorIds.contains(illust.userId)) {
    final name = state.authorNames[illust.userId] ?? illust.userName;
    return PixivMutedAuthor(name.isEmpty ? '${illust.userId}' : name);
  }
  return switch (state.mutedTagOf(illust)) {
    final entry? => PixivMutedTag(entry),
    null => null,
  };
}

String pixivMuteReasonText(L10n l10n, PixivMuteReason reason) => switch (reason) {
  PixivMutedWork() => l10n.plugin_pixiv_mute_gate_work,
  PixivMutedAuthor(:final name) => l10n.plugin_pixiv_mute_gate_author(name),
  PixivMutedTag(:final entry) => l10n.plugin_pixiv_mute_gate_tag(pixivMutePattern(entry) == null ? '#$entry' : entry),
};

/// Puts a muted work behind a notice saying why, with a way to see it this
/// once.
///
/// Once a work is on screen it stays there: muting it from its own menu leaves
/// the screen rather than swapping the work for this notice.
class PixivMuteGate extends StatefulWidget {
  final PixivIllust illust;
  final Widget child;

  const PixivMuteGate({super.key, required this.illust, required this.child});

  @override
  State<PixivMuteGate> createState() => _PixivMuteGateState();
}

class _PixivMuteGateState extends State<PixivMuteGate> {
  late final PluginViewStore<bool> _shown;

  @override
  void initState() {
    super.initState();
    _shown = PluginViewStore(!context.read<PixivMuteStore>().isMuted(widget.illust));
  }

  @override
  void dispose() {
    _shown.destroy();
    super.dispose();
  }

  void _show() => _shown.select(true);

  Future<void> _openMuteSettings() async {
    final mute = context.read<PixivMuteStore>();
    await Navigator.push(context, MaterialPageRoute<void>(builder: (_) => const PixivMuteScreen()));
    if (mounted && !mute.isMuted(widget.illust)) _show();
  }

  @override
  Widget build(BuildContext context) => ScopedBuilder<PluginViewStore<bool>, bool>(
    store: _shown,
    onState: (context, shown) => shown
        ? widget.child
        : ScopedBuilder<PixivMuteStore, PixivMuteState>(
            store: context.read<PixivMuteStore>(),
            onState: (context, mute) => switch (pixivMuteReason(mute, widget.illust)) {
              final reason? => _notice(context, reason),
              null => widget.child,
            },
          ),
  );

  Widget _notice(BuildContext context, PixivMuteReason reason) {
    final l10n = L10n.of(context);
    final theme = Theme.of(context);
    return Scaffold(
      key: const ValueKey('pixiv-mute-gate'),
      appBar: AppBar(title: Text(l10n.plugin_pixiv_title)),
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(24),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Icon(Icons.visibility_off_outlined, size: 48, color: theme.colorScheme.onSurfaceVariant),
                const SizedBox(height: 16),
                Text(
                  l10n.plugin_pixiv_mute_gate_title,
                  style: theme.textTheme.titleMedium,
                  textAlign: TextAlign.center,
                ),
                const SizedBox(height: 8),
                Text(pixivMuteReasonText(l10n, reason), textAlign: TextAlign.center),
                const SizedBox(height: 24),
                FilledButton(onPressed: _show, child: Text(l10n.plugin_pixiv_mute_gate_show)),
                const SizedBox(height: 8),
                TextButton(onPressed: _openMuteSettings, child: Text(l10n.plugin_pixiv_mute_settings)),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
