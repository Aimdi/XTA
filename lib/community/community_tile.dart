import 'package:flutter/material.dart';
import 'package:xta/client/client.dart';
import 'package:xta/community/community_screen.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/ui/capped_network_image.dart';

/// A community in a list: its banner, name and member count. Opens its page.
class CommunityTile extends StatelessWidget {
  final Community community;

  const CommunityTile({super.key, required this.community});

  @override
  Widget build(BuildContext context) {
    return ListTile(
      leading: ClipRRect(
        borderRadius: BorderRadius.circular(8),
        child: SizedBox.square(
          dimension: 48,
          child: community.bannerUrl == null
              ? ColoredBox(
                  color: Theme.of(context).colorScheme.surfaceContainerHighest,
                  child: const Icon(Icons.groups_outlined),
                )
              : CappedNetworkImage(url: community.bannerUrl!),
        ),
      ),
      title: Text(community.name, maxLines: 1, overflow: TextOverflow.ellipsis),
      subtitle: Text(L10n.of(context).group_member_count(community.memberCount)),
      onTap: () => Navigator.pushNamed(context, routeCommunity, arguments: CommunityScreenArguments(community.id)),
    );
  }
}
