import 'package:xta/plugins/plugin_registry.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/group/group_model.dart';
import 'package:xta/subscriptions/users_model.dart';
import 'package:xta/utils/local_undo.dart';

class UndoHost extends StatefulWidget {
  final Widget child;
  const UndoHost({super.key, required this.child});
  @override
  State<UndoHost> createState() => _UndoHostState();
}

class _UndoHostState extends State<UndoHost> {
  late final void Function() _disposeObserver;
  @override
  void initState() {
    super.initState();
    _disposeObserver = UndoStore.shared.observer(
      onState: (action) {
        if (action == null) return;
        WidgetsBinding.instance.addPostFrameCallback((_) {
          if (!mounted || !identical(UndoStore.shared.state, action)) return;
          final messenger = ScaffoldMessenger.of(context);
          final l10n = L10n.of(context);
          messenger.removeCurrentSnackBar();
          messenger.showSnackBar(
            SnackBar(
              content: Text(l10n.reader_changes_saved),
              duration: const Duration(seconds: 8),
              action: SnackBarAction(
                label: l10n.reader_undo,
                onPressed: () async {
                  UndoStore.shared.clear();
                  try {
                    if (!await action.restore()) throw StateError('Undo expired or superseded');
                    if (!mounted) return;
                    final subscriptions = context.read<SubscriptionsModel>();
                    final groups = context.read<GroupsModel>();
                    await subscriptions.reloadSubscriptions();
                    await groups.reloadGroups();
                    for (final source in subscriptionSources) {
                      if (!mounted) return;
                      await source.reloadFromDatabase(context);
                    }
                  } catch (_) {
                    if (mounted) messenger.showSnackBar(SnackBar(content: Text(l10n.reader_undo_unavailable)));
                  }
                },
              ),
            ),
          );
        });
      },
    );
  }

  @override
  void dispose() {
    _disposeObserver();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => widget.child;
}
