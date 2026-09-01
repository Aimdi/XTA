import 'dart:async';

import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/bluesky/bluesky_client.dart';
import 'package:xta/plugins/bluesky/bluesky_models.dart';
import 'package:xta/plugins/bluesky/bluesky_store.dart';
import 'package:xta/ui/errors.dart';

/// Copies a public account's following graph into local Bluesky follows.
///
/// Read-only AppView calls only — nothing is written to Bluesky.
class BlueskyImportFollowsScreen extends StatefulWidget {
  const BlueskyImportFollowsScreen({super.key});

  @override
  State<BlueskyImportFollowsScreen> createState() => _BlueskyImportFollowsScreenState();
}

class _BlueskyImportFollowsScreenState extends State<BlueskyImportFollowsScreen> {
  final _actor = TextEditingController();
  StreamController<int>? _progress;
  var _running = false;
  String? _inputError;

  @override
  void dispose() {
    _actor.dispose();
    unawaited(_progress?.close());
    super.dispose();
  }

  Future<void> _import() async {
    if (_running) {
      return;
    }

    // A handle that cannot be read used to return here in silence: no message,
    // no spinner, nothing at all, however many times the button was pressed.
    // The `@` prefix on the field invites a bare name, and a bare name is
    // exactly what this rejects — so the commonest input did the least. The
    // list-import screen beside this one has always said so.
    final actor = normaliseBlueskyHandle(_actor.text);
    if (actor == null) {
      setState(() => _inputError = L10n.of(context).plugin_bluesky_invalid_handle);
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
      var total = 0;
      String? cursor;

      while (true) {
        final page = await client.getFollows(actor, cursor: cursor);
        final added = await accounts.addMany(page.follows.map((p) => p.toAccount()));
        total += added;
        progress.add(total);

        final next = page.cursor;
        if (next == null || next.isEmpty || next == cursor || page.follows.isEmpty) {
          break;
        }
        cursor = next;
        // Stay polite on the public AppView — never burst pages.
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
      appBar: AppBar(title: Text(l10n.plugin_bluesky_import_following)),
      floatingActionButton: FloatingActionButton(
        onPressed: _running ? null : _import,
        child: const Icon(Icons.cloud_download),
      ),
      body: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(l10n.plugin_bluesky_import_following_intro),
            const SizedBox(height: 12),
            Text(l10n.plugin_bluesky_import_following_note),
            const SizedBox(height: 16),
            TextField(
              controller: _actor,
              decoration: InputDecoration(
                border: const OutlineInputBorder(),
                labelText: l10n.plugin_bluesky_add,
                hintText: l10n.plugin_bluesky_handle_hint,
                prefixText: '@',
                errorText: _inputError,
              ),
              // Clears the complaint as soon as the reader acts on it, rather
              // than leaving it under a field they have already corrected.
              onChanged: (_) {
                if (_inputError != null) {
                  setState(() => _inputError = null);
                }
              },
              autocorrect: false,
              onSubmitted: (_) => _import(),
            ),
            const SizedBox(height: 24),
            _BlueskyImportProgress(stream: _progress?.stream),
          ],
        ),
      ),
    );
  }
}

class _BlueskyImportProgress extends StatelessWidget {
  final Stream<int>? stream;

  const _BlueskyImportProgress({this.stream});

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
