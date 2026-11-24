"""Sashimi: one raw AoA fish into four slices, by hand or on Farmer's Delight's cutting board.

Textures come from the supplied pack at 32 by 32 - Minecraft scales them onto the item quad, so the finer
art is kept. Models, names and both recipes are generated here; the textures live in tools/art and are
copied in by the cake family's copy step.
"""
import io
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "aoaagriculture")
DATA = os.path.join(ROOT, "src", "main", "resources", "data")

# fish, ingredient (AoA item), English fish, Chinese fish
FISH = (
    ("rainbowfish", "raw_rainbowfish", "Rainbowfish", "彩虹鱼"),
    ("sailback", "sailback", "Sailback", "帆背鱼"),
    ("violet_skipper", "violet_skipper", "Violet Skipper", "紫鱼"),
    ("crimson_skipper", "crimson_skipper", "Crimson Skipper", "红鱼"),
    ("candlefish", "candlefish", "Candlefish", "烛鱼"),
    ("charred_char", "charred_char", "Charred Char", "焦焦鱼"),
    ("crimson_stripefish", "crimson_stripefish", "Crimson Stripefish", "深红条纹鱼"),
    ("dark_hatchetfish", "dark_hatchetfish", "Dark Hatchetfish", "暗斧鱼"),
    ("chocaw", "chocaw", "Chocaw", "秋扣鱼"),
    ("parapiranha", "parapiranha", "Parapiranha", "帕拉痞拉"),
    ("jamfish", "jamfish", "Jamfish", "果酱鱼"),
    ("sapphire_strider", "sapphire_strider", "Sapphire Strider", "蔚蓝条纹鱼"),
    ("ironback", "ironback", "Ironback", "铁背鱼"),
    ("pearl_stripefish", "pearl_stripefish", "Pearl Stripefish", "珍珠条纹鱼"),
    ("razorfish", "razorfish", "Razorfish", "剃刀鱼"),
    ("reeftooth", "reeftooth", "Reeftooth", "礁齿鱼"),
    ("rocketfish", "rocketfish", "Rocketfish", "箭鱼"),
    ("skelecanth", "skelecanth", "Skelecanth", "骷髅鱼"),
    ("turquoise_stripefish", "turquoise_stripefish", "Turquoise Stripefish", "绿松条纹鱼"),
    ("blue_gemtrap", "blue_gemtrap", "Blue Gemtrap", "蓝宝石陷阱鱼"),
    ("green_gemtrap", "green_gemtrap", "Green Gemtrap", "绿宝石陷阱鱼"),
    ("purple_gemtrap", "purple_gemtrap", "Purple Gemtrap", "紫宝石陷阱鱼"),
    ("red_gemtrap", "red_gemtrap", "Red Gemtrap", "红宝石陷阱鱼"),
    ("white_gemtrap", "white_gemtrap", "White Gemtrap", "白宝石陷阱鱼"),
    ("yellow_gemtrap", "yellow_gemtrap", "Yellow Gemtrap", "黄宝石陷阱鱼"),
)


def write_json(path, payload):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as fh:
        json.dump(payload, fh, indent=2, ensure_ascii=False, sort_keys=True)
        fh.write("\n")


def main():
    items = os.path.join(ASSETS, "models", "item")

    names = {}
    for fish, ingredient, english, chinese in FISH:
        name = fish + "_slice"
        write_json(os.path.join(items, name + ".json"),
                   {"parent": "item/generated",
                    "textures": {"layer0": "aoaagriculture:item/" + name}})

        names["item.aoaagriculture." + name] = (english + " Sashimi", chinese + "生鱼片")

        # by hand: one fish, four slices
        write_json(os.path.join(DATA, "aoaagriculture", "recipes", name + ".json"), {
            "type": "minecraft:crafting_shapeless",
            "ingredients": [{"item": "aoa3:" + ingredient}],
            "result": {"item": "aoaagriculture:" + name, "count": 4},
        })

        # Farmer's Delight: the same cut, on the cutting board with any knife
        write_json(os.path.join(DATA, "farmersdelight", "recipes", "cutting",
                                name + ".json"), {
            "type": "farmersdelight:cutting",
            "ingredients": [{"item": "aoa3:" + ingredient}],
            "tool": {"tag": "forge:tools/knives"},
            "result": [{"item": "aoaagriculture:" + name, "count": 4}],
        })

    for language, index in (("en_us", 0), ("zh_cn", 1)):
        path = os.path.join(ASSETS, "lang", language + ".json")
        with open(path, encoding="utf-8") as fh:
            table = json.load(fh)
        for key, values in names.items():
            table[key] = values[index]
        with open(path, "w", encoding="utf-8", newline="\n") as fh:
            json.dump(table, fh, indent=2, ensure_ascii=False, sort_keys=True)
            fh.write("\n")

    print("  sashimi: %d models, %d recipes, %d names" % (len(FISH), len(FISH) * 2, len(FISH)))


if __name__ == "__main__":
    main()
