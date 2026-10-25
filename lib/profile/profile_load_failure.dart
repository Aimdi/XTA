import 'package:flutter/material.dart';
import 'package:xta/client/errors.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/profile/profile_chrome.dart';
import 'package:xta/ui/reader_failure.dart';

/// A profile that did not load. When X said why (the account does not exist,
/// is suspended or private), that is the page, since a retry cannot change
/// it; anything else can be retried.
class ProfileLoadFailure extends StatelessWidget {
  final Object? error;
  final VoidCallback onRetry;

  const ProfileLoadFailure({super.key, required this.error, required this.onRetry});

  @override
  Widget build(BuildContext context) {
    final said = _xAnswer(L10n.of(context));
    if (said == null) return ReaderFailureNotice(error: error, recoverAutomatically: false, onRetry: onRetry);
    return ProfileEmptyState(icon: said.icon, message: said.message);
  }

  ({IconData icon, String message})? _xAnswer(L10n l10n) {
    final error = this.error;
    if (error is! TwitterError || readRetryOf(error) != ReadRetry.manual) return null;
    final described = describeTwitterErrorCode(l10n, error.code);
    if (described == null) return null;
    return (icon: error.code == 22 ? Icons.lock_outline : Icons.person_off_outlined, message: described.message);
  }
}
