# Project state

A short map for whoever picks this up next, including me after the conversation has been trimmed.
Read this first; only then open code.

## What it is

An add-on for Advent of Ascension 3 **1.16.5-3.6.11**. Mod id `aoaagriculture`, package
`com.posatasa.aoaagriculture`, jar `aoaagriculture-<version>.jar`. Open source add-on only: no AoA code
or assets are copied, and AoA is only ever reached by registry name or by reflection.

## Build and check

```
python tools/build.py           # compiles against the MCP-named Forge jar, remaps to SRG, writes dist/
python tools/verify_link.py     # links every class against the SRG jar, checks listeners
python tools/generate_resources.py   # regenerates every texture, model, blockstate, lang, recipe
```

`build.py` is also the audit: it checks AoA names, SRG renames, the property chain, the four ways
farmland reverts, the bucket rules, the mixin refmap, resources, optional integrations, and that the jar
is reproducible. Deliberate mutations should fail it - if a check can be made to pass by breaking the
code, the check is wrong.

Python with Pillow for the generator: `C:\Users\admin\.dsh\dsh-runtimes\dsh-primary-runtime\dependencies\python\python.exe`.

## Where things live

| Area | Class |
| --- | --- |
| Mod entry, registration calls | `AoAAgriculture` |
| Farmland blocks, 22 of them, soil order, hoe + fertilise + reversions | `registry/FarmlandBlocks` |
| Creative tabs (farmland / buckets / food) | `registry/Tabs` |
| Bucket families and every bucket registration | `registry/Buckets` |
| Bucket behaviour: pour, fill, cauldron, lazy fluids | `item/AoABucketItem`, `item/LazyFluidBucketItem`, `util/LazyPour` |
| Resource buckets (Spirit, Energy) | `item/ResourceRefillBucketItem`, `compat/AoAResourceRefill` |
| Infinite buckets (Shyre water, Dark lava) | `item/InfiniteBucketItem` |
| Lava tanks (Emberstone 4, Blazium 8) | `item/LavaTankBucketItem`, `item/LavaOnlyBucketItem` |
| Bottles (candied water, chilli water) | `registry/Bottles`, `item/CandiedWaterBottleItem`, `item/ChilliWaterBottleItem` |
| Chilli water: fluid, block, cauldron, items | `registry/ChilliWater`, `fluid/ChilliWaterFluid`, `block/ChilliWaterCauldronBlock` |
| Entity heat of chilli water | `mixin/MixinEntityChilliWater` |
| AoA skill experience | `compat/AoASkillExperience` |
| Petal station rituals | `ritual/*` |
| Client render layers | `client/ClientSetup` |
| Resource generation | `tools/generate_resources.py`, `tools/generate_chilli_water.py` |

## Rules that have bitten before

* **Registration order is blocks, items, then everything else alphabetically** - so fluids register
  *after* items. Anything holding another mod's fluid, or our own, must resolve it lazily.
* **Vanilla names its own items by identity** (`Items.WATER_BUCKET` in a cauldron, `Items.BUCKET` for a
  bucket's empty form). Every place our items meet those needs its own handling.
* **A cauldron is not an `ILiquidContainer`**; it has `LEVEL` and `setWaterLevel`.
* **Fluids render through the *fluid's* render layer**, not the block's: `RenderTypeLookup.setRenderLayer(Fluid, ...)`.
* **AoA's fluid blocks are built from the flowing fluid**, so `takeLiquid` returns the flowing one and
  `FluidState.isSource()` is false even for a full block. Compare sources, not identities.
* **AoA puts candied water in `minecraft:water`**, so vanilla's bottle and water logic treat it as water.
* **A completed use action goes through `finishUsingItem`**, not `releaseUsing`; `use` must call
  `startUsingItem` itself.
* Never let a vanilla item appear where ours should (`minecraft:bucket`, `minecraft:dirt`).

## Conventions

* Changelogs: one file per release in `changelogs/`, terse and factual; `changelogs/README.md` is the index.
* Version in `gradle.properties`; `dist/` holds the built jar and `SHA256SUMS.txt`.
* Textures: vanilla patterns, recoloured from AoA palettes; only colours are taken from AoA, never pixels.
* Farmer's Delight is optional and must never be linked (checked by the build).
