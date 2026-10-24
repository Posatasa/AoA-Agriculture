"""Generates this add-on's blockstates, models, loot tables, tags, lang files and textures.

Textures: vanilla farmland's light-and-dark pattern, drawn in each AoA soil's own colour.

Only vanilla farmland's *luminance* is taken, and it is applied as a per-pixel brightness
multiplier to the soil's average colour. Vanilla farmland's highlight pixels are slightly cooler
than its furrows; that is invisible on brown, but multiplied by a saturated soil it becomes blue
specks - which neither vanilla farmland nor AoA's fertilised farmland has. Keeping the luminance
alone means a highlight is simply a lighter version of the soil's own colour, so no soil can pick up
a colour that is not its own. The generator asserts exactly that (a neutral soil must come out
neutral).

The moist texture is that result re-shaded at vanilla's own moist brightness. The fertilised tops
are the same thing, darker and slightly richer, at the ratio AoA's own fertilised farmland uses
(0.89x for fertilised, 0.81x for well fertilised); nothing is painted on top of the vanilla pattern.

The block looks the way vanilla farmland looks: one top texture for moisture 0..6 and a darker one
at 7. The fertilised block repeats that split per `well_fertilised` state, like AoA's own block.

Models: like vanilla farmland, only the top face is the tilled texture. The sides and bottom
point at AoA's own soil texture (`aoa3:block/<soil>`), so they stay plain dirt - and since that
is a reference resolved by the game at runtime, no AoA asset is copied into this jar.
"""
import io
import json
import os
import zipfile

import numpy as np
from PIL import Image

MC_JAR = os.path.expanduser(
    r"~\.gradle\caches\forge_gradle\minecraft_repo\versions\1.16.5\client-extra.jar")
AOA_JAR = r"C:\Users\admin\curseforge\minecraft\Instances\Wuhui\mods\AoA3-1.16.5-3.6.11.jar"
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "aoaagriculture")
DATA = os.path.join(ROOT, "src", "main", "resources", "data")

# registry name -> (AoA soil texture/block name, AoA soil block id, English, Chinese)
SOILS = [
    ("aromatic_farmland",   "aromatic_dirt",   "aoa3:aromatic_dirt",   "Aromatic Farmland",   "芬芳耕地"),
    ("blackened_farmland",  "blackened_soil",  "aoa3:blackened_soil",  "Blackened Farmland",  "漆黑耕地"),
    ("candied_farmland",    "candied_dirt",    "aoa3:candied_dirt",    "Candied Farmland",    "糖果耕地"),
    ("celevian_farmland",   "celevian_dirt",   "aoa3:celevian_dirt",   "Celevian Farmland",   "玩具耕地"),
    ("coral_farmland",      "coral_soil",      "aoa3:coral_soil",      "Coral Farmland",      "珊瑚耕地"),
    ("creep_farmland",      "creep_dirt",      "aoa3:creep_dirt",      "Creep Farmland",      "蠕变耕地"),
    ("fungal_farmland",     "fungal_dirt",     "aoa3:fungal_dirt",     "Fungal Farmland",     "真菌耕地"),
    ("greckon_farmland",    "greckon_dirt",    "aoa3:greckon_dirt",    "Greckon Farmland",    "格瑞克耕地"),
    ("lunalyte_farmland",   "lunalyte_dirt",   "aoa3:lunalyte_dirt",   "Lunalyte Farmland",   "粉色月球耕地"),
    ("lunasole_farmland",   "lunasole_dirt",   "aoa3:lunasole_dirt",   "Lunasole Farmland",   "蓝色月球耕地"),
    ("weightless_farmland", "weightless_dirt", "aoa3:weightless_dirt", "Weightless Farmland", "轻盈耕地"),
]

# The AoA soils that act like Farmer's Delight's rich soil for mushrooms. Mirrored by
# FarmlandBlocks.COLONY_SOILS in Java, and the build cross-checks the two so a soil cannot end up
# converting mushrooms without the tag that lets the colony grow.
COLONY_SOILS = ["aoa3:blackened_soil", "aoa3:fungal_dirt"]

