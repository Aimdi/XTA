import 'package:extended_image/extended_image.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/substack/substack_archive_screen.dart';
import 'package:xta/plugins/substack/substack_models.dart';
import 'package:xta/plugins/substack/substack_note_screen.dart';
import 'package:xta/plugins/substack/substack_store.dart';
import 'package:xta/plugins/substack/substack_group.dart';
import 'package:xta/subscriptions/users_model.dart';
import 'package:xta/tweet/tweet_chrome.dart';
import 'package:xta/ui/dates.dart';

String _initial(String? name) {
  final trimmed = name?.trim() ?? '';
  if (trimmed.isEmpty) return '?';
  return trimmed.substring(0, 1).toUpperCase();
}

/// A public Note from Substack's discovery feed.
class SubstackNoteCard extends StatelessWidget {
  final SubstackNote note;

  const SubstackNoteCard({super.key, required this.note});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final l10n = L10n.of(context);
    final pub = note.publication;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        tweetFlatCard(
          color: theme.cardColor,
          child: InkWell(
            onTap: () => Navigator.push(
              context,
              MaterialPageRoute(builder: (_) => SubstackNoteScreen(note: note)),
            ),
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      if (note.authorPhotoUrl != null)
                        ClipOval(
                          child: ExtendedImage.network(
                            note.authorPhotoUrl!,
                            width: 28,
                            height: 28,
                            fit: BoxFit.cover,
                            cache: true,
                            cacheWidth:
                                (28 * MediaQuery.devicePixelRatioOf(context))
                                    .ceil(),
                          ),
                        )
                      else
                        CircleAvatar(
                          radius: 14,
                          child: Text(
                            _initial(note.authorName ?? note.authorHandle),
                          ),
                        ),
                      const SizedBox(width: 8),
                      Expanded(
                        child: Text(
                          note.authorName ?? note.authorHandle ?? '',
                          style: theme.textTheme.titleSmall,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                      if (note.at != null)
                        Text(
                          createCompactDate(note.at!),
                          style: theme.textTheme.bodySmall,
                        ),
                    ],
                  ),
                  const SizedBox(height: 8),
                  Text(note.body, style: theme.textTheme.bodyLarge),
                  if (note.imageUrl != null) ...[
                    const SizedBox(height: 12),
                    ClipRRect(
                      borderRadius: BorderRadius.circular(8),
                      child: RepaintBoundary(
                        child: ExtendedImage.network(
                          note.imageUrl!,
                          fit: BoxFit.cover,
                          cache: true,
                          cacheWidth:
                              ((MediaQuery.sizeOf(context).width - 32) *
                                      MediaQuery.devicePixelRatioOf(context))
                                  .ceil(),
                        ),
                      ),
                    ),
                  ],
                  if ((note.reactionCount ?? 0) > 0) ...[
                    const SizedBox(height: 8),
                    Row(
                      children: [
                        Icon(
                          Icons.favorite_border,
                          size: 14,
                          color: theme.colorScheme.onSurfaceVariant,
                        ),
                        const SizedBox(width: 4),
                        Text(
                          '${note.reactionCount}',
                          style: theme.textTheme.bodySmall,
                        ),
                      ],
                    ),
                  ],
                  if (pub != null) ...[
                    const SizedBox(height: 8),
                    Wrap(
                      spacing: 8,
                      children: [
                        ActionChip(
                          label: Text(pub.name),
                          onPressed: () => Navigator.push(
                            context,
                            MaterialPageRoute(
                              builder: (_) =>
                                  SubstackArchiveScreen(publication: pub),
                            ),
                          ),
                        ),
                        ActionChip(
                          avatar: const Icon(Icons.add, size: 16),
                          label: Text(l10n.plugin_substack_follow),
                          onPressed: () async {
                            final pubs = context
                                .read<SubstackPublicationsStore>();
                            final subscriptions = context
                                .read<SubscriptionsModel>();
                            final messenger = ScaffoldMessenger.of(context);
                            await pubs.add(pub);
                            await subscriptions.reloadSubscriptions();
                            messenger.showSnackBar(
                              SnackBar(
                                content: Text(
                                  l10n.plugin_substack_followed(pub.name),
                                ),
                              ),
                            );
                          },
                        ),
                        ActionChip(
                          avatar: const Icon(Icons.group_add, size: 16),
                          label: Text(l10n.add_to_group),
                          onPressed: () =>
                              addSubstackPublicationToGroup(context, pub),
                        ),
                      ],
                    ),
                  ],
                ],
              ),
            ),
          ),
        ),
        tweetHairlineDivider(context),
      ],
    );
  }
}
