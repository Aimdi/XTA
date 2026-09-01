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

### 3. Publish fingerprints (optional but useful)

```bash
keytool -list -v -keystore xta.jks -alias xta
```

Put the SHA-1 / SHA-256 into `release-notes.md` (and keep them in sync) so
users can verify downloads.

### 4. Cut a new release

After secrets are set, tag / dispatch a release as usual. The first install of
a properly signed build still requires uninstalling any previous
debug-signed Aimdi APK once. After that, Obtainium / sideload updates should
apply in place.

## What stays debug-signed

`ci.yml` may still produce **debug-signed** APK artifacts for PR testing.
Those are not for Obtainium or long-lived installs.

### Agent / API cut

From a token that can create `repository_dispatch` events (but not
`workflow_dispatch`):

```bash
gh api -X POST repos/Aimdi/XTA/dispatches \
  -f event_type=build-release \
  -f 'client_payload[tag]=aimdi78'
```

Or push tag `aimdiNN` to run `.github/workflows/release.yml`.
