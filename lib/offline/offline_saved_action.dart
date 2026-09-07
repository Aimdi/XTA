import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/offline/offline_saved_media.dart';
import 'package:xta/offline/offline_store.dart';
import 'package:xta/offline/offline_ui.dart';
import 'package:xta/saved/saved_content_index.dart';

export 'offline_saved_media.dart' show hasOfflineMedia;

class OfflineSavedAction extends StatelessWidget {
  final String id;
  final SavedContent content;
  final OfflineStore? store;
  const OfflineSavedAction({super.key, required this.id, required this.content, this.store});
  String get _key => 'saved:$id';

  Future<void> _keep(BuildContext context, OfflineStore model) async {
    final l10n = L10n.of(context);
    final messenger = ScaffoldMessenger.of(context);
    final sensitive = offlineSavedSensitive(content);
    if (sensitive && !await confirmOfflineSensitive(context)) return;
    if (!context.mounted) return;
    await model.keepMedia(id: _key, title: offlineSavedTitle(content), source: offlineSavedSource(content),
      url: offlineSavedUrl(content), media: offlineMediaOf(content), sensitive: sensitive);
    final entry = model.state.entry(_key);
    messenger.showSnackBar(SnackBar(content: Text(model.state.failed.contains(_key)
      ? l10n.offline_failed : entry == null ? l10n.offline_unavailable : offlineDetails(l10n, entry))));
  }

  @override
  Widget build(BuildContext context) {
    if (!hasOfflineMedia(content)) return const SizedBox.shrink();
    final model = store ?? OfflineStore.shared;
    model.load();
    return ScopedBuilder<OfflineStore, OfflineState>(store: model, onState: (context, state) {
      final l10n = L10n.of(context);
      final entry = state.entry(_key);
      final busy = state.busy.contains(_key);
      return ListTile(
        leading: busy ? const SizedBox.square(dimension: 24, child: CircularProgressIndicator(strokeWidth: 2))
          : Icon(entry?.canOpen == true ? Icons.offline_pin : Icons.offline_pin_outlined),
        title: Text(entry == null ? l10n.offline_keep : offlineStatus(l10n, entry)),
        subtitle: Text(content.reddit?.videoFallbackUrl != null ? l10n.offline_video_only :
          entry != null ? offlineDetails(l10n, entry) : l10n.offline_media_count(0, offlineMediaOf(content).length)),
        onTap: busy ? null : () => _keep(context, model),
        trailing: entry == null ? null : IconButton(icon: const Icon(Icons.delete_outline),
          tooltip: l10n.offline_remove, onPressed: () => model.remove(_key)),
      );
    });
  }
}
