import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/search/recent_searches_store.dart';

class RecentSearchesBar extends StatelessWidget {
  final RecentSearchesStore store;
  final String scope;
  final ValueChanged<String> onSelected;
  const RecentSearchesBar({super.key, required this.store, required this.scope, required this.onSelected});
  @override
  Widget build(BuildContext context) => ScopedBuilder<RecentSearchesStore, Map<String, List<String>>>(
    store: store, onState: (context, history) {
      final queries = history[scope] ?? const [];
      if (queries.isEmpty) return const SizedBox.shrink();
      final l10n = L10n.of(context);
      return SizedBox(height: MediaQuery.textScalerOf(context).scale(14) + 40,
        child: ListView.separated(scrollDirection: Axis.horizontal,
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 4), itemCount: queries.length,
          separatorBuilder: (_, _) => const SizedBox(width: 8),
          itemBuilder: (context, index) => Center(child: InputChip(
            avatar: const Icon(Icons.history, size: 18), label: Text(queries[index], maxLines: 1),
            tooltip: l10n.plugin_bluesky_recent_searches,
            onPressed: () => onSelected(queries[index]),
            onDeleted: () => store.remove(scope, queries[index]), deleteButtonTooltipMessage: l10n.delete,
          )),
        ),
      );
    },
  );
}
