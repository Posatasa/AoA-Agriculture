"""Builds the mod jar using only what is already on this machine.

The normal way to build this mod is `gradlew build`, which needs a JDK 8, a Gradle
distribution and a pile of ForgeGradle dependencies downloaded from the internet. This script is
the offline fallback: it compiles against the ForgeGradle caches (the MCP-named Forge jar and
the SRG mapping file), remaps the result to SRG with tools/remap.py, and packages the jar. The
result is equivalent to what `gradlew build` puts in dist/.

Usage:  python tools/build.py [--aoa-jar PATH]
"""
import argparse
import glob
import io
import json
import os
import re
import shutil
import subprocess
import sys
import time
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
sys.path.insert(0, HERE)

import remap  # noqa: E402  (local module)
import verify_refs  # noqa: E402  (local module)

GRADLE_HOME = os.path.join(os.path.expanduser("~"), ".gradle")
MCP_JAR = os.path.join(
    GRADLE_HOME, "caches", "forge_gradle", "minecraft_user_repo", "net", "minecraftforge",
    "forge", "1.16.5-36.2.34_mapped_official_1.16.5",
    "forge-1.16.5-36.2.34_mapped_official_1.16.5.jar")
MAPPINGS = os.path.join(
    GRADLE_HOME, "caches", "forge_gradle", "minecraft_user_repo", "de", "oceanlabs", "mcp",
    "mcp_config", "1.16.5-20210115.111550", "srg_to_official_1.16.5.tsrg")
SRG_JAR = os.path.join(
    GRADLE_HOME, "caches", "forge_gradle", "minecraft_user_repo", "net", "minecraftforge",
    "forge", "1.16.5-36.2.34", "forge-1.16.5-36.2.34-srg.jar")
JDK8 = r"C:\Program Files\Java\jdk-1.8"

MOD_ID = "aoaagriculture"


def read_properties():
    """gradle.properties is the single source of truth for the version and the mod metadata,
    so the Gradle build and this script can never disagree."""
    props = {}
    with open(os.path.join(ROOT, "gradle.properties"), encoding="utf-8") as fh:
        for line in fh:
            line = line.strip()
            if line and not line.startswith("#") and "=" in line:
                key, value = line.split("=", 1)
                props[key.strip()] = value.strip()
    return props


VERSION = read_properties()["mod_version"]


def find_aoa_jar(explicit):
    if explicit:
        return explicit
    patterns = [
        os.path.join(ROOT, "libs", "*.jar"),
        os.path.join(os.path.expanduser("~"), "curseforge", "minecraft", "Instances", "*",
                     "mods", "AoA3*.jar"),
        os.path.join(os.path.expanduser("~"), "curseforge", "minecraft", "Instances", "*",
                     "mods", "aoa3*.jar"),
        os.path.join(os.path.expanduser("~"), "AppData", "Roaming", ".minecraft", "mods",
                     "AoA3*.jar"),
    ]
    for pattern in patterns:
        for path in sorted(glob.glob(pattern)):
            return path
    return None


def javac_sources(source_dir, out_dir, classpath):
    javac = os.path.join(JDK8, "bin", "javac.exe")
    if not os.path.isfile(javac):
        javac = "javac"
    sources = []
    for root, _, files in os.walk(source_dir):
        sources += [os.path.join(root, f) for f in files if f.endswith(".java")]
    os.makedirs(out_dir, exist_ok=True)
    command = [javac, "-encoding", "UTF-8", "-source", "1.8", "-target", "1.8",
               # The mixin annotation processor would try to generate a refmap of its own and
               # needs a mapping service this build does not provide; the refmap is committed and
               # verified by verify_refmap() instead.
               "-proc:none",
               "-nowarn", "-cp", classpath, "-d", out_dir] + sources
    print("compiling %d sources" % len(sources))
    result = subprocess.run(command, capture_output=True, text=True)
    if result.returncode != 0:
        print(result.stdout)
        print(result.stderr, file=sys.stderr)
        raise SystemExit("javac failed")
    if result.stderr.strip():
        print(result.stderr.strip()[:4000])
    return len(sources)


