import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/ehviewer/eh_gallery_header.dart';
import 'package:xta/plugins/ehviewer/eh_models.dart';
import 'package:xta/plugins/ehviewer/eh_search_screen.dart';
import 'package:xta/plugins/ehviewer/eh_tags.dart';
import 'package:xta/plugins/plugin_tag_chip.dart';
import 'package:xta/ui/errors.dart';

/// From this width the namespaces get a label column beside their tags, as on
/// the site; narrower, each namespace heads its own run of chips.
const _ehTagColumnMinWidth = 480.0;
const _ehTagLabelWidth = 104.0;

/// The gallery's tags grouped under their namespaces. A tap searches for the
/// tag, a long press copies it.
class EhGalleryTags extends StatelessWidget {
  final List<String> tags;
  final Set<String> weakTags;

  const EhGalleryTags({super.key, required this.tags, this.weakTags = const {}});

  @override
  Widget build(BuildContext context) {
    final groups = ehTagGroups(tags);
    if (groups.isEmpty) return const SizedBox.shrink();
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        EhSectionTitle(L10n.of(context).plugin_eh_tags),
        LayoutBuilder(
          builder: (context, constraints) {
            final labelColumn = constraints.maxWidth >= _ehTagColumnMinWidth;
            return Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  for (final group in groups)
                    _EhTagGroupView(group: group, weakTags: weakTags, labelColumn: labelColumn),
                ],
              ),
            );
          },
        ),
      ],
    );
  }
}

class _EhTagGroupView extends StatelessWidget {
  final EhTagGroup group;
  final Set<String> weakTags;
  final bool labelColumn;

  const _EhTagGroupView({required this.group, required this.weakTags, required this.labelColumn});

  @override
  Widget build(BuildContext context) {
    final chips = Wrap(spacing: 6, children: [for (final tag in group.tags) _chip(context, tag)]);
    if (!labelColumn) {
      return Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Padding(padding: const EdgeInsets.only(top: 8), child: _label(context)),
          chips,
        ],
      );
    }
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        SizedBox(
          width: MediaQuery.textScalerOf(context).scale(_ehTagLabelWidth),
          child: Padding(padding: const EdgeInsets.only(top: 14), child: _label(context)),
        ),
        Expanded(child: chips),
      ],
    );
  }

  Widget _label(BuildContext context) {
    final theme = Theme.of(context);
    final color = pluginTagKindColor(ehTagKind(group.namespace), theme.colorScheme);
    return Semantics(
      header: true,
      child: Text(
        ehNamespaceLabel(L10n.of(context), group.namespace),
        style: theme.textTheme.labelLarge?.copyWith(color: color ?? theme.colorScheme.onSurfaceVariant),
      ),
    );
  }

  Widget _chip(BuildContext context, EhTag tag) {
    final l10n = L10n.of(context);
    final weak = weakTags.contains(tag.raw);
    return PluginTagChip(
      key: ValueKey('eh-tag-${tag.raw}'),
      label: tag.name,
      kind: ehTagKind(group.namespace),
      weak: weak,
      semanticsLabel: weak ? l10n.plugin_eh_gallery_weak_tag(tag.name) : null,
      longPressHint: l10n.plugin_eh_gallery_copy_tag,
      onLongPress: () => _copy(context, tag),
      onPressed: () =>
          Navigator.push(context, MaterialPageRoute(builder: (_) => EhSearchScreen(initialQuery: tag.query))),
    );
  }

  void _copy(BuildContext context, EhTag tag) {
    Clipboard.setData(ClipboardData(text: tag.raw));
    showSnackBar(context, icon: '📋', message: L10n.of(context).plugin_eh_gallery_tag_copied(tag.raw));
  }
}
