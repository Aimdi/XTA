#!/usr/bin/env bash
# Compiles a Compose Multiplatform module's commonMain and desktopMain sources
# for the JVM with a standalone kotlinc and the Compose compiler plugin,
# against the jars compose-libs.sh fetched and the classes of the plain modules
# it depends on (:core, ...): a compile check for machines where Gradle cannot
# reach Maven Central. Errors read file:line:column: error: message.
#
#   KOTLINC=<kotlinc home> COMPOSE_LIBS=<dir compose-libs.sh filled> bash native/tool/uicheck.sh [module dir]
#
# The module defaults to native/ui. Its commonMain and desktopMain (or jvmMain)
# go into one multiplatform compilation with a fragment each, the way Gradle
# compiles the desktop target, so expect/actual pairs are checked; SOURCE_SETS
# names another chain, e.g. "commonMain jvmMain desktopMain". androidMain is
# not compiled.
#
# composeResources in those source sets become a Res object with string,
# plurals, array, drawable and font accessors, as the Compose Gradle plugin
# generates it. Its package is RES_PACKAGE, else packageOfResClass in the
# module's build.gradle.kts, else the package the sources import Res from,
# else Gradle's default (<group>.<module>.generated.resources).
#
# The plain modules the build file names with project(":x") (:core, ...) are
# compiled first, once per change of their sources; DEPS="core store" names
# them instead. KOTLINC_ARGS adds compiler arguments (e.g. -opt-in=...);
# WARNINGS=1 shows warnings; OUT=<dir> keeps the compiled classes there.
set -euo pipefail
here=$(cd "$(dirname "$0")/.." && pwd)
module=$(cd "${1:-$here/ui}" && pwd)
K=${KOTLINC:?set KOTLINC to a kotlinc home}
# kotlinc asks for 512 MB; the whole :ui with the Compose plugin needs more.
export JAVA_OPTS="${JAVA_OPTS:--Xmx2g -Xms256M -Xss4m}"
LIBS=${COMPOSE_LIBS:?set COMPOSE_LIBS to the directory compose-libs.sh filled}
[ -f "$K/lib/compose-compiler-plugin.jar" ] || { echo "no compose-compiler-plugin.jar in $K/lib"; exit 1; }
jars=$(find "$LIBS" -maxdepth 1 -name '*.jar' | sort | tr '\n' ':')
jars=${jars%:}
[ -n "$jars" ] || { echo "no jars in $LIBS: run native/tool/compose-libs.sh first"; exit 1; }
# The module compiles against what Gradle shows it (compile-classpath.txt, which
# compose-libs.sh writes: a library another needs only at run time is not on it);
# the plain modules before it against every jar.
module_jars=$jars
if [ -f "$LIBS/compile-classpath.txt" ]; then
  module_jars=$(while IFS= read -r p; do [ ! -f "$LIBS/${p##*/}" ] || echo "$LIBS/${p##*/}"; done < "$LIBS/compile-classpath.txt" | tr '\n' ':')
  module_jars=${module_jars%:}
else
  echo "uicheck: no compile-classpath.txt in $LIBS (an older compose-libs.sh): compiling against every jar" >&2
fi
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
cache=${UICHECK_CACHE:-$LIBS/.uicheck-cache}
mkdir -p "$cache"

kotlinc() { # <log file> <args...>: runs kotlinc, keeps its output, returns its status
  local log=$1
  shift
  set +e
  "$K/bin/kotlinc" "$@" > "$log" 2>&1
  local status=$?
  set -e
  return $status
}

# The plain Kotlin modules (:core, ...) the module names with project(":x"), each
# after the ones it names itself; compiled once per change of their sources.
project_deps() { # <module dir>: the names its build file depends on
  [ -f "$1/build.gradle.kts" ] || return 0
  grep -v '^\s*//' "$1/build.gradle.kts" | grep -oE 'project\(":[A-Za-z0-9_-]+"\)' | sed -E 's/project\(":(.*)"\)/\1/' | sort -u
}
order=()
visit() { # <module name>
  local name=$1 dep
  case " ${order[*]} " in *" $name "*) return ;; esac
  for dep in $(project_deps "$here/$name"); do visit "$dep"; done
  order+=("$name")
}
if [ -n "${DEPS:-}" ]; then
  read -r -a order <<< "$DEPS"
else
  for dep in $(project_deps "$module"); do visit "$dep"; done
fi

