import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/booru/booru_labels.dart';
import 'package:xta/plugins/booru/booru_models.dart';
import 'package:xta/plugins/booru/booru_store.dart';
import 'package:xta/plugins/booru/booru_tag_style.dart';
import 'package:xta/plugins/plugin_tag_chip.dart';

typedef BooruTagGroup = ({BooruTagCategory? category, List<String> tags});

enum BooruTagSort {
  name,
  count;

  static BooruTagSort parse(String? raw) => raw == count.name ? count : name;
}

const booruTagCategoryOrder = [
  BooruTagCategory.artist,
  BooruTagCategory.copyright,
  BooruTagCategory.character,
  BooruTagCategory.species,
  BooruTagCategory.general,
  BooruTagCategory.meta,
];

/// [tags] under their kinds, in the order boorus list them, each group sorted
/// by [sort]. Without any kinds they stay one group; a tag whose kind is
/// missing counts as general.
List<BooruTagGroup> booruTagGroups(
  List<String> tags,
  Map<String, BooruTagInfo> info, {
  BooruTagSort sort = BooruTagSort.name,
}) {
  if (tags.isEmpty) return const [];
  final sorted = [...tags]..sort((a, b) => _compare(a, b, info, sort));
  if (!tags.any((tag) => info[tag]?.category != null)) return [(category: null, tags: sorted)];
  BooruTagCategory kindOf(String tag) => info[tag]?.category ?? BooruTagCategory.general;
  return [
    for (final category in booruTagCategoryOrder)
      if (sorted.where((tag) => kindOf(tag) == category).toList() case final members when members.isNotEmpty)
        (category: category, tags: members),
  ];
}

/// Most used first; tags the host gave no count for go last.
int _compare(String a, String b, Map<String, BooruTagInfo> info, BooruTagSort sort) {
  if (sort == BooruTagSort.count) {
    final byCount = (info[b]?.postCount ?? -1).compareTo(info[a]?.postCount ?? -1);
    if (byCount != 0) return byCount;
  }
  return a.compareTo(b);
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
String? booruArtistTag(List<String> tags, Map<String, BooruTagInfo> info) =>
    tags.where((tag) => info[tag]?.category == BooruTagCategory.artist && !_notAnArtist.contains(tag)).firstOrNull;

/// A post's tags grouped by kind as tinted chips with their post counts, each
/// opening its actions. Sorts by name or by use once the host sent counts.
class BooruTagSection extends StatelessWidget {
  final List<String> tags;
  final Map<String, BooruTagInfo> info;
  final void Function(String tag, BooruTagCategory? category) onTap;

  const BooruTagSection({super.key, required this.tags, required this.info, required this.onTap});

  @override
  Widget build(BuildContext context) {
    final prefs = context.dependOnInheritedWidgetOfExactType<PrefService>()?.service;
    final counted = info.values.any((tag) => tag.postCount != null);
    final sort = counted ? BooruTagSort.parse(prefs?.get<String>(optionPluginBooruTagSort)) : BooruTagSort.name;
    final groups = booruTagGroups(tags, info, sort: sort);
    if (groups.isEmpty) return const SizedBox.shrink();
    return ScopedBuilder<BooruTagsStore, List<String>>(
      store: context.read<BooruTagsStore>(),
      onState: (context, followed) => ScopedBuilder<BooruMuteStore, Set<String>>(
        store: context.read<BooruMuteStore>(),
        onState: (context, muted) => Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            _header(context, sort, counted ? prefs : null),
            for (final group in groups)
              _BooruTagGroupView(
                group: group,
                info: info,
                titled: group.category != null,
                followed: followed.toSet(),
                muted: muted,
                onTap: onTap,
              ),
          ],
        ),
      ),
    );
  }

  Widget _header(BuildContext context, BooruTagSort sort, BasePrefService? prefs) {
    final l10n = L10n.of(context);
    final theme = Theme.of(context);
    final next = sort == BooruTagSort.name ? BooruTagSort.count : BooruTagSort.name;
    return Padding(
      padding: const EdgeInsetsDirectional.fromSTEB(16, 8, 4, 0),
      child: Row(
        children: [
          Expanded(child: Text(l10n.plugin_booru_tags, style: theme.textTheme.titleMedium)),
          if (prefs != null)
            Tooltip(
              message: l10n.plugin_booru_tag_sort,
              child: TextButton.icon(
                key: const ValueKey('booru-tag-sort'),
                onPressed: () => prefs.set(optionPluginBooruTagSort, next.name),
                icon: const Icon(Icons.swap_vert, size: 18),
                label: Text(
                  sort == BooruTagSort.name ? l10n.plugin_booru_tag_sort_name : l10n.plugin_booru_tag_sort_count,
                ),
              ),
            ),
        ],
      ),
    );
  }
}

class _BooruTagGroupView extends StatelessWidget {
  final BooruTagGroup group;
  final Map<String, BooruTagInfo> info;
  final bool titled;
  final Set<String> followed;
  final Set<String> muted;
  final void Function(String tag, BooruTagCategory? category) onTap;

  const _BooruTagGroupView({
    required this.group,
    required this.info,
    required this.titled,
    required this.followed,
    required this.muted,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 4, 16, 0),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if (titled)
            Padding(
              padding: const EdgeInsets.only(top: 8),
              child: Text(
                booruTagCategoryLabel(L10n.of(context), group.category),
                style: theme.textTheme.titleSmall?.copyWith(color: booruTagColor(group.category, theme.colorScheme)),
              ),
            ),
          Wrap(spacing: 6, children: [for (final tag in group.tags) _chip(context, tag)]),
        ],
      ),
    );
  }

  Widget _chip(BuildContext context, String tag) {
    final name = booruTagDisplayName(tag);
    final count = info[tag]?.postCount;
    final locale = Localizations.localeOf(context).toString();
    return PluginTagChip(
      key: ValueKey('booru-post-tag-$tag'),
      label: name,
      detail: count == null ? null : pluginCompactCount(count, locale),
      kind: booruTagKind(group.category),
      marker: followed.contains(tag)
          ? Icons.check
          : muted.contains(tag)
          ? Icons.visibility_off_outlined
          : null,
      semanticsLabel: count == null ? name : '$name, ${L10n.of(context).plugin_booru_tag_count(count)}',
      onPressed: () => onTap(tag, group.category),
    );
  }
}
