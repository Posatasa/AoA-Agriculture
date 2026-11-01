"""Honey cakes and cake slices: textures, models, icons, names, and the three sets of recipes.

Honey cakes are the base cake with honey poured over it, so their textures are the base cake's blended
towards honey. Slices have no vanilla counterpart to recolour, so each one is the cake's own icon cut to a
wedge - the shape does the work, and the colours come from the cake itself.
"""
import io
import json
import os
import shutil
import zipfile

import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "aoaagriculture")
DATA = os.path.join(ROOT, "src", "main", "resources", "data")
VANILLA = (r"C:\Users\admin\.gradle\caches\forge_gradle\mcp_repo\de\oceanlabs\mcp\mcp_config"
           r"\1.16.5-20210115.111550\joined\downloadClient\client.jar")

HONEY = (222, 178, 104)

# name, base cake's block textures prefix, English, Chinese
HONEY_CAKES = (
    ("honey_cake", None, "Honey Cake", "蜂蜜蛋糕"),
    ("honey_chocolate_cake", "chocolate_cake", "Honey Chocolate Cake", "蜂蜜巧克力蛋糕"),
    ("honey_dark_chocolate_cake", "dark_chocolate_cake", "Honey Dark Chocolate Cake",
     "蜂蜜黑巧克力蛋糕"),
    ("honey_white_chocolate_cake", "white_chocolate_cake", "Honey White Chocolate Cake",
     "蜂蜜白巧克力蛋糕"),
)

# name, source icon in our assets (None means vanilla's cake icon), English, Chinese
SLICES = (
    ("cake_slice", None, "Cake Slice", "蛋糕片"),
    ("chocolate_cake_slice", "chocolate_cake", "Chocolate Cake Slice", "巧克力蛋糕片"),
    ("dark_chocolate_cake_slice", "dark_chocolate_cake", "Dark Chocolate Cake Slice", "黑巧克力蛋糕片"),
    ("white_chocolate_cake_slice", "white_chocolate_cake", "White Chocolate Cake Slice",
     "白巧克力蛋糕片"),
    ("honey_cake_slice", "honey_cake", "Honey Cake Slice", "蜂蜜蛋糕片"),
    ("honey_chocolate_cake_slice", "honey_chocolate_cake", "Honey Chocolate Cake Slice",
     "蜂蜜巧克力蛋糕片"),
    ("honey_dark_chocolate_cake_slice", "honey_dark_chocolate_cake",
     "Honey Dark Chocolate Cake Slice", "蜂蜜黑巧克力蛋糕片"),
    ("honey_white_chocolate_cake_slice", "honey_white_chocolate_cake",
     "Honey White Chocolate Cake Slice", "蜂蜜白巧克力蛋糕片"),
)

PARTS = ("cake_top", "cake_side", "cake_bottom", "cake_inner")
VANILLA_MODELS = ("cake", "cake_slice1", "cake_slice2", "cake_slice3", "cake_slice4",
                  "cake_slice5", "cake_slice6")

# The slice: a wedge with a vertical right edge and the point to the lower left, the way a slice of cake
# looks when it is standing up. The icing is the top row and a half, the body below it.
SLICE_TOP = 4
SLICE_ROWS = 9


def write_json(path, payload):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as fh:
        json.dump(payload, fh, indent=2, ensure_ascii=False, sort_keys=True)
        fh.write("\n")


def honeyed(image):
    """The cake with honey over it: colours pulled towards honey, alpha and shading kept."""
    out = image.copy()
    # A glaze, not a coat of paint: the cake keeps most of its own colour and gains a warm honey tone.
    out[:, :, :3] = np.clip(image[:, :, :3] * 0.62 + np.array(HONEY)[None, None, :] * 0.38, 0, 255)
    return out