# Display names per bucket kind: English and Chinese, with the metal substituted in.
BUCKET_NAMES = {
    "": ("{} Bucket", "{}桶"),
    "water_": ("{} Water Bucket", "{}水桶"),
    "lava_": ("{} Lava Bucket", "{}熔岩桶"),
    "candied_water_": ("{} Candied Water Bucket", "{}糖水桶"),
}
METAL_NAMES = {
    "limonite": ("Limonite", "褐铁"),
    "rosite": ("Rosite", "玫石"),
    "shyre": ("Shyre", "塞尔瑞"),
    "lunar": ("Lunar", "月锭"),
    "emberstone": ("Emberstone", "余烬"),
}

# Tooltips for the two buckets that do more than a bucket does. The keys are per item name, because
# each one describes that item's own behaviour.
BUCKET_TOOLTIPS = {
    "water_shyre_bucket": (
        "Pours without using up its water - hold to empty.",
        "倒水不会消耗水；按住右键 2 秒清空。"),
    "water_lunar_bucket": (
        "Protects from fall damage while held.",
        "手持时免疫摔落伤害。"),
    "emberstone_bucket": (
        "Takes lava only - up to four buckets of it.",
        "只装熔岩，最多 4 桶。"),
    "lava_emberstone_bucket": (
        "Lava: %s / %s",
        "熔岩：%s / %s"),
}

TAB_KEY = "itemGroup.aoaagriculture"
TAB_EN = "AoA Agriculture"
TAB_ZH = "虚无世界农业"

# FarmlandBlock#MOISTURE runs 0..7, and vanilla draws 0..6 with one texture and 7 with another.
MOISTURE_LEVELS = 8
WET_LEVEL = MOISTURE_LEVELS - 1

# How much darker the fertilised tops are, and how many pale flecks they carry.
#
# AoA's own fertilised farmland is only 0.89x the brightness of vanilla farmland, which turns out to
# read as "no difference at all" once the soil is dark, so these go a little deeper. The flecks are
# the other half of the same idea: AoA's fertilised top has a handful of pale pixels scattered over
# it, and that is what the eye actually latches onto.
#
# The flecks are deliberately not blue and not green. Each one is its own pixel desaturated towards
# grey and then lightened, so a fleck is a paler, dustier version of that very soil - the neutral grey
# highlight vanilla farmland has, drawn in the soil's own hue.
FERTILISED_BRIGHTNESS = 0.85
WELL_BRIGHTNESS = 0.74

# One entry per bucket kind: the registry-name prefix, the vanilla icon it starts from, and whether
# the fluid should be re-tinted (only candied water, whose contents are not vanilla's water).
BUCKET_KINDS = (("", "bucket", False),
                ("water_", "water_bucket", False),
                ("lava_", "lava_bucket", False),
                ("candied_water_", "water_bucket", True))
# Each AoA metal that gets buckets: its name, the ingot its crafting recipe uses, the colours its
# metal is drawn in (icon names with a brightness scale, top to bottom - one entry means flat), and
# which buckets it has.
#
# The scales matter as much as the hues. Shyre's gem and its ingot sit barely twenty degrees apart,
# so blending them at one brightness looked flat; the gem is lifted and the ingot dropped, which
# makes the two ends of the gradient obvious.
EMBER_KINDS = (("", "bucket", False), ("lava_", "lava_bucket", False))

BUCKET_METALS = (
    ("limonite", "limonite_ingot", (("limonite_ingot", 1.00),), BUCKET_KINDS),
    ("rosite", "rosite_ingot", (("rosite_ingot", 1.00),), BUCKET_KINDS),
    ("shyre", "shyrestone_ingot",
     (("shyregem", 1.35), ("shyrestone_ingot", 0.70)), BUCKET_KINDS),
    ("lunar", "lunar_ingot", (("lunar_ingot", 1.00),), BUCKET_KINDS),
    # The Emberstone bucket is a lava tank: it refuses water and candied water outright, so it has
    # only the two buckets that make sense for it. Its colour comes from the ore - the reddest
    # emberstone texture - and is dropped well under the limonite bucket's brightness, because at
    # full brightness the two oranges read as the same metal.
    ("emberstone", "emberstone_ingot", (("emberstone_ore", 0.68),), EMBER_KINDS),
)


# Fixed positions, so the result is identical on every run and machine.
FLECKS_FERTILISED = ((3, 2), (11, 4), (6, 7), (1, 11), (13, 9), (9, 1))
FLECKS_WELL = ((3, 2), (11, 4), (6, 7), (1, 11), (13, 9), (9, 1), (12, 12), (8, 13), (14, 5))
FLECK_DESATURATE = 0.60          # how far a fleck moves towards its own grey
FLECK_CONTRAST = 1.75            # how many times brighter a fleck is than the pixel under it
FLECK_STRENGTH = 0.85            # how far the pixel moves towards the fleck colour


