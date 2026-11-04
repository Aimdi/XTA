#!/usr/bin/env python3
"""Whether plugins built before still link against this tree's app.

    python3 -I native/tool/plugin_abi.py --app <dirs or jars, ':'-separated> <plugin jar>...

Every class, method and field a plugin's classes use from the app's own
modules (com.aimdi.xtanative.*, outside the plugin itself) has to be in the
app's classes with the same name and descriptor, declared or inherited; and
every abstract method a plugin's concrete class takes on from the app's
types (an interface member added since, which Kotlin 2.1 compiles abstract)
has to be implemented. What breaks either fails at run time, as
NoSuchMethodError, NoClassDefFoundError or AbstractMethodError.

The app's classes are its compiled modules (:ui, :core, :store, :services,
:networks, :plugin-host); a plugin is its desktop JAR (the Android APK is the
same classes, dexed). Libraries (Kotlin, Compose, OkHttp) are not checked: the
app and its plugins are built with the same versions. Prints what is missing
and exits 1 when anything is; reads class files only, runs nothing of them.
"""
import argparse
import os
import struct
import sys
import zipfile

APP = "com/aimdi/xtanative/"
OBJECT = "java/lang/Object"
OBJECT_METHODS = {
    ("equals", "(Ljava/lang/Object;)Z"), ("hashCode", "()I"), ("toString", "()Ljava/lang/String;"),
    ("getClass", "()Ljava/lang/Class;"), ("clone", "()Ljava/lang/Object;"), ("finalize", "()V"),
    ("notify", "()V"), ("notifyAll", "()V"), ("wait", "()V"), ("wait", "(J)V"), ("wait", "(JI)V"),
}
ABSTRACT, INTERFACE = 0x0400, 0x0200
REF_TAGS = {9: "field", 10: "method", 11: "method"}


class ClassFile:
    """A class file's name, supertypes, members and the references it makes."""

    def __init__(self, data):
        if data[:4] != b"\xca\xfe\xba\xbe":
            raise ValueError("not a class file")
        self.data, self.pos = data, 8
        pool = self._pool()
        self.access, this, parent = self._u2(3)
        self.name = self._class_name(pool, this)
        self.parent = self._class_name(pool, parent) if parent else None
        self.interfaces = [self._class_name(pool, i) for i in self._u2(self._u2(1)[0])]
        self.fields = self._members(pool)
        self.methods = self._members(pool)
        self.refs = self._refs(pool)
        self.classes = {strip(self._utf(pool, entry[1])) for entry in pool if entry and entry[0] == "class"}
        del self.data

    def _u2(self, count):
        values = struct.unpack_from(">" + "H" * count, self.data, self.pos)
        self.pos += 2 * count
        return values

    def _pool(self):
        count = self._u2(1)[0]
        pool, index = [None] * count, 1
        while index < count:
            tag = self.data[self.pos]
            self.pos += 1
            if tag == 1:
                length = self._u2(1)[0]
                pool[index] = ("utf8", self.data[self.pos:self.pos + length].decode("utf-8", "replace"))
                self.pos += length
            elif tag == 7:
                pool[index] = ("class", self._u2(1)[0])
            elif tag in REF_TAGS:
                pool[index] = (REF_TAGS[tag],) + self._u2(2)
            elif tag == 12:
                pool[index] = ("nat",) + self._u2(2)
            else:
                self.pos += {3: 4, 4: 4, 5: 8, 6: 8, 8: 2, 15: 3, 16: 2, 17: 4, 18: 4, 19: 2, 20: 2}[tag]
            index += 2 if tag in (5, 6) else 1
        return pool

    def _members(self, pool):
        members = {}
        for _ in range(self._u2(1)[0]):
            access, name, descriptor, attributes = self._u2(4)
            for _ in range(attributes):
                self.pos += 2
                self.pos += 4 + struct.unpack_from(">I", self.data, self.pos)[0]
            members[(self._utf(pool, name), self._utf(pool, descriptor))] = access
        return members

    def _refs(self, pool):
        refs = set()
        for entry in pool:
            if entry and entry[0] in ("field", "method"):
                _, owner, name_and_type = entry
                _, name, descriptor = pool[name_and_type]
                refs.add((entry[0], self._class_name(pool, owner), self._utf(pool, name), self._utf(pool, descriptor)))
        return refs

    @staticmethod
    def _utf(pool, index):
        return pool[index][1]

    def _class_name(self, pool, index):
        return self._utf(pool, pool[index][1])