def cake_colours(image):
    """The icing and the body of a cake, taken from its own icon: its brightest and darkest quarters."""
    visible = image[:, :, 3] > 0
    pixels = image[:, :, :3][visible]
    if len(pixels) == 0:
        return (240, 240, 240), (190, 160, 130)
    luminance = 0.2126 * pixels[:, 0] + 0.7152 * pixels[:, 1] + 0.0722 * pixels[:, 2]
    order = np.argsort(luminance)
    # the body is the middle-dark band, not the darkest quarter: for vanilla cake the darkest pixels are
    # its jam band, and a slice of plain cake should be cream rather than red
    low = len(order) // 5
    high = max(low + 1, len(order) * 9 // 20)
    darkest = pixels[order[low:high]].mean(axis=0)
    brightest = pixels[order[-max(1, len(order) // 4):]].mean(axis=0)
    return brightest, darkest


def wedge(image):
    """Draws a slice of the cake: a wedge in the cake's icing and body colours."""
    icing, body = cake_colours(image)
    out = np.zeros_like(image)
    for index in range(SLICE_ROWS):
        y = SLICE_TOP + index
        # the wedge narrows as it goes down, with its right edge straight
        span = SLICE_ROWS - index
        left, right = 13 - span, 13
        shading = 1.0 - 0.20 * (index / max(SLICE_ROWS - 1, 1))
        for x in range(left, right + 1):
            # the icing runs along the top edge, the body fills the rest
            colour = icing if index <= 1 else body
            # a little depth at the left edge of each row
            if x == left:
                colour = np.array(colour) * 0.82
            out[y, x, :3] = np.clip(np.array(colour) * shading, 0, 255)
            out[y, x, 3] = 255
    return out


def load_icon(path, jar):
    if path is None:
        with jar.open("assets/minecraft/textures/item/cake.png") as fh:
            return np.asarray(Image.open(io.BytesIO(fh.read())).convert("RGBA")).astype(float)
    with open(path, "rb") as fh:
        return np.asarray(Image.open(io.BytesIO(fh.read())).convert("RGBA")).astype(float)


def copy_art():
    """Puts the hand-drawn textures in place.

    They live in tools/art and are copied here rather than generated, because generate_resources.py
    clears the texture directories before asking for anything to be drawn.
    """
    art = os.path.join(os.path.dirname(os.path.abspath(__file__)), "art")
    copied = 0
    for entry in sorted(os.listdir(art)):
        if not entry.endswith(".png") or "__" not in entry:
            continue
        folder, name = entry.split("__", 1)
        target = os.path.join(ASSETS, "textures", folder, name)
        os.makedirs(os.path.dirname(target), exist_ok=True)
        shutil.copyfile(os.path.join(art, entry), target)
        copied += 1
    print("  cake textures: %d hand-drawn files copied in" % copied)


def main():
    copy_art()
    blockstates = os.path.join(ASSETS, "blockstates")
    block_models = os.path.join(ASSETS, "models", "block")
    item_models = os.path.join(ASSETS, "models", "item")
    block_textures = os.path.join(ASSETS, "textures", "block")
    item_textures = os.path.join(ASSETS, "textures", "item")

    with zipfile.ZipFile(VANILLA) as jar:
        for name, base, english, chinese in HONEY_CAKES:
            textures = {
                "top": "aoaagriculture:block/%s_cake_top" % name,
                "side": "aoaagriculture:block/%s_cake_side" % name,
                "bottom": "aoaagriculture:block/%s_cake_bottom" % name,
                "inside": "aoaagriculture:block/%s_cake_inner" % name,
            }
            for model in VANILLA_MODELS:
                write_json(os.path.join(block_models, "%s_%s.json" % (name, model)),
                           {"parent": "minecraft:block/" + model, "textures": textures})
            write_json(os.path.join(item_models, name + ".json"),
                       {"parent": "item/generated",
                        "textures": {"layer0": "aoaagriculture:item/" + name}})
            variants = {}
            for bites, model in enumerate(["cake"] + ["cake_slice%d" % i for i in range(1, 7)]):
                variants["bites=%d" % bites] = {
                    "model": "aoaagriculture:block/%s_%s" % (name, model)}
            write_json(os.path.join(blockstates, name + ".json"), {"variants": variants})

    # the slices' item models
    for name, _source, _english, _chinese in SLICES:
        write_json(os.path.join(item_models, name + ".json"),
                   {"parent": "item/generated",
                    "textures": {"layer0": "aoaagriculture:item/" + name}})

    # honey cakes: a bottle of honey over a cake, and the bottle comes back
    for name, base, english, chinese in HONEY_CAKES:
        cake = "minecraft:cake" if base is None else "aoaagriculture:" + base
        write_json(os.path.join(DATA, "aoaagriculture", "recipes", name + ".json"), {
            "type": "minecraft:crafting_shapeless",
            "ingredients": [{"item": "minecraft:honey_bottle"}, {"item": cake}],
            "result": {"item": "aoaagriculture:" + name},
        })

    # slices: seven from a cake, by hand and on a cutting board
    for name, source, english, chinese in SLICES:
        cake = ("minecraft:cake" if source is None else "aoaagriculture:" + source)
        write_json(os.path.join(DATA, "aoaagriculture", "recipes", name + "_from_cake.json"), {
            "type": "minecraft:crafting_shapeless",
            "ingredients": [{"item": cake}],
            "result": {"item": "aoaagriculture:" + name, "count": 7},
        })
        write_json(os.path.join(DATA, "farmersdelight", "recipes", "cutting",
                                name + "_from_cake.json"), {
            "type": "farmersdelight:cutting",
            "ingredients": [{"item": cake}],
            "tool": {"tag": "forge:tools/knives"},
            "result": [{"item": "aoaagriculture:" + name, "count": 7}],
        })

    for language, english_index in (("en_us", 2), ("zh_cn", 3)):
        path = os.path.join(ASSETS, "lang", language + ".json")
        with open(path, encoding="utf-8") as fh:
            table = json.load(fh)
        for name, _base, english, chinese in HONEY_CAKES:
            table["block.aoaagriculture." + name] = (english, chinese)[english_index - 2]
            table["item.aoaagriculture." + name] = (english, chinese)[english_index - 2]
        for name, _source, english, chinese in SLICES:
            table["item.aoaagriculture." + name] = (english, chinese)[english_index - 2]
        with open(path, "w", encoding="utf-8", newline="\n") as fh:
            json.dump(table, fh, indent=2, ensure_ascii=False, sort_keys=True)
            fh.write("\n")

    print("  cake family: 4 honey cakes, 8 slices, 20 recipes (textures are hand-drawn)")


if __name__ == "__main__":
    main()
