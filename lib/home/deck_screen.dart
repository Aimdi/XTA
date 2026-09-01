import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/group/_feed_shell.dart';
import 'package:xta/group/deck_groups.dart';
import 'package:xta/group/feed_refresh_controller.dart';
import 'package:xta/group/group_model.dart' show GroupModel, GroupsModel;
import 'package:xta/group/group_screen.dart';

class DeckScreen extends StatelessWidget {
  const DeckScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final prefs = PrefService.of(context);
    final ids = parseDeckGroupIds(prefs.get(optionDeckGroupIds) as String?);
    final model = context.read<GroupsModel>();

    return Scaffold(
      appBar: AppBar(title: Text(L10n.of(context).deck_title)),
      body: ScopedBuilder<GroupsModel, List<SubscriptionGroup>>(
        store: model,
        onState: (_, groups) {
          final pinned = [for (final id in ids) ...groups.where((g) => g.id == id)];
          if (pinned.isEmpty) {
            return Center(child: Text(L10n.of(context).deck_empty));
          }

          return Row(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              for (var i = 0; i < pinned.length; i++) ...[
                if (i > 0) const VerticalDivider(width: 1),
                Expanded(child: _DeckColumn(group: pinned[i])),
              ],
            ],
          );
        },
      ),
    );
  }
}

class _DeckColumn extends StatefulWidget {
  final SubscriptionGroup group;

  const _DeckColumn({required this.group});

  @override
  State<_DeckColumn> createState() => _DeckColumnState();
}

class _DeckColumnState extends State<_DeckColumn> {
  late final ScrollController _scrollController;
  late final GroupModel _groupModel;
  final FeedRefreshController _refreshController = FeedRefreshController();

  @override
  void initState() {
    super.initState();
    _scrollController = ScrollController();
    _groupModel = GroupModel(widget.group.id)..loadGroup();
  }

  @override
  void dispose() {
    _scrollController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Provider<GroupModel>.value(
      value: _groupModel,
      child: Provider<FeedRefreshController>.value(
        value: _refreshController,
        child: GroupFeedShell(
          scrollController: _scrollController,
          groupId: widget.group.id,
          titleBuilder: (context) => Text(widget.group.name, maxLines: 1, overflow: TextOverflow.ellipsis),
          bodyBuilder: (context) => SubscriptionGroupScreenContent(id: widget.group.id),
          actionsBuilder: (context) => const [],
        ),
      ),
    );
  }
}

bool isDeckEligible(BuildContext context) {
  final size = MediaQuery.sizeOf(context);
  return size.shortestSide >= 600 || size.width >= 900;
}
