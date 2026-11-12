#!/usr/bin/env bash
# Publishes the plugins a release or prerelease built, and their catalogue
# plugins.json (PLUGINS.md), as assets of the one rolling release the app reads
# the catalogue from, native-plugins: the files first, then the catalogue that
# names them, then the files no catalogue names any more. native-plugins is a
# prerelease never marked latest, so releases/latest stays XTA's release.
#
#   GH_TOKEN=… GITHUB_REPOSITORY=owner/repo GITHUB_SHA=… \
#     bash native/tool/publish_plugins.sh <android plugins dir> <desktop plugins dir>
set -euo pipefail
android=${1:?android plugins dir}
desktop=${2:?desktop plugins dir}
repo=${GITHUB_REPOSITORY:?GITHUB_REPOSITORY}
here=$(cd "$(dirname "$0")" && pwd)
out=release-plugins

python3 "$here/plugin_catalogue.py" \
  --base-url "https://github.com/$repo/releases/download/native-plugins" \
  --android "$android" --desktop "$desktop" --out "$out/plugins.json" --copy-to "$out"
cat "$out/plugins.json"
if ! gh release view native-plugins --repo "$repo" > /dev/null 2>&1; then
  gh release create native-plugins --repo "$repo" --target "${GITHUB_SHA:?GITHUB_SHA}" --prerelease --latest=false \
    --title "XTA plugins" \
    --notes "The plugins XTA's plugin store offers, and plugins.json, the list it reads. Installed from the store, never by hand."
fi
gh release upload native-plugins "$out"/xta-plugin-* --repo "$repo" --clobber
gh release upload native-plugins "$out/plugins.json" --repo "$repo" --clobber
for asset in $(gh release view native-plugins --repo "$repo" --json assets -q '.assets[].name'); do
  case "$asset" in
    xta-plugin-*) [ -f "$out/$asset" ] || gh release delete-asset native-plugins "$asset" --repo "$repo" --yes ;;
  esac
done