def expand_mods_toml(resources_dir):
    """Handles the ${version} style placeholders Gradle's processResources would fill in."""
    props = read_properties()
    path = os.path.join(resources_dir, "META-INF", "mods.toml")
    with open(path, encoding="utf-8") as fh:
        text = fh.read()
    for key, value in props.items():
        text = text.replace("${%s}" % key, value)
    text = text.replace("${version}", props.get("mod_version", VERSION))
    with open(path, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(text)
    if "${" in text:
        raise SystemExit("unexpanded placeholder remains in mods.toml")
    return props


def cached_jars():
    """Every jar in the ForgeGradle dependency cache that is not sources or javadoc.

    The MCP-named Forge jar only contains Minecraft and Forge; Forge's own compile
    dependencies (the event bus, FML's loader types, ModLauncher, jsr305, ...) live in the same
    cache as separate artifacts, so they all go on the classpath together.
    """
    root = os.path.join(GRADLE_HOME, "caches", "modules-2", "files-2.1")
    jars = []
    for dirpath, _, filenames in os.walk(root):
        for name in filenames:
            if not name.endswith(".jar"):
                continue
            if "sources" in name or "javadoc" in name:
                continue
            jars.append(os.path.join(dirpath, name))
    return sorted(jars)


def _declaring_ancestor(mappings, supertypes, name, params):
    """The ancestor that declares this member, decided from the class hierarchy alone.

    Deliberately not using Mappings.map_declared_method: a check that asks the rename code what it
    thinks it renamed cannot detect the rename code being wrong.
    """
    hierarchy = mappings.hierarchy
    if hierarchy is None:
        return None
    seen = set()
    stack = [s for s in supertypes if s]
    while stack:
        current = stack.pop(0)
        if current in seen:
            continue
        seen.add(current)
        if (name, params) in hierarchy.members.get(current, ()):
            return current
        if current in hierarchy.supers:
            stack.append(hierarchy.supers[current])
        stack.extend(hierarchy.interfaces.get(current, ()))
    return None


def verify_overrides(staging, mappings):
    """Checks that every overridden method carries its SRG name after remapping.

    This is the failure mode that shipped twice: a method that overrides a vanilla method is
    declared in our class and referenced nowhere, so nothing in the constant pool forces the
    remapper to rename it. Left alone, it silently stops overriding - and the JVM only complains
    later, as `AbstractMethodError`, when something calls the abstract method (opening the creative
    inventory, in the case that motivated this check).

    "Is this an override?" is answered from the class hierarchy, and the expected name comes
    straight from the mapping tables, so disabling the rename makes this check fail.
    """
    mcp_dir = os.path.join(staging, "classes-mcp")
    srg_dir = os.path.join(staging, "classes")
    problems = []
    checked = 0
    for root, _, files in os.walk(mcp_dir):
        for filename in files:
            if not filename.endswith(".class"):
                continue
            path = os.path.join(root, filename)
            rel = os.path.relpath(path, mcp_dir)
            with open(path, "rb") as fh:
                before = remap.ClassFile(fh.read())
            with open(os.path.join(srg_dir, rel), "rb") as fh:
                after = remap.ClassFile(fh.read())
            declared_after = after.declared_members()
            supertypes = [before.super_name] + before.interface_names()
            for kind, _, name_index, descriptor_index in before.declared_slots():
                name = before.utf8(name_index)
                descriptor = before.utf8(descriptor_index)
                if kind == "method":
                    params = remap.Mappings.params(descriptor)
                    ancestor = _declaring_ancestor(mappings, supertypes, name, params)
                    table = mappings.methods
                else:
                    params = None
                    ancestor = _declaring_ancestor(mappings, supertypes, name, None)
                    table = mappings.fields
                if ancestor is None:
                    continue                       # not an override: keeps its own name
                entry = table.get((ancestor, name, params))
                if entry is None:
                    continue                       # a Forge-added member; never remapped
                srg = entry[0] if kind == "method" else entry
                if srg == name:
                    continue
                checked += 1
                if (srg, params) not in declared_after:
                    problems.append("%s declares %s, which overrides %s.%s and must be %s"
                                    % (before.this_name(), name, ancestor, name, srg))
    for problem in problems:
        print("  " + problem)
    if problems:
        raise SystemExit("%d override(s) would not override anything in the built jar; refusing "
                         "to package" % len(problems))
    print("verified %d overridden member(s) carry their SRG names" % checked)


def verify_block_properties(staging):
    """Checks that the farmland blocks are built with vanilla farmland's properties.

    This exists because of a silent failure that shipped: the blocks were created without
    `randomTicks()`, so they never received random ticks, `FarmlandBlock#randomTick` never ran,
    moisture could never change, and every block stayed bone dry forever - no water nearby made any
    difference, and nothing logged an error. A class-file check cannot see that, so the required
    property chain is asserted here instead.

    The method references are read from the *pre-remap* class, where the builders still carry their
    MCP names.
    """
    REQUIRED = ("randomTicks", "strength", "sound", "isSuffocating", "isViewBlocking")
    path = os.path.join(staging, "classes-mcp", "com", "posatasa", "aoaagriculture", "registry",
                        "FarmlandBlocks.class")
    with open(path, "rb") as fh:
        cls = remap.ClassFile(fh.read())

    owner_wanted = "net/minecraft/block/AbstractBlock$Properties"
    found = set()
    for i in range(1, cls.count):
        entry = cls.entries[i]
        if entry is None or entry[0] != remap.CONSTANT_METHODREF:
            continue
        payload = entry[1]
        owner = cls.utf8(cls.u2(cls.entries[cls.u2(payload)][1]))
        if owner != owner_wanted:
            continue
        nat_payload = cls.entries[cls.u2(payload, 2)][1]
        found.add(cls.utf8(cls.u2(nat_payload)))

    missing = [name for name in REQUIRED if name not in found]
    if missing:
        raise SystemExit("farmland is missing vanilla farmland's properties: %s. Without "
                         "randomTicks() the block never ticks, so its moisture can never change "
                         "and it always looks bone dry." % ", ".join(missing))
    print("farmland properties include vanilla's own chain (%s)" % ", ".join(REQUIRED))


def verify_turn_to_soil_paths(staging):
    """Checks that every way farmland can be destroyed is overridden, and that none chains to vanilla.

    Vanilla turns farmland into dirt from four places, each naming `Blocks.DIRT` directly:

    * trampling (`fallOn`)
    * a block being placed on top (`tick`)
    * drying out (`randomTick`)
    * being *placed* where farmland cannot survive (`getStateForPlacement`)

    The first three reach a *static* helper, so no virtual dispatch gets in the way; the fourth
    returns a dirt state straight out of the placement method. Each therefore has to be overridden
    for the block to become its own soil - and, the part that actually bit, none of them may call
    the superclass version: `FarmlandBlock#fallOn` runs the same trample test again and converts
    whatever is there to dirt, undoing the override. Because `Entity#canTrample` rolls a random
    number, that double conversion showed up as an intermittent bug rather than a constant one.
    """
    REQUIRED = ("fallOn", "tick", "randomTick", "getStateForPlacement")
    VANILLA = "net/minecraft/block/FarmlandBlock"
    path = os.path.join(staging, "classes-mcp", "com", "posatasa", "aoaagriculture", "block",
                        "AoAFarmlandBlock.class")
    with open(path, "rb") as fh:
        data = fh.read()
    cls = remap.ClassFile(data)
    declared = {name for name, _ in cls.declared_members()}

    missing = [name for name in REQUIRED if name not in declared]
    if missing:
        raise SystemExit("AoAFarmlandBlock does not override %s; vanilla would turn these blocks "
                         "into minecraft:dirt on that path instead of their AoA soil."
                         % ", ".join(missing))

    _, _, methods = verify_refs.references(data)
    chained = sorted({name for owner, name, _ in methods
                      if owner == VANILLA and name in REQUIRED + ("turnToDirt",)})
    if chained:
        raise SystemExit("AoAFarmlandBlock calls FarmlandBlock#%s, whose body converts the block "
                         "to minecraft:dirt; the override must not chain to it."
                         % ", ".join(chained))
    print("block reverts to its soil on all four vanilla paths (%s), and never chains to vanilla"
          % ", ".join(REQUIRED))


def verify_aoa_bridge(aoa_jar):
    """Checks the AoA members this add-on reaches by name at runtime.

    The Farming experience bridge in `compat/AoASkillExperience` looks AoA's skill API up
    reflectively, because this mod deliberately does not compile against AoA. Nothing else can
    catch a mistake there: the compiler cannot see the call, and the SRG reference check only
    resolves Minecraft and Forge members. A typo or a drift in AoA's API would simply stop awarding
    experience, silently. So the names and descriptors are checked against the AoA jar itself.
    """
    from verify_refs import load_srg

    classes = load_srg(aoa_jar)
    skill = "Lnet/tslat/aoa3/player/skill/AoASkill;"
    wanted = [
        ("net/tslat/aoa3/common/registration/custom/AoASkills", "field", "FARMING",
         "Lnet/minecraftforge/fml/RegistryObject;"),
        ("net/tslat/aoa3/player/skill/AoASkill", "class", None, None),
        ("net/tslat/aoa3/util/PlayerUtil", "method", "getLevel",
         "(Lnet/minecraft/entity/player/PlayerEntity;" + skill + ")I"),
        ("net/tslat/aoa3/util/PlayerUtil", "method", "getTimeBasedXpForLevel", "(IF)F"),
        ("net/tslat/aoa3/util/PlayerUtil", "method", "giveXpToPlayer",
         "(Lnet/minecraft/entity/player/ServerPlayerEntity;" + skill + "FZ)V"),
    ]

    # The Spirit bridge reaches these by name too; a typo there only means the Elecanium bucket
    # silently stops refilling.
    resource = "Lnet/tslat/aoa3/player/resource/AoAResource;"
    instance = "Lnet/tslat/aoa3/player/resource/AoAResource$Instance;"
    wanted += [
        ("net/tslat/aoa3/common/registration/custom/AoAResources", "field", "SPIRIT",
         "Lnet/minecraftforge/fml/RegistryObject;"),
        ("net/tslat/aoa3/common/registration/custom/AoAResources", "field", "ENERGY",
         "Lnet/minecraftforge/fml/RegistryObject;"),
        ("net/tslat/aoa3/player/resource/AoAResource", "class", None, None),
        ("net/tslat/aoa3/player/resource/AoAResource$Instance", "class", None, None),
        ("net/tslat/aoa3/util/PlayerUtil", "method", "getResource",
         "(Lnet/minecraft/entity/player/PlayerEntity;" + resource + ")" + instance),
        ("net/tslat/aoa3/player/resource/AoAResource$Instance", "method", "getMaxValue", "()F"),
        ("net/tslat/aoa3/player/resource/AoAResource$Instance", "method", "setValue", "(F)V"),
    ]

    problems = []
    for owner, kind, name, descriptor in wanted:
        info = classes.get(owner)
        if info is None:
            problems.append("AoA has no class %s" % owner)
            continue
        if kind == "field" and info.fields.get(name) != descriptor:
            problems.append("%s.%s is not %s" % (owner, name, descriptor))
        elif kind == "method" and descriptor not in info.methods.get(name, ()):
            problems.append("%s.%s%s is missing" % (owner, name, descriptor))

    # And the names the bridges spell out have to be AoA's names. The list above checks the AoA jar
    # against what this add-on expects; this checks the add-on's own string literals against it, so a
    # typo in one of them cannot silently disable a bridge.
    known = set()
    for info in classes.values():
        known.update(info.fields)
        known.update(info.methods)
    for relative in ("compat/AoASkillExperience.java", "compat/AoAResourceRefill.java"):
        source_path = os.path.join(ROOT, "src", "main", "java", "com", "posatasa",
                                   "aoaagriculture", *relative.split("/"))
        with open(source_path, encoding="utf-8") as fh:
            source = fh.read()
        for spelled in re.findall(r'(?:getField|getMethod)\("([^"]+)"', source):
            if spelled not in known:
                problems.append("%s reaches AoA member %r, which this AoA version does not have"
                                % (relative, spelled))

    if problems:
        raise SystemExit("an AoA bridge does not match this AoA version:\n  "
                         + "\n  ".join(problems))
    print("AoA skill and Spirit APIs match their bridges (%d members)"
          % len(wanted))

    verify_aoa_names(staging, aoa_jar)


def verify_optional_integrations(staging, resources_dir, remapped_dir):
    """Checks that Farmer's Delight stayed optional.

    The rule is that only AoA is a prerequisite: with Farmer's Delight absent, none of the content
    that exists for it may appear. Three things could quietly break that, so all three are checked:

    * a Farmer's Delight class referenced from our code (would fail to load without it);
    * a dependency entry in mods.toml (would refuse to start);
    * a *vanilla* tag file, such as minecraft:mushroom_grow_block - vanilla reads several of them,
      so adding AoA soils to one makes vanilla content appear even without Farmer's Delight. That
      exact file was shipped once and is the reason this check exists.
    """
    problems = []
    for root, _, files in os.walk(remapped_dir):
        for name in files:
            if not name.endswith(".class"):
                continue
            with open(os.path.join(root, name), "rb") as fh:
                cls = remap.ClassFile(fh.read())
            for i in range(1, cls.count):
                entry = cls.entries[i]
                if entry is None or entry[0] != remap.CONSTANT_CLASS:
                    continue
                ref = cls.utf8(cls.u2(entry[1]))
                if ref.startswith(("vectorwing/", "farmersdelight/")):
                    problems.append("%s links against the Farmer's Delight type %s" % (name, ref))

    with open(os.path.join(resources_dir, "META-INF", "mods.toml"), encoding="utf-8") as fh:
        toml = fh.read()
    if "farmersdelight" in toml.lower():
        problems.append("mods.toml mentions Farmer's Delight; it must not be a dependency")

    vanilla_tag = os.path.join(resources_dir, "data", "minecraft", "tags", "blocks",
                               "mushroom_grow_block.json")
    if os.path.isfile(vanilla_tag):
        problems.append("data/minecraft/tags/blocks/mushroom_grow_block.json would enable giant "
                        "mushrooms even without Farmer's Delight")

    if problems:
        raise SystemExit("Farmer's Delight must stay optional:\n  " + "\n  ".join(problems))
    print("Farmer's Delight stays optional (no class link, no dependency, no vanilla tag)")


def verify_colony_soils(resources_dir):
    """Checks that the soils which form colonies are in the tag that lets a colony grow.

    Farmer's Delight converts a mushroom into a colony near its rich soil, and ages a colony only on
    blocks in `farmersdelight:mushroom_colony_growable_on` (it also needs
    `minecraft:mushroom_grow_block` to survive at any light level). Two lists therefore have to
    agree: `FarmlandBlocks.COLONY_SOILS`, which decides where conversion happens, and those tags.
    They were out of step once, and the symptom was colonies frozen at their first stage - which
    reads as a growth bug instead of a missing tag.
    """
    source_path = os.path.join(ROOT, "src", "main", "java", "com", "posatasa", "aoaagriculture",
                               "registry", "FarmlandBlocks.java")
    with open(source_path, encoding="utf-8") as fh:
        source = fh.read()
    match = re.search(r"COLONY_SOILS\s*=\s*\{([^}]*)\}", source)
    if not match:
        raise SystemExit("FarmlandBlocks has no COLONY_SOILS table")
    soils = set(re.findall(r'"(aoa3:[a-z_]+)"', match.group(1)))
    if not soils:
        raise SystemExit("FarmlandBlocks.COLONY_SOILS is empty")

    for path in ("data/farmersdelight/tags/blocks/mushroom_colony_growable_on.json",):
        full = os.path.join(resources_dir, path.replace("/", os.sep))
        if not os.path.isfile(full):
            raise SystemExit("missing %s; colonies there would not survive or not grow" % path)
        with open(full, encoding="utf-8") as fh:
            values = set(json.load(fh).get("values", []))
        missing = sorted(soils - values)
        if missing:
            raise SystemExit("%s does not list %s, so colonies on those soils never grow"
                             % (path, ", ".join(missing)))
    print("colony soils match the colony growth tag (%s)" % ", ".join(sorted(soils)))


def verify_buckets(staging, remapped_dir):
    """Checks that a modded bucket can never answer with a vanilla one.

    Vanilla's bucket logic names the iron buckets directly: emptying hands back
    `minecraft:bucket`, and filling produces `fluid.getBucket()`, which is the vanilla water or lava
    bucket. A bucket that merely inherits that behaviour turns into iron on first use - the item
    version of the farmland-becomes-dirt bug. Ours routes both answers through its family instead,
    so no code here should mention a vanilla bucket at all, and the two overrides that do the
    routing must be present.
    """
    VANILLA_BUCKETS = {
        "field_151133_ar": "minecraft:bucket",
        "field_151131_as": "minecraft:water_bucket",
        "field_151129_at": "minecraft:lava_bucket",
    }
    problems = []
    for root, _, files in os.walk(remapped_dir):
        for name in files:
            if not name.endswith(".class"):
                continue
            with open(os.path.join(root, name), "rb") as fh:
                data = fh.read()
            cls = remap.ClassFile(data)
            for i in range(1, cls.count):
                entry = cls.entries[i]
                if entry is None or entry[0] != remap.CONSTANT_FIELDREF:
                    continue
                payload = entry[1]
                owner = cls.utf8(cls.u2(cls.entries[cls.u2(payload)][1]))
                if owner != "net/minecraft/item/Items":
                    continue
                member = cls.utf8(cls.u2(cls.entries[cls.u2(payload, 2)][1]))
                if member in VANILLA_BUCKETS:
                    problems.append("%s names %s" % (name, VANILLA_BUCKETS[member]))

    # Picking a fluid up does not always hand back the fluid a bucket holds: FlowingFluidBlock
    # returns the fluid it was built from, and AoA builds its blocks from the FLOWING fluid. So the
    # mapping has to compare sources; without that, filling with AoA's candied water silently falls
    # back to AoA's own bucket.
    family_class = os.path.join(staging, "classes-mcp", "com", "posatasa", "aoaagriculture", "item",
                                "BucketFamily.class")
    with open(family_class, "rb") as fh:
        family = remap.ClassFile(fh.read())
    sources = []
    for i in range(1, family.count):
        entry = family.entries[i]
        if entry is None or entry[0] != remap.CONSTANT_METHODREF:
            continue
        payload = entry[1]
        owner = family.utf8(family.u2(family.entries[family.u2(payload)][1]))
        if owner == "net/minecraft/fluid/FlowingFluid":
            sources.append(family.utf8(family.u2(family.entries[family.u2(payload, 2)][1])))
    if "getSource" not in sources:
        problems.append("BucketFamily does not reduce flowing fluids to their source, so a fluid "
                        "block that reports a flowing fluid will not match any of our buckets")

    item_package = os.path.join(staging, "classes-mcp", "com", "posatasa", "aoaagriculture",
                                "item")
    required_overrides = {
        "AoABucketItem.class": ("getEmptySuccessItem", "use"),
        # The endless bucket pours without using itself up, and empties on a long hold. That needs
        # the use action started explicitly (use), the early-release pour (releaseUsing), the
        # completed-hold clear (finishUsingItem - a completed hold does NOT go through releaseUsing),
        # the hold length, and an animation to show for it.
        # The endless bucket: the hold, the pour, the clear, and the sound fix. playEmptySound is the
        # one that went missing - vanilla passes the player as the sound's exception, which is right
        # when both sides pour and silent when only the server does.
        "InfiniteBucketItem.class": ("use", "releaseUsing", "finishUsingItem", "getUseDuration",
                                     "getUseAnimation", "appendHoverText", "playEmptySound"),
        # The lunar water bucket does nothing different mechanically; the tooltip is how the player
        # is meant to learn what it does, so losing it would make the item look inert.
        "LunarWaterBucketItem.class": ("appendHoverText",),
        # The Emberstone tank cannot be a plain bucket: it must refuse everything but lava on both
        # paths, and it must override use to count what goes in and out.
        "LavaOnlyBucketItem.class": ("accepts", "filledFor", "appendHoverText"),
        # A tank has to override use (to count what goes in and out) and know how deep it is.
        "LavaTankBucketItem.class": ("use", "appendHoverText", "capacity", "storedLava",
                                     "withLava"),
        # AoA's candied water is registered after items, so this bucket resolves it lazily and has
        # to do the pour itself: vanilla's placement reads the eagerly-set fluid field.
        "CandiedWaterBucketItem.class": ("use", "emptyBucket", "getFluid"),
        # The resource buckets spend their fluid on the player instead of pouring it, and say so
        # in their tooltips; losing use() turns them back into plain buckets.
        "ResourceRefillBucketItem.class": ("use", "appendHoverText"),
        # A drink has to say how long it takes, what animation to play and what it sounds like,
        # otherwise it is eaten like bread; and it has to hand the glass bottle back when drunk.
        "CandiedWaterBottleItem.class": ("finishUsingItem", "getUseDuration", "getUseAnimation",
                                         "getDrinkingSound", "getEatingSound"),
    }
    # The bottle's properties have to do three things: make it food, make it stackable like a bottle,
    # and return a glass bottle when it is used up in a recipe. Missing the last one turns "sugar from
    # sugar water" into a recipe that quietly eats the bottle.
    bottles_class = os.path.join(staging, "classes-mcp", "com", "posatasa", "aoaagriculture",
                                 "registry", "Bottles.class")
    with open(bottles_class, "rb") as fh:
        bottles = remap.ClassFile(fh.read())
    references = set()
    for i in range(1, bottles.count):
        entry = bottles.entries[i]
        if entry is None or entry[0] not in (remap.CONSTANT_METHODREF,
                                             remap.CONSTANT_INTERFACE_METHODREF,
                                             remap.CONSTANT_FIELDREF):
            continue
        payload = entry[1]
        owner = bottles.utf8(bottles.u2(bottles.entries[bottles.u2(payload)][1]))
        member = bottles.utf8(bottles.u2(bottles.entries[bottles.u2(payload, 2)][1]))
        references.add((owner, member))
    for owner, member, why in (
            ("net/minecraft/item/Item$Properties", "craftRemainder",
             "crafting it into sugar would eat the glass bottle"),
            ("net/minecraft/item/Food$Builder", "nutrition",
             "it would not restore any hunger"),
            ("net/minecraft/block/FlowingFluidBlock", "LEVEL",
             "it could not tell a full candied water block from a flowing edge")):
        if (owner, member) not in references:
            problems.append("Bottles never calls %s.%s, so %s" % (owner, member, why))
    # The fluid path is RightClickItem: the client's block hit excludes fluids, so a click on a fluid
    # never reaches RightClickBlock. Dropping that listener would silently put the water bottle back.
    # Both event types have to be present, since each covers one of the two click paths.
    event_types = set()
    for i in range(1, bottles.count):
        entry = bottles.entries[i]
        if entry is not None and entry[0] == remap.CONSTANT_UTF8:
            event_types.add(bottles.utf8(i))
    for event, why in (
            ("PlayerInteractEvent$RightClickItem",
             "a click on a fluid takes the item path, so RightClickBlock never fires for one"),
            ("PlayerInteractEvent$RightClickBlock",
             "a click that lands on a block takes the block path")):
        if not any(event in name for name in event_types):
            problems.append("Bottles does not listen to %s: %s" % (event.split("$")[-1], why))

    # And each of them has to be handed to the bus. Checked against the source, because a forgotten
    # registration line compiles perfectly and leaves a handler that never runs.
    with open(os.path.join(ROOT, "src", "main", "java", "com", "posatasa", "aoaagriculture",
                           "registry", "Bottles.java"), encoding="utf-8") as fh:
        bottles_source = fh.read()
    # The resource buckets must not consume themselves in creative - the requested behaviour, and
    # easy to lose when the spending path is edited.
    with open(os.path.join(ROOT, "src", "main", "java", "com", "posatasa", "aoaagriculture",
                           "item", "ResourceRefillBucketItem.java"), encoding="utf-8") as fh:
        refill_source = fh.read()
    if "instabuild" not in refill_source:
        problems.append("ResourceRefillBucketItem does not check instabuild, so spending the fluid "
                        "would empty the bucket in creative too")

    for handler in ("onUseItem", "onUseBlock"):
        if "addListener(Bottles::%s)" % handler not in bottles_source:
            problems.append("Bottles defines %s but never registers it" % handler)

    # The tab's order is written out by hand, so it is worth checking that both lists are still wired
    # up: losing either call leaves a tab that is simply missing items, with no error anywhere.
    with open(os.path.join(ROOT, "src", "main", "java", "com", "posatasa", "aoaagriculture",
                           "registry", "FarmlandBlocks.java"), encoding="utf-8") as fh:
        farmland_source = fh.read()
    for call in ("Buckets.addToTab(items)", "Bottles.addToTab(items)"):
        if call not in farmland_source:
            problems.append("the creative tab never calls %s, so those items would be missing"
                            % call)
    if "fillItemList" not in farmland_source:
        problems.append("the creative tab does not override fillItemList, so the order is the "
                        "registry's again")

    if ("net/minecraft/block/IBucketPickupHandler", "takeLiquid") in references:
        problems.append("Bottles takes the fluid out of the world; candied water is an endless pool, "
                        "so bottling it must leave the block alone")
    if ("net/minecraft/fluid/FluidState", "isSource") in references:
        problems.append("Bottles gates on FluidState.isSource, which is false for AoA's fluid blocks "
                        "because they are built from the flowing fluid - the fill would never happen")

    for filename, wanted in required_overrides.items():
        path = os.path.join(item_package, filename)
        with open(path, "rb") as fh:
            declared = {name for name, _ in remap.ClassFile(fh.read()).declared_members()}
        for required in wanted:
            if required not in declared:
                problems.append("%s does not override %s, so a vanilla answer or the plain bucket "
                                "behaviour would stand" % (filename, required))

    if problems:
        raise SystemExit("buckets must never turn into vanilla ones:\n  " + "\n  ".join(problems))
    print("buckets never name a vanilla bucket, and override both conversion points")


def verify_aoa_names(staging, aoa_jar):
    """Checks that every AoA registry name and sound this add-on uses exists in the AoA jar.

    All of AoA's cooperation is by name, so a typo or a renamed entry would neither fail to compile
    nor crash: the feature it belongs to would simply never trigger. This pulls the `aoa3:...`
    strings back out of the compiled classes and looks each one up - block names in AoA's blockstate
    files (one per registered block), item names in its item models, and sound ids, which carry a
    dot and live in sounds.json.
    """
    import zipfile

    with zipfile.ZipFile(aoa_jar) as jar:
        entries = jar.namelist()
        known = {
            "block": {e.rsplit("/", 1)[-1][:-len(".json")] for e in entries
                      if e.startswith("assets/aoa3/blockstates/") and e.endswith(".json")},
            "item": {e.rsplit("/", 1)[-1][:-len(".json")] for e in entries
                     if e.startswith("assets/aoa3/models/item/") and e.endswith(".json")},
            "sound": set(json.loads(jar.read("assets/aoa3/sounds.json").decode("utf-8"))),
        }

    used = {}
    for root, _, files in os.walk(staging):
        for name in files:
            if not name.endswith(".class"):
                continue
            with open(os.path.join(root, name), "rb") as fh:
                data = fh.read()
            cls = remap.ClassFile(data)
            for i in range(1, cls.count):
                entry = cls.entries[i]
                if entry is None or entry[0] != remap.CONSTANT_UTF8:
                    continue
                text = cls.utf8(i)
                if text.startswith("aoa3:"):
                    used.setdefault(text, set()).add(name)

    missing = []
    for name in used:
        path_part = name.split(":", 1)[1]
        if "." in path_part:
            found = path_part in known["sound"]
        else:
            found = path_part in known["block"] or path_part in known["item"]
        if not found:
            missing.append("%s (%s)" % (name, ", ".join(sorted(used[name]))))

    if missing:
        raise SystemExit("these AoA names do not exist in the installed AoA jar:\n  "
                         + "\n  ".join(sorted(missing)))
    print("every AoA block, item and sound name the mod uses exists (%d names)" % len(used))


def verify_refmap(mappings, tsrg_path, srg_jar):
    """Checks that every name in the committed mixin refmap is the right SRG name.

    A mixin names its target method with the *source* name and relies on the refmap to find it in
    production. If an entry is wrong the failure happens while the class is being transformed, which
    means the game dies at startup - the least forgiving place to be wrong. So each entry is checked
    twice: against the TSRG tables, and against the method that actually exists in the SRG jar.
    """
    import verify_refs
    config_path = os.path.join(ROOT, "src", "main", "resources", "aoaagriculture.mixins.json")
    refmap_path = os.path.join(ROOT, "src", "main", "resources", "aoaagriculture.refmap.json")
    with open(config_path, encoding="utf-8") as fh:
        config = json.load(fh)
    with open(refmap_path, encoding="utf-8") as fh:
        refmap = json.load(fh)

    package = config["package"].replace(".", "/")
    refmap_name = config["refmap"]
    if os.path.basename(refmap_path) != refmap_name:
        raise SystemExit("mixin config references %s but the refmap is %s"
                         % (refmap_name, os.path.basename(refmap_path)))
    for mixin in config.get("mixins", []) + config.get("client", []):
        expected = os.path.join(ROOT, "build", "script", "classes", package, mixin + ".class")
        if not os.path.isfile(expected):
            raise SystemExit("mixin %s is listed in the config but was not compiled" % mixin)

    # The methods these mixins inject into, per source name. `mayPlaceOn` lives on BushBlock too -
    # CropsBlock and the stem blocks inherit or override the same method - which is why the owner
    # list has four entries for one name.
    TARGETS = {
        "mayPlaceOn": (
            ["net/minecraft/block/CropsBlock", "net/minecraft/block/StemBlock",
             "net/minecraft/block/AttachedStemBlock", "net/minecraft/block/BushBlock"],
            "(Lnet/minecraft/block/BlockState;Lnet/minecraft/world/IBlockReader;"
            "Lnet/minecraft/util/math/BlockPos;)Z"),
        "destroy": (
            ["net/minecraft/block/Block"],
            "(Lnet/minecraft/world/IWorld;Lnet/minecraft/util/math/BlockPos;"
            "Lnet/minecraft/block/BlockState;)V"),
        "getGrowthSpeed": (
            ["net/minecraft/block/CropsBlock"],
            "(Lnet/minecraft/block/Block;Lnet/minecraft/world/IBlockReader;"
            "Lnet/minecraft/util/math/BlockPos;)F"),
        "use": (
            ["net/minecraft/block/AbstractBlock"],
            "(Lnet/minecraft/block/BlockState;Lnet/minecraft/world/World;"
            "Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/entity/player/PlayerEntity;"
            "Lnet/minecraft/util/Hand;Lnet/minecraft/util/math/BlockRayTraceResult;)"
            "Lnet/minecraft/util/ActionResultType;"),
        "canSurvive": (
            ["net/minecraft/block/BushBlock"],
            "(Lnet/minecraft/block/BlockState;Lnet/minecraft/world/IWorldReader;"
            "Lnet/minecraft/util/math/BlockPos;)Z"),
        "randomTick": (
            ["net/minecraft/block/MushroomBlock"],
            "(Lnet/minecraft/block/BlockState;Lnet/minecraft/world/server/ServerWorld;"
            "Lnet/minecraft/util/math/BlockPos;Ljava/util/Random;)V"),
    }

    srg = verify_refs.load_srg(srg_jar)
    problems = []
    for mixin_class, members in refmap.get("mappings", {}).items():
        for source_name, srg_name in members.items():
            if source_name not in TARGETS:
                problems.append("refmap names %s, which this build does not know how to check"
                                % source_name)
                continue
            owners, descriptor = TARGETS[source_name]
            params = remap.Mappings.params(descriptor)
            for owner in owners:
                entry = mappings.methods.get((owner, source_name, params))
                if entry is None:
                    problems.append("TSRG has no %s.%s%s" % (owner, source_name, descriptor))
                elif entry[0] != srg_name:
                    problems.append("refmap says %s for %s.%s but TSRG says %s"
                                    % (srg_name, owner, source_name, entry[0]))
                info = srg.get(owner)
                if info is None:
                    problems.append("SRG jar has no class %s" % owner)
                elif descriptor not in info.methods.get(srg_name, ()):
                    problems.append("SRG jar has no %s.%s%s" % (owner, srg_name, descriptor))
    for env, table in refmap.get("data", {}).items():
        for mixin_class, members in table.items():
            for source_name, mapped in members.items():
                if env == "searge" and mapped != refmap["mappings"].get(mixin_class, {}).get(source_name):
                    problems.append("refmap data.searge disagrees with mappings for %s" % source_name)
    for problem in problems:
        print("  " + problem)
    if problems:
        raise SystemExit("mixin refmap does not match the game's actual names; refusing to package")
    print("mixin refmap verified against the TSRG tables and the SRG jar")


def _recipe_references(node):
    """Every item id a recipe mentions, from `"item"` keys and the result."""
    found = []

    def walk(value):
        if isinstance(value, dict):
            for key, inner in value.items():
                if key in ("item", "tag") and isinstance(inner, str):
                    found.append(inner)
                else:
                    walk(inner)
        elif isinstance(value, list):
            for inner in value:
                walk(inner)

    walk(node.get("key", {}))
    if isinstance(node.get("result"), dict):
        walk(node["result"])
    return found


def verify_resources(resources_dir, aoa_jar):
    """Checks that every blockstate, model, texture and translation key actually resolves.

    The resources are generated, and there are a lot of them (one model and one texture per soil
    per moisture level), so a typo in the generator would otherwise only show up as a missing model
    in game. References into AoA's namespace are checked against the AoA jar when one was found.
    """
    assets_root = os.path.join(resources_dir, "assets", "aoaagriculture")
    ours = set()
    for root, _, files in os.walk(assets_root):
        for filename in files:
            ours.add(os.path.relpath(os.path.join(root, filename), assets_root).replace(os.sep, "/"))

    aoa_assets = set()
    if aoa_jar and os.path.isfile(aoa_jar):
        with zipfile.ZipFile(aoa_jar) as jar:
            for entry in jar.namelist():
                if entry.startswith("assets/aoa3/"):
                    aoa_assets.add(entry[len("assets/aoa3/"):])

    problems = []

    def resolves(reference, kind):
        """Resolves "namespace:path" to a file under assets/<ns>/<kind>/ — `kind` is the asset
        root ("models" or "textures"), and the reference path already carries its subfolder."""
        ns, _, path = reference.partition(":")
        if ns == "aoaagriculture":
            wanted = "%s/%s%s" % (kind, path, _extension(kind))
            return wanted in ours, wanted
        if ns == "aoa3":
            wanted = "%s/%s%s" % (kind, path, _extension(kind))
            if not aoa_assets:
                return True, wanted          # AoA jar unavailable: cannot check
            return wanted in aoa_assets, wanted
        return True, path                    # vanilla or another mod: out of scope

    def _extension(kind):
        return ".png" if kind == "textures" else ".json"

    # blockstates -> models
    for entry in sorted(ours):
        if not entry.startswith("blockstates/"):
            continue
        with open(os.path.join(assets_root, entry), encoding="utf-8") as fh:
            data = json.load(fh)
        for key, variant in data.get("variants", {}).items():
            model = variant["model"] if isinstance(variant, dict) else variant[0]["model"]
            ok, wanted = resolves(model, "models")
            if not ok:
                problems.append("%s: %s -> missing %s" % (entry, key, wanted))

    # block and item models -> parents and textures
    for entry in sorted(ours):
        if not entry.startswith("models/"):
            continue
        with open(os.path.join(assets_root, entry), encoding="utf-8") as fh:
            data = json.load(fh)
        parent = data.get("parent")
        if parent and parent.partition(":")[0] == "aoaagriculture":
            ok, wanted = resolves(parent, "models")
            if not ok:
                problems.append("%s: parent -> missing %s" % (entry, wanted))
        for slot, texture in data.get("textures", {}).items():
            ok, wanted = resolves(texture, "textures")
            if not ok:
                problems.append("%s: texture '%s' -> missing %s" % (entry, slot, wanted))

    # every block needs a name in every language file
    lang_dir = os.path.join(assets_root, "lang")
    languages = {}
    for entry in sorted(ours):
        if entry.startswith("lang/") and entry.endswith(".json"):
            with open(os.path.join(assets_root, entry), encoding="utf-8") as fh:
                languages[entry] = json.load(fh)
    if not languages:
        problems.append("no language files")
    blocks = [e[len("blockstates/"):-len(".json")] for e in sorted(ours)
              if e.startswith("blockstates/")]
    for language, table in languages.items():
        if "itemGroup.aoaagriculture" not in table:
            problems.append("%s: missing itemGroup.aoaagriculture" % language)
        for block in blocks:
            if "block.aoaagriculture." + block not in table:
                problems.append("%s: missing name for %s" % (language, block))

    # The fertilised blocks carry AoA's extra `well_fertilised` state, so their blockstates must
    # cover both values at every moisture level. A missing variant shows up in game as an
    # invisible block, with no error anywhere.
    for entry in sorted(ours):
        if not (entry.startswith("blockstates/") and entry.endswith("_fertilised_farmland.json")):
            continue
        with open(os.path.join(assets_root, entry), encoding="utf-8") as fh:
            variants = json.load(fh).get("variants", {})
        for level in range(8):
            for well in ("false", "true"):
                key = "moisture=%d,well_fertilised=%s" % (level, well)
                if key not in variants:
                    problems.append("%s: missing variant %s" % (entry, key))

    # Item models that are not block items need a name of their own; the block items are named by
    # their block, which is checked above.
    for entry in sorted(ours):
        if not (entry.startswith("models/item/") and entry.endswith(".json")):
            continue
        name = entry[len("models/item/"):-len(".json")]
        with open(os.path.join(assets_root, entry), encoding="utf-8") as fh:
            parent = json.load(fh).get("parent", "")
        if parent.startswith("aoaagriculture:block/"):
            continue
        for language, table in languages.items():
            if "item.aoaagriculture." + name not in table:
                problems.append("%s: missing item name in %s" % (name, language))

    # Items that are neither blocks nor buckets still need a model, a texture and a name. A missing
    # model shows up in game as an invisible item, with nothing in any log.
    for name in ("candied_water_bottle",):
        for entry in ("models/item/%s.json" % name, "textures/item/%s.png" % name):
            if entry not in ours:
                problems.append("missing %s" % entry)
        for language, table in languages.items():
            if "item.aoaagriculture." + name not in table:
                problems.append("%s: missing name for %s" % (language, name))

    # Both buckets that do more than a bucket must explain themselves, in every language.
    for name in ("water_shyre_bucket", "water_lunar_bucket", "emberstone_bucket",
                 "lava_emberstone_bucket", "blazium_bucket", "lava_blazium_bucket",
                 "dark_bucket", "lava_dark_bucket"):
        for language, table in languages.items():
            if "item.aoaagriculture.%s.tooltip" % name not in table:
                problems.append("%s: missing tooltip for %s" % (language, name))

    # Recipes must only ask for things that exist: AoA's items from the AoA jar, ours from the item
    # models generated here.
    recipes_dir = os.path.join(resources_dir, "data", "aoaagriculture", "recipes")
    if os.path.isdir(recipes_dir):
        for filename in sorted(os.listdir(recipes_dir)):
            if not filename.endswith(".json"):
                continue
            with open(os.path.join(recipes_dir, filename), encoding="utf-8") as fh:
                recipe = json.load(fh)
            for reference in _recipe_references(recipe):
                namespace, _, item_path = reference.partition(":")
                if namespace == "aoa3":
                    if not aoa_assets:
                        continue                       # AoA jar unavailable: cannot check
                    known = ("models/item/%s.json" % item_path in aoa_assets
                             or "blockstates/%s.json" % item_path in aoa_assets)
                    if not known:
                        problems.append("%s: unknown AoA item %s" % (filename, reference))
                elif namespace == "aoaagriculture":
                    if "models/item/%s.json" % item_path not in ours:
                        problems.append("%s: unknown item %s" % (filename, reference))

    # The harvest modifier only runs if its data files line up with the registered serializer.
    data_root = os.path.join(resources_dir, "data")
    modifier = os.path.join(data_root, "aoaagriculture", "loot_modifiers",
                            "fertilised_farmland.json")
    if not os.path.isfile(modifier):
        problems.append("missing data/aoaagriculture/loot_modifiers/fertilised_farmland.json")
    else:
        with open(modifier, encoding="utf-8") as fh:
            if json.load(fh).get("type") != "aoaagriculture:fertilised_farmland":
                problems.append("the harvest modifier's type must be its serializer id")
    global_modifiers = os.path.join(data_root, "forge", "loot_modifiers",
                                    "global_loot_modifiers.json")
    if not os.path.isfile(global_modifiers):
        problems.append("missing data/forge/loot_modifiers/global_loot_modifiers.json")
    else:
        with open(global_modifiers, encoding="utf-8") as fh:
            entries = json.load(fh).get("entries", [])
        if "aoaagriculture:fertilised_farmland" not in entries:
            problems.append("the harvest modifier is not registered in the global list")
        if json.load(open(global_modifiers, encoding="utf-8")).get("replace"):
            problems.append("the global loot modifier list must merge, not replace")

    for problem in problems:
        print("  " + problem)
    if problems:
        raise SystemExit("%d broken resource reference(s); refusing to package" % len(problems))
    print("verified %d assets (%d blocks, %d languages)"
          % (len(ours), len(blocks), len(languages)))


def build(staging, out_jar):
    classpath_parts = [MCP_JAR] + cached_jars()
    aoa = find_aoa_jar(args.aoa_jar)
    if aoa:
        classpath_parts.append(aoa)
        print("using AoA jar for compile-time checks:", aoa)
    else:
        print("no AoA jar found; compiling without it (not needed, only used for verification)")
    classpath = os.pathsep.join(classpath_parts)
    print("classpath: %d jars" % len(classpath_parts))

    classes = os.path.join(staging, "classes-mcp")
    remapped = os.path.join(staging, "classes")
    javac_sources(os.path.join(ROOT, "src", "main", "java"), classes, classpath)

    mappings = remap.Mappings(MAPPINGS, MCP_JAR)
    if mappings.classes:
        # The remapper rewrites the constant pool only. Annotation element values and generic
        # Signature attributes also carry class names, as UTF8 descriptors rather than pool
        # entries, so they are not covered - which is harmless exactly as long as classes are
        # spelled the same in both naming schemes, as they are for 1.16.5.
        raise SystemExit("MCP and SRG disagree on %d class name(s); the remapper would leave "
                         "annotation and signature descriptors stale" % len(mappings.classes))
    print("remapping MCP -> SRG (%d methods, %d class hierarchies)"
          % (len(mappings.methods),
             len(mappings.hierarchy.supers) if mappings.hierarchy else 0))
    remap.remap_tree(classes, remapped, mappings, verbose=True)

    # A wrong mapping often shows up as a call the verifier rejects, not as a broken class file,
    # so every reference the remapped classes make is checked against the SRG jar's own members.
    import verify_refs
    checked, problems = verify_refs.check(remapped, SRG_JAR, cached_jars())
    for problem in problems:
        print("  " + problem)
    if problems:
        raise SystemExit("remapped classes reference %d member(s) that do not exist in the SRG "
                         "jar; refusing to package" % len(problems))
    print("verified %d member references against the SRG jar" % checked)

    # The mod must not link against any AoA type - that is the whole licensing story. This looks
    # at CONSTANT_Class entries, i.e. at types the JVM would actually resolve and load, rather
    # than at raw bytes: the mixins name AoA's classes in @Mixin(targets = "...") *strings* on
    # purpose (that is how they cooperate with AoA without depending on it at compile time), and
    # those strings are not references.
    for root, _, files in os.walk(remapped):
        for f in files:
            if not f.endswith(".class"):
                continue
            path = os.path.join(root, f)
            with open(path, "rb") as fh:
                cls = remap.ClassFile(fh.read())
            for i in range(1, cls.count):
                entry = cls.entries[i]
                if entry is None or entry[0] != remap.CONSTANT_CLASS:
                    continue
                name = cls.utf8(cls.u2(entry[1]))
                if name.startswith(("net/tslat/", "net/minecraft/aoa")):
                    raise SystemExit("%s links against the AoA type %s" % (path, name))
    print("no AoA type is linked by any built class")

    verify_overrides(staging, mappings)
    verify_block_properties(staging)
    verify_turn_to_soil_paths(staging)
    if aoa:
        verify_aoa_bridge(aoa)
    verify_buckets(staging, remapped)

    # A mixin that names the wrong SRG method fails at class-transform time, i.e. it takes the
    # game down at startup. The refmap is the only place those names live, so check every entry
    # against the TSRG tables *and* against the SRG jar before packaging.
    verify_refmap(mappings, MAPPINGS, SRG_JAR)

    resources = os.path.join(staging, "resources")
    shutil.copytree(os.path.join(ROOT, "src", "main", "resources"), resources)
    props = expand_mods_toml(resources)
    verify_resources(resources, aoa)
    verify_colony_soils(resources)
    verify_optional_integrations(staging, resources, remapped)

    manifest = (
        "Manifest-Version: 1.0\n"
        "Specification-Title: %s\n"
        "Specification-Vendor: %s\n"
        "Specification-Version: 1\n"
        "Implementation-Title: %s\n"
        "Implementation-Version: %s\n"
        "Implementation-Vendor: %s\n"
        "MixinConfigs: aoaagriculture.mixins.json\n"
        "\n" % (MOD_ID, props["mod_authors"], MOD_ID, VERSION, props["mod_authors"])
    ).replace("\n", "\r\n")

    entries = []
    for root, _, files in os.walk(remapped):
        for f in files:
            full = os.path.join(root, f)
            entries.append((os.path.relpath(full, remapped).replace(os.sep, "/"), full))
    for root, _, files in os.walk(resources):
        for f in files:
            full = os.path.join(root, f)
            entries.append((os.path.relpath(full, resources).replace(os.sep, "/"), full))
    entries.sort()

    os.makedirs(os.path.dirname(out_jar), exist_ok=True)
    # Fixed timestamps (the ZIP epoch, 1980-01-01) and a fixed entry order, so repeated builds
    # from unchanged sources produce byte-identical jars.
    stamp = (1980, 1, 1, 0, 0, 0)
    with zipfile.ZipFile(out_jar, "w", zipfile.ZIP_DEFLATED) as jar:
        for name, path in entries:
            info = zipfile.ZipInfo(name, stamp)
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o644 << 16
            with open(path, "rb") as fh:
                jar.writestr(info, fh.read())
        info = zipfile.ZipInfo("META-INF/MANIFEST.MF", stamp)
        info.compress_type = zipfile.ZIP_DEFLATED
        info.external_attr = 0o644 << 16
        jar.writestr(info, manifest)
    print("wrote %s (%d entries, %d bytes)" % (out_jar, len(entries) + 1,
                                               os.path.getsize(out_jar)))


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--aoa-jar", default=None)
    args = parser.parse_args()

    for path, what in ((MCP_JAR, "MCP-named Forge jar"), (MAPPINGS, "SRG mappings"),
                       (SRG_JAR, "SRG-named Forge jar")):
        if not os.path.isfile(path):
            raise SystemExit("missing %s: %s" % (what, path))

    staging = os.path.join(ROOT, "build", "script")
    if os.path.isdir(staging):
        shutil.rmtree(staging)
    os.makedirs(staging)

    jar_path = os.path.join(ROOT, "dist", "%s-%s.jar" % (MOD_ID, VERSION))
    build(staging, jar_path)

    import hashlib
    digest = hashlib.sha256(open(jar_path, "rb").read()).hexdigest()
    sums = os.path.join(ROOT, "dist", "SHA256SUMS.txt")
    with open(sums, "w", encoding="utf-8", newline="\n") as fh:
        fh.write("%s  %s\n" % (digest, os.path.basename(jar_path)))
    print("sha256 %s" % digest)
