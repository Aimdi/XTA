import 'package:flutter/material.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/ehviewer/eh_reader_store.dart';

String ehReadingModeLabel(L10n l10n, EhReadingMode mode) => switch (mode) {
  EhReadingMode.leftToRight => l10n.plugin_eh_reader_mode_ltr,
  EhReadingMode.rightToLeft => l10n.plugin_eh_reader_mode_rtl,
  EhReadingMode.vertical => l10n.plugin_eh_reader_mode_vertical,
};

IconData ehReadingModeIcon(EhReadingMode mode) => switch (mode) {
  EhReadingMode.leftToRight => Icons.format_textdirection_l_to_r,
  EhReadingMode.rightToLeft => Icons.format_textdirection_r_to_l,
  EhReadingMode.vertical => Icons.view_day_outlined,
};

Future<EhReadingMode?> showEhReadingModeSheet(BuildContext context, EhReadingMode current) {
  return showModalBottomSheet<EhReadingMode>(
    context: context,
    showDragHandle: true,
    builder: (context) => _EhSheet(
      title: L10n.of(context).plugin_eh_reader_mode,
      children: [
        for (final mode in EhReadingMode.values)
          ListTile(
            key: ValueKey('eh-reader-mode-${mode.name}'),
            leading: Icon(ehReadingModeIcon(mode)),
            title: Text(ehReadingModeLabel(L10n.of(context), mode)),
            trailing: mode == current ? const Icon(Icons.check) : null,
            selected: mode == current,
            onTap: () => Navigator.pop(context, mode),
          ),
      ],
    ),
  );
}

enum EhPageAction { reload, openOriginal, save, copyLink }

IconData _pageActionIcon(EhPageAction action) => switch (action) {
  EhPageAction.reload => Icons.refresh,
  EhPageAction.openOriginal => Icons.open_in_new,
  EhPageAction.save => Icons.download_outlined,
  EhPageAction.copyLink => Icons.link,
};

String _pageActionLabel(L10n l10n, EhPageAction action) => switch (action) {
  EhPageAction.reload => l10n.plugin_eh_reload_image,
  EhPageAction.openOriginal => l10n.plugin_eh_reader_open_original,
  EhPageAction.save => l10n.download,
  EhPageAction.copyLink => l10n.plugin_eh_copy_link,
};

/// Long-press sheet for one page; lists only the [available] actions, in a fixed order.
Future<EhPageAction?> showEhPageActions(
  BuildContext context, {
  required int page,
  required int total,
  required Set<EhPageAction> available,
}) {
  return showModalBottomSheet<EhPageAction>(
    context: context,
    showDragHandle: true,
    builder: (context) => _EhSheet(
      title: L10n.of(context).plugin_eh_page_of(page, total),
      children: [
        for (final action in EhPageAction.values.where(available.contains))
          ListTile(
            key: ValueKey('eh-page-action-${action.name}'),
            leading: Icon(_pageActionIcon(action)),
            title: Text(_pageActionLabel(L10n.of(context), action)),
            onTap: () => Navigator.pop(context, action),
          ),
      ],
    ),
  );
}

class _EhSheet extends StatelessWidget {
  final String title;
  final List<Widget> children;

  const _EhSheet({required this.title, required this.children});

  @override
  Widget build(BuildContext context) => SafeArea(
    child: SingleChildScrollView(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Padding(
            padding: const EdgeInsetsDirectional.fromSTEB(16, 0, 16, 8),
            child: Text(title, style: Theme.of(context).textTheme.titleSmall),
          ),
          ...children,
        ],
      ),
    ),
  );
}
