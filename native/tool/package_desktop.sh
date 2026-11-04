#!/usr/bin/env bash
# Packs XTA Native's desktop app (what :desktop:createDistributable made) for a
# prerelease, as scripts/package_linux.sh packs XTA's:
#   <out>/xta-native-desktop-<version>-linux-x64.tar.gz  the app (bin/, and lib/ with its
#                                                       Java runtime and, in lib/app/resources,
#                                                       the voice engine tool/voice_engine.py
#                                                       put in the build and the video engine's
#                                                       shim the build made, which plays through
#                                                       the system's libmpv) and, under share/,
#                                                       its menu entry and icon
#   <out>/PKGBUILD                                       the Arch package of that tarball
# With --makepkg (on Arch, or in an archlinux container) it also builds
#   <out>/xta-native-<version>-1-x86_64.pkg.tar.zst
#
#   bash native/tool/package_desktop.sh <app dir> <version> <out dir> [--makepkg]
set -euo pipefail
app=${1:?the app folder createDistributable made}
version=${2:?version}
out=${3:?output folder}
build_package=${4:-}
here=$(cd "$(dirname "$0")/.." && pwd)
root=$(cd "$here/.." && pwd)
repo=${GITHUB_REPOSITORY:-Aimdi/XTA}

[[ -x "$app/bin/xta-native" && -d "$app/lib" ]] || {
  echo "error: $app is not the desktop app (expected bin/xta-native and lib/)" >&2
  exit 1
}
[[ -f "$app/lib/app/resources/video-engine/libxta_mpv.so" ]] \
  || echo "warning: $app carries no video engine (built without cc?): it will hand videos to the system's player" >&2
# Arch versions cannot carry '-'.
[[ "$version" =~ ^[0-9][0-9A-Za-z.]*$ ]] || {
  echo "error: $version is not a version Arch can take" >&2
  exit 1
}

name="xta-native-desktop-$version-linux-x64"
stage=$(mktemp -d)
trap 'rm -rf "$stage"' EXIT
mkdir -p "$stage/$name" "$out"
cp -a "$app/bin" "$app/lib" "$stage/$name/"
install -m644 "$root/LICENSE" "$stage/$name/"
install -Dm644 "$here/packaging/linux/com.aimdi.xtanative.desktop" "$stage/$name/share/applications/com.aimdi.xtanative.desktop"
install -Dm644 "$here/packaging/linux/com.aimdi.xtanative.svg" "$stage/$name/share/icons/hicolor/scalable/apps/com.aimdi.xtanative.svg"

# Same bytes for the same app: fixed order, owners and times.
epoch=${SOURCE_DATE_EPOCH:-$(git -C "$root" log -1 --format=%ct)}
tarball="$out/$name.tar.gz"
tar --sort=name --owner=0 --group=0 --numeric-owner --mtime="@$epoch" \
  -C "$stage" -cf - "$name" | gzip -9n > "$tarball"

sha=$(sha256sum "$tarball" | cut -d' ' -f1)
sed -e "s/@PKGVER@/$version/g" -e "s/@SHA256@/$sha/g" -e "s|@REPO@|$repo|g" \
  "$here/packaging/arch/PKGBUILD.in" > "$out/PKGBUILD"
echo "Packaged $tarball ($sha), PKGBUILD for xta-native $version"

if [[ "$build_package" == --makepkg ]]; then
  # makepkg takes the tarball beside the PKGBUILD instead of downloading it,
  # and still checks it against sha256sums.
  (cd "$out" && makepkg --force --nodeps --noconfirm)
fi
