# aimdi137 release preparation

Integrate the completed X, Mastodon, Bluesky and Substack reader upgrades with
the Plugin Store, Antenna and RSS cover improvements from PRs #287–#289.
The latest published release remains aimdi136.

Prepare the next APK identity without creating a release tag or publishing:

- Keep version name 4.12.0 and advance the base Android version code to
  400001126, above aimdi136's highest ABI variant (400001125).
- Name universal and ABI-specific outputs `xta-aimdi137*.apk`.
- Prepend release notes describing the combined features and pending native
  verification; retain previous notes and installation/signing guidance.
- Remove unused license source generation from the verification workflow,
  matching the release builders and documented Flutter 3.44.4 incompatibility.

Preserve dependency and SDK pins, application id, signing configuration,
certificate record, database and API client. Check metadata consistency,
workflow syntax and release-integrity tests. Native compilation and signed
artifact verification must pass before an APK can be presented as released.
