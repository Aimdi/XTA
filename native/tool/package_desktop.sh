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
install -m644 "$root/LICENSE" "$stage/$name/"
install -m755 "$here/packaging/linux/install.sh" "$stage/$name/"
install -Dm644 "$here/packaging/linux/com.aimdi.xtanative.desktop" "$stage/$name/share/applications/com.aimdi.xtanative.desktop"
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
# The AppStream data names this build's version and day in its <releases>, so it never
# has to be kept up to date by hand.
releases="  <releases>
    <release version=\"$version\" date=\"$(date -u -d "@$epoch" +%F)\" type=\"$release_type\">
      <url type=\"details\">https://github.com/$repo/releases/tag/$tag</url>
    </release>
  </releases>"
metainfo="$stage/$name/share/metainfo/com.aimdi.xtanative.metainfo.xml"
mkdir -p "$(dirname "$metainfo")"
grep -q '^  <!-- @RELEASES@ -->$' "$here/packaging/linux/com.aimdi.xtanative.metainfo.xml" || {
  echo "error: com.aimdi.xtanative.metainfo.xml has no <!-- @RELEASES@ --> line for the releases" >&2
  exit 1
}
while IFS= read -r line; do
  if [[ "$line" == '  <!-- @RELEASES@ -->' ]]; then printf '%s\n' "$releases"; else printf '%s\n' "$line"; fi
done <"$here/packaging/linux/com.aimdi.xtanative.metainfo.xml" >"$metainfo"
chmod 644 "$metainfo"

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
