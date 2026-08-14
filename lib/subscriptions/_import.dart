import 'dart:async';

import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:xta/client/client.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/database/repository.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/group/group_model.dart';
import 'package:xta/import_data_model.dart';
import 'package:xta/subscriptions/group_ungrouped_screen.dart';
import 'package:xta/subscriptions/import_handles.dart';
import 'package:xta/subscriptions/users_model.dart';
import 'package:xta/ui/errors.dart';

class SubscriptionImportScreen extends StatefulWidget {
  const SubscriptionImportScreen({super.key});

  @override
  State<SubscriptionImportScreen> createState() =>
      _SubscriptionImportScreenState();
}

class _SubscriptionImportScreenState extends State<SubscriptionImportScreen> {
  final _handles = TextEditingController();
  StreamController<int>? _streamController;
  String? _currentHandle;
  var _finished = false;

  @override
  void dispose() {
    _handles.dispose();
    _streamController?.close();
    super.dispose();
  }

  Future<void> importSubscriptions() async {
    final handles = parseImportHandles(_handles.text);
    if (handles.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(L10n.of(context).import_handles_none)),
      );
      return;
    }

    final importModel = context.read<ImportDataModel>();
    final groupModel = context.read<GroupsModel>();
    final subscriptionsModel = context.read<SubscriptionsModel>();

    setState(() {
      _finished = false;
      _currentHandle = handles.first;
      _streamController = StreamController();
    });

    try {
      var total = 0;
      _streamController?.add(0);
      for (final handle in handles) {
        if (!mounted) return;
        setState(() => _currentHandle = handle);
        total += await _importFollowing(
          handle,
          importModel: importModel,
          createdAt: DateTime.now(),
          onPage: (count) => _streamController?.add(total + count),
        );
        _streamController?.add(total);
      }

      await groupModel.reloadGroups();
      await subscriptionsModel.reloadSubscriptions();
      if (!mounted) return;
      setState(() => _finished = true);
      _streamController?.close();
    } catch (e, stackTrace) {
      _streamController?.addError(e, stackTrace);
    }
  }

  Future<int> _importFollowing(
    String screenName, {
    required ImportDataModel importModel,
    required DateTime createdAt,
    required void Function(int pageCount) onPage,
  }) async {
    String? cursor;
    var total = 0;
    final seenIds = <String>{};
    final userId = (await Twitter.getProfileByScreenName(
      screenName,
    )).user.idStr;

    while (true) {
      final response = await Twitter.getProfileFollows(
        screenName,
        'following',
        cursor: cursor,
        id: userId,
      );
      final next = response.cursorBottom;
      final fresh = response.users
          .where((e) => e.idStr != null && seenIds.add(e.idStr!))
          .toList();
      if (fresh.isNotEmpty) {
        total += fresh.length;
        await importModel.importData({
          tableSubscription: [
            ...fresh.map(
              (e) => UserSubscription(
                id: e.idStr!,
                name: e.name!,
                profileImageUrlHttps: e.profileImageUrlHttps,
                screenName: e.screenName!,
                verified: e.verified ?? false,
                createdAt: createdAt,
                inFeed: true,
              ),
            ),
          ],
        });
        onPage(total);
      }
      if (next == null ||
          next.isEmpty ||
          next == '0' ||
          next == cursor ||
          fresh.isEmpty) {
        break;
      }
      cursor = next;
    }
    return total;
  }

  void _openSortUngrouped() {
    Navigator.push(
      context,
      MaterialPageRoute(builder: (_) => const SortUngroupedScreen()),
    );
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return Scaffold(
      appBar: AppBar(title: Text(l10n.import_subscriptions)),
      body: Padding(
        padding: const EdgeInsets.all(8),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Padding(
              padding: const EdgeInsets.only(bottom: 16),
              child: Text(l10n.import_following_help),
            ),
            Padding(
              padding: const EdgeInsets.only(bottom: 16),
              child: Text(
                l10n.please_note_that_the_method_fritter_uses_to_import_subscriptions_is_heavily_rate_limited_by_twitter_so_this_may_fail_if_you_have_a_lot_of_followed_accounts,
              ),
            ),
            Padding(
              padding: const EdgeInsets.only(bottom: 16),
              child: TextFormField(
                controller: _handles,
                minLines: 3,
                maxLines: 8,
                decoration: InputDecoration(
                  border: const OutlineInputBorder(),
                  hintText: l10n.import_handles_hint,
                  helperText: l10n
                      .to_import_subscriptions_from_an_existing_twitter_account_enter_your_username_below,
                  helperMaxLines: 4,
                  labelText: l10n.username,
                ),
              ),
            ),
            Center(child: _progress(l10n)),
          ],
        ),
      ),
      floatingActionButton: FloatingActionButton(
        child: const Icon(Icons.cloud_download),
        onPressed: () async => await importSubscriptions(),
      ),
    );
  }

  Widget _progress(L10n l10n) {
    return StreamBuilder(
      stream: _streamController?.stream,
      builder: (context, snapshot) {
        final error = snapshot.error;
        if (error != null) {
          return FullPageErrorWidget(
            error: snapshot.error,
            stackTrace: snapshot.stackTrace,
            prefix: l10n.unable_to_import,
          );
        }

        switch (snapshot.connectionState) {
          case ConnectionState.none:
          case ConnectionState.waiting:
            return const SizedBox.shrink();
          case ConnectionState.active:
            return Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                const Padding(
                  padding: EdgeInsets.all(16),
                  child: CircularProgressIndicator(),
                ),
                if (_currentHandle != null)
                  Text(l10n.import_from_account(_currentHandle!)),
                Text(
                  l10n.imported_snapshot_data_users_so_far(
                    snapshot.data.toString(),
                  ),
                ),
              ],
            );
          default:
            return Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                const Padding(
                  padding: EdgeInsets.all(16),
                  child: Icon(
                    Icons.check_circle,
                    size: 36,
                    color: Colors.green,
                  ),
                ),
                Text(
                  l10n.finished_with_snapshotData_users(
                    snapshot.data.toString(),
                  ),
                ),
                if (_finished) ...[
                  const SizedBox(height: 16),
                  FilledButton.tonal(
                    onPressed: _openSortUngrouped,
                    child: Text(l10n.import_sort_ungrouped),
                  ),
                ],
              ],
            );
        }
      },
    );
  }
}
