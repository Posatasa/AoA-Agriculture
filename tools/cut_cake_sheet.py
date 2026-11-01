"""Cuts the supplied cake sheet into the mod's textures.

Each row of the sheet is one cake, five columns: top face, side, cut face, bottom face, inventory icon.
The last band is the row of slices. The sheet's background is removed so the textures are transparent
where it showed through.
"""
import io
import os
import shutil

import numpy as np
from PIL import Image

SHEET = r"C:\Users\admin\Documents\GitHub\.cache\cake_sheet.png"
ROOT = r"C:\Users\admin\Documents\GitHub\AoA-3.6.11-Dirt-Farmland-Addon"
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "aoaagriculture")
ART = os.path.join(ROOT, "tools", "art")
PREVIEW = r"C:\Users\admin\Documents\GitHub\.cache\texdump\sheet_extract.png"

CAKES = ("chocolate_cake", "dark_chocolate_cake", "white_chocolate_cake", "honey_cake",
         "honey_chocolate_cake", "honey_dark_chocolate_cake", "honey_white_chocolate_cake")
SLICES = ("cake_slice", "chocolate_cake_slice", "dark_chocolate_cake_slice",
          "white_chocolate_cake_slice", "honey_cake_slice", "honey_chocolate_cake_slice",
          "honey_dark_chocolate_cake_slice", "honey_white_chocolate_cake_slice")

# column order on the sheet
COLUMNS = ("cake_top", "cake_side", "cake_inner", "cake_bottom", None)


def bands(profile):
    """Runs of True in a one-dimensional profile, as (start, end) inclusive."""
    out, start = [], None
    for index, value in enumerate(profile):
        if value and start is None:
            start = index
        elif not value and start is not None:
            out.append((start, index - 1))
            start = None
    if start is not None:
        out.append((start, len(profile) - 1))
    return out


def strip_background(image):
    """Clears the sheet's background, keeping any pixel that is not connected to the border."""
    data = np.asarray(image.convert("RGB")).astype(int)
    colours, counts = np.unique(data.reshape(-1, 3), axis=0, return_counts=True)
    background = colours[counts.argmax()]
    similar = np.abs(data - background).max(axis=2) <= 14

    height, width = similar.shape
    reached = np.zeros_like(similar)
    stack = [(x, 0) for x in range(width)] + [(x, height - 1) for x in range(width)]
    stack += [(0, y) for y in range(height)] + [(width - 1, y) for y in range(height)]
    while stack:
        x, y = stack.pop()
        if x < 0 or y < 0 or x >= width or y >= height or reached[y, x] or not similar[y, x]:
            continue
        reached[y, x] = True
        stack.extend(((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)))

    rgba = np.asarray(image.convert("RGBA")).copy()
    rgba[:, :, 3][reached] = 0
    return Image.fromarray(rgba, "RGBA"), ~reached


def main():
    image, content = strip_background(Image.open(SHEET))

    rows = [band for band in bands(content.any(axis=1)) if band[1] - band[0] > 30]
    print("cake rows:", len(rows))

    os.makedirs(ART, exist_ok=True)
    extracted = []

    def save(crop, folder, name):
        art = crop.resize((16, 16), Image.BOX)
        target = os.path.join(ART, "%s__%s.png" % (folder, name))
        art.save(target)
        extracted.append((name, art))
        return target

    for cake, (top, bottom) in zip(CAKES, rows[:-1]):
        strip = content[top:bottom + 1]
        columns = [band for band in bands(strip.any(axis=0)) if band[1] - band[0] > 20]
        assert len(columns) == 5, "%s has %d columns" % (cake, len(columns))
        for (left, right), suffix in zip(columns, COLUMNS):
            crop = image.crop((left, top, right + 1, bottom + 1))
            if suffix is None:
                save(crop, "item", cake)
            else:
                save(crop, "block", "%s_%s" % (cake, suffix))

    slice_band = rows[-1]
    strip = content[slice_band[0]:slice_band[1] + 1]
    columns = [band for band in bands(strip.any(axis=0)) if band[1] - band[0] > 20]
    print("slices:", len(columns))
    for name, (left, right) in zip(SLICES, columns):
        crop = image.crop((left, slice_band[0], right + 1, slice_band[1] + 1))
        save(crop, "item", name)

    # the generator copies from here, so the assets are refreshed as well
    for entry in sorted(os.listdir(ART)):
        if not entry.endswith(".png") or "__" not in entry:
            continue
        folder, name = entry.split("__", 1)
        target = os.path.join(ASSETS, "textures", folder, name)
        os.makedirs(os.path.dirname(target), exist_ok=True)
        shutil.copyfile(os.path.join(ART, entry), target)

    scale = 7
    sheet = Image.new("RGBA", (16 * scale * 8 + 7 * 4, 16 * scale * 5 + 4 * 4), (30, 30, 34, 255))
    for index, (name, art) in enumerate(extracted):
        column, row = index % 8, index // 8
        big = art.resize((16 * scale, 16 * scale), Image.NEAREST)
        sheet.paste(big, (column * (16 * scale + 4), row * (16 * scale + 4)), big)
    sheet.save(PREVIEW)
    print("extracted %d textures, preview at %s" % (len(extracted), PREVIEW))


if __name__ == "__main__":
    main()
