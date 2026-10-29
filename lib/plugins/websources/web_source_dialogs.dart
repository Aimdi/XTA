/// The dialogs of web sources: agreeing to a source's sites, why a file was
/// refused, removing a source, and waiting for a source to be read.
library;

import 'dart:async';

import 'package:flutter/material.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/websources/web_source_client.dart';
import 'package:xta/plugins/websources/web_source_definition.dart';
import 'package:xta/plugins/websources/web_source_mark.dart';

/// Runs [work] behind a dialog saying the source is being read. Null when
/// it fails, or when the reader closes the dialog first.
Future<T?> withWebSourceProgress<T>(BuildContext context, Future<T> Function() work) async {
  final navigator = Navigator.of(context);
  var closed = false;
  unawaited(
    showDialog<void>(
      context: context,
      builder: (context) => AlertDialog(
        content: Row(
          children: [
            const CircularProgressIndicator(),
            const SizedBox(width: 16),
            Expanded(child: Text(L10n.of(context).plugin_websources_reading)),
          ],
        ),
      ),
    ).then((_) => closed = true),
  );
  try {
    final result = await work();
    return closed ? null : result;
  } on Exception {
    return null;
  } finally {
    if (!closed) navigator.pop();
  }
}

/// Asks the reader to agree to [source] and every site it reads, before
/// anything is fetched from them. [update] for a newer version of one
/// already installed.
Future<bool> confirmWebSource(
  BuildContext context,
  WebSourceDefinition source, {
  Uri? origin,
  bool update = false,
}) async {
  final l10n = L10n.of(context);
  final agreed = await showDialog<bool>(
    context: context,
    builder: (context) => AlertDialog(
      icon: WebSourceMark(source: source, size: 40),
      title: Text(
        update ? l10n.plugin_websources_update_title(source.name) : l10n.plugin_websources_consent_title(source.name),
      ),
      content: SingleChildScrollView(
        child: _Consent(source: source, origin: origin),
      ),
      actions: [
        TextButton(onPressed: () => Navigator.pop(context, false), child: Text(l10n.cancel)),
        FilledButton(
          onPressed: () => Navigator.pop(context, true),
          child: Text(update ? l10n.ok : l10n.plugin_websources_install),
        ),
      ],
    ),
  );
  return agreed ?? false;
}

class _Consent extends StatelessWidget {
  final WebSourceDefinition source;
  final Uri? origin;

  const _Consent({required this.source, required this.origin});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final theme = Theme.of(context);
    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        if (source.description.isNotEmpty) Text(source.description),
        if (origin case final origin?)
          Padding(
            padding: const EdgeInsets.only(top: 4),
            child: Text(l10n.plugin_websources_consent_from(origin.host), style: theme.textTheme.bodySmall),
          ),
        const SizedBox(height: 16),
        Text(l10n.plugin_websources_consent_sites, style: theme.textTheme.titleSmall),
        for (final host in source.hosts)
          Padding(
            padding: const EdgeInsets.only(top: 6),
            child: Row(
              children: [
                const Icon(Icons.public, size: 18),
                const SizedBox(width: 8),
                Expanded(child: Text(host, style: theme.textTheme.bodyMedium)),
              ],
            ),
          ),
        const SizedBox(height: 16),
        Text(l10n.plugin_websources_consent_read_only),
      ],
    );
  }
}

/// Why a source file cannot be installed, with the key it broke.
Future<void> showWebSourceRefusal(BuildContext context, RefusedWebSource refused) {
  final l10n = L10n.of(context);
  final message = switch (refused.refusal) {
    WebSourceRefusal.unreadable => l10n.plugin_websources_refused_unreadable,
    WebSourceRefusal.newerFormat => l10n.plugin_websources_refused_newer,
    WebSourceRefusal.invalid => l10n.plugin_websources_refused_invalid,
    WebSourceRefusal.insecure => l10n.plugin_websources_refused_insecure,
    WebSourceRefusal.tooLarge => l10n.plugin_websources_refused_too_large,
  };
  return _notice(context, [
    Text(message),
    if (refused.detail.isNotEmpty)
      Padding(
        padding: const EdgeInsets.only(top: 8),
        child: Text(l10n.plugin_websources_refused_detail(refused.detail)),
      ),
  ]);
}

/// A source file that could not be read at all.
Future<void> showWebSourceUnreachable(BuildContext context) =>
    _notice(context, [Text(L10n.of(context).plugin_websources_unreachable)]);

Future<void> _notice(BuildContext context, List<Widget> content) => showDialog<void>(
  context: context,
  builder: (context) => AlertDialog(
    content: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.start, children: content),
    actions: [TextButton(onPressed: () => Navigator.pop(context), child: Text(L10n.of(context).ok))],
  ),
);

Future<bool> confirmWebSourceRemoval(BuildContext context, WebSourceDefinition source) async {
  final l10n = L10n.of(context);
  final removed = await showDialog<bool>(
    context: context,
    builder: (context) => AlertDialog(
      title: Text(l10n.plugin_websources_remove_confirm(source.name)),
      actions: [
        TextButton(onPressed: () => Navigator.pop(context, false), child: Text(l10n.cancel)),
        FilledButton(onPressed: () => Navigator.pop(context, true), child: Text(l10n.plugin_websources_remove)),
      ],
    ),
  );
  return removed ?? false;
}

/// What went wrong reading a page, for the reader.
String webSourceFailureMessage(L10n l10n, Object? error) => switch (error) {
  WebSourceFailure(kind: WebSourceFailureKind.blocked) => l10n.plugin_websources_failure_blocked,
  WebSourceFailure(kind: WebSourceFailureKind.status, :final status) => l10n.plugin_websources_failure_status(
    '${status ?? ''}',
  ),
  WebSourceFailure(kind: WebSourceFailureKind.tooLarge) => l10n.plugin_websources_failure_too_large,
  _ => l10n.plugin_websources_failure_unreachable,
};
