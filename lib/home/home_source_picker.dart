import 'package:xta/group/feed_read_position.dart';
import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/group/group_model.dart';
import 'package:xta/group/group_unread_store.dart';
import 'package:xta/home/_feed.dart';
import 'package:xta/home/alt_microblogging.dart';
import 'package:xta/home/feed_strip_add_sheet.dart';
import 'package:xta/home/feed_strip_store.dart';
import 'package:xta/home/home_group_drawer.dart';
import 'package:xta/home/home_timeline_picker.dart';
import 'package:xta/subscriptions/group_identity.dart';

/// Both Home entry points use the same live selection surface.
Future<HomeTimelineSelection?> showHomeSourcePicker(BuildContext context) async {
  final strip = context.read<FeedStripStore>();
  final groups = context.read<GroupsModel>();
  final tabs = context.read<FeedTabStore>();
  final prefs = PrefService.of(context, listen: false);
  final unreadStore = maybeGroupUnreadStore(context);
  final groupingStore = context.read<AltMicrobloggingStore?>();
  final picked = await showModalBottomSheet<HomeTimelineSelection>(
    context: context,
    isScrollControlled: true,
    useSafeArea: true,
    showDragHandle: true,
    builder: (_) => _PickerSources(
      unread: unreadStore,
      grouping: groupingStore,
      child: ScopedBuilder<FeedStripStore, List<String>>(
        store: strip,
        onState: (context, pins) => ScopedBuilder<GroupsModel, List<SubscriptionGroup>>(
          store: groups,
          onState: (context, items) => GroupUnreadScope(
            builder: (context, unread) => AltMicrobloggingScope(
              builder: (context, grouped) => HomeTimelinePicker(
                selected: tabs.state.id,
                groupMicroblogs: grouped,
                rememberedMicroblog: prefs.get<String>(optionAltMicrobloggingLastSource),
                options: [
                  for (final source in availableFeedTabsFromIds(pins, prefs))
                    HomeTimelineOption(
                      id: source.id.id,
                      label: source.titleBuilder(context),
                      mark: source.mark ?? Icon(source.icon),
                      plugin: source.id.isPlugin,
                      unread: unread.contains(
                        source.id == FeedTab.following
                            ? feedKeyFollowing
                            : source.id == FeedTab.x
                            ? feedKeyForYou
                            : source.id.id,
                      ),
                    ),
                ],
                groups: [
                  for (final group in drawerGroupsForQuery(items, ''))
                    HomeTimelineOption(
                      id: group.id,
                      label: group.name,
                      mark: GroupMark.forGroup(group, size: 24),
                      plugin: false,
                      unread: unread.contains(group.id),
                    ),
                ],
              ),
            ),
          ),
        ),
      ),
    ),
  );
  if (!context.mounted || picked == null || picked.groupId != null || picked.id != null) return picked;
  final added = await showFeedStripAddSheet(context);
  return context.mounted && added != null ? HomeTimelineSelection.source(added) : null;
}

class _PickerSources extends StatelessWidget {
  final GroupUnreadStore? unread;
  final AltMicrobloggingStore? grouping;
  final Widget child;
  const _PickerSources({required this.unread, required this.grouping, required this.child});
  @override
  Widget build(BuildContext context) {
    Widget result = child;
    if (unread != null) result = Provider<GroupUnreadStore>.value(value: unread!, child: result);
    if (grouping != null) result = Provider<AltMicrobloggingStore>.value(value: grouping!, child: result);
    return result;
  }
}
