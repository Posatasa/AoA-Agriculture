"""Generates chilli water's textures, blockstates, models, icons and its one crafting recipe.

Kept as its own module and called from `generate_resources.py` at the end, because it needs to run
*after* that script clears the directories it writes into.
"""
import io
import json
import os
import zipfile

import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "aoaagriculture")
DATA = os.path.join(ROOT, "src", "main", "resources", "data")
VANILLA = (r"C:\Users\admin\.gradle\caches\forge_gradle\mcp_repo\de\oceanlabs\mcp\mcp_config"
           r"\1.16.5-20210115.111550\joined\downloadClient\client.jar")
AOA = r"C:\Users\admin\curseforge\minecraft\Instances\Wuhui\mods\AoA3-1.16.5-3.6.11.jar"

COLOR = (0xE8, 0x33, 0x24)
# How much of the block underneath shows through. Vanilla water is about 0.7; chilli water is dropped
# to 0.55 so it reads as a liquid rather than as a painted surface.
OPACITY = 1.0

NAMES = {
    "block.aoaagriculture.chilli_water": ("Chilli Water", "辣椒水"),
    "block.aoaagriculture.chilli_water_cauldron": ("Chilli Water Cauldron", "辣椒水炼药锅"),
    "item.aoaagriculture.chilli_water_bucket": ("Chilli Water Bucket", "辣椒水桶"),
    "item.aoaagriculture.chilli_water_bottle": ("Chilli Water Bottle", "辣椒水瓶"),
    "item.aoaagriculture.chilli_water_bottle.tooltip": (
        "No food in it, and it sets you alight.", "不顶饿，还会让你着火。"),
    "message.aoaagriculture.chilli_water.not_enough": (
        "A cauldron of chilli water takes %s chillies.",
        "一锅辣椒水需要 %s 个辣椒。"),
}

METAL_NAMES = {
    "limonite": ("Limonite", "褐铁"),
    "rosite": ("Rosite", "玫石"),
    "lunar": ("Lunar", "月锭"),
    "shyre": ("Shyre", "塞尔瑞"),
    "elecanium": ("Elecanium", "荧银"),
    "varsium": ("Varsium", "超金"),
}
for metal, (english, chinese) in METAL_NAMES.items():
    NAMES["item.aoaagriculture.chilli_water_%s_bucket" % metal] = (
        "Chilli Water %s Bucket" % english, "%s辣椒水桶" % chinese)

# The metal each bucket's own icon is coloured from, and how far to darken it.
METAL_ICONS = {
    "limonite": ("limonite_ingot", 1.00),
    "rosite": ("rosite_ingot", 1.00),
    "lunar": ("lunar_ingot", 1.00),
    "shyre": ("shyrestone_ingot", 0.70),
    "elecanium": ("elecanium_ingot", 1.00),
    "varsium": ("varsium_block", 1.00),
}


def write_json(path, payload):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as fh:
        json.dump(payload, fh, indent=2, ensure_ascii=False)
        fh.write("\n")


def tint_vanilla(path):
    """Vanilla's greyscale water texture, tinted: shading kept, colour replaced."""
    with zipfile.ZipFile(VANILLA) as jar:
        with jar.open(path) as fh:
            image = np.asarray(Image.open(io.BytesIO(fh.read())).convert("RGBA")).astype(float)
    visible = image[:, :, 3] > 0
    luminance = 0.2126 * image[:, :, 0] + 0.7152 * image[:, :, 1] + 0.0722 * image[:, :, 2]
    reference = luminance[visible].mean() if visible.any() else 255.0
    out = image.copy()
    # Proportional tint, exactly like tinting vanilla water: every pixel keeps its own brightness, so
    # the texture keeps water's contrast instead of flattening into one solid colour.
    out[:, :, :3] = np.clip(np.array(COLOR)[None, None, :]
                            * (luminance / max(reference, 1e-6))[:, :, None], 0, 255)
    out[:, :, 3] = np.clip(image[:, :, 3] * OPACITY, 0, 255)
    return np.clip(np.rint(out), 0, 255).astype(np.uint8)


