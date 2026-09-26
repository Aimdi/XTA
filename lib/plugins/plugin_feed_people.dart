import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:xta/plugins/plugin_home_chrome.dart';

/// One unfollowed person surfaced from a plugin feed (often via a repost).
class PluginFeedPerson {
  final String handle;
  final String name;
  final String? avatarUrl;
  final bool fromRepost;

  const PluginFeedPerson({
    required this.handle,
    required this.name,
    this.avatarUrl,
    this.fromRepost = false,
  });
}

/// People suggestions are secondary to reading, but never removed from Home.
class PluginFeedPeopleStrip extends StatelessWidget {
  final String title;
  final String followLabel;
  final List<PluginFeedPerson> people;
  final Widget Function(PluginFeedPerson person) avatar;
  final ValueChanged<PluginFeedPerson> onOpen;
  final ValueChanged<PluginFeedPerson> onFollow;

  const PluginFeedPeopleStrip({
    super.key,
    required this.title,
    required this.followLabel,
    required this.people,
    required this.avatar,
    required this.onOpen,
    required this.onFollow,
  });

  @override
  Widget build(BuildContext context) {
    if (people.isEmpty) return const SizedBox.shrink();
    final theme = Theme.of(context);
    if (PluginEmbedded.maybeOf(context)) {
      return Material(
        color: theme.scaffoldBackgroundColor,
        child: ExpansionTile(
          key: PageStorageKey('plugin-feed-people-$title'),
          minTileHeight: 48,
          visualDensity: VisualDensity.standard,
          tilePadding: const EdgeInsetsDirectional.only(start: 12, end: 12),
          childrenPadding: const EdgeInsets.only(bottom: 4),
          shape: const Border(),
          collapsedShape: const Border(),
          leading: Badge.count(count: people.length, child: const Icon(Icons.people_outline, size: 20)),
          title: Text(title, maxLines: 1, overflow: TextOverflow.ellipsis, style: theme.textTheme.labelLarge),
          children: [_peopleRow(context)],
        ),
      );
    }
    return Padding(
      padding: const EdgeInsetsDirectional.fromSTEB(16, 12, 0, 8),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(title, style: theme.textTheme.titleSmall?.copyWith(fontWeight: FontWeight.w700)),
          const SizedBox(height: 8),
          _peopleRow(context),
        ],
      ),
    );
  }

  Widget _peopleRow(BuildContext context) {
    final style = Theme.of(context).textTheme.labelLarge;
    final height = math.max(
      48.0,
      MediaQuery.textScalerOf(context).scale(style?.fontSize ?? 14) * (style?.height ?? 1.4) + 24,
    );
    return SizedBox(
      height: height,
      child: ListView.separated(
        scrollDirection: Axis.horizontal,
        primary: false,
        padding: EdgeInsetsDirectional.only(start: PluginEmbedded.maybeOf(context) ? 12 : 0, end: 16),
        itemCount: people.length,
        separatorBuilder: (_, _) => const SizedBox(width: 8),
        itemBuilder: (context, index) => _personActions(context, people[index]),
      ),
    );
  }

  Widget _personActions(BuildContext context, PluginFeedPerson person) => Row(
    mainAxisSize: MainAxisSize.min,
    children: [
      ActionChip(
        avatar: avatar(person),
        label: Text('@${person.handle}', style: Theme.of(context).textTheme.labelLarge),
        materialTapTargetSize: MaterialTapTargetSize.padded,
        onPressed: () => onOpen(person),
      ),
      TextButton(
        style: TextButton.styleFrom(minimumSize: const Size(48, 48)),
        onPressed: () => onFollow(person),
        child: Text(followLabel),
      ),
    ],
  );
}
