import 'package:extended_image/extended_image.dart';
import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/plugin_counts.dart';
import 'package:xta/plugins/plugin_link_post.dart';
import 'package:xta/plugins/plugin_post_media.dart';
import 'package:xta/plugins/substack/substack_archive_screen.dart';
import 'package:xta/plugins/substack/substack_discussion_text.dart';
import 'package:xta/plugins/substack/substack_group.dart';
import 'package:xta/plugins/substack/substack_models.dart';
import 'package:xta/plugins/substack/substack_store.dart';
import 'package:xta/subscriptions/users_model.dart';
import 'package:xta/subscriptions/widgets/fallback_avatar.dart';
import 'package:xta/ui/dates.dart';
import 'package:xta/utils/urls.dart';

Future<void> showSubstackNoteActions(BuildContext context, SubstackNote note) async {
  final url = substackDiscussionUrl(note.url);
  if (url == null) return;
  await showPluginLinkPostActions(
    context,
    source: 'substack',
    url: url,
    author: note.authorName ?? note.authorHandle ?? '',
    text: note.body,
    images: [?substackDiscussionUrl(note.imageUrl)],
    onGroup: note.publication == null ? null : () => addSubstackPublicationToGroup(context, note.publication!),
  );
}

class SubstackNoteContent extends StatelessWidget {
  final SubstackNote note;
  final bool detail;
  const SubstackNoteContent({super.key, required this.note, this.detail = false});
  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final image = substackDiscussionUrl(note.imageUrl);
    final pub = note.publication;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        _NoteHeader(note: note, detail: detail),
        const SizedBox(height: 12),
        SubstackDiscussionText(
          text: note.body,
          selectable: detail,
          style: theme.textTheme.bodyLarge?.copyWith(height: 1.45),
        ),
        if (image != null) ...[
          const SizedBox(height: 12),
          PluginPostMedia(
            items: [PluginMediaItem(url: image)],
            sourceName: 'substack',
          ),
        ],
        _NoteReactions(note: note),
        if (pub != null) ...[const SizedBox(height: 12), _NotePublication(publication: pub)],
      ],
    );
  }
}

class _NoteHeader extends StatelessWidget {
  final SubstackNote note;
  final bool detail;
  const _NoteHeader({required this.note, required this.detail});
  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final name =
        [
          note.authorName,
          note.authorHandle,
        ].whereType<String>().where((value) => value.trim().isNotEmpty).firstOrNull ??
        l10n.unknown;
    final handle = note.authorHandle?.trim().replaceFirst(RegExp(r'^@'), '');
    final profileUrl = handle?.isNotEmpty == true ? Uri.https('substack.com', '/@$handle').toString() : null;
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _NoteAvatar(note: note, name: name),
        const SizedBox(width: 10),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              if (profileUrl == null)
                Text(name, style: Theme.of(context).textTheme.titleSmall)
              else
                InkWell(
                  onTap: () => openUri(context, profileUrl),
                  child: Padding(
                    padding: const EdgeInsets.symmetric(vertical: 4),
                    child: Text(
                      name,
                      style: Theme.of(context).textTheme.titleSmall?.copyWith(fontWeight: FontWeight.w700),
                    ),
                  ),
                ),
              if (handle?.isNotEmpty == true)
                Text(
                  '@$handle',
                  style: Theme.of(context).textTheme.bodySmall,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
              if (note.at != null) Text(createCompactDate(note.at!), style: Theme.of(context).textTheme.bodySmall),
            ],
          ),
        ),
        if (!detail && substackDiscussionUrl(note.url) != null)
          IconButton(
            tooltip: MaterialLocalizations.of(context).moreButtonTooltip,
            onPressed: () => showSubstackNoteActions(context, note),
            icon: const Icon(Icons.more_horiz),
          ),
      ],
    );
  }
}

class _NoteAvatar extends StatelessWidget {
  final SubstackNote note;
  final String name;
  const _NoteAvatar({required this.note, required this.name});
  @override
  Widget build(BuildContext context) {
    final fallback = FallbackAvatar(
      seed: note.authorHandle ?? note.id,
      displayName: name,
      size: 40,
      accent: Theme.of(context).colorScheme.primary,
    );
    final photo = substackDiscussionUrl(note.authorPhotoUrl);
    return ClipOval(
      child: photo == null
          ? fallback
          : ExtendedImage.network(
              photo,
              width: 40,
              height: 40,
              fit: BoxFit.cover,
              cacheWidth: (40 * MediaQuery.devicePixelRatioOf(context)).ceil(),
              excludeFromSemantics: true,
              loadStateChanged: (state) => state.extendedImageLoadState == LoadState.failed ? fallback : null,
            ),
    );
  }
}

class _NoteReactions extends StatelessWidget {
  final SubstackNote note;
  const _NoteReactions({required this.note});
  @override
  Widget build(BuildContext context) {
    final count = note.reactionCount;
    if (count == null || count <= 0) return const SizedBox.shrink();
    final prefs = context.dependOnInheritedWidgetOfExactType<PrefService>()?.service;
    if (prefs?.get(optionCalmMode) == true || prefs?.get(optionZenMode) == true) return const SizedBox.shrink();
    final color = Theme.of(context).colorScheme.onSurfaceVariant;
    return Padding(
      padding: const EdgeInsets.only(top: 12),
      child: Semantics(
        label: '${L10n.of(context).favorites}: $count',
        child: ExcludeSemantics(
          child: Row(
            children: [
              Icon(Icons.favorite_outline, size: 16, color: color),
              const SizedBox(width: 6),
              Text(compactCount(count), style: Theme.of(context).textTheme.bodySmall?.copyWith(color: color)),
            ],
          ),
        ),
      ),
    );
  }
}

class _NotePublication extends StatelessWidget {
  final SubstackPublication publication;
  const _NotePublication({required this.publication});
  Future<void> _follow(BuildContext context) async {
    final store = context.read<SubstackPublicationsStore>();
    final subscriptions = context.read<SubscriptionsModel?>();
    final messenger = ScaffoldMessenger.of(context);
    final l10n = L10n.of(context);
    await store.add(publication);
    final followed = store.state.any((pub) => pub.id == publication.id);
    if (followed) await subscriptions?.reloadSubscriptions();
    if (!context.mounted) return;
    messenger.showSnackBar(
      SnackBar(
        content: Text(
          followed ? l10n.plugin_substack_followed(publication.displayName) : l10n.plugin_substack_load_error,
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final store = context.read<SubstackPublicationsStore>();
    return TripleBuilder<SubstackPublicationsStore, List<SubstackPublication>>(
      store: store,
      builder: (context, triple) {
        final followed = triple.state.any((pub) => pub.id == publication.id);
        return Wrap(
          spacing: 8,
          runSpacing: 4,
          children: [
            ActionChip(
              avatar: const Icon(Icons.newspaper_outlined, size: 16),
              label: Text(publication.displayName, maxLines: 2, overflow: TextOverflow.ellipsis),
              onPressed: () => openSubstackPublication(context, publication),
            ),
            ActionChip(
              key: ValueKey('substack-note-follow-${publication.id}'),
              avatar: Icon(followed ? Icons.check : Icons.add, size: 16),
              label: Text(followed ? l10n.following : l10n.plugin_substack_follow),
              onPressed: followed || triple.isLoading ? null : () => _follow(context),
            ),
            ActionChip(
              avatar: const Icon(Icons.group_add_outlined, size: 16),
              label: Text(l10n.add_to_group),
              onPressed: () => addSubstackPublicationToGroup(context, publication),
            ),
          ],
        );
      },
    );
  }
}
