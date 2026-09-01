import 'dart:async';

import 'package:flutter/material.dart';

import 'package:xta/client/client.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/database/repository.dart';
import 'package:xta/group/group_model.dart';
import 'package:xta/import_data_model.dart';
import 'package:xta/subscriptions/users_model.dart';
import 'package:xta/ui/errors.dart';
import 'package:xta/utils/urls.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';

/// Thrown when ListByRestId returns no list (deleted, or private and thus
/// inaccessible); rendered through the standard error widget.
class ListNotFoundException implements Exception {
  @override
  String toString() {
    return L10n.current.list_not_found;
  }
}

class ListImportScreen extends StatefulWidget {
  final String? initialListId;

  const ListImportScreen({super.key, this.initialListId});

  @override
  State<ListImportScreen> createState() => _ListImportScreenState();
}

class _ListImportScreenState extends State<ListImportScreen> {
  late String _input = widget.initialListId ?? '';
  String? _inputError;
  StreamController<int>? _streamController;

  Future importList() async {
    final listId = extractListId(_input);
    if (listId == null) {
      setState(() => _inputError = L10n.of(context).invalid_list_url_or_id);
      return;
    }

    setState(() {
      _inputError = null;
      _streamController = StreamController();
    });

    try {
      _streamController?.add(0);

      var importModel = context.read<ImportDataModel>();
      var groupModel = context.read<GroupsModel>();
      var subscriptionsModel = context.read<SubscriptionsModel>();

      var details = await Twitter.getListDetails(listId);
      var listName = details.name;
      if (listName == null) {
        throw ListNotFoundException();
      }

      // Already-followed accounts join the group but are not re-inserted, so
      // their subscription date and in-feed flag survive the import.
      var existing = subscriptionsModel.state.map((e) => e.id).toSet();

      String? cursor;
      int total = 0;
      var memberIds = <String>{};
      var createdAt = DateTime.now();

      while (true) {
        var response = await Twitter.getListMembers(listId, cursor: cursor);

        var next = response.cursorBottom;
        var fresh = response.users
            .where((e) => e.idStr != null && e.name != null && e.screenName != null && memberIds.add(e.idStr!))
            .toList();

        if (fresh.isNotEmpty) {
          total = total + fresh.length;
          await importModel.importData({
            tableSubscription: [
              ...fresh.where((e) => !existing.contains(e.idStr)).map((e) => UserSubscription(
                  id: e.idStr!,
                  name: e.name!,
                  profileImageUrlHttps: e.profileImageUrlHttps,
                  screenName: e.screenName!,
                  verified: e.verified ?? false,
                  createdAt: createdAt,
                  inFeed: true))
            ]
          });

          _streamController?.add(total);
        }

        if (next == null || next.isEmpty || next == '0' || next == cursor || fresh.isEmpty) {
          break;
        }
        cursor = next;
      }

      await subscriptionsModel.reloadSubscriptions();
      await groupModel.saveGroup(null, listName, defaultGroupIcon, null, memberIds);
      _streamController?.close();
    } catch (e, stackTrace) {
      _streamController?.addError(e, stackTrace);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text(L10n.of(context).import_list_as_group)),
      body: Padding(
          padding: EdgeInsets.all(8.0),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Padding(
                padding: const EdgeInsets.only(bottom: 16),
                child: Text(
                  L10n.of(context).to_import_a_list_enter_its_url_or_id_below,
                ),
              ),
              Padding(
                padding: const EdgeInsets.only(bottom: 16),
                child: Text(
                  L10n.of(context).please_note_that_importing_a_large_list_is_heavily_rate_limited,
                ),
              ),
              Padding(
                padding: const EdgeInsets.only(bottom: 16),
                child: TextFormField(
                  initialValue: _input,
                  decoration: InputDecoration(
                    border: const OutlineInputBorder(),
                    hintText: L10n.of(context).enter_a_list_url_or_id,
                    labelText: L10n.of(context).list_url_or_id,
                    errorText: _inputError,
                  ),
                  onChanged: (value) {
                    setState(() {
                      _input = value;
                    });
                  },
                ),
              ),
              Center(
                child: StreamBuilder(
                  stream: _streamController?.stream,
                  builder: (context, snapshot) {
                    var error = snapshot.error;
                    if (error != null) {
                      return FullPageErrorWidget(
                        error: snapshot.error,
                        stackTrace: snapshot.stackTrace,
                        prefix: L10n.of(context).unable_to_import,
                      );
                    }

                    switch (snapshot.connectionState) {
                      case ConnectionState.none:
                      case ConnectionState.waiting:
                        return Container();
                      case ConnectionState.active:
                        return Column(
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            const Padding(
                              padding: EdgeInsets.all(16),
                              child: CircularProgressIndicator(),
                            ),
                            Text(
                              L10n.of(context).imported_snapshot_data_users_so_far(
                                snapshot.data.toString(),
                              ),
                            )
                          ],
                        );
                      default:
                        return Column(
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            const Padding(
                              padding: EdgeInsets.all(16),
                              child: Icon(Icons.check_circle, size: 36, color: Colors.green),
                            ),
                            Text(
                              L10n.of(context).finished_with_snapshotData_users(
                                snapshot.data.toString(),
                              ),
                            )
                          ],
                        );
                    }
                  },
                ),
              ),
            ],
          )),
      floatingActionButton: FloatingActionButton(
        child: const Icon(Icons.cloud_download),
        onPressed: () async => await importList(),
      ),
    );
  }
}
