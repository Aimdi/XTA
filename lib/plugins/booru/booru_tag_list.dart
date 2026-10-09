import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/booru/booru_labels.dart';
import 'package:xta/plugins/booru/booru_models.dart';
import 'package:xta/plugins/booru/booru_store.dart';
import 'package:xta/plugins/booru/booru_tag_style.dart';

typedef BooruTagGroup = ({BooruTagCategory? category, List<String> tags});

const booruTagCategoryOrder = [
  BooruTagCategory.artist,
  BooruTagCategory.copyright,
  BooruTagCategory.character,
  BooruTagCategory.species,
  BooruTagCategory.general,
  BooruTagCategory.meta,
];

/// [tags] under their kinds, in the order boorus list them. Without [kinds]
/// they stay one group; a tag missing from [kinds] counts as general.
List<BooruTagGroup> booruTagGroups(List<String> tags, Map<String, BooruTagCategory> kinds) {
  if (tags.isEmpty) return const [];
  if (kinds.isEmpty) return [(category: null, tags: tags)];
  BooruTagCategory kindOf(String tag) => kinds[tag] ?? BooruTagCategory.general;
  return [
    for (final category in booruTagCategoryOrder)
      if (tags.where((tag) => kindOf(tag) == category).toList() case final members when members.isNotEmpty)
        (category: category, tags: members),
  ];
}

/// Artist tags that name no artist.
const _notAnArtist = {
  'anonymous_artist',
  'artist_request',
  'avoid_posting',
  'banned_artist',
  'conditional_dnp',
  'sound_warning',
  'third-party_edit',
  'unknown_artist',
  'unknown_artist_signature',
};

/// The post's first real artist tag, for "More from".
String? booruArtistTag(List<String> tags, Map<String, BooruTagCategory> kinds) =>
    tags.where((tag) => kinds[tag] == BooruTagCategory.artist && !_notAnArtist.contains(tag)).firstOrNull;

/// A post's tags grouped by kind, each one opening its actions.
class BooruTagGroups extends StatelessWidget {
  final List<BooruTagGroup> groups;
  final void Function(String tag, BooruTagCategory? category) onTap;

  const BooruTagGroups({super.key, required this.groups, required this.onTap});

  @override
  Widget build(BuildContext context) {
    return ScopedBuilder<BooruTagsStore, List<String>>(
      store: context.read<BooruTagsStore>(),
      onState: (context, followed) => ScopedBuilder<BooruMuteStore, Set<String>>(
        store: context.read<BooruMuteStore>(),
        onState: (context, muted) => Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [for (final group in groups) _group(context, group, followed.toSet(), muted)],
        ),
      ),
    );
  }

  Widget _group(BuildContext context, BooruTagGroup group, Set<String> followed, Set<String> muted) {
    final theme = Theme.of(context);
    final color = booruTagColor(group.category, theme.colorScheme);
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 8, 16, 0),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            booruTagCategoryLabel(L10n.of(context), group.category),
            style: theme.textTheme.titleSmall?.copyWith(color: color ?? theme.colorScheme.primary),
          ),
          const SizedBox(height: 4),
          Wrap(
            spacing: 6,
            runSpacing: 6,
            children: [
              for (final tag in group.tags)
                ActionChip(
                  key: ValueKey('booru-post-tag-$tag'),
                  avatar: _mark(tag, followed, muted),
                  label: Text(tag, style: TextStyle(color: color)),
                  materialTapTargetSize: MaterialTapTargetSize.padded,
                  onPressed: () => onTap(tag, group.category),
                ),
            ],
          ),
        ],
      ),
    );
  }

  Widget? _mark(String tag, Set<String> followed, Set<String> muted) {
    if (followed.contains(tag)) return const Icon(Icons.check, size: 16);
    if (muted.contains(tag)) return const Icon(Icons.visibility_off_outlined, size: 16);
    return null;
  }
}
