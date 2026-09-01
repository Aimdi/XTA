#!/usr/bin/env bash
# Idempotent toolchain bootstrap for Cursor Cloud agents working on XTA.
# Works on a warm snapshot (refresh only) and on a cold VM (install FVM +
# Android cmdline-tools first).
set -euo pipefail

export ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk}"
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$ANDROID_HOME}"
export FVM_INSTALL_DIR="${FVM_INSTALL_DIR:-$HOME/fvm}"
export PATH="$FVM_INSTALL_DIR/bin:$HOME/fvm/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"

echo "==> Git merge drivers"
"$(dirname "$0")/setup_git_merge_drivers.sh"

# Latest Google Android cmdline-tools zip (linux). Bump when Google retires it.
CMDLINE_TOOLS_URL="${CMDLINE_TOOLS_URL:-https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip}"

ensure_fvm() {
  if command -v fvm >/dev/null 2>&1; then
    echo "==> Flutter (FVM) — already on PATH: $(command -v fvm)"
    return 0
  fi

  echo "==> Flutter (FVM) — not found; installing into $FVM_INSTALL_DIR"
  local installer
  installer="$(mktemp)"
  curl -fsSL https://fvm.app/install.sh -o "$installer"
  bash "$installer"
  rm -f "$installer"

  export PATH="$FVM_INSTALL_DIR/bin:$PATH"
  hash -r 2>/dev/null || true

  if ! command -v fvm >/dev/null 2>&1; then
    echo "ERROR: FVM install finished but 'fvm' is still not on PATH." >&2
    echo "       Expected binary under $FVM_INSTALL_DIR/bin" >&2
    ls -la "$FVM_INSTALL_DIR/bin" >&2 || true
    exit 1
  fi
}

ensure_android_sdk() {
  if [[ -x "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" ]]; then
    echo "==> Android SDK — cmdline-tools present at $ANDROID_HOME"
    return 0
  fi

  echo "==> Android SDK — bootstrapping cmdline-tools into $ANDROID_HOME"
  mkdir -p "$ANDROID_HOME/cmdline-tools"
  local zip tmp
  zip="$(mktemp --suffix=.zip)"
  tmp="$(mktemp -d)"
  curl -fsSL "$CMDLINE_TOOLS_URL" -o "$zip"
  unzip -q "$zip" -d "$tmp"
  rm -rf "$ANDROID_HOME/cmdline-tools/latest"
  # Google ships a top-level "cmdline-tools/" dir; sdkmanager expects …/latest/
  mv "$tmp/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
  rm -rf "$tmp" "$zip"

  export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"

  if [[ ! -x "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" ]]; then
    echo "ERROR: Android cmdline-tools install failed (sdkmanager missing)." >&2
    exit 1
  fi

  echo "==> Android SDK — accepting licenses + installing platforms"
  yes | sdkmanager --licenses >/dev/null 2>&1 || true
  # compileSdk / targetSdk 37 (android/app/build.gradle). Google currently ships
  # platforms;android-37.0 — fix_android_37_platform remaps the hash AGP expects.
  sdkmanager --install \
    "platform-tools" \
    "platforms;android-37.0" \
    "build-tools;35.0.0"
}

fix_android_37_platform() {
  if [[ -d "$ANDROID_HOME/platforms/android-37.0" && ! -d "$ANDROID_HOME/platforms/android-37" ]]; then
    echo "==> Android SDK — fixing platforms/android-37 hash quirk"
    cp -r "$ANDROID_HOME/platforms/android-37.0" "$ANDROID_HOME/platforms/android-37"
    sed -i 's/AndroidVersion.ApiLevel=37.0/AndroidVersion.ApiLevel=37/' \
      "$ANDROID_HOME/platforms/android-37/source.properties" || true
  fi
}

persist_env_hint() {
  local bashrc="$HOME/.bashrc"
  local marker="# XTA cloud agent toolchain"
  if [[ -f "$bashrc" ]] && grep -qF "$marker" "$bashrc"; then
    return 0
  fi
  {
    echo ""
    echo "$marker"
    echo "export ANDROID_HOME=\"\${ANDROID_HOME:-$HOME/android-sdk}\""
    echo "export ANDROID_SDK_ROOT=\"\${ANDROID_SDK_ROOT:-\$ANDROID_HOME}\""
    echo "export FVM_INSTALL_DIR=\"\${FVM_INSTALL_DIR:-$HOME/fvm}\""
    echo "export PATH=\"\$FVM_INSTALL_DIR/bin:\$ANDROID_HOME/cmdline-tools/latest/bin:\$ANDROID_HOME/platform-tools:\$PATH\""
  } >>"$bashrc"
}

ensure_fvm
persist_env_hint

echo "==> Flutter (FVM) — install pinned SDK + deps"
fvm install
fvm use
fvm flutter pub get
fvm dart run intl_utils:generate || true
fvm dart run flutter_iconpicker:generate_packs --packs material || true

ensure_android_sdk
fix_android_37_platform

echo "==> cloud_install complete"
echo "Note: interactive Android UI needs /dev/kvm (not available on Cursor Cloud VMs)."
echo "      Use scripts/cloud_verify.sh, or adb connect <device> for a real phone."
