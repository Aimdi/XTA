import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/ehviewer/eh_gallery_store.dart';
import 'package:xta/plugins/ehviewer/eh_store.dart';

/// From this width (at 1x text) Read and Continue fit side by side; narrower,
/// they stack rather than wrap their labels.
const _ehActionsRowWidth = 440.0;

/// Read from the start, and continue where history left off when that is past
/// the first page.
class EhReadActions extends StatelessWidget {
  final EhGalleryStore store;
  final ValueChanged<int> onRead;

  const EhReadActions({super.key, required this.store, required this.onRead});

  @override
  Widget build(BuildContext context) {
    return ScopedBuilder<EhHistoryStore, List<EhHistoryEntry>>(
      store: store.history,
      onState: (context, _) => Padding(
        padding: const EdgeInsets.fromLTRB(16, 16, 16, 0),
        child: LayoutBuilder(
          builder: (context, constraints) => _buttons(context, store.continuePage, constraints.maxWidth),
        ),
      ),
    );
  }

  Widget _buttons(BuildContext context, int? continuePage, double width) {
    final l10n = L10n.of(context);
    const size = Size.fromHeight(48);
    final read = FilledButton.icon(
      key: const ValueKey('eh-gallery-read'),
      style: FilledButton.styleFrom(minimumSize: size),
      onPressed: () => onRead(1),
      icon: const Icon(Icons.menu_book_outlined),
      label: Text(l10n.plugin_eh_read),
    );
    if (continuePage == null) return read;
    final resume = OutlinedButton.icon(
      key: const ValueKey('eh-gallery-continue'),
      style: OutlinedButton.styleFrom(minimumSize: size),
      onPressed: () => onRead(continuePage),
      icon: const Icon(Icons.play_arrow),
      label: Text(l10n.plugin_eh_gallery_continue_page(continuePage), textAlign: TextAlign.center),
    );
    if (width >= MediaQuery.textScalerOf(context).scale(_ehActionsRowWidth)) {
      return Row(
        children: [
          Expanded(child: read),
          const SizedBox(width: 8),
          Expanded(child: resume),
        ],
      );
    }
    return Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [read, const SizedBox(height: 8), resume]);
  }
}
