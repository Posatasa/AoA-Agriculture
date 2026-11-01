"""Chocolate cakes: recoloured cake textures, vanilla's cake models, names, and the recipe."""
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

# name, sauce item, English, Chinese, cake colour
CAKES = (
    ("chocolate_cake", "chocolate_sauce", "Chocolate Cake", "巧克力蛋糕", (142, 92, 58)),
    ("dark_chocolate_cake", "dark_chocolate_sauce", "Dark Chocolate Cake", "黑巧克力蛋糕",
     (84, 52, 32)),
    # Vanilla cake is white, so this one is a clear cream rather than another white.
    ("white_chocolate_cake", "white_chocolate_sauce", "White Chocolate Cake", "白巧克力蛋糕",
     (236, 214, 152)),
)

# The three textures vanilla's cake models use, and their number of models (bites 0 to 6).
PARTS = ("cake_top", "cake_side", "cake_bottom", "cake_inner")
VANILLA_MODELS = ("cake", "cake_slice1", "cake_slice2", "cake_slice3", "cake_slice4",
                  "cake_slice5", "cake_slice6")


def write_json(path, payload):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as fh:
        json.dump(payload, fh, indent=2, ensure_ascii=False, sort_keys=True)
        fh.write("\n")


def tint(path, colour, jar):
    with jar.open(path) as fh:
        image = np.asarray(Image.open(io.BytesIO(fh.read())).convert("RGBA")).astype(float)
    visible = image[:, :, 3] > 0
    luminance = 0.2126 * image[:, :, 0] + 0.7152 * image[:, :, 1] + 0.0722 * image[:, :, 2]
    reference = luminance[visible].mean() if visible.any() else 255.0
    out = image.copy()
    # Proportional, like tinting water: the cake keeps its own shading and only changes colour.
    # Clamped at one, so a bright pixel lands exactly on the colour instead of being pushed past it:
    # the cake icon is mostly near-white, and scaling that up is what washed the colour out.
    scale = np.minimum(luminance / max(reference, 1e-6), 1.0)
    out[:, :, :3] = np.clip(np.array(colour)[None, None, :] * scale[:, :, None], 0, 255)
    return np.clip(np.rint(out), 0, 255).astype(np.uint8)


def main():
    blockstates = os.path.join(ASSETS, "blockstates")
    block_models = os.path.join(ASSETS, "models", "block")
    item_models = os.path.join(ASSETS, "models", "item")
    block_textures = os.path.join(ASSETS, "textures", "block")
    item_textures = os.path.join(ASSETS, "textures", "item")
    os.makedirs(block_textures, exist_ok=True)
    os.makedirs(item_textures, exist_ok=True)

    with zipfile.ZipFile(VANILLA) as jar:
        for name, sauce, english, chinese, colour in CAKES:
            # textures: vanilla's cake parts, tinted
            for part in PARTS:
                source = "assets/minecraft/textures/block/%s.png" % part
                if source not in jar.namelist():
                    continue
                pixels = tint(source, colour, jar)
                target = "%s_%s.png" % (name, part)
                Image.fromarray(pixels).save(os.path.join(block_textures, target))

            # models: vanilla's own cake models with our textures on top of them
            textures = {
                "top": "aoaagriculture:block/%s_cake_top" % name,
                "side": "aoaagriculture:block/%s_cake_side" % name,
                "bottom": "aoaagriculture:block/%s_cake_bottom" % name,
            }
            for model in VANILLA_MODELS:
                write_json(os.path.join(block_models, "%s_%s.json" % (name, model)),
                           {"parent": "minecraft:block/" + model, "textures": textures})
            # The inventory icon is vanilla's own cake icon, tinted, rather than the block model: a cake
            # in a slot should look like a cake icon, as vanilla's does.
            icon = tint("assets/minecraft/textures/item/cake.png", colour, jar)
            Image.fromarray(icon).save(os.path.join(item_textures, name + ".png"))
            write_json(os.path.join(item_models, name + ".json"),
                       {"parent": "item/generated",
                        "textures": {"layer0": "aoaagriculture:item/" + name}})

            # blockstate: vanilla's bites, one model each
            variants = {}
            for bites, model in enumerate(["cake"] + ["cake_slice%d" % i for i in range(1, 7)]):
                variants["bites=%d" % bites] = {
                    "model": "aoaagriculture:block/%s_%s" % (name, model)}
            write_json(os.path.join(blockstates, name + ".json"), {"variants": variants})

            # three sauces poured over a vanilla cake
            write_json(os.path.join(DATA, "aoaagriculture", "recipes", name + ".json"), {
                "type": "minecraft:crafting_shapeless",
                "ingredients": [
                    {"item": "aoaagriculture:" + sauce},
                    {"item": "aoaagriculture:" + sauce},
                    {"item": "aoaagriculture:" + sauce},
                    {"item": "minecraft:cake"},
                ],
                "result": {"item": "aoaagriculture:" + name},
            })

    for language, index in (("en_us", 2), ("zh_cn", 3)):
        path = os.path.join(ASSETS, "lang", language + ".json")
        with open(path, encoding="utf-8") as fh:
            table = json.load(fh)
        for name, _sauce, english, chinese, _colour in CAKES:
            table["block.aoaagriculture." + name] = (english, chinese)[index - 2]
            # the item model is an icon rather than the block model, so it carries an item name too
            table["item.aoaagriculture." + name] = (english, chinese)[index - 2]
        with open(path, "w", encoding="utf-8", newline="\n") as fh:
            json.dump(table, fh, indent=2, ensure_ascii=False, sort_keys=True)
            fh.write("\n")

    print("  chocolate cakes: 3 textures each, an icon each, 7 models each, 3 recipes")


if __name__ == "__main__":
    main()