def fertilised_name(block):
    return block.replace("_farmland", "_fertilised_farmland")


def model_name(block, suffix, fertilised):
    """Texture/model name: the block's own name plus a state suffix ("" / "_moist" / "_well" ...)."""
    return (fertilised_name(block) if fertilised else block) + suffix


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as fh:
        json.dump(obj, fh, indent=2, ensure_ascii=False)
        fh.write("\n")


def clean(directory):
    """Removes generated files so a changed naming scheme cannot leave stale ones behind."""
    if not os.path.isdir(directory):
        return
    for name in os.listdir(directory):
        path = os.path.join(directory, name)
        if os.path.isfile(path):
            os.remove(path)


def darken(rgb, factor):
    """Scales brightness only, so the pattern keeps exactly the soil's own hue."""
    return np.clip(np.rint(rgb.astype(np.float64) * factor), 0, 255).astype(np.uint8)


def fleck(rgb, positions):
    """Adds pale, near-neutral dust flecks, each in its own pixel's hue.

    The fleck colour is the pixel desaturated towards its own grey, then scaled to a fixed multiple
    of that pixel's brightness. Defining it by contrast rather than by a fixed colour is what makes
    the flecks readable on both a pale soil and a near-black one - and keeping the hue is what stops
    them turning into the blue and green dots this started with.
    """
    out = rgb.astype(np.float64)
    for x, y in positions:
        base = out[y, x]
        grey = base.mean()
        colour = base * (1.0 - FLECK_DESATURATE) + grey * FLECK_DESATURATE
        target = base.mean() * FLECK_CONTRAST + 10.0
        colour = colour * (target / max(colour.mean(), 1e-6))
        out[y, x] = base * (1.0 - FLECK_STRENGTH) + np.clip(colour, 0, 255) * FLECK_STRENGTH
    return np.clip(np.rint(out), 0, 255).astype(np.uint8)


def generate_textures():
    zm = zipfile.ZipFile(MC_JAR)
    za = zipfile.ZipFile(AOA_JAR)

    def load(archive, path):
        with archive.open(path) as fh:
            return np.asarray(Image.open(io.BytesIO(fh.read())).convert("RGB")).astype(np.float64)

    dirt = load(zm, "assets/minecraft/textures/block/dirt.png")
    farmland = load(zm, "assets/minecraft/textures/block/farmland.png")
    farmland_moist = load(zm, "assets/minecraft/textures/block/farmland_moist.png")

    def luminance(rgb):
        return 0.2126 * rgb[:, :, 0] + 0.7152 * rgb[:, :, 1] + 0.0722 * rgb[:, :, 2]

    # The tilled pattern, as a per-pixel brightness multiplier with a mean of 1.
    #
    # Only vanilla farmland's *light and dark* is taken, not its colour. Vanilla's highlight pixels
    # are slightly cooler than its furrows - unnoticeable on brown, but multiplied by a saturated
    # soil it turns into blue specks, which vanilla farmland has nowhere and AoA's fertilised
    # farmland does not have either. Keeping the luminance alone means every highlight comes out as
    # a lighter version of the soil's own colour, which is what "the vanilla pattern in that soil's
    # palette" actually means.
    pattern = luminance(farmland) / luminance(farmland).mean()
    wet_brightness = luminance(farmland_moist).mean() / luminance(farmland).mean()

    def recolor(soil, wet):
        out = soil.mean(axis=(0, 1), keepdims=True) * pattern[:, :, None]
        if wet:
            # Re-shade at vanilla's moist brightness: keep the colour, move only the brightness.
            lum = luminance(out)
            colour = out / np.maximum(lum[:, :, None], 1e-6)
            out = colour * (lum * wet_brightness)[:, :, None]
        return np.clip(np.rint(out), 0, 255).astype(np.uint8)

    # The pattern must not carry a colour of its own: a neutral soil has to stay neutral, or that
    # colour shows up as specks on soils of a different hue. This is what caught the blue dots.
    neutral = np.full((16, 16, 3), 128.0)
    spread = np.abs(recolor(neutral, False).astype(np.float64)
                    - recolor(neutral, False).mean(axis=2, keepdims=True)).max()
    assert spread <= 1.0, "the tilled pattern carries a colour cast (%.1f)" % spread
    # And its brightness structure must be vanilla's own, at the same overall level.
    assert abs(float(pattern.mean()) - 1.0) < 1e-9
    print("tilled pattern is vanilla farmland's luminance, with no colour of its own")

    out_dir = os.path.join(ASSETS, "textures", "block")
    os.makedirs(out_dir, exist_ok=True)
    clean(out_dir)
    for name, texture, _, _, _ in SOILS:
        soil = load(za, "assets/aoa3/textures/block/%s.png" % texture)
        dry, moist = recolor(soil, False), recolor(soil, True)
        tops = [
            (model_name(name, "", False), dry),
            (model_name(name, "_moist", False), moist),
            (model_name(name, "", True),
             fleck(darken(dry, FERTILISED_BRIGHTNESS), FLECKS_FERTILISED)),
            (model_name(name, "_moist", True),
             fleck(darken(moist, FERTILISED_BRIGHTNESS), FLECKS_FERTILISED)),
            (model_name(name, "_well", True),
             fleck(darken(dry, WELL_BRIGHTNESS), FLECKS_WELL)),
            (model_name(name, "_well_moist", True),
             fleck(darken(moist, WELL_BRIGHTNESS), FLECKS_WELL)),
        ]
        for model, array in tops:
            # RGBA like every vanilla block texture, even though these are fully opaque.
            Image.fromarray(array).convert("RGBA").save(os.path.join(out_dir, model + ".png"))
        print("  %s: %d textures" % (name, len(tops)))

    generate_bucket_icons(zm, za)
    generate_bottle_icon(zm, za)


