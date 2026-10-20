import 'package:flutter/material.dart';
import 'package:xta/ui/floating_notice.dart';
import 'package:xta/ui/loading_hairline.dart';
import 'package:xta/ui/reader_failure.dart';

/// The state of content that stays readable while it reloads: the loading line
/// over its top edge while it loads again, and a failed reload floating over
/// it. Neither takes room, so the content never moves for them.
///
/// Pass [loading] and [error] only while there is content to keep showing;
/// with nothing to show, the surface's own spinner or failure row says it.
/// [failed] shows a failure with no single error to describe (some of
/// several sources failed); it defaults to whether there is an [error].
class ReloadStatus extends StatelessWidget {
  final bool loading;
  final Object? error;
  final bool failed;
  final VoidCallback onRetry;

  /// The plugin that failed, as [ReaderFailureNotice.source] takes it.
  final String source;

  /// What failed, shown in the failure's details.
  final String? contextMessage;
  final Widget child;

  const ReloadStatus({
    super.key,
    required this.loading,
    required this.error,
    required this.onRetry,
    required this.source,
    required this.child,
    this.contextMessage,
    bool? failed,
  }) : failed = failed ?? error != null;

  @override
  Widget build(BuildContext context) {
    return FloatingNotice(
      notice: !failed || loading
          ? null
          : NoticePill(
              child: ReaderFailureNotice(
                source: source,
                error: error,
                compact: true,
                contextMessage: contextMessage,
                onRetry: onRetry,
              ),
            ),
      child: LoadingHairline(loading: loading, child: child),
    );
  }
}
