"""Chocolate sauces: icons, models, names, and both sets of recipes."""
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

# name, AoA block, English, Chinese, soup colour
SAUCES = (
    ("chocolate_sauce", "aoa3:chocolate_block", "Chocolate Sauce", "巧克力酱", (94, 56, 32)),
    ("dark_chocolate_sauce", "aoa3:dark_chocolate_block", "Dark Chocolate Sauce", "黑巧克力酱",
     (52, 32, 24)),
    ("white_chocolate_sauce", "aoa3:white_chocolate_block", "White Chocolate Sauce", "白巧克力酱",
     (232, 214, 178)),
)


def write_json(path, payload):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as fh:
        json.dump(payload, fh, indent=2, ensure_ascii=False, sort_keys=True)
        fh.write("\n")


def main():
    with zipfile.ZipFile(VANILLA) as jar:
        with jar.open("assets/minecraft/textures/item/mushroom_stew.png") as fh:
            stew = np.asarray(Image.open(io.BytesIO(fh.read())).convert("RGBA")).astype(float)

    textures = os.path.join(ASSETS, "textures", "item")
    models = os.path.join(ASSETS, "models", "item")
    os.makedirs(textures, exist_ok=True)

    # The soup is the saturated part of the icon, and the bowl is the desaturated rest: keeping the
    # vanilla bowl's own pixels is what makes these read as stew in a bowl rather than as a brown blob.
    rgb = stew[:, :, :3]
    alpha = stew[:, :, 3]
    top, bottom = rgb.max(axis=2), rgb.min(axis=2)
    saturation = np.where(top > 0, (top - bottom) / np.maximum(top, 1), 0)
    soup = (alpha > 0) & (saturation >= 0.20)
    luminance = 0.2126 * rgb[:, :, 0] + 0.7152 * rgb[:, :, 1] + 0.0722 * rgb[:, :, 2]
    reference = luminance[soup].mean()

    for name, block, english, chinese, colour in SAUCES:
        out = stew.copy()
        out[:, :, :3][soup] = np.clip(np.array(colour)[None, :]
                                      * (luminance[soup] / reference)[:, None], 0, 255)
        Image.fromarray(np.clip(np.rint(out), 0, 255).astype(np.uint8)).save(
            os.path.join(textures, name + ".png"))
        write_json(os.path.join(models, name + ".json"),
                   {"parent": "item/generated",
                    "textures": {"layer0": "aoaagriculture:item/" + name}})

        # three blocks and a bowl in the crafting grid
        write_json(os.path.join(DATA, "aoaagriculture", "recipes", name + ".json"), {
            "type": "minecraft:crafting_shaped",
            "pattern": ["   ", "CCC", " B "],
            "key": {
                "C": {"item": block},
                "B": {"item": "minecraft:bowl"},
            },
            "result": {"item": "aoaagriculture:" + name},
        })

        # two blocks in Farmer's Delight's cooking pot, taken out with a bowl: eight seconds
        write_json(os.path.join(DATA, "farmersdelight", "recipes", "cooking",
                                name + ".json"), {
            "type": "farmersdelight:cooking",
            "ingredients": [{"item": block}, {"item": block}],
            "result": {"item": "aoaagriculture:" + name},
            "container": {"item": "minecraft:bowl"},
            "cookingtime": 160,
            "experience": 0.35,
        })

    for language, index in (("en_us", 2), ("zh_cn", 3)):
        path = os.path.join(ASSETS, "lang", language + ".json")
        with open(path, encoding="utf-8") as fh:
            table = json.load(fh)
        for name, _block, english, chinese, _colour in SAUCES:
            table["item.aoaagriculture." + name] = (english, chinese)[index - 2]
        with open(path, "w", encoding="utf-8", newline="\n") as fh:
            json.dump(table, fh, indent=2, ensure_ascii=False, sort_keys=True)
            fh.write("\n")

    print("  chocolate sauces: 3 icons, 3 models, 3 crafting recipes, 3 cooking pot recipes")


if __name__ == "__main__":
    main()