def strip(name):
    """The class an array or a class constant names: [Lcom/x/Y; is com/x/Y."""
    name = name.lstrip("[")
    return name[1:-1] if name.startswith("L") and name.endswith(";") else name


def classes_of(path):
    """Every class in a directory of class files or a JAR, by name."""
    found = {}
    if os.path.isdir(path):
        for root, _, files in os.walk(path):
            for file in files:
                if file.endswith(".class"):
                    with open(os.path.join(root, file), "rb") as handle:
                        keep(found, handle.read())
    else:
        with zipfile.ZipFile(path) as archive:
            for entry in archive.namelist():
                if entry.endswith(".class") and not entry.startswith("META-INF/"):
                    keep(found, archive.read(entry))
    return found


def keep(found, data):
    parsed = ClassFile(data)
    found[parsed.name] = parsed


def supertypes(name, world):
    """[name] and every class and interface above it that [world] holds, nearest first."""
    seen, order, queue = set(), [], [name]
    while queue:
        current = queue.pop(0)
        if current in seen or current not in world:
            continue
        seen.add(current)
        order.append(current)
        known = world[current]
        queue += ([known.parent] if known.parent else []) + known.interfaces
    return order


def from_libraries(name, world):
    """Whether a supertype of [name] other than Object lies outside [world]: a library's, which may hold what is looked for."""
    for current in supertypes(name, world):
        known = world[current]
        if any(t not in world and t != OBJECT for t in ([known.parent] if known.parent else []) + known.interfaces):
            return True
    return False


def resolves(kind, owner, name, descriptor, world):
    holders = supertypes(owner, world)
    members = (lambda c: world[c].fields) if kind == "field" else (lambda c: world[c].methods)
    if any((name, descriptor) in members(c) for c in holders):
        return True
    return (kind == "method" and (name, descriptor) in OBJECT_METHODS) or from_libraries(owner, world)


def missing_references(plugin, app):
    """What [plugin]'s classes use of the app that [app] does not have."""
    problems = set()
    for parsed in plugin.values():
        for name in parsed.classes:
            if name.startswith(APP) and name not in plugin and name not in app:
                problems.add(f"class {name}, used by {parsed.name}")
        for kind, owner, name, descriptor in parsed.refs:
            owner = strip(owner)
            if not owner.startswith(APP) or owner in plugin or owner not in app:
                continue
            if not resolves(kind, owner, name, descriptor, app):
                problems.add(f"{kind} {owner}.{name}{descriptor if kind == 'method' else ': ' + descriptor}, used by {parsed.name}")
    return problems


def unimplemented(plugin, app):
    """The abstract methods of the app's types that a concrete class of [plugin] leaves without a body."""
    world = {**app, **plugin}
    problems = set()
    for parsed in plugin.values():
        if parsed.access & (ABSTRACT | INTERFACE):
            continue
        chain = supertypes(parsed.name, world)
        bodies = OBJECT_METHODS | {key for c in chain for key, access in world[c].methods.items() if not access & ABSTRACT}
        for c in chain:
            if c not in app:
                continue
            for key, access in world[c].methods.items():
                if access & ABSTRACT and key not in bodies:
                    problems.add(f"{parsed.name} lacks {c}.{key[0]}{key[1]}")
    return problems


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--app", required=True, help="the app's class directories and JARs, ':'-separated")
    parser.add_argument("plugins", nargs="+", help="plugin JARs built before")
    args = parser.parse_args()
    app = {}
    for path in filter(None, args.app.split(":")):
        app.update(classes_of(path))
    failed = False
    for path in args.plugins:
        plugin = classes_of(path)
        problems = sorted(missing_references(plugin, app) | unimplemented(plugin, app))
        name = os.path.basename(path)
        print(f"{name}: {len(plugin)} classes, " + (f"{len(problems)} problems" if problems else "links"))
        for problem in problems:
            print(f"  {problem}")
        failed = failed or bool(problems)
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
