import 'dart:async';

import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/bluesky/bluesky_client.dart';
import 'package:xta/plugins/bluesky/bluesky_models.dart';
import 'package:xta/plugins/bluesky/bluesky_store.dart';
import 'package:xta/ui/errors.dart';

/// Copies members of a public Bluesky list into local follows.
///
/// Read-only AppView calls only — nothing is written to Bluesky.
class BlueskyImportListScreen extends StatefulWidget {
  final String? initialList;

  const BlueskyImportListScreen({super.key, this.initialList});

  @override
  State<BlueskyImportListScreen> createState() => _BlueskyImportListScreenState();
}

class _BlueskyImportListScreenState extends State<BlueskyImportListScreen> {
  late final TextEditingController _input;
  String? _inputError;
  StreamController<int>? _progress;
  var _running = false;

  @override
  void initState() {
    super.initState();
    _input = TextEditingController(text: widget.initialList ?? '');
  }

  @override
  void dispose() {
    _input.dispose();
    unawaited(_progress?.close());
    super.dispose();
  }

  Future<void> _import() async {
    if (_running) {
      return;
    }

    final ref = parseBlueskyListRef(_input.text);
    if (ref == null) {
      setState(() => _inputError = L10n.of(context).plugin_bluesky_invalid_list);
      return;
    }

    setState(() {
      _inputError = null;
      _running = true;
      _progress = StreamController();
    });
    final progress = _progress!;
    progress.add(0);

    try {
      final client = context.read<BlueskyClient>();
      final accounts = context.read<BlueskyAccountsStore>();
      final listUri = await client.resolveListUri(ref);
      var total = 0;
      String? cursor;

      while (true) {
        final page = await client.getList(listUri, cursor: cursor);
        final added = await accounts.addMany(page.members.map((p) => p.toAccount()));
        total += added;
        progress.add(total);

        final next = page.cursor;
        if (next == null || next.isEmpty || next == cursor || page.members.isEmpty) {
          break;
        }
        cursor = next;
        await Future<void>.delayed(const Duration(milliseconds: 200));
      }

      if (mounted) {
        await context.read<BlueskyFeedStore>().refresh();
      }
      await progress.close();
    } catch (e, stackTrace) {
      progress.addError(e, stackTrace);
    } finally {
      if (mounted) {
        setState(() => _running = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);

    return Scaffold(
      appBar: AppBar(title: Text(l10n.plugin_bluesky_import_list)),
      floatingActionButton: FloatingActionButton(
        onPressed: _running ? null : _import,
        child: const Icon(Icons.cloud_download),
      ),
      body: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(l10n.plugin_bluesky_import_list_intro),
            const SizedBox(height: 12),
            Text(l10n.plugin_bluesky_import_list_note),
            const SizedBox(height: 16),
            TextField(
              controller: _input,
              decoration: InputDecoration(
                border: const OutlineInputBorder(),
                labelText: l10n.plugin_bluesky_import_list,
                hintText: l10n.plugin_bluesky_import_list_hint,
                errorText: _inputError,
              ),
              autocorrect: false,
              onSubmitted: (_) => _import(),
            ),
            const SizedBox(height: 24),
            _BlueskyListImportProgress(stream: _progress?.stream),
          ],
        ),
      ),
    );
  }
}

class _BlueskyListImportProgress extends StatelessWidget {
  final Stream<int>? stream;

  const _BlueskyListImportProgress({this.stream});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    if (stream == null) {
      return const SizedBox.shrink();
    }

    return StreamBuilder<int>(
      stream: stream,
      builder: (context, snapshot) {
        if (snapshot.hasError) {
          return FullPageErrorWidget(
            error: snapshot.error,
            stackTrace: snapshot.stackTrace,
            prefix: l10n.unable_to_import,
          );
        }

        final count = '${snapshot.data ?? 0}';
        if (snapshot.connectionState == ConnectionState.done) {
          return Column(
            children: [
              const Icon(Icons.check_circle, size: 36, color: Colors.green),
              const SizedBox(height: 12),
              Text(l10n.finished_with_snapshotData_users(count)),
            ],
          );
        }

        if (snapshot.connectionState == ConnectionState.active ||
            snapshot.connectionState == ConnectionState.waiting) {
          return Column(
            children: [
              const CircularProgressIndicator(),
              const SizedBox(height: 12),
              Text(l10n.imported_snapshot_data_users_so_far(count)),
            ],
          );
        }

        return const SizedBox.shrink();
      },
    );
  }
}
