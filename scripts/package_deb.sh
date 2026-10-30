#!/usr/bin/env bash
# Builds the Debian/Ubuntu package of a release tarball made by
# scripts/package_linux.sh. It installs what the Arch package does: XTA in
# /opt/xta, `xta` on the PATH, its menu entry, icons and AppStream data.
#   <out>/xta_<pkgver>_amd64.deb             (no flavour)
#   <out>/xta_<pkgver>_<flavour>_amd64.deb   (a flavour, such as ubuntu22.04)
# The package depends on the libmpv the bundle was linked against: libmpv2,
# or libmpv1 for a bundle built on Ubuntu 22.04. See docs/desktop.md.
#
# Usage: scripts/package_deb.sh <tarball> <tag> <out-dir> [<flavour>]
set -euo pipefail

tarball=${1:?release tarball}
tag=${2:?release tag}
out=${3:?output directory}
flavour=${4:-}
root=$(cd "$(dirname "$0")/.." && pwd)

# The Arch package's version (see package_linux.sh), in the characters a
# Debian version allows. A flavour sorts below the plain version ("~").
version=$(sed -n 's/^version: *\([0-9.]*\).*/\1/p' "$root/pubspec.yaml")
build=$(grep -o '[0-9]*$' <<<"$tag" || true)
pkgver="$version.${build:-$(tr -c 'A-Za-z0-9.\n' '+' <<<"$tag")}"
debver="$pkgver${flavour:+~$flavour}"
file="xta_${pkgver}${flavour:+_$flavour}_amd64.deb"

stage=$(mktemp -d)
trap 'rm -rf "$stage"' EXIT
tar -xzf "$tarball" -C "$stage"
bundle=$(find "$stage" -mindepth 1 -maxdepth 1 -type d -name 'xta-*' | head -1)
[[ -x "$bundle/xta" && -d "$bundle/share" ]] || {
  echo "error: $tarball is not a release tarball from package_linux.sh" >&2
  exit 1
}

pkg="$stage/pkg"
install -d "$pkg/DEBIAN" "$pkg/opt/xta" "$pkg/usr/bin" "$pkg/usr/share/doc/xta"
cp -a "$bundle/xta" "$bundle/lib" "$bundle/data" "$pkg/opt/xta/"
ln -s /opt/xta/xta "$pkg/usr/bin/xta"
cp -a "$bundle/share/." "$pkg/usr/share/"
install -m644 "$bundle/LICENSE" "$pkg/usr/share/doc/xta/copyright"

# The video plugin is linked against one libmpv; the package needs that one.
mpv=$(readelf -d "$pkg/opt/xta/lib/libmedia_kit_video_plugin.so" | grep -o 'libmpv\.so\.[0-9]*' | head -1 || true)
case "$mpv" in
  libmpv.so.1) libmpv=libmpv1 ;;
  libmpv.so.2) libmpv=libmpv2 ;;
  *)
    echo "error: the video plugin links ${mpv:-no libmpv}; expected libmpv.so.1 or libmpv.so.2" >&2
    exit 1
    ;;
esac

# GTK, its libraries and the bundle's own (Flutter, SQLite, ONNX Runtime,
# sherpa-onnx) cover every library the bundle loads besides libmpv. Files
# and folders go through the desktop portal, links through xdg-open.
cat >"$pkg/DEBIAN/control" <<EOF
Package: xta
Version: $debver
Architecture: amd64
Maintainer: XTA <https://github.com/Aimdi/XTA>
Installed-Size: $(du -sk "$pkg" | cut -f1)
Depends: libc6 (>= 2.34), libgtk-3-0t64 | libgtk-3-0, $libmpv, libstdc++6, zlib1g, xdg-utils, xdg-desktop-portal
Recommends: xdg-desktop-portal-gtk | xdg-desktop-portal-kde, xdg-user-dirs
Section: net
Priority: optional
Homepage: https://github.com/Aimdi/XTA
Description: private, read-oriented reader for X
 XTA reads X and other sources, such as Bluesky, Mastodon, Reddit,
 Substack and RSS feeds, and never posts, likes or shares anything in your
 name. The accounts you follow, your groups, saved posts and likes stay on
 this computer.
EOF

# Same bytes for the same tarball: dpkg-deb dates every file with this.
export SOURCE_DATE_EPOCH=${SOURCE_DATE_EPOCH:-$(git -C "$root" log -1 --format=%ct)}
mkdir -p "$out"
dpkg-deb --root-owner-group -Zxz --build "$pkg" "$out/$file" >/dev/null
echo "Packaged $out/$file (xta $debver, needs $libmpv)"
