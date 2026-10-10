#!/usr/bin/env bash
# Packages a `flutter build linux --release` bundle for a release:
#   <out>/xta-<tag>-linux-x86_64.tar.gz  the bundle, its launcher and icon
#   <out>/PKGBUILD                       the Arch package of that tarball
# With --makepkg (on Arch, or in an archlinux container) it also builds
#   <out>/xta-bin-<pkgver>-1-x86_64.pkg.tar.zst
#
# Usage: scripts/package_linux.sh <bundle-dir> <tag> <out-dir> [--makepkg]
set -euo pipefail

bundle=${1:?bundle directory}
tag=${2:?release tag}
out=${3:?output directory}
build_package=${4:-}
root=$(cd "$(dirname "$0")/.." && pwd)

[[ -x "$bundle/xta" && -d "$bundle/lib" && -d "$bundle/data" ]] || {
  echo "error: $bundle is not a Linux release bundle (expected xta, lib/, data/)" >&2
  exit 1
}

# Arch versions cannot carry '-': 4.12.0 plus the fork build, so aimdi174 is
# 4.12.0.174 and every release sorts above the one before it.
version=$(sed -n 's/^version: *\([0-9.]*\).*/\1/p' "$root/pubspec.yaml")
build=$(grep -o '[0-9]*$' <<<"$tag" || true)
pkgver="$version.${build:-$(tr -c 'A-Za-z0-9.\n' '_' <<<"$tag")}"

name="xta-$tag-linux-x86_64"
stage=$(mktemp -d)
trap 'rm -rf "$stage"' EXIT
mkdir -p "$stage/$name" "$out"
cp -a "$bundle/xta" "$bundle/lib" "$bundle/data" "$stage/$name/"
install -m644 "$root/packaging/linux/com.aimdi.xta.desktop" "$stage/$name/"
install -m644 "$root/LICENSE" "$stage/$name/"
python3 - "$root/assets/icon.png" "$stage/$name/com.aimdi.xta.png" <<'PY'
import sys
from PIL import Image
Image.open(sys.argv[1]).convert("RGBA").resize((512, 512), Image.LANCZOS).save(sys.argv[2], optimize=True)
PY

# Same bytes for the same bundle: fixed order, owners and times.
epoch=${SOURCE_DATE_EPOCH:-$(git -C "$root" log -1 --format=%ct)}
tarball="$out/$name.tar.gz"
tar --sort=name --owner=0 --group=0 --numeric-owner --mtime="@$epoch" \
  -C "$stage" -cf - "$name" | gzip -9n >"$tarball"

sha=$(sha256sum "$tarball" | cut -d' ' -f1)
sed -e "s/@TAG@/$tag/g" -e "s/@PKGVER@/$pkgver/g" -e "s/@SHA256@/$sha/g" \
  "$root/packaging/arch/PKGBUILD.in" >"$out/PKGBUILD"
echo "Packaged $tarball ($sha), PKGBUILD for xta-bin $pkgver"

if [[ "$build_package" == --makepkg ]]; then
  # makepkg takes a source already beside the PKGBUILD instead of downloading
  # it, and still checks it against sha256sums.
  (cd "$out" && makepkg --force --nodeps --noconfirm)
fi
