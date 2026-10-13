import 'dart:async';

import 'package:flutter/material.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/booru/booru_labels.dart';
import 'package:xta/plugins/booru/booru_prompt.dart';

/// The kinds of tag "Copy tags as prompt" writes, in its order: drag a kind
/// to move it, switch it off to leave it out.
class BooruPromptSettings extends StatelessWidget {
  const BooruPromptSettings({super.key});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final prefs = PrefService.of(context);
    final kinds = booruPromptKinds(prefs.get<String>(optionPluginBooruPromptKinds));
    Future<void> save(List<BooruPromptKind> next) async {
      await prefs.set(optionPluginBooruPromptKinds, booruPromptStored(next));
    }

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        PrefTitle(title: Text(l10n.plugin_booru_prompt_section)),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16),
          child: Text(l10n.plugin_booru_prompt_help, style: Theme.of(context).textTheme.bodySmall),
        ),
        ReorderableListView(
          shrinkWrap: true,
          physics: const NeverScrollableScrollPhysics(),
          buildDefaultDragHandles: false,
          onReorderItem: (from, to) => unawaited(save(booruPromptMoved(kinds, from, to))),
          children: [
            for (final (index, kind) in kinds.indexed)
              SwitchListTile(
                key: ValueKey('booru-prompt-${kind.category.name}'),
                value: kind.included,
                title: Text(booruTagCategoryLabel(l10n, kind.category)),
                secondary: ReorderableDragStartListener(index: index, child: const Icon(Icons.drag_handle)),
                onChanged: (included) => unawaited(save(booruPromptToggled(kinds, kind.category, included))),
              ),
          ],
        ),
      ],
    );
  }
}
