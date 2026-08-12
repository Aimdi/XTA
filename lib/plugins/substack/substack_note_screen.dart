import 'package:extended_image/extended_image.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:share_plus/share_plus.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/substack/substack_archive_screen.dart';
import 'package:xta/plugins/substack/substack_models.dart';
import 'package:xta/plugins/substack/substack_store.dart';
import 'package:xta/plugins/substack/substack_group.dart';
import 'package:xta/subscriptions/users_model.dart';
import 'package:xta/ui/dates.dart';
import 'package:xta/utils/urls.dart';

/// In-app Note view — stand-in for opening every note only on substack.com.
class SubstackNoteScreen extends StatelessWidget {
  final SubstackNote note;

  const SubstackNoteScreen({super.key, required this.note});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final l10n = L10n.of(context);
    final pub = note.publication;

    return Scaffold(
      appBar: AppBar(
        title: Text(l10n.plugin_substack_tab_notes),
        actions: [
          if (note.url != null) ...[
            IconButton(
              tooltip: l10n.share_link,
              icon: const Icon(Icons.share_outlined),
              onPressed: () =>
                  SharePlus.instance.share(ShareParams(text: note.url!)),
            ),
            IconButton(
              tooltip: l10n.open_in_browser,
              icon: const Icon(Icons.open_in_new),
              onPressed: () => openUri(context, note.url!),
            ),
          ],
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(20, 16, 20, 32),
        children: [
          Row(
            children: [
              if (note.authorPhotoUrl != null)
                ClipOval(
                  child: ExtendedImage.network(
                    note.authorPhotoUrl!,
                    width: 40,
                    height: 40,
                    fit: BoxFit.cover,
                    cache: true,
                    cacheWidth: (40 * MediaQuery.devicePixelRatioOf(context))
                        .ceil(),
                  ),
                )
              else
                CircleAvatar(
                  radius: 20,
                  child: Text(
                    (note.authorName ?? note.authorHandle ?? '?').trim().isEmpty
                        ? '?'
                        : (note.authorName ?? note.authorHandle)!
                              .trim()[0]
                              .toUpperCase(),
                  ),
                ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      note.authorName ?? note.authorHandle ?? '',
                      style: theme.textTheme.titleMedium!.copyWith(
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                    if (note.at != null)
                      Text(
                        createCompactDate(note.at!),
                        style: theme.textTheme.bodySmall,
                      ),
                  ],
                ),
              ),
            ],
          ),
          const SizedBox(height: 16),
          Text(
            note.body,
            style: theme.textTheme.bodyLarge!.copyWith(height: 1.45),
          ),
          if (note.imageUrl != null) ...[
            const SizedBox(height: 16),
            ClipRRect(
              borderRadius: BorderRadius.circular(12),
              child: RepaintBoundary(
                child: ExtendedImage.network(
                  note.imageUrl!,
                  fit: BoxFit.cover,
                  cache: true,
                  cacheWidth:
                      (MediaQuery.sizeOf(context).width *
                              MediaQuery.devicePixelRatioOf(context))
                          .ceil(),
                ),
              ),
            ),
          ],
          if ((note.reactionCount ?? 0) > 0) ...[
            const SizedBox(height: 16),
            Row(
              children: [
                Icon(
                  Icons.favorite_outline,
                  size: 16,
                  color: theme.colorScheme.onSurfaceVariant,
                ),
                const SizedBox(width: 6),
                Text('${note.reactionCount}', style: theme.textTheme.bodySmall),
              ],
            ),
          ],
          if (pub != null) ...[
            const SizedBox(height: 24),
            Text(
              l10n.plugin_substack_publication,
              style: theme.textTheme.labelLarge,
            ),
            const SizedBox(height: 8),
            ListTile(
              contentPadding: EdgeInsets.zero,
              leading: pub.logoUrl == null
                  ? const CircleAvatar(child: Icon(Icons.newspaper))
                  : ClipOval(
                      child: ExtendedImage.network(
                        pub.logoUrl!,
                        width: 40,
                        height: 40,
                        fit: BoxFit.cover,
                        cache: true,
                        cacheWidth:
                            (40 * MediaQuery.devicePixelRatioOf(context))
                                .ceil(),
                      ),
                    ),
              title: Text(pub.name),
              subtitle: Text(Uri.tryParse(pub.baseUrl)?.host ?? pub.baseUrl),
              trailing: const Icon(Icons.chevron_right),
              onTap: () => Navigator.push(
                context,
                MaterialPageRoute(
                  builder: (_) => SubstackArchiveScreen(publication: pub),
                ),
              ),
            ),
            const SizedBox(height: 8),
            Align(
              alignment: Alignment.centerLeft,
              child: Wrap(
                spacing: 8,
                runSpacing: 8,
                children: [
                  FilledButton.tonalIcon(
                    icon: const Icon(Icons.add, size: 18),
                    label: Text(l10n.plugin_substack_follow),
                    onPressed: () async {
                      final pubs = context.read<SubstackPublicationsStore>();
                      final subscriptions = context.read<SubscriptionsModel>();
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
                  OutlinedButton.icon(
                    onPressed: () =>
                        addSubstackPublicationToGroup(context, pub),
                    icon: const Icon(Icons.group_add, size: 18),
                    label: Text(l10n.add_to_group),
                  ),
                ],
              ),
            ),
          ],
        ],
      ),
    );
  }
}
