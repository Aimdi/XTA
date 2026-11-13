#!/usr/bin/env bash
# Compiles the screens' plain-Kotlin rules in :ui (the files that use neither
# Compose, AndroidX, Coil nor the generated resources) against :core, :store,
# :services and :networks with a standalone kotlinc, then runs the tests in
# ui/src/desktopTest that are plain too: a check for machines where Gradle
# cannot fetch Compose. CI compiles and tests everything with Gradle.
#
#   KOTLINC=<kotlinc home> LIBS=<dir of jars> bash native/tool/uilogic.sh [package to test]
#
# A plain file that needs a type from a Compose file fails here: keep such
# types in plain files, beside the rules that use them.
set -euo pipefail
here=$(cd "$(dirname "$0")/.." && pwd)
package=${1:-}
K=${KOTLINC:?set KOTLINC to a kotlinc home}
LIBS=${LIBS:?set LIBS to a directory of the jars}
junit=$(ls "$LIBS"/junit-platform-console-standalone-*.jar | head -1)
cp=$(ls "$LIBS"/*.jar | grep -v console-standalone | tr '\n' ':')
testlibs="$K/lib/kotlin-test.jar:$K/lib/kotlin-test-junit5.jar:$junit"
out=$(mktemp -d)
trap 'rm -rf "$out"' EXIT

compile() { # <out dir> <classpath> <friend dir or empty> <files...>
  local dest=$1 classpath=$2 friend=$3
  shift 3
  mkdir -p "$dest"
  [ "$#" -gt 0 ] || return 0
  "$K/bin/kotlinc" "$@" -cp "$classpath" -d "$dest" -jvm-target 17 -nowarn ${friend:+-Xfriend-paths=$friend} 2>&1 \
    | grep -v JAVA_TOOL_OPTIONS || true
  [ -f "$dest/META-INF/main.kotlin_module" ] || { echo "COMPILE FAILED: $dest"; exit 1; }
}

kotlin_files() { find "$@" -name '*.kt' 2> /dev/null | sort; }

# Files of the screens that need none of what only Gradle can fetch.
plain() {
  kotlin_files "$@" | while read -r f; do
    grep -qE '^import (androidx|org\.jetbrains\.compose|coil3|com\.aimdi\.xtanative\.ui\.generated)\.|@Composable' "$f" || echo "$f"
  done
}

# The plain modules change rarely: each is compiled once per content of its
# sources (and of those before it), kept in UILOGIC_CACHE for later runs.
cache=${UILOGIC_CACHE:-${TMPDIR:-/tmp}/xta-uilogic-cache}
mkdir -p "$cache"
classes=""
key=""
for module in core store services networks; do
  mapfile -t files < <(kotlin_files "$here/$module/src/main/kotlin")
  key=$( { echo "$key"; sha256sum "${files[@]}" | sed "s#$here/##"; } | sha256sum | cut -c1-16)
  kept="$cache/$module-$key"
  if [ ! -f "$kept/META-INF/main.kotlin_module" ]; then
    compile "$out/$module" "$cp${classes:+:$classes}" "" "${files[@]}"
    { cp -r "$out/$module" "$kept.tmp.$$" && mv -T "$kept.tmp.$$" "$kept"; } 2> /dev/null || rm -rf "$kept.tmp.$$"
  fi
  [ -f "$kept/META-INF/main.kotlin_module" ] || kept="$out/$module"
  classes="${classes:+$classes:}$kept"
done

mapfile -t ui < <(plain "$here/ui/src/commonMain/kotlin" "$here/ui/src/desktopMain/kotlin")
echo "plain :ui files: ${#ui[@]}"
compile "$out/ui" "$cp:$classes" "" "${ui[@]}"
classes="$classes:$out/ui"

mapfile -t tests < <(plain "$here/ui/src/desktopTest/kotlin")
[ "${#tests[@]}" -gt 0 ] || { echo "no plain tests in ui/src/desktopTest"; exit 0; }
compile "$out/test" "$cp:$classes:$testlibs" "$out/ui" "${tests[@]}"
cp -r "$here/ui/src/desktopTest/resources/." "$out/test/" 2> /dev/null || true

runtime="$out/test:$classes:$cp:$K/lib/kotlin-stdlib.jar:$K/lib/kotlin-test.jar:$K/lib/kotlin-test-junit5.jar"

# A test that returns a value is never run by JUnit, and nothing says so: list them first.
compile "$out/returns" "$K/lib/kotlin-stdlib.jar" "" "$here/tool/TestReturns.kt"
if ! report=$(java -cp "$out/returns:$runtime:$junit" TestReturnsKt "$out/test" 2>&1); then returns=failed; fi
printf '%s\n' "$report" | grep -v JAVA_TOOL_OPTIONS || true

select=(--scan-class-path)
[ -n "$package" ] && select=(--select-package "$package")
java -jar "$junit" execute --class-path "$runtime" \
  "${select[@]}" --details=tree --disable-banner 2>&1 | grep -v JAVA_TOOL_OPTIONS | tail -60
[ "${returns:-}" != failed ] || { echo "SOME TESTS NEVER RUN: see NOT RUN BY JUNIT above"; exit 1; }
