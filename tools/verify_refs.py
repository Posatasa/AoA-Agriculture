"""Checks remapped classes against the real SRG jar.

tools/remap.py can only be as correct as its mapping tables, and a wrong method mapping does not
always announce itself as a class-file error - it can produce a call that the verifier rejects
much later, at runtime, inside the game. So after remapping, every class/field/method reference
in the output is resolved against the SRG jar's own member tables. Anything that does not exist
there is a mapping mistake, and the build fails instead of shipping it.

Inherited members are handled by walking the superclass chain, which is what the verifier does.
"""
import os
import re
import struct

VALID_TAGS = {1, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 15, 16, 17, 18, 19, 20}
TAG_SIZES = {
    1: None, 3: 4, 4: 4, 5: 8, 6: 8, 7: 2, 8: 2, 9: 4, 10: 4, 11: 4, 12: 4,
    15: 3, 16: 2, 17: 4, 18: 4, 19: 2, 20: 2,
}


class Pool:
    def __init__(self, data):
        self.raw = data
        count = struct.unpack_from(">H", data, 8)[0]
        self.entries = [None] * count
        pos = 10
        i = 1
        while i < count:
            tag = data[pos]
            if tag not in VALID_TAGS:
                raise ValueError("bad tag %d at %d" % (tag, i))
            start = pos
            pos += 1
            size = TAG_SIZES[tag]
            if size is None:
                length = struct.unpack_from(">H", data, pos)[0]
                pos += 2 + length
            else:
                pos += size
            self.entries[i] = (tag, data[start + 1:pos])
            if tag in (5, 6):
                i += 1
            i += 1
        # Where the pool ends, i.e. where access_flags / this_class / super_class begin. Counting
        # is required: `count` counts slots, and long/double entries take two of them, so the end
        # is not at any fixed offset.
        self.end = pos

    def utf8(self, index):
        tag, payload = self.entries[index]
        if tag != 1:
            return None
        length = struct.unpack_from(">H", payload, 0)[0]
        return payload[2:2 + length].decode("utf-8", "surrogateescape")

    def u2(self, payload, offset=0):
        return struct.unpack_from(">H", payload, offset)[0]

    def class_name(self, index):
        return self.utf8(self.u2(self.entries[index][1]))


class ClassInfo:
    """Name, superclass/interfaces, and declared members of one class file."""

    def __init__(self, data):
        pool = Pool(data)
        base = pool.end
        self.name = pool.class_name(struct.unpack_from(">H", data, base + 2)[0])
        super_index = struct.unpack_from(">H", data, base + 4)[0]
        self.super = pool.class_name(super_index) if super_index else None
        pos = base + 6                             # interfaces_count
        interfaces = struct.unpack_from(">H", data, pos)[0]
        pos += 2
        self.interfaces = []
        for _ in range(interfaces):
            self.interfaces.append(pool.class_name(struct.unpack_from(">H", data, pos)[0]))
            pos += 2

        self.fields = {}                           # name -> descriptor
        self.methods = {}                          # name -> set of descriptors
        for table, target in (("fields", self.fields), ("methods", self.methods)):
            count = struct.unpack_from(">H", data, pos)[0]
            pos += 2
            for _ in range(count):
                access, name_index, descriptor_index, attributes = struct.unpack_from(">HHHH", data, pos)
                pos += 8
                name = pool.utf8(name_index)
                descriptor = pool.utf8(descriptor_index)
                if table == "fields":
                    target[name] = descriptor
                else:
                    target.setdefault(name, set()).add(descriptor)
                for _ in range(attributes):
                    _, length = struct.unpack_from(">HI", data, pos)
                    pos += 6 + length
        self.pool = pool


def load_srg(jar_path):
    import zipfile
    classes = {}
    with zipfile.ZipFile(jar_path) as jar:
        for entry in jar.namelist():
            if not entry.endswith(".class"):
                continue
            try:
                info = ClassInfo(jar.read(entry))
            except Exception:
                continue
            classes[info.name] = info
    return classes


def load_dir(directory):
    classes = {}
    for root, _, files in os.walk(directory):
        for filename in files:
            if not filename.endswith(".class"):
                continue
            with open(os.path.join(root, filename), "rb") as fh:
                try:
                    info = ClassInfo(fh.read())
                except Exception:
                    continue
            classes[info.name] = info
    return classes


def references(data):
    """Every class/field/method named by a class file's constant pool."""
    pool = Pool(data)
    classes, fields, methods = set(), set(), set()
    for i in range(1, len(pool.entries)):
        entry = pool.entries[i]
        if entry is None:
            continue
        tag, payload = entry
        if tag == 7:
            classes.add(pool.utf8(pool.u2(payload)))
        elif tag in (9, 10, 11):
            classes.add(pool.class_name(pool.u2(payload)))
            nat = pool.entries[pool.u2(payload, 2)][1]
            name = pool.utf8(pool.u2(nat))
            descriptor = pool.utf8(pool.u2(nat, 2))
            if tag == 9:
                fields.add((pool.class_name(pool.u2(payload)), name, descriptor))
            else:
                methods.add((pool.class_name(pool.u2(payload)), name, descriptor))
    return classes, fields, methods


