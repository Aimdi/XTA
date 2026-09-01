import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:intl/intl.dart';
import 'package:pref/pref.dart';
import 'package:quax/generated/l10n.dart';
import 'package:quax/settings/diagnostics_model.dart';
import 'package:quax/settings/diagnostics_report.dart';
import 'package:quax/settings/settings_chrome.dart';
import 'package:quax/ui/errors.dart';

/// Makes the account-selection and endpoint machinery visible.
///
/// Both already exist and both decide whether a request works, but neither
/// leaves any trace in the UI — a reader whose feed is empty can only report
/// "it stopped working". This turns that into a report naming the endpoint, the
/// account and the query id.
class DiagnosticsScreen extends StatefulWidget {
  const DiagnosticsScreen({super.key});

  @override
  State<DiagnosticsScreen> createState() => _DiagnosticsScreenState();
}

class _DiagnosticsScreenState extends State<DiagnosticsScreen> {
  late final DiagnosticsModel _model;

  @override
  void initState() {
    super.initState();
    _model = DiagnosticsModel(PrefService.of(context, listen: false));
    _model.load();
  }

  @override
  void dispose() {
    _model.destroy();
    super.dispose();
  }

  Future<void> _copy(DiagnosticsReport report) async {
    await Clipboard.setData(ClipboardData(text: report.toPlainText()));
    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(L10n.of(context).diagnostics_report_copied)),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    return SettingsPageScaffold(
      title: L10n.of(context).diagnostics,
      actions: [
        ScopedBuilder<DiagnosticsModel, DiagnosticsReport>(
          store: _model,
          onState: (_, report) => IconButton(
            icon: const Icon(Icons.copy_all),
            tooltip: L10n.of(context).diagnostics_copy_report,
            onPressed: () => _copy(report),
          ),
        ),
      ],
      body: RefreshIndicator(
        onRefresh: _model.load,
        child: ScopedBuilder<DiagnosticsModel, DiagnosticsReport>.transition(
          store: _model,
          onLoading: (_) => const SettingsListSkeleton(),
          onError: (_, error) => FullPageErrorWidget(
            error: error,
            stackTrace: null,
            prefix: L10n.of(context).diagnostics_description,
            onRetry: _model.load,
          ),
          onState: (_, report) => _DiagnosticsBody(report: report),
        ),
      ),
    );
  }
}

class _DiagnosticsBody extends StatelessWidget {
  final DiagnosticsReport report;

  const _DiagnosticsBody({required this.report});

  @override
  Widget build(BuildContext context) {
    final dates = DateFormat.yMd(
      Localizations.localeOf(context).toString(),
    ).add_Hm();

    return SettingsList(
      children: [
        SettingsSection(
          title: L10n.of(context).account,
          children: [
            if (report.accounts.isEmpty)
              SettingsRow(
                icon: Icons.person_off_outlined,
                title: L10n.of(context).diagnostics_no_accounts,
              ),
            ...report.accounts.map(
              (account) => _AccountTile(account: account, dates: dates),
            ),
          ],
        ),
        SettingsSection(
          title: L10n.of(context).diagnostics_endpoints,
          children: [
            SettingsRow(
              icon: Icons.sync,
              title: report.registryFetchedAt == null
                  ? L10n.of(context).diagnostics_registry_never_checked
                  : L10n.of(context).diagnostics_registry_checked(
                      dates.format(report.registryFetchedAt!),
                    ),
              description: report.registryEnabled
                  ? null
                  : L10n.of(context).disabled,
            ),
            ...report.endpoints.map(
              (endpoint) => _EndpointTile(endpoint: endpoint),
            ),
          ],
        ),
      ],
    );
  }
}

class _AccountTile extends StatelessWidget {
  final AccountDiagnostics account;
  final DateFormat dates;

  const _AccountTile({required this.account, required this.dates});

  @override
  Widget build(BuildContext context) {
    final problems = <String>[
      if (account.notFoundUntil != null)
        L10n.of(context).diagnostics_account_sign_in_broken(
          dates.format(account.notFoundUntil!),
        ),
      if (account.rateLimited.isNotEmpty)
        L10n.of(
          context,
        ).diagnostics_account_rate_limited(account.rateLimited.length),
    ];

    return SettingsRow(
      icon: account.isHealthy
          ? Icons.check_circle_outline
          : Icons.error_outline,
      title: '@${account.screenName ?? account.id}',
      description: problems.isEmpty
          ? L10n.of(context).diagnostics_account_ok
          : problems.join('\n'),
    );
  }
}

class _EndpointTile extends StatelessWidget {
  final EndpointDiagnostics endpoint;

  const _EndpointTile({required this.endpoint});

  @override
  Widget build(BuildContext context) {
    return SettingsRow(
      icon: endpoint.isOverridden
          ? Icons.cloud_download_outlined
          : Icons.check_outlined,
      title: endpoint.name,
      description: endpoint.isOverridden
          ? '${endpoint.queryId} · ${L10n.of(context).diagnostics_endpoint_updated}'
          : endpoint.queryId,
      value: endpoint.host,
    );
  }
}
