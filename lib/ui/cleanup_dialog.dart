import 'package:flutter/material.dart';
import 'package:xta/generated/l10n.dart';

/// Outcome of checking one item against X during a cleanup scan.
sealed class CleanupCheck {}

class CleanupOk extends CleanupCheck {}

/// The item was stale but could be fixed in place (e.g. a renamed account).
class CleanupRepaired extends CleanupCheck {
  final String note;

  CleanupRepaired(this.note);
}

/// The item definitively no longer exists on X.
class CleanupBroken extends CleanupCheck {
  final String? reason;

  CleanupBroken({this.reason});
}

/// The check failed for a transient reason; the item is kept.
class CleanupUnreachable extends CleanupCheck {}

/// The scan must stop early; unchecked items are kept.
class CleanupRateLimited extends CleanupCheck {}

/// Scans [items] one by one with [check], then lists the broken ones and
/// offers to delete them via [onDelete] after confirmation.
class CleanupScanDialog<T> extends StatefulWidget {
  final String title;
  final String checkingLabel;
  final String foundMessage;
  final String noneFoundMessage;
  final String unreachableMessage;
  final String? repairedMessage;
  final List<T> items;
  final Future<CleanupCheck> Function(T item) check;
  final String Function(T item) itemLabel;
  final Future<void> Function(List<T> broken) onDelete;
  final Future<void> Function(int repairedCount)? onScanDone;

  const CleanupScanDialog(
      {super.key,
      required this.title,
      required this.checkingLabel,
      required this.foundMessage,
      required this.noneFoundMessage,
      required this.unreachableMessage,
      this.repairedMessage,
      required this.items,
      required this.check,
      required this.itemLabel,
      required this.onDelete,
      this.onScanDone});

  @override
  State<CleanupScanDialog<T>> createState() => _CleanupScanDialogState<T>();
}

class _CleanupScanDialogState<T> extends State<CleanupScanDialog<T>> {
  final List<({T item, String? reason})> _broken = [];
  final List<String> _repairedNotes = [];
  int _checked = 0;
  int _unreachable = 0;
  bool _done = false;

  @override
  void initState() {
    super.initState();
    _scan();
  }

  Future<void> _scan() async {
    for (final item in widget.items) {
      if (!mounted) {
        return;
      }

      var aborted = false;
      switch (await widget.check(item)) {
        case CleanupOk():
          break;
        case CleanupRepaired(note: final note):
          _repairedNotes.add(note);
        case CleanupBroken(reason: final reason):
          _broken.add((item: item, reason: reason));
        case CleanupUnreachable():
          _unreachable++;
        case CleanupRateLimited():
          aborted = true;
      }

      if (aborted || !mounted) {
        break;
      }

      setState(() {
        _checked++;
      });

      await Future.delayed(const Duration(milliseconds: 200));
    }

    if (!mounted) {
      return;
    }
    await widget.onScanDone?.call(_repairedNotes.length);
    if (mounted) {
      setState(() {
        _unreachable += widget.items.length - _checked;
        _done = true;
      });
    }
  }

  Future<void> _deleteBroken() async {
    final navigator = Navigator.of(context);

    await widget.onDelete(_broken.map((e) => e.item).toList());

    if (mounted) {
      navigator.pop();
    }
  }

  Widget _buildProgress(BuildContext context) {
    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        LinearProgressIndicator(
          value: widget.items.isEmpty ? null : _checked / widget.items.length,
        ),
        const SizedBox(height: 16),
        Text('${widget.checkingLabel} ($_checked / ${widget.items.length})'),
      ],
    );
  }

  Widget _buildHint(BuildContext context, String text) {
    return Padding(
      padding: const EdgeInsets.only(top: 8),
      child: Text(
        text,
        style: TextStyle(color: Theme.of(context).hintColor, fontSize: 13),
      ),
    );
  }

  Widget _buildBrokenList(BuildContext context) {
    return Flexible(
      child: SizedBox(
        width: double.maxFinite,
        child: ListView.builder(
          shrinkWrap: true,
          itemCount: _broken.length,
          itemBuilder: (context, index) {
            final broken = _broken[index];
            return ListTile(
              dense: true,
              contentPadding: EdgeInsets.zero,
              title: Text(widget.itemLabel(broken.item), maxLines: 2, overflow: TextOverflow.ellipsis),
              subtitle: broken.reason == null ? null : Text(broken.reason!),
            );
          },
        ),
      ),
    );
  }

  Widget _buildResults(BuildContext context) {
    final repairedMessage = widget.repairedMessage;

    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        if (_broken.isEmpty) Text(widget.noneFoundMessage),
        if (_broken.isNotEmpty) Text(widget.foundMessage),
        if (_broken.isNotEmpty) const SizedBox(height: 8),
        if (_broken.isNotEmpty) _buildBrokenList(context),
        if (_repairedNotes.isNotEmpty && repairedMessage != null)
          _buildHint(context, '$repairedMessage\n${_repairedNotes.join('\n')}'),
        if (_unreachable > 0) _buildHint(context, widget.unreachableMessage),
      ],
    );
  }

  @override
  Widget build(BuildContext context) {
    final actions = _done && _broken.isNotEmpty
        ? [
            TextButton(
              onPressed: () => Navigator.pop(context),
              child: Text(L10n.of(context).cancel),
            ),
            TextButton(
              onPressed: _deleteBroken,
              child: Text('${L10n.of(context).delete} (${_broken.length})'),
            ),
          ]
        : [
            TextButton(
              onPressed: () => Navigator.pop(context),
              child: Text(_done ? L10n.of(context).close : L10n.of(context).cancel),
            ),
          ];

    return AlertDialog(
      title: Text(widget.title),
      content: _done ? _buildResults(context) : _buildProgress(context),
      actions: actions,
    );
  }
}
