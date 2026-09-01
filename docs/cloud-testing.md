# Cloud testing for XTA

XTA is **Android-only**. Cursor Cloud VMs currently **do not expose `/dev/kvm`**, so a
usable Android emulator (hardware-accelerated) is not available. Software
emulation (`-accel off`) can start but is too slow/unstable for interactive UI
work.

## What works in Cloud today

| Check | Command |
|---|---|
| Lint | `fvm flutter analyze` |
| Unit / characterization tests | `fvm flutter test` |
| Live guest API smoke | `fvm flutter test test/live/guest_api_smoke_test.dart --dart-define=RUN_LIVE=true` |
| Debug APK | `fvm flutter build apk --debug` |
| One-shot verify | `bash scripts/cloud_verify.sh` |

## Interactive app use

1. **Preferred:** pair a physical phone via wireless ADB  
   `bash scripts/adb_wireless_connect.sh <pair_host:port> <code> <connect_host:port>`  
   then `adb install -r build/app/outputs/flutter-apk/app-debug.apk`
2. **Not viable here:** local AVD without KVM (Cursor feature request: KVM for Cloud Agents)
3. **Desktop/Chrome `flutter devices`:** listed by Flutter but unusable — no `linux/` / `web/` platform folders and Android-only plugins

## Environment bootstrap

- `.cursor/environment.json` → runs `scripts/cloud_install.sh`
- **Cold VMs:** `cloud_install.sh` bootstraps FVM (into `~/fvm`) and Android
  cmdline-tools (into `~/android-sdk`) when they are missing, then runs
  `fvm install` / `pub get` / codegen and applies the `platforms/android-37`
  hash quirk fix.
- **Warm snapshots (preferred):** after one successful cold setup, save a VM
  snapshot from the Cloud Agents dashboard so later agents only refresh deps.
