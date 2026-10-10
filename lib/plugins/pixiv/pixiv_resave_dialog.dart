import 'package:flutter/material.dart';
import 'package:xta/generated/l10n.dart';

enum PixivResave { all, newOnly }

/// Asks before saving pages again: [saved] of the [total] about to be saved
/// are on the device already. Null when the reader cancels.
Future<PixivResave?> showPixivResaveDialog(BuildContext context, {required int saved, required int total}) =>
    showDialog<PixivResave>(
      context: context,
      builder: (context) => PixivResaveDialog(saved: saved, total: total),
    );

class PixivResaveDialog extends StatelessWidget {
  final int saved;
  final int total;

  const PixivResaveDialog({super.key, required this.saved, required this.total});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return AlertDialog(
      title: Text(l10n.plugin_pixiv_already_saved),
      content: Text(
        total == 1 ? l10n.plugin_pixiv_already_saved_page : l10n.plugin_pixiv_already_saved_pages(saved, total),
      ),
      actions: [
        TextButton(onPressed: () => Navigator.pop(context), child: Text(l10n.cancel)),
        if (saved < total)
          TextButton(
            key: const ValueKey('pixiv-resave-new'),
            onPressed: () => Navigator.pop(context, PixivResave.newOnly),
            child: Text(l10n.plugin_pixiv_save_new_only),
          ),
        FilledButton(
          key: const ValueKey('pixiv-resave-all'),
          onPressed: () => Navigator.pop(context, PixivResave.all),
          child: Text(l10n.plugin_pixiv_save_again),
        ),
      ],
    );
  }
}
