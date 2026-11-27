#!/usr/bin/env bash
# Packs XTA Native's desktop app (what :desktop:createDistributable made) for a
# prerelease, as scripts/package_linux.sh packs XTA's:
#   <out>/xta-native-desktop-<version>-linux-x64.tar.gz  the app (bin/, and lib/ with its
#                                                       Java runtime and, in lib/app/resources,
#                                                       the voice engine tool/voice_engine.py
#                                                       put in the build and the video engine's
#                                                       shim the build made, which plays through
#                                                       the system's libmpv), install.sh, which
#                                                       adds it to the user's menu, and, under
#                                                       share/, its menu entry, icon and
#                                                       AppStream data
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
# What the menu entry and the Arch package start: the app with each user's class-data
# sharing archive, made from the classes tool/cds_classlist.sh listed (xta-native-launch).
install -m755 "$here/packaging/linux/xta-native-launch" "$stage/$name/bin/xta-native-launch"
[[ -f "$app/lib/app/xta-native.classlist" ]] \
  || echo "warning: $app lists no classes (tool/cds_classlist.sh): starts will not get a class-data archive" >&2
install -m644 "$root/LICENSE" "$stage/$name/"
install -m755 "$here/packaging/linux/install.sh" "$stage/$name/"
install -Dm644 "$here/packaging/linux/com.aimdi.xtanative.desktop" "$stage/$name/share/applications/com.aimdi.xtanative.desktop"
install -Dm644 "$here/packaging/linux/com.aimdi.xtanative.mime.xml" "$stage/$name/share/mime/packages/com.aimdi.xtanative.xml"
install -Dm644 "$here/packaging/linux/com.aimdi.xtanative.svg" "$stage/$name/share/icons/hicolor/scalable/apps/com.aimdi.xtanative.svg"
install -Dm644 "$here/packaging/linux/com.aimdi.xtanative-symbolic.svg" "$stage/$name/share/icons/hicolor/symbolic/apps/com.aimdi.xtanative-symbolic.svg"
# The same drawing in pixels (rendered once from the SVG, packaging/linux/icons/), for
# menus, docks and software centres that take no SVG. After a change to the SVG:
#   for s in 48 64 128 256 512; do rsvg-convert -w $s -h $s com.aimdi.xtanative.svg -o icons/com.aimdi.xtanative-$s.png; done
for size in 48 64 128 256 512; do
  install -Dm644 "$here/packaging/linux/icons/com.aimdi.xtanative-$size.png" "$stage/$name/share/icons/hicolor/${size}x$size/apps/com.aimdi.xtanative.png"
done

# Same bytes for the same app: fixed order, owners and times.
epoch=${SOURCE_DATE_EPOCH:-$(git -C "$root" log -1 --format=%ct)}

# The release this is, as CI tags it: a release (MAJOR.MINOR.PATCH, native/VERSION) is
# v<version>, a prerelease (the release's version with the run's number after it) native-<version>.
if [[ "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  tag=v$version release_type=stable
else
  tag=native-$version release_type=development
fi
# The AppStream data names this build's version and day first in its <releases>, before the
# releases listed in the file (one line each), of which one of the same version goes.
release="    <release version=\"$version\" date=\"$(date -u -d "@$epoch" +%F)\" type=\"$release_type\"><url type=\"details\">https://github.com/$repo/releases/tag/$tag</url></release>"
metainfo="$stage/$name/share/metainfo/com.aimdi.xtanative.metainfo.xml"
mkdir -p "$(dirname "$metainfo")"
grep -q '^    <!-- @RELEASE@ -->$' "$here/packaging/linux/com.aimdi.xtanative.metainfo.xml" || {
  echo "error: com.aimdi.xtanative.metainfo.xml has no <!-- @RELEASE@ --> line for this build's release" >&2
  exit 1
}
while IFS= read -r line; do
  if [[ "$line" == '    <!-- @RELEASE@ -->' ]]; then
    printf '%s\n' "$release"
  elif [[ "$line" != *"<release version=\"$version\""* ]]; then
    printf '%s\n' "$line"
  fi
done <"$here/packaging/linux/com.aimdi.xtanative.metainfo.xml" >"$metainfo"
chmod 644 "$metainfo"
# Where the tools are installed, what software centres and menus read is checked before it is packed.
if command -v appstreamcli >/dev/null 2>&1; then
  appstreamcli validate --no-net --explain "$metainfo" || { echo "error: $metainfo is not valid AppStream data" >&2; exit 1; }
fi
if command -v desktop-file-validate >/dev/null 2>&1; then
  desktop-file-validate "$stage/$name/share/applications/com.aimdi.xtanative.desktop" || { echo "error: the desktop entry is not valid" >&2; exit 1; }
fi

tarball="$out/$name.tar.gz"
tar --sort=name --owner=0 --group=0 --numeric-owner --mtime="@$epoch" \
  -C "$stage" -cf - "$name" | gzip -9n > "$tarball"

sha=$(sha256sum "$tarball" | cut -d' ' -f1)
sed -e "s/@PKGVER@/$version/g" -e "s/@SHA256@/$sha/g" -e "s|@REPO@|$repo|g" -e "s|@TAG@|$tag|g" \
  "$here/packaging/arch/PKGBUILD.in" > "$out/PKGBUILD"
echo "Packaged $tarball ($sha), PKGBUILD for xta-native $version"

if [[ "$build_package" == --makepkg ]]; then
  # makepkg takes the tarball beside the PKGBUILD instead of downloading it,
  # and still checks it against sha256sums.
  (cd "$out" && makepkg --force --nodeps --noconfirm)
fi
