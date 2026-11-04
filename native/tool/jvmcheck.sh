#!/usr/bin/env bash
# Compiles :core, and :networks when asked, with a standalone kotlinc and runs
# the tests with JUnit's console launcher: a check for machines where Gradle
# cannot reach Maven Central.
#
#   KOTLINC=<kotlinc home> LIBS=<dir of jars> bash native/tool/jvmcheck.sh [core|networks|plugins/<id>|...] [package to test]
#
# LIBS holds okhttp, okio, kotlinx-coroutines-core-jvm,
# kotlinx-serialization-{core,json}-jvm, jsoup and
# junit-platform-console-standalone. Without a package every test runs.
set -euo pipefail
here=$(cd "$(dirname "$0")/.." && pwd)
module=${1:-core}
package=${2:-}
K=${KOTLINC:?set KOTLINC to a kotlinc home}
LIBS=${LIBS:?set LIBS to a directory of the jars}
junit=$(ls "$LIBS"/junit-platform-console-standalone-*.jar | head -1)
cp=$(ls "$LIBS"/*.jar | grep -v console-standalone | tr '\n' ':')
testlibs="$K/lib/kotlin-test.jar:$K/lib/kotlin-test-junit5.jar:$junit"
out=$(mktemp -d)
trap 'rm -rf "$out"' EXIT

compile() { # <out dir> <classpath> <friend dir or empty> <source dirs...>
  local dest=$1 classpath=$2 friend=$3
  shift 3
  local sources
  sources=$(find "$@" -name '*.kt' 2> /dev/null || true)
  mkdir -p "$dest"
  [ -n "$sources" ] || return 0
  # shellcheck disable=SC2086
  "$K/bin/kotlinc" $sources -cp "$classpath" -d "$dest" -jvm-target 17 -nowarn ${friend:+-Xfriend-paths=$friend} 2>&1 \
    | grep -v JAVA_TOOL_OPTIONS || true
  [ -f "$dest/META-INF/main.kotlin_module" ] || { echo "COMPILE FAILED: $*"; exit 1; }
}

# The plain-Kotlin modules a module's build file names with project(":..."), in order.
deps_of() {
  grep -o 'project(":[a-z-]*")' "$here/$1/build.gradle.kts" 2> /dev/null | sed 's/project(":\(.*\)")/\1/' | grep -v '^core$' || true
}

compile "$out/core" "$cp" "" "$here/core/src/main/kotlin"
classes="$out/core"
if [ "$module" = core ]; then
  compile "$out/test" "$cp:$classes:$testlibs" "$out/core" "$here/core/src/test/kotlin"
  cp -r "$here/core/src/test/resources/." "$out/test/" 2> /dev/null || true
elif [ "${module#plugins/}" != "$module" ]; then
  # A plugin (plugins/<id>): its reading layer, the plain Kotlin under
  # src/commonMain/kotlin/com/aimdi/xtanative/networks/<id>, and its tests in
  # src/desktopTest; its screens are Compose, which uicheck.sh compiles.
  id=${module#plugins/}
  compile "$out/networks" "$cp:$classes" "" "$here/networks/src/main/kotlin"
  classes="$classes:$out/networks"
  # :store as well, which the plugin build provides beside :networks, for a
  # reading layer that keeps XTA's rows (Stocks' watchlist).
  layer=("$here/$module/src/commonMain/kotlin/com/aimdi/xtanative/networks/$id" "$here/$module/src/desktopTest/kotlin/com/aimdi/xtanative/networks/$id")
  if grep -rqs '^import com\.aimdi\.xtanative\.store\.' "${layer[@]}"; then
    compile "$out/store" "$cp:$classes" "" "$here/store/src/main/kotlin"
    classes="$classes:$out/store"
  fi
  compile "$out/plugin" "$cp:$classes" "" "$here/$module/src/commonMain/kotlin/com/aimdi/xtanative/networks/$id"
  classes="$classes:$out/plugin"
  compile "$out/test" "$cp:$classes:$testlibs" "$out/plugin" "$here/$module/src/desktopTest/kotlin/com/aimdi/xtanative/networks/$id"
  cp -r "$here/$module/src/desktopTest/resources/." "$out/test/" 2> /dev/null || true
else
  for dep in $(deps_of "$module"); do
    compile "$out/$dep" "$cp:$classes" "" "$here/$dep/src/main/kotlin"
    classes="$classes:$out/$dep"
  done
  compile "$out/$module" "$cp:$classes" "" "$here/$module/src/main/kotlin"
  classes="$classes:$out/$module"
  compile "$out/test" "$cp:$classes:$testlibs" "$out/$module" "$here/$module/src/test/kotlin"
  cp -r "$here/$module/src/test/resources/." "$out/test/" 2> /dev/null || true
fi

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
