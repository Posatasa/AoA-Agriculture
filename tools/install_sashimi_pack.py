"""Installs the sashimi asset pack.

The pack's 32x textures already carry their transparent border, so they are taken as they are; the 16x set
and the preview are skipped. Each file becomes `item__<name>.png`, which is how tools/art keeps the item
tree apart in one directory.
"""
import io
import os
import sys
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ART = os.path.join(ROOT, "tools", "art")


def main(source):
    with zipfile.ZipFile(source) as pack:
        names = pack.namelist()

        slices = [n for n in names if n.endswith(".png") and "/" not in n and not n.startswith("preview")]

        removed = 0
        for entry in os.listdir(ART):
            if entry.startswith("item__") and entry.endswith("_slice.png") and entry not in {
                    "item__cake_slice.png", "item__chocolate_cake_slice.png",
                    "item__dark_chocolate_cake_slice.png", "item__white_chocolate_cake_slice.png",
                    "item__honey_cake_slice.png", "item__honey_chocolate_cake_slice.png",
                    "item__honey_dark_chocolate_cake_slice.png",
                    "item__honey_white_chocolate_cake_slice.png"}:
                os.remove(os.path.join(ART, entry))
                removed += 1

        count = 0
        for name in slices:
            file_name = name.rsplit("/", 1)[-1]
            with open(os.path.join(ART, "item__" + file_name), "wb") as fh:
                fh.write(pack.read(name))
            count += 1
    print("installed %d sashimi textures as-is; removed %d stale" % (count, removed))


if __name__ == "__main__":
    main(sys.argv[1])
