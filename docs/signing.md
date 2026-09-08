# Release signing (Aimdi / XTA)

Android only allows an app update when the **new APK is signed with the same
certificate** as the installed one. Upstream [Teskann/QuaX](https://github.com/Teskann/QuaX)
uses a private release keystore. This fork does **not** have that key.

If GitHub Actions has no `SIGNING_KEY` secret, release workflows used to fall
back to the runner’s **debug.keystore**. That file is created fresh on every
ephemeral CI machine, so **every release was signed with a different key**.
Android then refuses in-place updates — you have to uninstall and reinstall
each time.

Release workflows (`release.yml`, `build-release.yml`) now **fail** until a
stable keystore is configured.

## One-time setup

### 1. Create a keystore (on your machine)

```bash
keytool -genkey -v \
  -keystore xta.jks \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias xta \
  -storepass 'CHOOSE_A_STORE_PASSWORD' \
  -keypass 'CHOOSE_A_KEY_PASSWORD' \
  -dname 'CN=XTA, OU=Aimdi, O=Aimdi, L=Unknown, ST=Unknown, C=US'
```

Back up `xta.jks` somewhere safe. Losing it means users must uninstall
to install future builds again.

### 2. Add GitHub Actions secrets

Repo → **Settings → Secrets and variables → Actions**:

| Secret | Value |
|---|---|
| `SIGNING_KEY` | `base64 -w0 xta.jks` (single line) |
| `KEY_STORE_PASSWORD` | store password from step 1 |
| `KEY_PASSWORD` | key password from step 1 |
| `KEY_ALIAS` | `xta` (or whatever `-alias` you used) |

A keystore created before the rename keeps working — its file name and alias are
not part of the app's identity, so there is nothing to recreate. The secrets
already set are authoritative; the names above are only what a new keystore
would be called.

With the GitHub CLI:

```bash
base64 -w0 xta.jks | gh secret set SIGNING_KEY
gh secret set KEY_STORE_PASSWORD --body 'CHOOSE_A_STORE_PASSWORD'
gh secret set KEY_PASSWORD --body 'CHOOSE_A_KEY_PASSWORD'
gh secret set KEY_ALIAS --body 'xta'
```

### 3. Publish and verify fingerprints

```bash
keytool -list -v -keystore xta.jks -alias xta
```

Keep SHA-1 / SHA-256 in `certificate-fingerprints.txt` so users and release
checks can verify downloads. On 2026-09-08 this file was corrected against
the actual published aimdi128 arm64 APK, verified with `apksigner`. Its SHA256
is `b4706dc61aebaa6c40663455e615592d3db8bcfc77467c6d31ae222875cb0e34`.
This corrects the public record; it does not change the existing signing key.

### 4. Cut a new release

After secrets are set, tag / dispatch a release as usual. The first install of
a properly signed build still requires uninstalling any previous
debug-signed Aimdi APK once. After that, Obtainium / sideload updates should
apply in place.

## What stays debug-signed

`ci.yml` may still produce **debug-signed** APK artifacts for review.
The artifact publisher rejects them. It requires all four APK variants, the
release application ID, matching versions/ABIs, and the certificate recorded
in `certificate-fingerprints.txt`.

## Release source and artifact checks

Release workflows resolve an existing full tag ref to its commit and check
that commit out before setup/build. They verify translations, skill sync,
analysis and app tests before generating APKs. Publication uses the resolved
commit, not the event's `github.sha`.

Each build includes `release-build.json` and `SHA256SUMS`. To publish a `ci.yml`
artifact, the selected run must have succeeded in this repository on the tag's
exact commit; its manifest must record the same release-tag build argument,
source and run attempt. The actual APK checksums and metadata are inspected
again. Untagged builds and older artifacts without a manifest must be rebuilt
through the tagged release workflow; they cannot be relabeled as releases.

The workflow saves its helper and current certificate record in runner
temporary storage before checking out a requested historical tag. This lets
it verify releases such as aimdi128 whose source still had the outdated
fingerprint file. It does not change the historical tag.

These guards establish build traceability and signing identity. They are not
a reproducible-build result or an independently signed attestation. See
`docs/specs/release-integrity.md` and `docs/app-development-audit-2026-09.md`.

### Agent / API cut

From a token that can create `repository_dispatch` events (but not
`workflow_dispatch`):

```bash
gh api -X POST repos/Aimdi/XTA/dispatches \
  -f event_type=build-release \
  -f 'client_payload[tag]=aimdi78'
```

Or push tag `aimdiNN` to run `.github/workflows/release.yml`.
