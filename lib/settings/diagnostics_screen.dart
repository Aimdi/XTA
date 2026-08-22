import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:intl/intl.dart';
import 'package:pref/pref.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/settings/crash_log_screen.dart';
import 'package:xta/settings/diagnostics_model.dart';
import 'package:xta/settings/diagnostics_report.dart';

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
    return Scaffold(
      appBar: AppBar(
        title: Text(L10n.of(context).diagnostics),
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
      ),
      body: RefreshIndicator(
        onRefresh: _model.load,
        child: ScopedBuilder<DiagnosticsModel, DiagnosticsReport>.transition(
          store: _model,
          onLoading: (_) => const Center(child: CircularProgressIndicator()),
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

    return ListView(
      padding: EdgeInsets.only(
        bottom: 16 + MediaQuery.of(context).padding.bottom,
      ),
      children: [
        ListTile(
          leading: const Icon(Icons.bug_report_outlined),
          title: Text(L10n.of(context).crash_log),
          subtitle: Text(L10n.of(context).crash_log_settings_description),
          trailing: const Icon(Icons.chevron_right),
          onTap: () => Navigator.of(
            context,
          ).push(MaterialPageRoute(builder: (_) => const CrashLogScreen())),
        ),
        _SectionHeader(title: L10n.of(context).account),
        if (report.accounts.isEmpty)
          ListTile(
            leading: const Icon(Icons.person_off_outlined),
            title: Text(L10n.of(context).diagnostics_no_accounts),
          ),
        ...report.accounts.map(
          (account) => _AccountTile(account: account, dates: dates),
        ),
        _SectionHeader(title: L10n.of(context).diagnostics_endpoints),
        ListTile(
          leading: const Icon(Icons.sync),
          title: Text(
            report.registryFetchedAt == null
                ? L10n.of(context).diagnostics_registry_never_checked
                : L10n.of(context).diagnostics_registry_checked(
                    dates.format(report.registryFetchedAt!),
                  ),
          ),
          subtitle: report.registryEnabled
              ? null
              : Text(L10n.of(context).disabled),
        ),
        ...report.endpoints.map(
          (endpoint) => _EndpointTile(endpoint: endpoint),
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

    return ListTile(
      leading: Icon(
        account.isHealthy ? Icons.check_circle_outline : Icons.error_outline,
        color: account.isHealthy ? null : Theme.of(context).colorScheme.error,
      ),
      title: Text('@${account.screenName ?? account.id}'),
      subtitle: Text(
        problems.isEmpty
            ? L10n.of(context).diagnostics_account_ok
            : problems.join('\n'),
      ),
      isThreeLine: problems.length > 1,
    );
  }
}

class _EndpointTile extends StatelessWidget {
  final EndpointDiagnostics endpoint;

  const _EndpointTile({required this.endpoint});

  @override
  Widget build(BuildContext context) {
    return ListTile(
      dense: true,
      leading: Icon(
        endpoint.isOverridden
            ? Icons.cloud_download_outlined
            : Icons.check_outlined,
      ),
      title: Text(endpoint.name),
      subtitle: Text(
        endpoint.isOverridden
            ? '${endpoint.queryId} · ${L10n.of(context).diagnostics_endpoint_updated}'
            : endpoint.queryId,
      ),
      trailing: Text(
        endpoint.host,
        style: Theme.of(context).textTheme.bodySmall,
      ),
    );
  }
}

class _SectionHeader extends StatelessWidget {
  final String title;

  const _SectionHeader({required this.title});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 20, 16, 4),
      child: Text(
        title,
        style: Theme.of(context).textTheme.titleSmall?.copyWith(
          color: Theme.of(context).colorScheme.primary,
          fontWeight: FontWeight.w600,
        ),
      ),
    );
  }
}