def with_fluid(base):
    """Vanilla's icon with its fluid region tinted, everything else left alone."""
    rgb, alpha = base[:, :, :3], base[:, :, 3]
    visible = alpha > 0
    top, bottom = rgb.max(axis=2), rgb.min(axis=2)
    saturation = np.where(top > 0, (top - bottom) / np.maximum(top, 1), 0)
    fluid = visible & (saturation >= 0.25)
    out = rgb.copy()
    if fluid.any():
        luminance = 0.2126 * rgb[:, :, 0] + 0.7152 * rgb[:, :, 1] + 0.0722 * rgb[:, :, 2]
        reference = luminance[fluid].mean()
        out[fluid] = np.clip(np.array(COLOR)[None, :]
                             * (luminance[fluid] / reference)[:, None], 0, 255)
    result = base.copy()
    result[:, :, :3] = out
    return np.clip(np.rint(result), 0, 255).astype(np.uint8)


def metal_bucket(base, icon, scale):
    """The chilli water bucket in one metal: the fluid stays, the metal takes the metal's colour."""
    with zipfile.ZipFile(AOA) as jar:
        entries = set(jar.namelist())
        path = None
        for folder in ("item", "block"):
            candidate = "assets/aoa3/textures/%s/%s.png" % (folder, icon)
            if candidate in entries:
                path = candidate
                break
        if path is None:
            raise SystemExit("AoA has no texture for %s" % icon)
        with jar.open(path) as fh:
            source = np.asarray(Image.open(io.BytesIO(fh.read())).convert("RGBA")).astype(float)

    visible = source[:, :, 3] > 0
    colour = source[:, :, :3][visible].mean(axis=0)
    colour = colour * (119.0 / max(0.2126 * colour[0] + 0.7152 * colour[1]
                                   + 0.0722 * colour[2], 1e-6)) * scale

    image = with_fluid(base)
    rgb, alpha = image[:, :, :3].copy(), image[:, :, 3]
    top, bottom = rgb.max(axis=2), rgb.min(axis=2)
    saturation = np.where(top > 0, (top - bottom) / np.maximum(top, 1), 0)
    metal = (alpha > 0) & (saturation < 0.25)
    luminance = 0.2126 * rgb[:, :, 0] + 0.7152 * rgb[:, :, 1] + 0.0722 * rgb[:, :, 2]
    reference = luminance[metal].mean()
    out = image.copy()
    out[:, :, :3][metal] = np.clip(colour[None, :] * (luminance[metal] / reference)[:, None], 0, 255)
    return np.clip(np.rint(out), 0, 255).astype(np.uint8)