def _resolve(classes, owner, predicate):
    """Walks the superclass chain, like the verifier does.

    A class that is not in `classes` still gets walked: its superclass name is recorded in its own
    class file, and the member may well be inherited from a class that *is* known.
    """
    seen = set()
    stack = [owner]
    while stack:
        name = stack.pop()
        if name in seen:
            continue
        seen.add(name)
        info = classes.get(name)
        if info is not None:
            if predicate(info):
                return True
            if info.super:
                stack.append(info.super)
            stack.extend(info.interfaces)
        elif not name.startswith(("net/minecraft/", "net/minecraftforge/")):
            # The chain has left the Minecraft and Forge jars for a JDK or library supertype (this is
            # how NonNullList inherits add() from ArrayList). Those members keep their names, so a
            # reference through them is out of scope rather than unresolved.
            return True
    return False


def check(remapped_dir, srg_jar, extra_jars=()):
    """Resolves every reference the remapped classes make.

    `srg_jar` is the authority for Minecraft and for the Forge hooks that patch it. Forge's other
    dependencies (the event bus, ModLauncher, ...) live in their own jars - `extra_jars` - and are
    not remapped at all, so they are only used to recognise that an owner exists. A reference into
    a Minecraft or Forge-patched class that cannot be resolved is a mapping mistake; a reference
    into some other library is out of scope for this check.

    The one thing that *is* checked for those other libraries is that they were not renamed: JDK
    and Mojang library members keep their names, so an SRG-looking name on one of them means a
    shared constant-pool entry was rewritten through it. That is how `java.util.List.isEmpty()`
    once became `java.util.List.func_190926_b()` and crashed the first harvest.
    """
    srg = load_srg(srg_jar)
    for jar in extra_jars:
        if os.path.isfile(jar):
            srg.update(load_srg(jar))
    # The add-on's own classes are not in any of those jars, so load them too: a reference to our
    # own member is as much a mapping result as a reference to a Minecraft one.
    own = load_dir(remapped_dir) if os.path.isdir(remapped_dir) else {}
    srg.update(own)

    def in_scope(owner):
        # Our own classes have to be checked too. A reference whose owner is one of our classes
        # (an inherited member referenced through the subclass) is just as much a remapping result
        # as one against a Minecraft class - and skipping them is how a `NoSuchFieldError: MOISTURE`
        # got past this check once already.
        return owner.startswith(("net/minecraft/", "net/minecraftforge/")) or owner in own

    def is_srg_named(member):
        return bool(re.match(r"^(func|field)_\d", member))

    problems = []
    checked = 0
    for root, _, files in os.walk(remapped_dir):
        for filename in files:
            if not filename.endswith(".class"):
                continue
            path = os.path.join(root, filename)
            with open(path, "rb") as fh:
                data = fh.read()
            _, fields, methods = references(data)
            for owner, name, descriptor in sorted(fields) + sorted(methods):
                if in_scope(owner):
                    continue
                if owner.startswith(("java/", "javax/", "jdk/", "sun/", "com/mojang/", "org/")) \
                        and is_srg_named(name):
                    problems.append("%s: library member %s.%s was renamed"
                                    % (os.path.basename(path), owner, name))
            for owner, name, descriptor in sorted(fields):
                if not in_scope(owner):
                    continue
                checked += 1
                if owner not in srg:
                    problems.append("unknown owner %s for field %s" % (owner, name))
                    continue
                if not _resolve(srg, owner, lambda i, n=name, d=descriptor: i.fields.get(n) == d):
                    problems.append("unresolved field %s.%s:%s" % (owner, name, descriptor))
            for owner, name, descriptor in sorted(methods):
                if not in_scope(owner):
                    continue
                checked += 1
                if owner not in srg:
                    problems.append("unknown owner %s for method %s" % (owner, name))
                    continue
                if not _resolve(srg, owner,
                               lambda i, n=name, d=descriptor: d in i.methods.get(n, ())):
                    problems.append("unresolved method %s.%s%s" % (owner, name, descriptor))
    return checked, problems


if __name__ == "__main__":
    import sys
    if len(sys.argv) < 3:
        print("usage: verify_refs.py <remapped-dir> <srg-jar> [extra-jar ...]")
        raise SystemExit(2)
    n, problems = check(sys.argv[1], sys.argv[2], sys.argv[3:])
    for problem in problems:
        print("  " + problem)
    print("checked %d references, %d problems" % (n, len(problems)))
    raise SystemExit(1 if problems else 0)
