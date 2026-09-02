# Aimdi126 release stamp correction

## Problem

The verified aimdi126 source was released with `XTA_RELEASE_TAG=aimdi126`, but
the Android output filenames and numeric build version still named aimdi125.

## Change

- Advance the Android build number by one.
- Name universal and ABI-specific APK outputs `xta-aimdi126*.apk`.
- On an attach-workflow rerun, remove only existing APK assets from the same
  release after a successful build and before uploading the replacement set.

## Preserve

- Do not change application id, signing configuration, dependencies, data
  schema, Home implementation, or any other product behavior.
- Keep the release tag and release branch on the exact verified source commit.
