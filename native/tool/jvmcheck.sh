#!/usr/bin/env bash
# Compiles :core, and :networks when asked, with a standalone kotlinc and runs
# the tests with JUnit's console launcher: a check for machines where Gradle
# cannot reach Maven Central.
#
#   KOTLINC=<kotlinc home> LIBS=<dir of jars> bash native/tool/jvmcheck.sh [core|networks] [package to test]
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

compile "$out/core" "$cp" "" "$here/core/src/main/kotlin"
classes="$out/core"
if [ "$module" = core ]; then
  compile "$out/test" "$cp:$classes:$testlibs" "$out/core" "$here/core/src/test/kotlin"
  cp -r "$here/core/src/test/resources/." "$out/test/" 2> /dev/null || true
else
  compile "$out/$module" "$cp:$classes" "" "$here/$module/src/main/kotlin"
  classes="$classes:$out/$module"
  compile "$out/test" "$cp:$classes:$testlibs" "$out/$module" "$here/$module/src/test/kotlin"
  cp -r "$here/$module/src/test/resources/." "$out/test/" 2> /dev/null || true
fi

select=(--scan-class-path)
[ -n "$package" ] && select=(--select-package "$package")
java -jar "$junit" execute --class-path "$out/test:$classes:$cp:$K/lib/kotlin-stdlib.jar:$K/lib/kotlin-test.jar:$K/lib/kotlin-test-junit5.jar" \
  "${select[@]}" --details=tree --disable-banner 2>&1 | grep -v JAVA_TOOL_OPTIONS | tail -60
