import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/offline/offline_article.dart';
import 'package:xta/offline/offline_store.dart';
import 'package:xta/offline/offline_ui.dart';

class OfflineArticleAction extends StatelessWidget {
  final OfflineArticle article;
  final OfflineStore? store;
  const OfflineArticleAction({super.key, required this.article, this.store});

  Future<void> _keep(BuildContext context, OfflineStore model) async {
    final messenger = ScaffoldMessenger.of(context);
    final l10n = L10n.of(context);
    await model.keepArticle(article);
    final entry = model.state.entry(article.id);
    messenger.showSnackBar(SnackBar(content: Text(model.state.failed.contains(article.id)
      ? l10n.offline_failed : entry == null ? l10n.offline_unavailable : offlineDetails(l10n, entry))));
  }

  @override
  Widget build(BuildContext context) {
    final model = store ?? OfflineStore.shared;
    model.load();
    return ScopedBuilder<OfflineStore, OfflineState>(store: model, onState: (context, state) {
      final l10n = L10n.of(context);
      final entry = state.entry(article.id);
      final busy = state.busy.contains(article.id);
      return PopupMenuButton<String>(
        tooltip: entry == null ? l10n.offline_keep : offlineStatus(l10n, entry),
        icon: busy ? const SizedBox.square(dimension: 20, child: CircularProgressIndicator(strokeWidth: 2))
          : Icon(entry?.hasArticle == true ? Icons.offline_pin : Icons.offline_pin_outlined),
        onSelected: (value) => value == 'remove' ? model.remove(article.id) : _keep(context, model),
        itemBuilder: (context) => [
          PopupMenuItem(enabled: !busy && article.bodyHtml.trim().isNotEmpty,
            value: 'keep', child: Text(entry == null ? l10n.offline_keep : l10n.retry)),
          if (entry != null) PopupMenuItem(value: 'remove', child: Text(l10n.offline_remove)),
          if (entry != null) PopupMenuItem(enabled: false, child: Text(offlineDetails(l10n, entry))),
          PopupMenuItem(enabled: false, child: Text(l10n.offline_article_text_only)),
        ],
      );
    });
  }
}
