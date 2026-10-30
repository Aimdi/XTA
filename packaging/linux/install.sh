#!/bin/sh
# Adds XTA, unpacked from its release tarball, to this user's application
# menu, or removes it again with --uninstall. Nothing outside the user's own
# data folder is touched, so no root is needed. See docs/desktop.md.
set -eu

app=com.aimdi.xta
here=$(cd "$(dirname "$0")" && pwd)
data=${XDG_DATA_HOME:-$HOME/.local/share}

# Menus watch these folders themselves; a cache already there is brought up
# to date, and none is made where there was none.
refresh_caches() {
  if [ -f "$data/applications/mimeinfo.cache" ] && command -v update-desktop-database >/dev/null 2>&1; then
    update-desktop-database -q "$data/applications" || true
  fi
  if [ -f "$data/icons/hicolor/icon-theme.cache" ] && command -v gtk-update-icon-cache >/dev/null 2>&1; then
    gtk-update-icon-cache -q -t "$data/icons/hicolor" || true
  fi
}

install_entry() {
  # The menu entry runs XTA from here, so a path the desktop entry format
  # would have to escape is refused rather than written wrongly.
  case "$here" in
    *[\"\`\$\\]*)
      echo "Move XTA to a folder whose path has no quotes, backslashes or \$ signs, then run this again." >&2
      exit 1
      ;;
  esac
  mkdir -p "$data/applications"
  while IFS= read -r line; do
    case "$line" in
      Exec=*) printf 'Exec="%s"\n' "$here/xta" ;;
      *) printf '%s\n' "$line" ;;
    esac
  done <"$here/share/applications/$app.desktop" >"$data/applications/$app.desktop"
  (cd "$here/share" && find icons metainfo -type f) | while IFS= read -r file; do
    mkdir -p "$data/$(dirname "$file")"
    cp "$here/share/$file" "$data/$file"
  done
  refresh_caches
  echo "XTA is in your application menu. Remove it with: $here/install.sh --uninstall"
}

uninstall_entry() {
  rm -f "$data/applications/$app.desktop" "$data/metainfo/$app.metainfo.xml"
  for icon in "$data"/icons/hicolor/*/apps/"$app".png; do
    rm -f "$icon"
  done
  refresh_caches
  echo "XTA is no longer in your application menu. Your data in $data/$app is kept."
}

case "${1:-}" in
  "") install_entry ;;
  --uninstall) uninstall_entry ;;
  *)
    echo "Usage: $0 [--uninstall]" >&2
    exit 2
    ;;
esac
