"""Installs the cake asset pack into tools/art, from where the generator copies it into the mod.

The pack is laid out exactly as the mod's assets are - `block/` and `item/` - so this only renames each
file to `<folder>__<name>.png`, which is how tools/art keeps the two trees apart in one directory.
"""
import io
import os
import sys
import zipfile

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ART = os.path.join(ROOT, "tools", "art")


def main(source):
    with zipfile.ZipFile(source) as pack:
        for entry in os.listdir(ART):
            if entry.endswith(".png") and "__" in entry:
                os.remove(os.path.join(ART, entry))
        count = 0
        sizes = set()
        for name in pack.namelist():
            if not name.endswith(".png"):
                continue
            folder, _, file_name = name.partition("/")
            if folder not in ("block", "item") or not file_name:
                continue
            data = pack.read(name)
            sizes.add(Image.open(io.BytesIO(data)).size)
            with open(os.path.join(ART, "%s__%s" % (folder, file_name)), "wb") as fh:
                fh.write(data)
            count += 1
    print("installed %d textures from %s; sizes %s" % (count, os.path.basename(source), sizes))


if __name__ == "__main__":
    main(sys.argv[1])
