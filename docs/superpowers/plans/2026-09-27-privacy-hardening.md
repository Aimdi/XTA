# Privacy Hardening Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Reduce accidental disclosure without changing the app's reading or account workflows.

**Architecture:** Filter diagnostic data at the outbound report and logging boundaries. Add explicit Android backup exclusions without migrating stored data.

**Tech Stack:** Flutter 3.44.4, Dart, logging/http, Android manifest and XML resources.

**Spec:** `docs/specs/privacy-hardening.md`

## Global Constraints

- Flutter **3.44.4**, existing pinned dependencies, no upgrades.
- No edits in `lib/client/` or `lib/database/`; no schema or credential migration.
- Use existing `flutter_triple` state; no user-facing setting or new UI copy.
- Preserve login, cookies, media and explicit account backups.

## Review Focus

- Free-form exception/context data and unrecognized stack lines must not reach GitHub.
- Release log text can contain credentials even when `record.error` is null.
- Error handling must remain nonfatal and preserve debug diagnostics.
- Two accounts must remain distinguishable in a report without identifying either user.
- Android root exclusions must cover WebView storage as well as preferences/databases.

### Task 1: Safe diagnostic boundaries

**Files:** create `lib/utils/diagnostic_privacy.dart` and `test/diagnostic_privacy_test.dart`; modify `lib/utils/crash_reporter.dart`, `lib/main.dart`, `test/crash_reporter_test.dart`.

**Interfaces:** produce `String diagnosticStack(StackTrace? stack)` and `void writeDiagnosticLog(LogRecord record, {bool releaseMode = kReleaseMode, DiagnosticLogWriter? writer})`; consume these in the reporter and root logger. Code frames accept package/dart source locations; unknown text is omitted. Keep `buildIssueBody` and reporter public signatures compatible.

- [ ] Add outbound-payload tests with synthetic cookies, signed URLs, text and file paths; assert no synthetic secrets and retain `package:xta/...:line:column` frames and app version.
- [ ] Add log-sink tests: release records retain component/severity/type but no raw message/error; debug retains original data. Add framework-handler test for release sanitization and existing debug delegation.
- [ ] Run focused tests; confirm the new privacy assertions fail against current behavior.
- [ ] Implement the shared formatter, hashed deduplication, safe report body/title and safe release handler/sink.
- [ ] Run `fvm flutter test --no-pub test/diagnostic_privacy_test.dart test/crash_reporter_test.dart test/export_preferences_test.dart`; expect all pass, including reporting gates, repeated errors and failed delivery.
- [ ] Commit the verified task.

### Task 2: Anonymous copied reports

**Files:** modify `lib/settings/diagnostics_report.dart` and `test/diagnostics_report_test.dart`.

**Interfaces:** retain `DiagnosticsReport.toPlainText()` and structured UI data. Number accounts in report order.

- [ ] Change report tests to require `account 1`, `account 2`, health details and no names/session IDs; observe failure with existing named reports.
- [ ] Change only copied-report labels; retain on-device account names.
- [ ] Run `fvm flutter test --no-pub test/diagnostics_report_test.dart`; expect all pass.
- [ ] Commit the verified task.

### Task 3: Explicit automatic backup exclusions and final validation

**Files:** modify `android/app/src/main/AndroidManifest.xml`; create `android/app/src/main/res/xml/data_extraction_rules.xml`.

**Interfaces:** `android:dataExtractionRules` references rules excluding `root`, `file`, `database`, `sharedpref`, `external`, `device_root`, `device_file`, `device_database`, `device_sharedpref` at `.` for both cloud and device transfer. Keep `allowBackup=false`; set `fullBackupContent=false`.

- [ ] Implement the manifest/XML policy; validate XML structure and domain coverage, then compile resources if the Android SDK is available. Do not substitute source-text tests for device transfer testing.
- [ ] Run `fvm flutter test --no-pub`; expect green apart from documented live-test skips.
- [ ] Run `fvm flutter analyze --no-pub`; expect no errors or warnings beyond existing informational lints.
- [ ] Obtain one independent whole-branch review; resolve material findings and publish a PR against `claude/main`.
- [ ] Record validation and any device-testing limitation in the PR.
