import 'package:flutter/material.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/utils/crash_reporter.dart';

/// Diagnostics: opt-in GitHub crash reports (requires a user-supplied PAT).
class SettingsCrashReportsSection extends StatelessWidget {
  const SettingsCrashReportsSection({super.key});

  PrefDialog _repoDialog(BuildContext context, BasePrefService prefs) {
    final controller = TextEditingController(text: prefs.get(optionCrashGithubRepo) ?? defaultCrashGithubRepo);
    return PrefDialog(
      title: Text(L10n.of(context).crash_reports_repo),
      actions: [
        TextButton(onPressed: () => Navigator.pop(context), child: Text(L10n.of(context).cancel)),
        TextButton(
          onPressed: () async {
            await prefs.set(optionCrashGithubRepo, controller.text.trim());
            if (context.mounted) Navigator.pop(context);
          },
          child: Text(L10n.of(context).save),
        ),
      ],
      children: [
        SizedBox(
          width: MediaQuery.sizeOf(context).width,
          child: TextFormField(
            controller: controller,
            decoration: const InputDecoration(hintText: defaultCrashGithubRepo),
          ),
        ),
      ],
    );
  }

  PrefDialog _tokenDialog(BuildContext context, BasePrefService prefs) {
    final controller = TextEditingController(text: prefs.get(optionCrashGithubToken) ?? '');
    return PrefDialog(
      title: Text(L10n.of(context).crash_reports_token),
      actions: [
        TextButton(onPressed: () => Navigator.pop(context), child: Text(L10n.of(context).cancel)),
        TextButton(
          onPressed: () async {
            await prefs.set(optionCrashGithubToken, controller.text.trim());
            if (context.mounted) Navigator.pop(context);
          },
          child: Text(L10n.of(context).save),
        ),
      ],
      children: [
        SizedBox(
          width: MediaQuery.sizeOf(context).width,
          child: TextFormField(
            controller: controller,
            obscureText: true,
            decoration: InputDecoration(hintText: L10n.of(context).crash_reports_token_hint),
          ),
        ),
      ],
    );
  }

  Future<void> _sendTest(BuildContext context) async {
    final messenger = ScaffoldMessenger.of(context);
    final l10n = L10n.of(context);
    final reporter = CrashReporter.instance;
    if (reporter == null) {
      messenger.showSnackBar(SnackBar(content: Text(l10n.crash_reports_test_failed)));
      return;
    }
    final result = await reporter.sendTestReport();
    if (!context.mounted) return;
    messenger.showSnackBar(SnackBar(content: Text(_messageFor(result, l10n))));
  }

  String _messageFor(CrashReportResult result, L10n l10n) {
    switch (result) {
      case CrashReportResult.sent:
        return l10n.crash_reports_test_sent;
      case CrashReportResult.missingToken:
        return l10n.crash_reports_missing_token;
      case CrashReportResult.authFailed:
        return l10n.crash_reports_auth_failed;
      case CrashReportResult.invalidRepo:
        return l10n.crash_reports_invalid_repo;
      case CrashReportResult.rateLimited:
        return l10n.crash_reports_rate_limited;
      case CrashReportResult.duplicate:
        return l10n.crash_reports_duplicate;
      case CrashReportResult.disabled:
        return l10n.crash_reports_disabled_toast;
      case CrashReportResult.ignored:
      case CrashReportResult.failed:
        return l10n.crash_reports_test_failed;
    }
  }

  @override
  Widget build(BuildContext context) {
    final prefs = PrefService.of(context);
    return Column(
      children: [
        PrefSwitch(
          title: Text(L10n.of(context).crash_reports_enabled),
          subtitle: Text(L10n.of(context).crash_reports_enabled_description),
          pref: optionCrashReportsEnabled,
        ),
        PrefDisabler(
          pref: optionCrashReportsEnabled,
          reversed: true,
          children: [
            PrefDialogButton(
              title: Text(L10n.of(context).crash_reports_repo),
              subtitle: Text(L10n.of(context).crash_reports_repo_description),
              dialog: _repoDialog(context, prefs),
            ),
            PrefDialogButton(
              title: Text(L10n.of(context).crash_reports_token),
              subtitle: Text(L10n.of(context).crash_reports_token_description),
              dialog: _tokenDialog(context, prefs),
            ),
            PrefLabel(
              title: Text(L10n.of(context).crash_reports_send_test),
              subtitle: Text(L10n.of(context).crash_reports_send_test_description),
              onTap: () => _sendTest(context),
            ),
          ],
        ),
      ],
    );
  }
}
