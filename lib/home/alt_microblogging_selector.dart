import 'package:flutter/material.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/home/alt_microblogging.dart';
import 'package:xta/plugins/plugin_brand.dart';
import 'package:xta/plugins/plugin_registry.dart';

class AltMicrobloggingSelector extends StatelessWidget {
  final List<String> sourceIds;
  final String selected;
  final Set<String> unread;
  final bool compact;
  final ValueChanged<String> onSelected;

  const AltMicrobloggingSelector({
    super.key,
    required this.sourceIds,
    required this.selected,
    required this.onSelected,
    this.unread = const {},
    this.compact = false,
  });

  @override
  Widget build(BuildContext context) {
    if (compact) {
      return Row(
        mainAxisSize: MainAxisSize.min,
        children: [for (final id in sourceIds.where(isAltMicrobloggingSource)) _compactService(context, id)],
      );
    }
    return Semantics(
      container: true,
      label: L10n.of(context).alt_microblogging,
      child: SingleChildScrollView(
        key: const PageStorageKey('alt-microblogging-services'),
        primary: false,
        scrollDirection: Axis.horizontal,
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 2),
        child: Row(
          children: [
            for (final id in sourceIds.where(isAltMicrobloggingSource))
              Padding(padding: const EdgeInsetsDirectional.only(end: 8), child: _service(context, id)),
          ],
        ),
      ),
    );
  }

  Widget _compactService(BuildContext context, String id) {
    final plugin = pluginById(id)!;
    final label = plugin.title(context);
    final active = id == selected;
    return Semantics(
      selected: active,
      label: unread.contains(id) ? '$label, ${L10n.of(context).group_has_unread}' : label,
      button: true,
      onTap: () => onSelected(id),
      excludeSemantics: true,
      child: SizedBox.square(
        dimension: 48,
        child: IconButton(
          key: ValueKey('alt-microblogging-service-$id'),
          tooltip: label,
          style: IconButton.styleFrom(
            minimumSize: const Size.square(48),
            backgroundColor: active ? Theme.of(context).colorScheme.primaryContainer : null,
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
          ),
          onPressed: () => onSelected(id),
          icon: Badge(isLabelVisible: unread.contains(id), child: pluginBrandIcon(context, plugin, size: 22)),
        ),
      ),
    );
  }

  Widget _service(BuildContext context, String id) {
    final plugin = pluginById(id)!;
    final label = plugin.title(context);
    return Semantics(
      label: unread.contains(id) ? '$label, ${L10n.of(context).group_has_unread}' : label,
      selected: selected == id,
      button: true,
      onTap: () => onSelected(id),
      excludeSemantics: true,
      child: ChoiceChip(
        key: ValueKey('alt-microblogging-service-$id'),
        avatar: Badge(isLabelVisible: unread.contains(id), child: pluginBrandIcon(context, plugin, size: 20)),
        label: Text(label),
        selected: selected == id,
        showCheckmark: false,
        materialTapTargetSize: MaterialTapTargetSize.padded,
        visualDensity: VisualDensity.standard,
        onSelected: (_) => onSelected(id),
      ),
    );
  }
}