compile_dep() { # <module name> <classpath>
  local name=$1 classpath=$2 src="$here/$1/src/main/kotlin" key dest
  key=$( (cat "$K/build.txt"; echo "$classpath"; find "$src" -name '*.kt' -print0 | sort -z | xargs -0 sha256sum) | sha256sum | cut -c1-16)
  dest="$cache/$name-$key"
  if [ ! -f "$dest/META-INF/main.kotlin_module" ]; then
    echo "uicheck: compiling :$name" >&2
    # Into a folder of its own, then moved into place: another run (another
    # worktree with the same sources) may be compiling the same module.
    local tmp
    tmp=$(mktemp -d "$cache/.$name.XXXXXX")
    find "$src" -name '*.kt' > "$work/$name.sources"
    if ! kotlinc "$work/$name.log" @"$work/$name.sources" -cp "$classpath" -d "$tmp" -jvm-target 17 -nowarn; then
      grep -v JAVA_TOOL_OPTIONS "$work/$name.log" >&2
      echo "uicheck: :$name does not compile" >&2
      rm -rf "$tmp"
      exit 1
    fi
    mv -T "$tmp" "$dest" 2> /dev/null || rm -rf "$tmp"
  fi
  echo "$dest"
}

deps=""
for name in "${order[@]}"; do
  if [ ! -d "$here/$name/src/main/kotlin" ]; then
    echo "uicheck: :$name has no src/main/kotlin; left off the classpath" >&2
    continue
  fi
  deps="${deps:+$deps:}$(compile_dep "$name" "$jars${deps:+:$deps}")"
done

# Source sets from the most common to the desktop one, each refining the one before.
if [ -n "${SOURCE_SETS:-}" ]; then
  read -r -a sets <<< "$SOURCE_SETS"
else
  platform=desktopMain
  [ -d "$module/src/desktopMain" ] || [ ! -d "$module/src/jvmMain" ] || platform=jvmMain
  sets=(commonMain "$platform")
fi

# The Res object the Compose Gradle plugin would generate.
python3 -I - "$module" "$work/generated" "${sets[*]}" "${RES_PACKAGE:-}" <<'PY'
import os
import re
import sys
import xml.etree.ElementTree as ET

module, out, source_sets, package = sys.argv[1:5]
source_sets = source_sets.split()
KEYWORDS = {"as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if", "in", "interface",
            "is", "null", "object", "package", "return", "super", "this", "throw", "true", "try", "typealias",
            "typeof", "val", "var", "when", "while"}


def read(path):
    try:
        with open(path, encoding="utf-8") as f:
            return f.read()
    except OSError:
        return ""


def sources():
    for source_set in source_sets:
        for root, _, files in os.walk(os.path.join(module, "src", source_set)):
            for name in files:
                if name.endswith(".kt"):
                    yield read(os.path.join(root, name))


def res_package():
    if package:
        return package
    m = re.search(r'packageOfResClass\s*=\s*"([^"]+)"', read(os.path.join(module, "build.gradle.kts")))
    if m:
        return m.group(1)
    for text in sources():
        m = re.search(r"^import\s+([\w.]+\.generated\.resources)\.", text, re.M) \
            or re.search(r"^import\s+([\w.]+)\.Res\s*$", text, re.M)
        if m:
            return m.group(1)
    settings = read(os.path.join(module, "..", "settings.gradle.kts"))
    root = re.search(r'rootProject\.name\s*=\s*"([^"]+)"', settings)
    group = re.search(r'^\s*group\s*=\s*"([^"]+)"', read(os.path.join(module, "build.gradle.kts")), re.M)
    group = group.group(1) if group else (root.group(1) if root else "")
    ident = lambda s: re.sub(r"[^a-z0-9_.]", "_", s.lower())
    name = ident(os.path.basename(module))
    return f"{ident(group)}.{name}.generated.resources" if group else f"{name}.generated.resources"


def identifier(name):
    name = re.sub(r"[^A-Za-z0-9_]", "_", name)
    name = "_" + name if name[:1].isdigit() else name
    return f"`{name}`" if name in KEYWORDS else name


kinds = {"string": set(), "plurals": set(), "array": set(), "drawable": set(), "font": set()}
for source_set in source_sets:
    base = os.path.join(module, "src", source_set, "composeResources")
    if not os.path.isdir(base):
        continue
    for folder in sorted(os.listdir(base)):
        kind, path = folder.split("-")[0], os.path.join(base, folder)
        if not os.path.isdir(path):
            continue
        for name in sorted(os.listdir(path)):
            if kind == "values" and name.endswith(".xml"):
                for el in ET.parse(os.path.join(path, name)).getroot():
                    tag = {"string": "string", "plurals": "plurals", "string-array": "array"}.get(el.tag)
                    if tag and el.get("name"):
                        kinds[tag].add(el.get("name"))
            elif kind in ("drawable", "font") and not name.startswith("."):
                kinds[kind].add(os.path.splitext(name)[0])

uses_res = any("generated.resources" in text or re.search(r"\bRes\.", text) for text in sources())
if not any(kinds.values()) and not uses_res:
    sys.exit(0)