def generate_bucket_icons(zm, za):
    """Vanilla bucket icons in each AoA metal's colour, keeping each bucket's contents."""
    item_dir = os.path.join(ASSETS, "textures", "item")
    os.makedirs(item_dir, exist_ok=True)
    clean(item_dir)

    def load(archive, path):
        with archive.open(path) as fh:
            return np.asarray(Image.open(io.BytesIO(fh.read())).convert("RGBA")).astype(np.float64)

    def luminance(rgb):
        return 0.2126 * rgb[:, :, 0] + 0.7152 * rgb[:, :, 1] + 0.0722 * rgb[:, :, 2]

    def saturation(rgb):
        top, bottom = rgb.max(axis=2), rgb.min(axis=2)
        return np.where(top > 0, (top - bottom) / np.maximum(top, 1), 0)

    def recolour(base, metal, fluid_tint):
        """Keeps each region's shading and moves its colour: grey parts become metal."""
        rgb, alpha = base[:, :, :3], base[:, :, 3]
        visible = alpha > 0
        is_metal = visible & (saturation(rgb) < 0.25)
        is_fluid = visible & (saturation(rgb) >= 0.25)
        out = rgb.copy()
        lum = luminance(rgb)
        if is_metal.any():
            reference = lum[is_metal].mean()
            out[is_metal] = np.clip(metal[is_metal] * (lum[is_metal] / reference)[:, None], 0, 255)
        if fluid_tint is not None and is_fluid.any():
            reference = lum[is_fluid].mean()
            out[is_fluid] = np.clip(np.array(fluid_tint)[None, :]
                                    * (lum[is_fluid] / reference)[:, None], 0, 255)
        result = base.copy()
        result[:, :, :3] = out
        return np.clip(np.rint(result), 0, 255).astype(np.uint8)

    # Every bucket is drawn at the brightness of vanilla's, so the metals only change hue.
    vanilla_metal = load(zm, "assets/minecraft/textures/item/bucket.png")
    target_lum = luminance(vanilla_metal[:, :, :3])[vanilla_metal[:, :, 3] > 0].mean()

    # AoA's own candied water bucket shows the fluid colour; only that one colour is taken from it,
    # never any pixels - the icons below are drawn from vanilla's textures.
    candied = load(za, "assets/aoa3/textures/item/candied_water_bucket.png")
    candied_rgb = candied[:, :, :3][(candied[:, :, 3] > 0) & (saturation(candied[:, :, :3]) >= 0.25)]
    candied_tint = candied_rgb.mean(axis=0)

    aoa_entries = set(za.namelist())

    def colour_of(icon_name):
        """An AoA icon's average colour, brought to the brightness of vanilla's bucket metal.

        Looks under both texture folders: the metals are items, but one of Shyre's three colours
        comes from bright rock, which is a block.
        """
        for folder in ("item", "block"):
            path = "assets/aoa3/textures/%s/%s.png" % (folder, icon_name)
            if path in aoa_entries:
                icon = load(za, path)
                break
        else:
            raise SystemExit("AoA has no texture for %s" % icon_name)
        visible = icon[:, :, 3] > 0
        colour = icon[:, :, :3][visible].mean(axis=0)
        return colour * (target_lum / max(luminance(colour.reshape(1, 1, 3))[0, 0], 1e-6))

    def metal_map(base, stops):
        """The metal colour per pixel: one colour, or a top-to-bottom blend through the stops."""
        height, width = base.shape[:2]
        if len(stops) == 1:
            return np.broadcast_to(stops[0], (height, width, 3))
        out = np.zeros((height, width, 3))
        for row in range(height):
            position = (len(stops) - 1) * row / max(height - 1, 1)
            low = min(int(position), len(stops) - 2)
            t = position - low
            out[row] = stops[low] * (1.0 - t) + stops[low + 1] * t
        return out

    for metal_name, ingot_name, colour_stops, kinds in BUCKET_METALS:
        stops = [colour_of(icon) * scale for icon, scale in colour_stops]

        for prefix, shape, tint_fluid in kinds:
            base = load(zm, "assets/minecraft/textures/item/%s.png" % shape)
            image = recolour(base, metal_map(base, stops),
                             candied_tint if tint_fluid else None)
            Image.fromarray(image).save(
                os.path.join(item_dir, "%s%s_bucket.png" % (prefix, metal_name)))
        print("  %s: %d bucket icons%s" % (metal_name, len(kinds),
                                           " (gradient)" if len(stops) > 1 else ""))


