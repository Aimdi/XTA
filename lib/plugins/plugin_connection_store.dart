import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/tweet/tweet_chrome.dart';

enum PluginConnectionStatus { idle, running, ok, warning, failed }

@immutable
class PluginConnectionState {
  final PluginConnectionStatus status;
  final String? message;
  final bool obscureKey;
  final bool obscureSecret;
  const PluginConnectionState({this.status = PluginConnectionStatus.idle, this.message,
    this.obscureKey = true, this.obscureSecret = true});
}

/// Presentation only: a result belongs to the exact fields that were tested.
/// Editing them invalidates both visible feedback and any outstanding result.
class PluginConnectionStore extends Store<PluginConnectionState> {
  PluginConnectionStore() : super(const PluginConnectionState());
  int _revision = 0;
  bool isCurrent(int revision) => revision == _revision;

  void invalidate() {
    _revision++;
    update(PluginConnectionState(obscureKey: state.obscureKey, obscureSecret: state.obscureSecret));
  }

  int begin() {
    final revision = ++_revision;
    update(PluginConnectionState(status: PluginConnectionStatus.running,
      obscureKey: state.obscureKey, obscureSecret: state.obscureSecret));
    return revision;
  }

  void finish(int revision, PluginConnectionStatus status, String message) {
    if (!isCurrent(revision)) return;
    update(PluginConnectionState(status: status, message: message,
      obscureKey: state.obscureKey, obscureSecret: state.obscureSecret));
  }

  void toggleVisibility({bool secret = false}) => update(PluginConnectionState(
    status: state.status, message: state.message,
    obscureKey: secret ? state.obscureKey : !state.obscureKey,
    obscureSecret: secret ? !state.obscureSecret : state.obscureSecret,
  ));
}

class PluginConnectionActions extends StatelessWidget {
  final PluginConnectionState state;
  final String testLabel;
  final VoidCallback onTest;
  final VoidCallback onSave;
  const PluginConnectionActions({super.key, required this.state, required this.testLabel,
    required this.onTest, required this.onSave});

  @override
  Widget build(BuildContext context) {
    final busy = state.status == PluginConnectionStatus.running;
    return Wrap(spacing: 12, runSpacing: 8, children: [
      FilledButton.icon(onPressed: busy ? null : onTest,
        style: FilledButton.styleFrom(minimumSize: const Size(48, 48)),
        icon: busy
          ? const SizedBox.square(dimension: 18, child: CircularProgressIndicator(strokeWidth: 2))
          : const Icon(Icons.wifi_tethering),
        label: Text(testLabel)),
      OutlinedButton(onPressed: busy ? null : onSave,
        style: OutlinedButton.styleFrom(minimumSize: const Size(48, 48)),
        child: Text(L10n.of(context).save)),
    ]);
  }
}

class PluginConnectionFeedback extends StatelessWidget {
  final PluginConnectionState state;
  const PluginConnectionFeedback({super.key, required this.state});

  @override
  Widget build(BuildContext context) {
    if (state.message == null) return const SizedBox.shrink();
    final failed = state.status == PluginConnectionStatus.failed;
    final color = failed ? Theme.of(context).colorScheme.error : tweetPrimaryColor(context);
    return Semantics(liveRegion: true, child: Padding(
      padding: const EdgeInsets.symmetric(vertical: 16),
      child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Icon(switch (state.status) {
          PluginConnectionStatus.ok => Icons.check_circle_outline,
          PluginConnectionStatus.warning => Icons.info_outline,
          _ => Icons.error_outline,
        }, color: color, size: 24),
        const SizedBox(width: 12),
        Expanded(child: Text(state.message!, style: TextStyle(color: color))),
      ]),
    ));
  }
}