pkg = res_package()
types = {"string": "StringResource", "plurals": "PluralStringResource", "array": "StringArrayResource",
         "drawable": "DrawableResource", "font": "FontResource"}
alls = {"string": "allStringResources", "plurals": "allPluralStringResources", "array": "allStringArrayResources",
        "drawable": "allDrawableResources", "font": "allFontResources"}
lines = [
    "@file:OptIn(InternalResourceApi::class, ExperimentalResourceApi::class)",
    "@file:Suppress(\"unused\")",
    "",
    f"package {pkg}",
    "",
    "import org.jetbrains.compose.resources.*",
    "",
    "internal object Res {",
    f"    suspend fun readBytes(path: String): ByteArray = readResourceBytes(\"composeResources/{pkg}/\" + path)",
    f"    fun getUri(path: String): String = getResourceUri(\"composeResources/{pkg}/\" + path)",
    "    object drawable",
    "    object string",
    "    object array",
    "    object plurals",
    "    object font",
    "}",
    "",
]
for kind, names in kinds.items():
    t = types[kind]
    keyed = kind in ("string", "plurals", "array")
    for name in sorted(names):
        args = f'"{kind}:{name}", "{name}", emptySet()' if keyed else f'"{kind}:{name}", emptySet()'
        lines.append(f"internal val Res.{kind}.{identifier(name)}: {t} get() = {t}({args})")
    entries = ", ".join(f'"{n}" to Res.{kind}.{identifier(n)}' for n in sorted(names))
    lines.append(f"internal val Res.{alls[kind]}: Map<String, {t}> get() = mapOf({entries})")
    lines.append("")
os.makedirs(out, exist_ok=True)
with open(os.path.join(out, "Res.kt"), "w", encoding="utf-8") as f:
    f.write("\n".join(lines))
print(f"uicheck: Res in {pkg}: " + ", ".join(f"{len(v)} {k}" for k, v in kinds.items()), file=sys.stderr)
PY

# One multiplatform compilation with a fragment per source set, as Gradle compiles
# the desktop target with Kotlin 2; the generated Res belongs to the first.
total=0
for set in "${sets[@]}"; do
  { find "$module/src/$set/kotlin" -name '*.kt' 2> /dev/null || true; } | sort > "$work/sources-$set"
  [ "$set" != "${sets[0]}" ] || find "$work/generated" -name '*.kt' >> "$work/sources-$set" 2> /dev/null || true
  total=$((total + $(grep -c . "$work/sources-$set" || true)))
done
[ "$total" -gt 0 ] || { echo "no Kotlin sources in ${sets[*]} under $module/src"; exit 1; }
arg() { # one argument per line, quoted for kotlinc's argument file
  printf '"%s"\n' "$(printf '%s' "$1" | sed 's/[\\"]/\\&/g')"
}
args="$work/args"
{
  arg -Xmulti-platform
  arg -Xexpect-actual-classes
  arg "-Xfragments=$(IFS=,; echo "${sets[*]}")"
  for ((i = 1; i < ${#sets[@]}; i++)); do arg "-Xfragment-refines=${sets[i]}:${sets[i - 1]}"; done
  for set in "${sets[@]}"; do
    while IFS= read -r f; do arg "-Xfragment-sources=$set:$f"; done < "$work/sources-$set"
  done
  arg "-Xplugin=$K/lib/compose-compiler-plugin.jar"
  # For @Serializable routes (type-safe navigation); it changes nothing where unused.
  [ ! -f "$K/lib/kotlinx-serialization-compiler-plugin.jar" ] || arg "-Xplugin=$K/lib/kotlinx-serialization-compiler-plugin.jar"
  arg -jvm-target
  arg 17
  [ "${WARNINGS:-0}" = 1 ] || arg -nowarn
  arg -cp
  arg "$module_jars${deps:+:$deps}"
  arg -d
  arg "${OUT:-$work/classes}"
  # shellcheck disable=SC2086
  for a in ${KOTLINC_ARGS:-}; do arg "$a"; done
  for set in "${sets[@]}"; do
    while IFS= read -r f; do arg "$f"; done < "$work/sources-$set"
  done
} > "$args"

echo "uicheck: compiling $total files of ${module#"$PWD"/} (${sets[*]})" >&2
status=0
kotlinc "$work/ui.log" @"$args" || status=$?
grep -v JAVA_TOOL_OPTIONS "$work/ui.log" | sed "s#$PWD/##g" || true
errors=$(grep -c ': error: ' "$work/ui.log" || true)
if [ "$status" -ne 0 ]; then
  echo "uicheck: FAILED ($errors errors)"
  exit 1
fi
echo "uicheck: OK"
