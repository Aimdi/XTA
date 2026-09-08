# Release integrity

Base: `fb5eb74309390c211e3885096006bf076f6c25cb` (`aimdi128`), audited
2026-09-08. Scope: release tooling; application behavior and version unchanged.

## Problems confirmed in source

- A manual `release.yml` invocation validates the requested tag but builds
  the workflow's original checkout, which can be a different branch.
- Build/attach workflows use an unqualified checkout ref even though this
  repository has both branches and tags named `aimdiNN`.
- `build-release.yml` uses the event's `github.sha` as the release target;
  checking out a different ref does not update that event value.
- `publish-apks.yml` accepts an arbitrary artifact run without binding its
  source, embedded release tag, or signing certificate to the requested release.
- Release builders do not run the application's verification before signing.

## Implementation

1. Resolve an existing full `refs/tags/…` reference to its commit, then detach
   at that commit before loading the SDK, generating sources, or reading keys.
   Pass event inputs through environment variables rather than shell source.
2. Use one tested Python helper for tag resolution, APK metadata/checksums,
   and validation of downloaded CI artifacts. Preserve the helper in runner
   temporary storage before switching to a historical tag.
3. Record the checked-out source, embedded release tag, run identity, APK
   hashes, application/version metadata and actual signing fingerprints.
   Check release identity and published signing fingerprints before upload.
4. Allow artifact publication only from a successful same-repository `ci.yml`
   run for the requested tag's exact commit, with matching recorded metadata.
   Untagged and debug-signed CI builds remain review artifacts.
5. Run ARB checks, skill sync, analysis and app tests before producing APKs.
   Existing signing secrets, version codes, filenames and toolchain pins remain.

## Acceptance and limits

- Regression tests exercise real temporary Git repositories, including
  annotated tags and a same-name branch pointing at different source.
- Reject absent tags, wrong source, failed/foreign workflow runs, missing or
  altered artifacts, wrong application identity, and unexpected certificates.
- Validate workflow wiring and shell syntax; run checks on the draft PR.
- Do not trigger a release, merge unrelated work, or claim device validation.
- The manifest provides traceability within the trusted GitHub build workflow;
  it is not an independently signed supply-chain attestation.

PR #261 already fixes the shallow-checkout formatting gap on its branch.
Its reading/download/offline implementation is outside this change.
