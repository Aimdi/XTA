import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:intl/intl.dart';
import 'package:share_plus/share_plus.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/settings/crash_log_model.dart';
import 'package:xta/utils/crash_log.dart';
import 'package:xta/utils/crash_log_entry.dart';

/// The screen that ends the guessing.
///
/// Everything the app knows about its own failures, on the device, with a copy
/// and a share button. Nothing is uploaded: the reader decides who sees it.
class CrashLogScreen extends StatefulWidget {
  const CrashLogScreen({super.key});

  @override
  State<CrashLogScreen> createState() => _CrashLogScreenState();
}

class _CrashLogScreenState extends State<CrashLogScreen> {
  late final CrashLogModel _model;

  @override
  void initState() {
    super.initState();
    _model = CrashLogModel(
      CrashLog.instance ?? CrashLog(storage: FileCrashLogStorage()),
    );
    _model.load();
  }

  @override
  void dispose() {
    _model.destroy();
    super.dispose();
  }

  Future<void> _copy(CrashLogView view) async {
    final message = L10n.of(context).crash_log_copied;
    final messenger = ScaffoldMessenger.of(context);
    await Clipboard.setData(ClipboardData(text: view.toPlainText()));
    messenger.showSnackBar(SnackBar(content: Text(message)));
  }

  Future<void> _share(CrashLogView view) async {
    final subject = L10n.of(context).crash_log;
    await SharePlus.instance.share(
      ShareParams(text: view.toPlainText(), subject: subject),
    );
  }

  Future<void> _clear() async {
    final message = L10n.of(context).crash_log_cleared;
    final messenger = ScaffoldMessenger.of(context);
    await _model.clear();
    messenger.showSnackBar(SnackBar(content: Text(message)));
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);

    return Scaffold(
      appBar: AppBar(
        title: Text(l10n.crash_log),
        actions: [
          ScopedBuilder<CrashLogModel, CrashLogView>(
            store: _model,
            onState: (_, view) => _Actions(
              enabled: view.entries.isNotEmpty,
              onCopy: () => _copy(view),
              onShare: () => _share(view),
              onClear: _clear,
              onTestEntry: _model.addTestEntry,
            ),
          ),
        ],
      ),
      body: RefreshIndicator(
        onRefresh: _model.load,
        child: ScopedBuilder<CrashLogModel, CrashLogView>.transition(
          store: _model,
          onLoading: (_) => const Center(child: CircularProgressIndicator()),
          onState: (_, view) => view.entries.isEmpty
              ? const _EmptyLog()
              : _EntryList(entries: view.entries),
        ),
      ),
    );
  }
}

class _Actions extends StatelessWidget {
  final bool enabled;
  final VoidCallback onCopy;
  final VoidCallback onShare;
  final VoidCallback onClear;
  final VoidCallback onTestEntry;

  const _Actions({
    required this.enabled,
    required this.onCopy,
    required this.onShare,
    required this.onClear,
    required this.onTestEntry,
  });

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);

    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        IconButton(
          icon: const Icon(Icons.copy_all),
          tooltip: l10n.crash_log_copy,
          onPressed: enabled ? onCopy : null,
        ),
        IconButton(
          icon: const Icon(Icons.ios_share),
          tooltip: l10n.crash_log_share,
          onPressed: enabled ? onShare : null,
        ),
        PopupMenuButton<VoidCallback>(
          onSelected: (action) => action(),
          itemBuilder: (context) => [
            PopupMenuItem(
              value: onTestEntry,
              child: Text(l10n.crash_log_add_test_entry),
            ),
            PopupMenuItem(
              value: onClear,
              enabled: enabled,
              child: Text(l10n.crash_log_clear),
            ),
          ],
        ),
      ],
    );
  }
}

class _EmptyLog extends StatelessWidget {
  const _EmptyLog();

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final theme = Theme.of(context);

    return ListView(
      padding: const EdgeInsets.fromLTRB(24, 64, 24, 24),
      children: [
        Icon(
          Icons.health_and_safety_outlined,
          size: 56,
          color: theme.colorScheme.onSurfaceVariant,
        ),
        const SizedBox(height: 16),
        Text(
          l10n.crash_log_empty,
          textAlign: TextAlign.center,
          style: theme.textTheme.titleMedium!.copyWith(
            fontWeight: FontWeight.w700,
          ),
        ),
        const SizedBox(height: 8),
        Text(
          l10n.crash_log_empty_description,
          textAlign: TextAlign.center,
          style: theme.textTheme.bodyMedium!.copyWith(
            color: theme.colorScheme.onSurfaceVariant,
          ),
        ),
      ],
    );
  }
}

class _EntryList extends StatelessWidget {
  final List<CrashLogEntry> entries;

  const _EntryList({required this.entries});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final theme = Theme.of(context);
    final dates = DateFormat.yMd(
      Localizations.localeOf(context).toString(),
    ).add_Hms();

    return ListView.separated(
      padding: EdgeInsets.only(
        bottom: 24 + MediaQuery.of(context).padding.bottom,
      ),
      itemCount: entries.length + 1,
      separatorBuilder: (_, _) => const Divider(height: 1),
      itemBuilder: (context, index) {
        if (index == 0) {
          return Padding(
            padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
            child: Text(
              l10n.crash_log_description,
              style: theme.textTheme.bodySmall!.copyWith(
                color: theme.colorScheme.onSurfaceVariant,
              ),
            ),
          );
        }
        return _EntryTile(entry: entries[index - 1], dates: dates);
      },
    );
  }
}

class _EntryTile extends StatelessWidget {
  final CrashLogEntry entry;
  final DateFormat dates;

  const _EntryTile({required this.entry, required this.dates});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final theme = Theme.of(context);

    return ExpansionTile(
      leading: Icon(
        _iconFor(entry.source),
        color: _colorFor(context, entry.source),
      ),
      title: Text(
        crashSourceLabel(l10n, entry.source),
        style: theme.textTheme.titleSmall!.copyWith(
          fontWeight: FontWeight.w700,
        ),
      ),
      subtitle: Text(
        '${dates.format(entry.lastSeen)}'
        '${entry.count > 1 ? ' · ${l10n.crash_log_occurrences(entry.count)}' : ''}',
        style: theme.textTheme.bodySmall,
      ),
      childrenPadding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
      children: [
        SelectableText(
          entry.format(),
          style: theme.textTheme.bodySmall!.copyWith(
            fontFamily: 'monospace',
            height: 1.35,
          ),
        ),
      ],
    );
  }

  IconData _iconFor(CrashSource source) => switch (source) {
    CrashSource.flutter => Icons.broken_image_outlined,
    CrashSource.asyncError => Icons.cloud_off_outlined,
    CrashSource.isolate => Icons.memory_outlined,
    CrashSource.nativeDeath => Icons.power_settings_new,
    CrashSource.test => Icons.science_outlined,
  };

  Color? _colorFor(BuildContext context, CrashSource source) =>
      source == CrashSource.test ? null : Theme.of(context).colorScheme.error;
}

String crashSourceLabel(L10n l10n, CrashSource source) => switch (source) {
  CrashSource.flutter => l10n.crash_log_source_interface,
  CrashSource.asyncError => l10n.crash_log_source_background,
  CrashSource.isolate => l10n.crash_log_source_worker,
  CrashSource.nativeDeath => l10n.crash_log_source_killed,
  CrashSource.test => l10n.crash_log_source_test,
};
