# Privacy hardening

Reduce accidental disclosure through support reports, production logs and
automatic Android transfers without changing account storage, network APIs or
reading behavior. This is a targeted audit, not a claim of anonymity.

## Findings and design

- The opt-in GitHub crash reporter currently publishes arbitrary exception
  messages, diagnostic context and stack text. Publish the exception class,
  app/build/mode and recognized Dart code frames instead. Omit free text and
  local file paths. Retain the opt-in setting, manual test, authentication,
  duplicate suppression and rate limit. Store only a digest for deduplication;
  never log GitHub response bodies.
- The root logging listener forwards raw messages and errors, including URLs,
  cache keys and response content, to `dart:developer`. Release builds will
  retain severity, component, exception class and recognized code frames only.
  Debug/profile builds keep existing local debugging detail. The framework
  error handler will use the same safe release sink while retaining automatic
  crash reporting and the existing debug handler. Route the direct startup,
  audio-service and subscription-group error logs through this sink as well.
- Copied diagnostics currently identify signed-in accounts by screen name.
  Use numbered account labels in copied reports while preserving health and
  endpoint information and the names shown on the device.
- `allowBackup=false` alone does not exclude device transfers on every Android
  12+ implementation. Explicitly exclude all nine supported storage domains
  from cloud backup and device transfer, alongside disabled legacy full backup.
  Manual export/import and user-configured WebDAV remain available.

## Compatibility constraints

- Flutter **3.44.4**, existing pinned dependencies, no upgrades.
- No edits in `lib/client/` or `lib/database/`; no schema or credential migration.
- Preserve read-oriented behavior, login, cookies, media, subscriptions and
  explicit account backups. No new background requests or telemetry.
- Use existing `flutter_triple` state; no user-facing setting or new UI copy.
- Existing RSS/Substack sanitizers remove publisher scripts. Authentication,
  embedded video and trusted reader bridges still require JavaScript, so no
  blanket WebView scripting or cookie change is part of this patch.
- Existing preference exports already omit declared plugin credentials and
  WebDAV account export is opt-in. Keep these protections and test them.

## Verification and limits

Add regression tests at the outbound HTTP, log sink and copied-report
boundaries using synthetic secrets. Exercise both reporting enabled/disabled,
duplicate/rate-limit behavior, failed delivery, code-frame retention, unknown
stack formats, and unchanged debug logging. Run the full Flutter suite and
analyzer. Validate Android resources/manifest with Android build tooling when
available. A real-device backup/transfer test and interactive login/media smoke
test require an attached device; automated checks cannot substitute for them.

Stack sanitization deliberately drops unrecognized frames rather than guessing
whether their contents are private. This reduces details in some crash reports.
No change prevents the requested content provider from seeing normal requests.

## Primary references

- [Android backup rules and device-transfer behavior](https://developer.android.com/identity/data/autobackup)
- [Android log information disclosure](https://developer.android.com/privacy-and-security/risks/log-info-disclosure)

## Android security alignment

Area: sensitive account data in automatic backups. Impact: session database,
preferences and WebView storage excluded from supported Android backup modes.
Files: `android/app/src/main/AndroidManifest.xml` and
`android/app/src/main/res/xml/data_extraction_rules.xml`. Implementation: retain
`allowBackup=false`, disable legacy full backup, and reference explicit modern
exclusion rules. The accompanying PR supplies the unified diff. Exported launcher,
share/deep-link and media components retain their required interoperability.

## Validation record

- Full Flutter suite: 3,184 passed, 6 skipped.
- Focused crash/log/export/diagnostics tests: 32 passed. The new disclosure
  assertions failed before their corresponding fixes.
- Analyzer: no errors or warnings; 138 existing informational lints.
- Manifest/XML validation: referenced resource exists; both transfer sections
  exclude all nine domains. Android compilation is delegated to the PR's
  `reader Android check` because this workspace has no Android SDK.
- Independent whole-branch review: no critical, important or minor findings.

Review boundaries: real-device transfers and login/media need a device;
third-party/native plugin logs are outside the app-owned logging filter. XTA
has no iOS counterpart or transfer mapping, so no cross-platform transfer rule
is added. Existing report-preparation failures (for example, unavailable package
metadata) are unchanged; this patch introduces no new preparation dependency.
