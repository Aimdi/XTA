import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/generated/l10n.dart';

class LocalPostTile extends StatelessWidget {
  final LocalPost post;
  final VoidCallback onEdit;
  final VoidCallback onDelete;

  const LocalPostTile({
    super.key,
    required this.post,
    required this.onEdit,
    required this.onDelete,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final l10n = L10n.of(context);
    final locale = Localizations.localeOf(context).toString();
    final stamp = DateFormat.yMMMd(locale).add_Hm().format(post.createdAt.toLocal());

    return Card(
      margin: const EdgeInsets.fromLTRB(12, 6, 12, 6),
      elevation: 0,
      color: theme.colorScheme.surfaceContainerHighest.withValues(alpha: 0.45),
      child: InkWell(
        onTap: onEdit,
        borderRadius: BorderRadius.circular(12),
        child: Padding(
          padding: const EdgeInsets.fromLTRB(12, 12, 4, 12),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Icon(
                Icons.sticky_note_2_outlined,
                color: theme.colorScheme.primary,
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      post.body,
                      style: theme.textTheme.bodyLarge,
                      maxLines: 8,
                      overflow: TextOverflow.ellipsis,
                    ),
                    const SizedBox(height: 8),
                    Text(
                      stamp,
                      style: theme.textTheme.bodySmall?.copyWith(
                        color: theme.hintColor,
                      ),
                    ),
                  ],
                ),
              ),
              PopupMenuButton<String>(
                onSelected: (value) {
                  if (value == 'edit') {
                    onEdit();
                  } else if (value == 'delete') {
                    onDelete();
                  }
                },
                itemBuilder: (context) => [
                  PopupMenuItem(
                    value: 'edit',
                    child: Text(l10n.local_note_edit_title),
                  ),
                  PopupMenuItem(
                    value: 'delete',
                    child: Text(l10n.delete),
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}
