import 'package:flutter/material.dart';

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

/// Horizontal chips of people the current feed just showed.
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
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 12, 0, 8),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            title,
            style: theme.textTheme.titleSmall?.copyWith(
              fontWeight: FontWeight.w700,
            ),
          ),
          const SizedBox(height: 8),
          SizedBox(
            height: 48,
            child: ListView.separated(
              scrollDirection: Axis.horizontal,
              padding: const EdgeInsets.only(right: 16),
              itemCount: people.length,
              separatorBuilder: (_, _) => const SizedBox(width: 8),
              itemBuilder: (context, index) {
                final person = people[index];
                return Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    ActionChip(
                      avatar: avatar(person),
                      label: Text(
                        '@${person.handle}',
                        overflow: TextOverflow.ellipsis,
                      ),
                      onPressed: () => onOpen(person),
                    ),
                    TextButton(
                      onPressed: () => onFollow(person),
                      child: Text(followLabel),
                    ),
                  ],
                );
              },
            ),
          ),
        ],
      ),
    );
  }
}
