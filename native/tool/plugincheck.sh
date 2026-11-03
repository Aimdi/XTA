#!/usr/bin/env bash
# Compiles a plugin's commonMain and desktopMain (its reading layer and its
# Compose screens) without Gradle, as uicheck.sh compiles :ui, against :ui's
# classes and the plain modules a plugin builds with:
#
#   KOTLINC=<kotlinc home> COMPOSE_LIBS=<dir compose-libs.sh filled> bash native/tool/plugincheck.sh <id>
#
# UI_CLASSES=<dir> skips compiling :ui, taking the classes a run of
# `OUT=<dir> bash native/tool/uicheck.sh` left there.
#
# 1. The plugin's Strings object, as the plugins build generates it from
#    plugins/<id>/strings: the generator's own functions, taken from
#    plugins/build.gradle.kts and run with kotlinc -script.
# 2. :ui, compiled by uicheck.sh, as a jar beside the Compose jars.
# 3. The plugin, compiled by uicheck.sh from a copy of it holding the Strings.
#
# The reading layer's tests run with jvmcheck.sh plugins/<id>; androidMain is
# not compiled here (CI builds the APK).
set -euo pipefail
here=$(cd "$(dirname "$0")/.." && pwd)
id=${1:?name the plugin: plugins/<id>}
K=${KOTLINC:?set KOTLINC to a kotlinc home}
LIBS=${COMPOSE_LIBS:?set COMPOSE_LIBS to the directory compose-libs.sh filled}
plugin=$here/plugins/$id
[ -f "$plugin/plugin.json" ] || { echo "no plugins/$id/plugin.json"; exit 1; }
entry=$(python3 -I -c 'import json,sys; print(json.load(open(sys.argv[1]))["entry"])' "$plugin/plugin.json")
package=${entry%.*}
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
cache=${UICHECK_CACHE:-$LIBS/.uicheck-cache}
# The jars the plugin compiles against, in a folder that keeps its name, so
# uicheck.sh compiles :core and the others for it once per change, not per run.
libs=$cache/plugincheck-libs

# 1. Strings.
{
  echo 'import java.io.File'
  echo 'import javax.xml.parsers.DocumentBuilderFactory'
  echo 'import org.w3c.dom.Element'
  echo 'class PluginMeta(val id: String, val packageName: String)'
  sed -n '/^\/\/ The strings generator, from here/,/^\/\/ The end of the strings generator/p' "$here/plugins/build.gradle.kts"
  echo 'val generated = stringsSource(PluginMeta(args[0], args[1]), readStrings(File(args[2], "strings")))'
  echo 'File(args[3]).apply { parentFile.mkdirs() }.writeText(generated)'
} > "$work/strings.kts"
mkdir -p "$work/$id"
cp -r "$plugin/src" "$work/$id/src"
"$K/bin/kotlinc" -script "$work/strings.kts" "$id" "$package" "$plugin" "$work/$id/src/commonMain/kotlin/${package//.//}/Strings.kt" 2>&1 \
  | grep -v JAVA_TOOL_OPTIONS || true
[ -f "$work/$id/src/commonMain/kotlin/${package//.//}/Strings.kt" ] || { echo "plugincheck: the strings were not generated"; exit 1; }

# 2. :ui as a jar, beside the Compose jars: compiled here, or the classes
#    UI_CLASSES names (uicheck.sh's OUT from a run on this tree).
ui=${UI_CLASSES:-$work/ui}
if [ -z "${UI_CLASSES:-}" ]; then
  mkdir -p "$ui"
  UICHECK_CACHE=$cache OUT=$ui bash "$here/tool/uicheck.sh" "$here/ui" > "$work/ui.log" 2>&1 \
    || { grep -v JAVA_TOOL_OPTIONS "$work/ui.log" | tail -40; echo "plugincheck: :ui does not compile"; exit 1; }
fi
rm -rf "$libs"
mkdir -p "$libs"
find "$LIBS" -maxdepth 1 -name '*.jar' -exec ln -s {} "$libs/" \;
jar cf "$libs/xta-native-ui.jar" -C "$ui" . 2>&1 | grep -v JAVA_TOOL_OPTIONS || true

# 3. The plugin.
COMPOSE_LIBS=$libs DEPS="core networks store services" UICHECK_CACHE=$cache bash "$here/tool/uicheck.sh" "$work/$id"