def generate_bottle_icon(zm, za):
    """The candied water bottle: vanilla's honey bottle, recoloured to AoA's candied water.

    The honey bottle is the closest vanilla thing to a sweet drink in a bottle, so it is also the
    right shape; only its colour changes, and only its colour is taken from AoA - never a pixel.
    """
    item_dir = os.path.join(ASSETS, "textures", "item")
    os.makedirs(item_dir, exist_ok=True)

    def load(archive, path):
        with archive.open(path) as fh:
            return np.asarray(Image.open(io.BytesIO(fh.read())).convert("RGBA")).astype(np.float64)

    def luminance(rgb):
        return 0.2126 * rgb[:, :, 0] + 0.7152 * rgb[:, :, 1] + 0.0722 * rgb[:, :, 2]

    with za.open("assets/aoa3/textures/item/candied_water_bucket.png") as fh:
        bucket = np.asarray(Image.open(io.BytesIO(fh.read())).convert("RGBA")).astype(np.float64)
    bucket_rgb = bucket[:, :, :3]
    top, bottom = bucket_rgb.max(axis=2), bucket_rgb.min(axis=2)
    fluid = bucket_rgb[(bucket[:, :, 3] > 0) & (top - bottom > 30)]
    tint = fluid.mean(axis=0)

    base = load(zm, "assets/minecraft/textures/item/honey_bottle.png")
    visible = base[:, :, 3] > 0
    reference = luminance(base[:, :, :3])[visible].mean()
    out = base.copy()
    out[:, :, :3] = np.clip(tint[None, None, :]
                            * (luminance(base[:, :, :3]) / reference)[:, :, None], 0, 255)
    Image.fromarray(np.clip(np.rint(out), 0, 255).astype(np.uint8)).save(
        os.path.join(item_dir, "candied_water_bottle.png"))
    print("  candied water bottle: 1 icon (%d, %d, %d)" % tuple(int(v) for v in tint))