def main():
    textures = os.path.join(ASSETS, "textures", "block")
    os.makedirs(textures, exist_ok=True)

    for source, target in (("water_still", "chilli_water_still"),
                           ("water_flow", "chilli_water_flowing")):
        pixels = tint_vanilla("assets/minecraft/textures/block/%s.png" % source)
        Image.fromarray(pixels).save(os.path.join(textures, target + ".png"))
        meta = {"animation": {"frametime": 2}} if source == "water_still" else {"animation": {}}
        write_json(os.path.join(textures, target + ".png.mcmeta"), meta)

    blockstates = os.path.join(ASSETS, "blockstates")
    models = os.path.join(ASSETS, "models", "block")
    items = os.path.join(ASSETS, "models", "item")
    item_textures = os.path.join(ASSETS, "textures", "item")
    os.makedirs(item_textures, exist_ok=True)

    # the fluid block, named as vanilla names water's
    write_json(os.path.join(blockstates, "chilli_water.json"),
               {"variants": {"": {"model": "aoaagriculture:block/chilli_water"}}})
    write_json(os.path.join(models, "chilli_water.json"),
               {"textures": {"particle": "aoaagriculture:block/chilli_water_still"}})

    with zipfile.ZipFile(VANILLA) as jar:
        # the cauldron: vanilla's own models with the water swapped for chilli water, no biome tint
        variants = {}
        for level in (1, 2, 3):
            source = json.loads(jar.read("assets/minecraft/models/block/cauldron_level%d.json" % level))
            source.pop("parent", None)
            source["textures"]["water"] = "aoaagriculture:block/chilli_water_still"
            for element in source.get("elements", []):
                for face in element.get("faces", {}).values():
                    if face.get("texture") == "#water":
                        face.pop("tintindex", None)
            write_json(os.path.join(models, "chilli_water_cauldron_level%d.json" % level), source)
            variants["level=%d" % level] = {
                "model": "aoaagriculture:block/chilli_water_cauldron_level%d" % level}
        write_json(os.path.join(blockstates, "chilli_water_cauldron.json"), {"variants": variants})

        # the empty cauldron, so the item has a block model of ours to point at
        empty = json.loads(jar.read("assets/minecraft/models/block/cauldron.json"))
        empty.pop("parent", None)
        write_json(os.path.join(models, "chilli_water_cauldron.json"), empty)

        def load(path):
            with jar.open(path) as fh:
                return np.asarray(Image.open(io.BytesIO(fh.read())).convert("RGBA")).astype(float)

        bucket = load("assets/minecraft/textures/item/water_bucket.png")
        glass = load("assets/minecraft/textures/item/glass_bottle.png")
        liquid = load("assets/minecraft/textures/item/potion_overlay.png")

    write_json(os.path.join(items, "chilli_water_bucket.json"),
               {"parent": "item/generated",
                "textures": {"layer0": "aoaagriculture:item/chilli_water_bucket"}})
    write_json(os.path.join(items, "chilli_water_bottle.json"),
               {"parent": "item/generated",
                "textures": {"layer0": "aoaagriculture:item/chilli_water_bottle"}})
    for metal in METAL_NAMES:
        name = "chilli_water_%s_bucket" % metal
        write_json(os.path.join(items, name + ".json"),
                   {"parent": "item/generated",
                    "textures": {"layer0": "aoaagriculture:item/" + name}})

    Image.fromarray(with_fluid(bucket)).save(os.path.join(item_textures, "chilli_water_bucket.png"))

    # the bottle: vanilla's glass bottle with the potion liquid layer tinted, as potions are drawn
    liquid_alpha = liquid[:, :, 3] / 255.0
    liquid_shade = 0.35 + 0.65 * ((0.2126 * liquid[:, :, 0] + 0.7152 * liquid[:, :, 1]
                                   + 0.0722 * liquid[:, :, 2]) / 255.0)
    bottle = glass.copy()
    bottle[:, :, :3] = (glass[:, :, :3] * (1.0 - liquid_alpha[:, :, None])
                        + (np.array(COLOR, dtype=float)[None, None, :]
                           * liquid_shade[:, :, None]) * liquid_alpha[:, :, None])
    bottle[:, :, 3] = np.maximum(glass[:, :, 3], liquid[:, :, 3])
    Image.fromarray(np.clip(np.rint(bottle), 0, 255).astype(np.uint8)).save(
        os.path.join(item_textures, "chilli_water_bottle.png"))

    for metal, (icon, scale) in METAL_ICONS.items():
        Image.fromarray(metal_bucket(bucket, icon, scale)).save(
            os.path.join(item_textures, "chilli_water_%s_bucket.png" % metal))

    # one bottle, gunpowder and a limonite nugget make AoA's own Hellfire
    write_json(os.path.join(DATA, "aoaagriculture", "recipes",
                            "hellfire_from_chilli_water.json"), {
        "type": "minecraft:crafting_shapeless",
        "ingredients": [
            {"item": "aoaagriculture:chilli_water_bottle"},
            {"item": "minecraft:gunpowder"},
            {"item": "aoa3:limonite_nugget"},
        ],
        "result": {"item": "aoa3:hellfire"},
    })

    for language, index in (("en_us", 0), ("zh_cn", 1)):
        path = os.path.join(ASSETS, "lang", language + ".json")
        with open(path, encoding="utf-8") as fh:
            table = json.load(fh)
        for key, values in NAMES.items():
            table[key] = values[index]
        with open(path, "w", encoding="utf-8", newline="\n") as fh:
            json.dump(table, fh, indent=2, ensure_ascii=False, sort_keys=True)
            fh.write("\n")

    print("  chilli water: textures, blockstates, models, recipe, %d names" % len(NAMES))


if __name__ == "__main__":
    main()