def generate_resources():
    en = {"_comment": "Generated by tools/generate_resources.py"}
    zh = {"_comment": "由 tools/generate_resources.py 生成"}
    en[TAB_KEY] = TAB_EN
    zh[TAB_KEY] = TAB_ZH
    dirt_tag = []

    models_dir = os.path.join(ASSETS, "models", "block")
    blockstates_dir = os.path.join(ASSETS, "blockstates")
    clean(models_dir)
    clean(blockstates_dir)
    # The three tag directories are generated too, and a tag that stops being written has to stop
    # existing: one of them was dropped from this script but stayed in the source tree and kept
    # being shipped, which quietly undid the change.
    for owner, path in (("minecraft", "mushroom_grow_block"),
                        ("farmersdelight", "mushroom_colony_growable_on"),
                        ("forge", "dirt")):
        tag_dir = os.path.join(DATA, owner, "tags", "blocks")
        stale = os.path.join(tag_dir, path + ".json")
        if os.path.isfile(stale):
            os.remove(stale)

    def write_model(block, suffix, fertilised, soil_texture):
        model = model_name(block, suffix, fertilised)
        write_json(os.path.join(models_dir, model + ".json"), {
            "parent": "minecraft:block/template_farmland",
            "textures": {
                "top": "aoaagriculture:block/" + model,
                "dirt": "aoa3:block/" + soil_texture,
            },
        })
        return model

    for name, texture, soil, english, chinese in SOILS:
        bid = "aoaagriculture:" + name
        fertilised = fertilised_name(name)

        # ---- plain farmland: exactly vanilla's split, dry for 0..6 and moist at 7
        variants = {}
        for level in range(MOISTURE_LEVELS):
            suffix = "_moist" if level == WET_LEVEL else ""
            variants["moisture=%d" % level] = {
                "model": "aoaagriculture:block/" + model_name(name, suffix, False)}
        write_json(os.path.join(blockstates_dir, name + ".json"), {"variants": variants})
        write_model(name, "", False, texture)
        write_model(name, "_moist", False, texture)
        # The item shows the dry state, like vanilla's farmland item.
        write_json(os.path.join(ASSETS, "models", "item", name + ".json"),
                   {"parent": "aoaagriculture:block/" + name})

        # ---- fertilised farmland: AoA's extra `well_fertilised` state, same moisture split
        variants = {}
        for level in range(MOISTURE_LEVELS):
            suffix = "_moist" if level == WET_LEVEL else ""
            variants["moisture=%d,well_fertilised=false" % level] = {
                "model": "aoaagriculture:block/" + model_name(name, suffix, True)}
            variants["moisture=%d,well_fertilised=true" % level] = {
                "model": "aoaagriculture:block/" + model_name(name, "_well" + suffix, True)}
        write_json(os.path.join(blockstates_dir, fertilised + ".json"), {"variants": variants})
        write_model(name, "", True, texture)
        write_model(name, "_moist", True, texture)
        write_model(name, "_well", True, texture)
        write_model(name, "_well_moist", True, texture)
        write_json(os.path.join(ASSETS, "models", "item", fertilised + ".json"),
                   {"parent": "aoaagriculture:block/" + fertilised})

        # loot: both drop the AoA soil they were tilled from, not vanilla dirt
        for lootName in (name, fertilised):
            write_json(os.path.join(DATA, "aoaagriculture", "loot_tables", "blocks",
                                    lootName + ".json"), {
                "type": "minecraft:block",
                "pools": [{
                    "rolls": 1,
                    "entries": [{"type": "minecraft:item", "name": soil}],
                    "conditions": [{"condition": "minecraft:survives_explosion"}],
                }],
            })

        en["block.aoaagriculture." + name] = english
        zh["block.aoaagriculture." + name] = chinese
        en["block.aoaagriculture." + fertilised] = english.replace("Farmland",
                                                                 "Fertilised Farmland")
        zh["block.aoaagriculture." + fertilised] = chinese.replace("耕地", "肥沃耕地")
        dirt_tag.append(bid)
        dirt_tag.append("aoaagriculture:" + fertilised)

    # ---- buckets: item models, one crafting recipe per metal, and names
    for metal_name, ingot_name, _colours, kinds in BUCKET_METALS:
        for prefix, _shape, _tint in kinds:
            item_name = "%s%s_bucket" % (prefix, metal_name)
            write_json(os.path.join(ASSETS, "models", "item", item_name + ".json"), {
                "parent": "item/generated",
                "textures": {"layer0": "aoaagriculture:item/" + item_name},
            })
            english, chinese = BUCKET_NAMES[prefix]
            en["item.aoaagriculture." + item_name] = english.format(METAL_NAMES[metal_name][0])
            zh["item.aoaagriculture." + item_name] = chinese.format(METAL_NAMES[metal_name][1])
            if item_name in BUCKET_TOOLTIPS:
                tip_en, tip_zh = BUCKET_TOOLTIPS[item_name]
                en["item.aoaagriculture." + item_name + ".tooltip"] = tip_en
                zh["item.aoaagriculture." + item_name + ".tooltip"] = tip_zh

        # The vanilla bucket recipe, in this metal's ingots - except Shyre, which takes three ingots
        # and a gem, so it is shapeless rather than forced into the bucket's three-slot shape.
        if metal_name == "shyre":
            write_json(os.path.join(DATA, "aoaagriculture", "recipes",
                                    metal_name + "_bucket.json"), {
                "type": "minecraft:crafting_shapeless",
                "ingredients": [
                    {"item": "aoa3:" + ingot_name},
                    {"item": "aoa3:" + ingot_name},
                    {"item": "aoa3:" + ingot_name},
                    {"item": "aoa3:shyregem"},
                ],
                "result": {"item": "aoaagriculture:" + metal_name + "_bucket"},
            })
        else:
            write_json(os.path.join(DATA, "aoaagriculture", "recipes",
                                    metal_name + "_bucket.json"), {
                "type": "minecraft:crafting_shaped",
                "pattern": ["# #", " # "],
                "key": {"#": {"item": "aoa3:" + ingot_name}},
                "result": {"item": "aoaagriculture:" + metal_name + "_bucket"},
            })

    # ---- the candied water bottle: a drink that hands the glass bottle back
    write_json(os.path.join(ASSETS, "models", "item", "candied_water_bottle.json"), {
        "parent": "item/generated",
        "textures": {"layer0": "aoaagriculture:item/candied_water_bottle"},
    })
    en["item.aoaagriculture.candied_water_bottle"] = "Candied Water Bottle"
    zh["item.aoaagriculture.candied_water_bottle"] = "糖水瓶"

    # Vanilla's honey bottle becomes three sugar this way, with its glass bottle returned by the
    # item's crafting remainder.
    write_json(os.path.join(DATA, "aoaagriculture", "recipes",
                            "sugar_from_candied_water_bottle.json"), {
        "type": "minecraft:crafting_shapeless",
        "group": "sugar",
        "ingredients": [{"item": "aoaagriculture:candied_water_bottle"}],
        "result": {"item": "minecraft:sugar", "count": 3},
    })

    write_json(os.path.join(ASSETS, "lang", "en_us.json"), en)
    write_json(os.path.join(ASSETS, "lang", "zh_cn.json"), zh)

    # These blocks are dirt that happens to be tilled, so they join AoA's own dirt tag; the entry
    # list is deliberately explicit rather than "the forge:dirt tag" so it cannot go circular.
    #
    # Being in this tag does not make them hoe-tillable: Forge's tilling rule is a hardcoded
    # dirt -> farmland map, not a tag test. What it does affect is other mods' "is this soil?"
    # checks, which is the point. Tag files carry no comment key: they are parsed as data.
    write_json(os.path.join(DATA, "forge", "tags", "blocks", "dirt.json"), {
        "replace": False,
        "values": dirt_tag,
    })

    # A colony only *ages* on blocks in Farmer's Delight's own tag, which ships with nothing but
    # farmersdelight:rich_soil - that is why colonies used to freeze at their first stage.
    #
    # Deliberately not using minecraft:mushroom_grow_block to keep them alive instead: that tag is
    # read by vanilla, so adding our soils to it would let giant mushrooms grow on them even when
    # Farmer's Delight is not installed. Colonies survive on these soils through an FD-targeted
    # mixin instead (MixinMushroomColonySurvive), which only applies while that mod is present.
    write_json(os.path.join(DATA, "farmersdelight", "tags", "blocks",
                            "mushroom_colony_growable_on.json"), {
        "replace": False,
        "values": COLONY_SOILS,
    })

    # The harvest modifier: AoA does this for its own fertilised farmland with a global loot
    # modifier; ours is the per-soil equivalent, registered the same way.
    write_json(os.path.join(DATA, "aoaagriculture", "loot_modifiers",
                            "fertilised_farmland.json"), {
        "conditions": [],
        "type": "aoaagriculture:fertilised_farmland",
    })
    write_json(os.path.join(DATA, "forge", "loot_modifiers",
                            "global_loot_modifiers.json"), {
        "_comment": "Adds this add-on's harvest modifier; AoA's own entries are merged in.",
        "replace": False,
        "entries": ["aoaagriculture:fertilised_farmland"],
    })


if __name__ == "__main__":
    generate_textures()
    generate_resources()
    print("done")
